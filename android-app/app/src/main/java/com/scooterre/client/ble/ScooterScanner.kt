package com.scooterre.client.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class FoundDevice(val name: String, val address: String)

/**
 * Scans for nearby BLE devices so a new user can pick their scooter instead of typing its MAC
 * address by hand. The scooter doesn't advertise a recognizable service UUID we could filter
 * on (confirmed live: a Web Bluetooth `filters: [{services: [FE95_UUID]}]` scan found nothing
 * even after 20+ seconds), only a plain advertised name ("xiaomi.scooter.5pro" for the 5 Pro) -
 * so this does an unfiltered scan and surfaces every named device found; the user picks the
 * right one visually rather than us guessing a name pattern that might not hold for every unit.
 */
class ScooterScanner(private val context: Context) {
    @SuppressLint("MissingPermission")
    fun scan(): Flow<FoundDevice> = callbackFlow {
        val manager = context.getSystemService(BluetoothManager::class.java)
        val scanner = manager?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            close()
            return@callbackFlow
        }

        val seen = mutableSetOf<String>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: return
                if (name.isBlank() || !seen.add(result.device.address)) return
                trySend(FoundDevice(name, result.device.address))
            }
        }

        try {
            scanner.startScan(callback)
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    /** Emits once a real over-the-air advertisement from exactly [mac] is seen. `connectGatt()`
     * with `autoConnect=false` ("direct connect") only succeeds against a device that is already
     * advertising at the very instant it's called - it does not itself keep listening for the
     * device to show up a moment later. Waiting for an actual advertisement here first (confirmed
     * live: switching the scooter on mid-attempt never connected without this) turns that into a
     * connect that reacts the moment the scooter is switched on, instead of a blind GATT-timeout
     * retry loop. Filtered by address (not name) - cheaper on the radio and matches even if a
     * later firmware ever changes/drops the advertised name. */
    @SuppressLint("MissingPermission")
    fun watchForDevice(mac: String): Flow<Unit> = callbackFlow {
        val manager = context.getSystemService(BluetoothManager::class.java)
        val scanner = manager?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            close()
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(Unit)
            }
        }
        val filter = ScanFilter.Builder().setDeviceAddress(mac).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(listOf(filter), settings, callback)
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }
}
