package com.scooterre.client.protocol

import com.scooterre.client.ble.PacketType
import com.scooterre.client.ble.Protocol
import com.scooterre.client.ble.Registers
import com.scooterre.client.ble.ScooterBleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class ProtocolException(message: String) : Exception(message)

fun ByteArray.toChunks(size: Int): List<ByteArray> {
    if (isEmpty()) return listOf(ByteArray(0))
    val out = mutableListOf<ByteArray>()
    var i = 0
    while (i < this.size) {
        val end = minOf(i + size, this.size)
        out.add(copyOfRange(i, end))
        i = end
    }
    return out
}

/**
 * Port of dreame_auth.py's Transport class: the channel-transport layer shared by every FE95
 * characteristic (CTR/ACK/DATA/SINGLE/MNG/MNG_ACK). Used for the login handshake (CONTROL +
 * LOGIN characteristics); the MIoT SPEC property channel has its own client (SpecClient.kt)
 * since its send+receive are interleaved differently.
 *
 * Keeps ONE notification subscription open on LOGIN for the entire object's lifetime (from
 * construction until [close]), instead of opening/closing a fresh one per call - opening a new
 * subscription between every step left a gap where a fast device reply landed with no active
 * collector and was lost forever (no replay cache). This was the second half of the actual
 * login blocker: the first half (the pubkey CTR's own ACK) was fixed by subscribing before that
 * specific write, but the very next step (waiting for the device's own pubkey reply) re-opened
 * the subscription from scratch and could just as easily miss a fast reply the same way.
 */
class ChannelTransport(private val ble: ScooterBleManager) {

    var maxPackageNum = 6
        private set
    var dmtu = 242
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private class Subscription(private val channel: Channel<ByteArray>, private val job: Job) {
        suspend fun receiveOrNull(timeoutMs: Long): ByteArray? = withTimeoutOrNull(timeoutMs) { channel.receive() }
        fun close() {
            job.cancel()
            channel.cancel()
        }
    }

    private fun subscribeTo(register: UUID): Subscription {
        val channel = Channel<ByteArray>(Channel.BUFFERED)
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            ble.notificationFlow.filter { it.characteristicUuid == register }.collect { channel.send(it.value) }
        }
        return Subscription(channel, job)
    }

    // Every login-flow call in this class (a4Handshake/sendStream/recvMessage) only ever
    // operates on LOGIN in practice - one persistent subscription for the whole handshake.
    private val loginNotifications = subscribeTo(Registers.LOGIN)

    // The final login result (21../22..) arrives on CONTROL - subscribed from construction for
    // the same reason as loginNotifications: a fresh subscription opened only after sending the
    // confirmation can miss a fast reply that arrives before it's set up.
    private val controlNotifications = subscribeTo(Registers.CONTROL)

    /** Waits for the next raw notification on CONTROL (the plain 4-byte login result 21../22..). */
    suspend fun waitOnControl(timeoutMs: Long): ByteArray? = controlNotifications.receiveOrNull(timeoutMs)

    /** Releases the persistent subscriptions. Call once this transport is no longer needed. */
    fun close() {
        loginNotifications.close()
        controlNotifications.close()
        scope.cancel()
    }

    private fun charFor(uuid: UUID) = ble.findCharacteristic(Registers.AUTH_SERVICE, uuid)
        ?: throw ProtocolException("Characteristic $uuid not found")

    suspend fun write(register: UUID, data: ByteArray) {
        // Confirmed live: both CONTROL and LOGIN only support write-WITHOUT-response (forcing
        // write-with-response on either fails immediately with GATT status 3/WRITE_NOT_PERMITTED).
        if (!ble.write(charFor(register), data)) {
            throw ProtocolException("Write to $register failed")
        }
    }

    /** A4 handshake: write 0xA4 to CONTROL, wait for MNG on LOGIN, ack with MNG_ACK. Once per connection. */
    suspend fun a4Handshake(): Boolean {
        write(Registers.CONTROL, byteArrayOf(Protocol.A4))
        val frame = loginNotifications.receiveOrNull(3000L) ?: return false
        if (frame.size < 6 || frame[0].toInt() != 0 || frame[1].toInt() != 0 ||
            (frame[2].toInt() and 0xFF) != PacketType.MNG
        ) return false

        maxPackageNum = frame[4].toInt() and 0xFF
        dmtu = frame[5].toInt() and 0xFF
        android.util.Log.d("ChannelTransport", "A4 MNG: maxPackageNum=$maxPackageNum dmtu=$dmtu")
        val ack = byteArrayOf(0, 0, PacketType.MNG_ACK.toByte(), frame[3], maxPackageNum.toByte(), dmtu.toByte())
        write(Registers.LOGIN, ack)
        // The reference (dreame_auth.py's a4_handshake) waits a full second here
        // (`await self.drain(1.0)`) before returning - the scooter apparently needs this
        // settling time after the MNG_ACK before it's ready for the next command.
        delay(1000)
        return true
    }

    /**
     * Sends `payload` as a channel message on `channel` via `writeChar` (always LOGIN in
     * practice): CTR -> wait ACK(01) -> DATA frames -> handle ACK(05 pull)/ACK(00 done).
     */
    suspend fun sendStream(
        writeChar: UUID,
        channel: Int,
        payload: ByteArray,
        // The device reassembles a message at seq*chunk offsets, so this MUST match the dmtu the
        // scooter itself reported in the A4 handshake (reference: chunk = dmtu - 2), not a fixed
        // constant - a mismatch here doesn't error, it just desyncs reassembly silently.
        frameSize: Int = dmtu - 2,
        timeoutMs: Long = 8000L,
    ): Boolean {
        val frames = payload.toChunks(frameSize)
        val fc = frames.size

        suspend fun sendSeq(n: Int) {
            if (n in 1..frames.size) {
                write(writeChar, byteArrayOf((n and 0xFF).toByte(), ((n shr 8) and 0xFF).toByte()) + frames[n - 1])
            }
        }

        write(writeChar, byteArrayOf(0, 0, PacketType.CTR.toByte(), channel.toByte(), (fc and 0xFF).toByte(), ((fc shr 8) and 0xFF).toByte()))

        val deadline = System.currentTimeMillis() + timeoutMs
        var sentAll = false
        while (System.currentTimeMillis() < deadline) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) break
            val frame = loginNotifications.receiveOrNull(remaining) ?: break
            if (frame.size >= 3 && frame[0].toInt() == 0 && frame[1].toInt() == 0 && (frame[2].toInt() and 0xFF) == PacketType.ACK) {
                val status = if (frame.size > 3) frame[3].toInt() and 0xFF else -1
                when (status) {
                    0x01 -> if (!sentAll) {
                        sentAll = true
                        for (n in 1..frames.size) {
                            sendSeq(n)
                            delay(30)
                        }
                    }
                    0x05 -> {
                        var i = 4
                        while (i + 1 < frame.size) {
                            val seq = (frame[i].toInt() and 0xFF) or ((frame[i + 1].toInt() and 0xFF) shl 8)
                            sendSeq(seq)
                            delay(30)
                            i += 2
                        }
                    }
                    0x00 -> return true
                }
            }
        }
        return false
    }

    /**
     * Waits for a device-initiated message on `notifyChar` (always LOGIN in practice): a single
     * SINGLE frame, or a CTR+DATA*+ourACK exchange. Acks/starts are written back on `ackChar`.
     */
    suspend fun recvMessage(notifyChar: UUID, ackChar: UUID = notifyChar, timeoutMs: Long = 8000L): ByteArray? {
        var fc: Int? = null
        val frames = sortedMapOf<Int, ByteArray>()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) break
            val frame = loginNotifications.receiveOrNull(remaining) ?: break
            if (frame.size < 3) continue
            val seq = (frame[0].toInt() and 0xFF) or ((frame[1].toInt() and 0xFF) shl 8)
            if (seq == 0) {
                when (frame[2].toInt() and 0xFF) {
                    PacketType.SINGLE -> {
                        val data = frame.copyOfRange(4, frame.size)
                        write(ackChar, byteArrayOf(0, 0, PacketType.SINGLE_ACK.toByte(), 0))
                        return data
                    }
                    PacketType.CTR -> {
                        fc = if (frame.size >= 6) {
                            (frame[4].toInt() and 0xFF) or ((frame[5].toInt() and 0xFF) shl 8)
                        } else {
                            frame.getOrNull(4)?.toInt()?.and(0xFF) ?: 0
                        }
                        write(ackChar, byteArrayOf(0, 0, PacketType.ACK.toByte(), 1))
                    }
                }
            } else {
                frames[seq] = frame.copyOfRange(2, frame.size)
                if (fc != null && frames.size >= fc!!) {
                    write(ackChar, byteArrayOf(0, 0, PacketType.ACK.toByte(), 0))
                    break
                }
            }
        }
        return if (frames.isEmpty()) null else frames.values.reduce { a, b -> a + b }
    }
}
