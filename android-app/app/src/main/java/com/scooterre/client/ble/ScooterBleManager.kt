package com.scooterre.client.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Thin coroutine-friendly wrapper around Android's callback-based BluetoothGatt API.
 * GATT operations must be serialized (only one outstanding request at a time) - callers
 * are responsible for awaiting each suspend call before issuing the next one, matching
 * how the reference Rust implementation (btleplug) also serializes access.
 */
class ScooterBleManager(private val context: Context) {

    data class CharacteristicUpdate(val serviceUuid: UUID, val characteristicUuid: UUID, val value: ByteArray)

    private var gatt: BluetoothGatt? = null

    private val notifications = MutableSharedFlow<CharacteristicUpdate>(extraBufferCapacity = 64)
    val notificationFlow: SharedFlow<CharacteristicUpdate> = notifications

    // Some BLE stacks (confirmed live on MIUI) silently drop an established connection without
    // ever firing onConnectionStateChange(DISCONNECTED) - the app then has no other way to notice
    // than a GATT operation suddenly failing to even start. That used to be invisible: callers just
    // kept retrying the same doomed request forever (each one burning its full timeout), the UI sat
    // on the last values it ever successfully read, and the user had no way to tell the connection
    // was dead versus just slow. A real, explicit disconnect (see onConnectionStateChange below) is
    // unambiguous and emits immediately; an immediate "did not even start" write failure is not -
    // it can also be a one-off GATT-busy hiccup - so that path only counts after
    // [HARD_FAILURE_THRESHOLD] CONSECUTIVE such failures (any successful write resets the count),
    // and even each individual failure gets one quick retry first. Callers only need to watch this
    // one flow either way.
    private val _connectionLost = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val connectionLost: SharedFlow<Unit> = _connectionLost
    private var consecutiveHardFailures = 0

    private var connectDeferred: CompletableDeferred<Boolean>? = null
    private var servicesDeferred: CompletableDeferred<Boolean>? = null
    private var writeDeferred: CompletableDeferred<Boolean>? = null
    private var descriptorDeferred: CompletableDeferred<Boolean>? = null
    private var readDeferred: CompletableDeferred<ByteArray?>? = null
    private var mtuDeferred: CompletableDeferred<Int>? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            android.util.Log.d(TAG, "onConnectionStateChange status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> connectDeferred?.complete(true)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectDeferred?.complete(false)
                    // Only while gatt is still set: during connect() itself (before gatt is ever
                    // considered "up" for callers) or after our own disconnect() already cleared
                    // it, this transition is expected and not a connection loss to report.
                    if (gatt != null) _connectionLost.tryEmit(Unit)
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            servicesDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            android.util.Log.d(TAG, "onMtuChanged mtu=$mtu status=$status")
            mtuDeferred?.complete(if (status == BluetoothGatt.GATT_SUCCESS) mtu else -1)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            android.util.Log.d(TAG, "onCharacteristicWrite ${characteristic.uuid} status=$status")
            writeDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            descriptorDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION")
            val value = characteristic.value
            readDeferred?.complete(if (status == BluetoothGatt.GATT_SUCCESS) value else null)
        }

        // Deprecated (API<33) overload: reads the characteristic's shared, mutable `.value`
        // buffer, which is a known race on some Android/vendor BLE stacks when notifications on
        // the same characteristic arrive close together - the next notification can overwrite
        // that buffer before this callback finishes reading it. Only relied on below API 33.
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (android.os.Build.VERSION.SDK_INT >= 33) return // handled by the (value: ByteArray) overload below instead
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: ByteArray(0)
            handleNotification(characteristic, value)
        }

        // API 33+ overload: the framework hands us our OWN copy of the value, immune to the
        // race above - this is the diagnostic fix being tried for the never-acked pubkey frame.
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleNotification(characteristic, value)
        }

        private fun handleNotification(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val serviceUuid = characteristic.service?.uuid ?: return
            android.util.Log.d(TAG, "<<< notify ${characteristic.uuid} (${value.size}B) ${value.joinToString(" ") { "%02x".format(it) }}")
            notifications.tryEmit(CharacteristicUpdate(serviceUuid, characteristic.uuid, value))
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean {
        android.util.Log.d(TAG, "connect() calling connectGatt for ${device.address}")
        connectDeferred = CompletableDeferred()
        gatt = try {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "connectGatt threw", e)
            throw e
        }
        android.util.Log.d(TAG, "connectGatt returned $gatt, awaiting connection result ...")
        // A just-killed previous process can leave the OS Bluetooth stack in a state where this
        // callback never fires at all for the first attempt right after - without a bound here
        // that hangs the coroutine forever instead of failing fast enough for a caller to retry.
        // 8s, not longer: a real connection consistently completes within ~5s live against the
        // actual scooter - 10s just made every failed/retried attempt (3 of them, back to back)
        // take needlessly long before the user saw any feedback at all.
        val result = withTimeoutOrNull(8_000L) { connectDeferred!!.await() } ?: false
        android.util.Log.d(TAG, "connect() result=$result")
        if (result) {
            val prioritySet = gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) ?: false
            android.util.Log.d(TAG, "requestConnectionPriority(HIGH) -> $prioritySet")
        }
        return result
    }

    private companion object {
        const val TAG = "ScooterBle"

        // A single immediate write failure is ambiguous (see connectionLost's doc comment) - this
        // many IN A ROW, each having already had its own quick retry, is not: every property in a
        // sweep failing the same way is what a truly dead link looks like, not a one-off hiccup.
        const val HARD_FAILURE_THRESHOLD = 3
    }

    /** Negotiates a larger ATT MTU (device reports 247 in the reference dump; the default
     * Android connection MTU of 23 is apparently too small for the scooter to accept the
     * channel-transport traffic at all). Returns the MTU the device actually granted, or -1. */
    @SuppressLint("MissingPermission")
    suspend fun requestMtu(mtu: Int): Int {
        val g = gatt ?: return -1
        mtuDeferred = CompletableDeferred()
        if (!g.requestMtu(mtu)) return -1
        return mtuDeferred!!.await()
    }

    @SuppressLint("MissingPermission")
    suspend fun discoverServices(): Boolean {
        servicesDeferred = CompletableDeferred()
        val ok = gatt?.discoverServices() ?: false
        if (!ok) return false
        return servicesDeferred!!.await()
    }

    fun findCharacteristic(serviceUuid: UUID, charUuid: UUID): BluetoothGattCharacteristic? =
        gatt?.getService(serviceUuid)?.getCharacteristic(charUuid)

    /** Diagnostic dump of every service/characteristic actually present on the connected
     * device - used to verify (or correct) the assumed UUIDs from GattConstants.kt. */
    fun dumpServices(): String {
        val g = gatt ?: return "(not connected)"
        val sb = StringBuilder()
        for (service in g.services) {
            sb.append("Service ").append(service.uuid).append('\n')
            for (ch in service.characteristics) {
                val props = mutableListOf<String>()
                if (ch.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) props += "READ"
                if (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) props += "WRITE"
                if (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) props += "WRITE_NR"
                if (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) props += "NOTIFY"
                if (ch.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) props += "INDICATE"
                sb.append("  Char ").append(ch.uuid).append(" [").append(props.joinToString(",")).append("]\n")
            }
        }
        return sb.toString()
    }

    @SuppressLint("MissingPermission")
    suspend fun enableNotifications(characteristic: BluetoothGattCharacteristic): Boolean {
        val g = gatt ?: return false
        if (!g.setCharacteristicNotification(characteristic, true)) return false

        val cccd = characteristic.getDescriptor(Registers.CLIENT_CHARACTERISTIC_CONFIG) ?: return false
        descriptorDeferred = CompletableDeferred()

        @Suppress("DEPRECATION")
        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        @Suppress("DEPRECATION")
        val started = g.writeDescriptor(cccd)
        if (!started) return false
        return descriptorDeferred!!.await()
    }

    /**
     * `withResponse=null` (the default) auto-detects the write type from the characteristic's
     * actual declared properties: some of this device's characteristics only advertise WRITE
     * (with response), not WRITE_NO_RESPONSE (e.g. the 0x0017 "command" characteristic) - writing
     * without response to those fails silently at the GATT layer (the scooter never reacts).
     * The reference implementation always uses WriteType::WithoutResponse because its target
     * device's characteristics all support it; this one apparently doesn't uniformly.
     */
    @SuppressLint("MissingPermission")
    suspend fun write(characteristic: BluetoothGattCharacteristic, data: ByteArray, withResponse: Boolean? = null): Boolean {
        val g = gatt ?: return false
        writeDeferred = CompletableDeferred()

        val useResponse = withResponse ?: run {
            val supportsNoResponse = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            !supportsNoResponse
        }
        characteristic.writeType = if (useResponse)
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        else
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

        android.util.Log.d(TAG, ">>> write ${characteristic.uuid} withResponse=$useResponse (${data.size}B) ${data.joinToString(" ") { "%02x".format(it) }}")
        @Suppress("DEPRECATION")
        characteristic.value = data
        @Suppress("DEPRECATION")
        var started = g.writeCharacteristic(characteristic)
        if (!started) {
            // Often just the GATT stack still busy with the previous operation for a moment
            // (despite our own request mutex serializing calls, the OS/HAL layer can still show
            // this) - one short, quiet retry clears most of these without ever being visible as a
            // failure at all.
            delay(150)
            @Suppress("DEPRECATION")
            started = g.writeCharacteristic(characteristic)
        }
        if (!started) {
            consecutiveHardFailures++
            android.util.Log.e(TAG, "writeCharacteristic() returned false (did not even start), $consecutiveHardFailures in a row")
            // Only after several of these IN A ROW: a single one (even after its own retry above)
            // can still be a one-off hiccup, but a characteristic that worked moments ago
            // consistently refusing to even start a write is what a silently-dropped connection
            // (see connectionLost's doc comment) looks like.
            if (consecutiveHardFailures >= HARD_FAILURE_THRESHOLD) _connectionLost.tryEmit(Unit)
            return false
        }
        consecutiveHardFailures = 0
        val ok = writeDeferred!!.await()
        android.util.Log.d(TAG, "write ${characteristic.uuid} completed ok=$ok")
        return ok
    }

    @SuppressLint("MissingPermission")
    suspend fun read(characteristic: BluetoothGattCharacteristic): ByteArray? {
        val g = gatt ?: return null
        readDeferred = CompletableDeferred()
        val started = g.readCharacteristic(characteristic)
        if (!started) return null
        return readDeferred!!.await()
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }
}
