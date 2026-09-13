package com.scooterre.client.protocol

import com.scooterre.client.ble.PacketType
import com.scooterre.client.ble.Protocol
import com.scooterre.client.ble.Registers
import com.scooterre.client.ble.ScooterBleManager
import com.scooterre.client.crypto.MiCrypto
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

enum class SpecType(val code: Int) {
    BOOL(0), UINT8(1), INT8(2), UINT16(3), INT16(4),
    UINT32(5), INT32(6), UINT64(7), INT64(8), FLOAT(9), STRING(10);

    companion object {
        fun fromCode(code: Int) = entries.firstOrNull { it.code == code }
    }
}

data class SpecProperty(val siid: Int, val piid: Int, val name: String, val type: SpecType)

data class SpecReadResult(val property: SpecProperty, val raw: ByteArray, val status: Int) {
    val ok get() = status == 0
    val value: Any? by lazy { if (ok) decodeValue(property.type, raw) else null }
}

fun decodeValue(type: SpecType, raw: ByteArray): Any? {
    if (raw.isEmpty()) return null
    return when (type) {
        SpecType.FLOAT -> if (raw.size == 4) {
            Float.fromBits((raw[0].toInt() and 0xFF) or ((raw[1].toInt() and 0xFF) shl 8) or ((raw[2].toInt() and 0xFF) shl 16) or ((raw[3].toInt() and 0xFF) shl 24))
        } else null
        SpecType.STRING -> String(raw, Charsets.UTF_8)
        SpecType.INT8, SpecType.INT16, SpecType.INT32, SpecType.INT64 -> {
            var v = 0L
            for (i in raw.indices.reversed()) v = (v shl 8) or (raw[i].toLong() and 0xFF)
            val bits = raw.size * 8
            if (bits < 64 && (v and (1L shl (bits - 1))) != 0L) v -= (1L shl bits)
            v
        }
        else -> {
            var v = 0L
            for (i in raw.indices.reversed()) v = (v shl 8) or (raw[i].toLong() and 0xFF)
            v
        }
    }
}

/** Inverse of [decodeValue]: encodes a Kotlin value into the little-endian wire bytes SET expects. */
fun encodeValue(type: SpecType, value: Long): ByteArray {
    val size = when (type) {
        SpecType.BOOL, SpecType.UINT8, SpecType.INT8 -> 1
        SpecType.UINT16, SpecType.INT16 -> 2
        SpecType.UINT32, SpecType.INT32, SpecType.FLOAT -> 4
        SpecType.UINT64, SpecType.INT64 -> 8
        SpecType.STRING -> throw IllegalArgumentException("Use the String overload for STRING properties")
    }
    return ByteArray(size) { i -> ((value shr (8 * i)) and 0xFF).toByte() }
}

/** Well-known properties from the MIoT spec, extracted from the Mi Home plugin by the
 * KuziaMother/SCOOTER_5_PRO project. Read-only where the device doesn't accept SET for them. */
object SpecProperties {
    val ALL = listOf(
        SpecProperty(1, 1, "RIDING_MODE", SpecType.UINT8),
        SpecProperty(1, 2, "BATTERY_LEVEL", SpecType.UINT8),
        SpecProperty(1, 3, "REMAINING_BATTERY", SpecType.UINT16),
        SpecProperty(1, 4, "VOLTAGE", SpecType.FLOAT),
        SpecProperty(1, 5, "CURRENT", SpecType.FLOAT),
        SpecProperty(1, 6, "POWER", SpecType.FLOAT),
        SpecProperty(1, 7, "REMAINING_MILEAGE", SpecType.FLOAT),
        SpecProperty(1, 8, "FAULT", SpecType.UINT8),
        SpecProperty(1, 9, "CURRENT_MILEAGE", SpecType.FLOAT),
        SpecProperty(2, 1, "AVERAGE_SPEED", SpecType.FLOAT),
        SpecProperty(2, 2, "IS_LOCKED", SpecType.BOOL),
        SpecProperty(2, 3, "CRUISE_IS_ON", SpecType.BOOL),
        SpecProperty(2, 4, "TAIL_LIGHT_IS_ON", SpecType.BOOL),
        SpecProperty(2, 5, "ENERGY_RECOVERY", SpecType.UINT8),
        SpecProperty(2, 6, "TOTAL_MILEAGE", SpecType.FLOAT),
        SpecProperty(2, 7, "IS_RIDING", SpecType.UINT8),
        SpecProperty(2, 8, "RIDING_TIME", SpecType.FLOAT),
        SpecProperty(2, 9, "HIGHEST_SPEED", SpecType.FLOAT),
        SpecProperty(2, 10, "ASR_IS_ON", SpecType.BOOL),
        SpecProperty(2, 12, "AUTO_LIGHT", SpecType.BOOL),
        SpecProperty(2, 13, "TCS", SpecType.BOOL),
        SpecProperty(2, 14, "INTELLIGENT_DOWNHILL", SpecType.BOOL),
        SpecProperty(2, 15, "HILL_PARKING", SpecType.BOOL),
        SpecProperty(2, 16, "ATMOSPHERE_LIGHT", SpecType.UINT8),
        SpecProperty(2, 17, "BLUETOOTH_SEARCH_ON", SpecType.BOOL),
        SpecProperty(3, 1, "BATTERY_STATUS", SpecType.UINT8),
        SpecProperty(3, 2, "BATTERY_TEMPERATURE", SpecType.INT8),
        SpecProperty(3, 3, "SCOOTER_TEMPERATURE", SpecType.INT8),
        SpecProperty(3, 5, "MILEAGE_UNIT", SpecType.UINT8),
        SpecProperty(3, 8, "ACTIVATION_DATE", SpecType.STRING),
        SpecProperty(3, 10, "IS_CHARGING", SpecType.BOOL),
        SpecProperty(3, 11, "NUMBER_OF_CYCLES", SpecType.UINT8),
        SpecProperty(3, 12, "SOH", SpecType.UINT8),
        SpecProperty(4, 1, "PRODUCTION_DATE", SpecType.STRING),
        SpecProperty(4, 2, "BATTERY_SN", SpecType.STRING),
        SpecProperty(4, 3, "BMS_FIRMWARE_VERSION", SpecType.STRING),
        SpecProperty(4, 4, "SCOOTER_SN", SpecType.STRING),
        SpecProperty(4, 5, "FIRMWARE_VERSION", SpecType.STRING),
    )

    /** Properties the device is documented to accept SET for. RIDING_MODE is included because
     * all three of its documented values (P/D/S) are manufacturer presets within the region's
     * legal speed cap - it does not let you exceed it.
     *
     * ATMOSPHERE_LIGHT went through a real scare during development: SET on it appeared to time
     * out completely on a real 5 Pro, which looked like a device-side limitation and was
     * temporarily made read-only. Root-caused instead to two real app bugs (see requestMutex's
     * comment and SpecClient.requestWithRetry): concurrent BLE requests silently corrupting each
     * other, and the device occasionally going silent on one exchange with no automatic retry.
     * With those fixed (plus incrementing tid per request, see nextTid), repeated live tests set
     * ATMOSPHERE_LIGHT through all three values with no failures. */
    val SETTABLE = setOf(
        "IS_LOCKED", "TAIL_LIGHT_IS_ON", "ENERGY_RECOVERY", "ASR_IS_ON", "AUTO_LIGHT", "TCS",
        "INTELLIGENT_DOWNHILL", "HILL_PARKING", "BLUETOOTH_SEARCH_ON",
        "RIDING_MODE", "CRUISE_IS_ON", "MILEAGE_UNIT", "ATMOSPHERE_LIGHT",
    )

    /** Properties that only accept a fixed set of values (confirmed against the plugin's own
     * setProperty calls, not guessed) - shown in the UI as cycle buttons instead of a free-text
     * numeric field, since e.g. ENERGY_RECOVERY silently rejects anything other than 30/60/90.
     * The values themselves (language-independent) live here; their display labels are bilingual
     * and live in ui/Strings.kt ([com.scooterre.client.ui.cycleLabel]). */
    val CYCLE_PROPERTIES = setOf("RIDING_MODE", "ENERGY_RECOVERY", "MILEAGE_UNIT", "ATMOSPHERE_LIGHT")

    /** Settable properties with a legal/safety catch that varies by country or situation - this
     * app can't know which jurisdiction an install is in or how it's being used, so instead of
     * silently blocking or silently allowing these, the UI shows the relevant warning and
     * requires explicit confirmation before turning them ON, leaving the actual legal judgment
     * to the user. Reviewed against general EU e-scooter equipment rules (front/rear lights,
     * reflectors, two independent brakes are consistently required across member states; cruise
     * control's availability is the one that varies by market) - not exhaustive legal advice.
     * Properties considered and left out of this list on purpose: IS_LOCKED, ENERGY_RECOVERY,
     * ASR_IS_ON, AUTO_LIGHT, TCS, INTELLIGENT_DOWNHILL, HILL_PARKING, ATMOSPHERE_LIGHT,
     * BLUETOOTH_SEARCH_ON, MILEAGE_UNIT - no EU or per-country rule found that plausibly restricts
     * these (MILEAGE_UNIT is a pure display preference, km vs. mi), and
     * RIDING_MODE, whose presets are already hardware-calibrated per-region by the manufacturer
     * (see project research log) so the app can't exceed the local limit through it regardless.
     * The warning text itself is bilingual and lives in ui/Strings.kt ([com.scooterre.client.ui.regionWarning]). */
    val REGION_SENSITIVE_PROPERTIES = setOf("CRUISE_IS_ON", "TAIL_LIGHT_IS_ON")
}

/**
 * MIoT-spec property GET/SET over the encrypted SPEC channel (0x001a write / 0x001b notify,
 * channel 0). Port of probes/spec_read.py + probes/set_prop.py's interleaved send+receive loop.
 */
class SpecClient(private val ble: ScooterBleManager, private val keys: MiCrypto.SessionKeys) {

    private var appCounter = 0

    // The reference probes (probes/spec_read.py, probes/set_prop.py) hardcode tid=1 too, but each
    // of those is a one-shot script: connect, send ONE request, disconnect - tid=1 is never
    // reused within a session there. This app keeps one BLE session alive for many consecutive
    // GET/SET calls, and sending the SAME tid=1 for every single one of them is a plausible
    // reason the device's own firmware sometimes rejects an otherwise-valid, correctly-encoded
    // SET (confirmed via real device status=4097 rejections that Mi Home's requests - which very
    // likely DO vary their tid - don't hit) - if the firmware keeps any short window of
    // recently-seen transaction ids per property for duplicate/replay detection, a constant tid
    // would occasionally look like an already-handled repeat. Incrementing per request costs
    // nothing and matches how a real client would behave.
    private var tidCounter = 1

    // Android's BLE stack allows only ONE outstanding GATT operation at a time per connection -
    // if two request() calls overlap (e.g. a UI action firing while a previous refresh/set is
    // still in flight), the second writeCharacteristic() silently fails ("did not even start")
    // instead of queuing. That failure was never checked, so the frame was just dropped, the
    // scooter never saw a complete message, and the request timed out - which then permanently
    // desyncs the AES-CCM frame counter from the device's for the rest of the session (see
    // SpecProperties.SETTABLE's ATMOSPHERE_LIGHT comment for the counter-desync mechanics).
    // Serializing every request through this mutex is the actual fix, not a workaround.
    private val requestMutex = kotlinx.coroutines.sync.Mutex()

    private fun buildGetFrame(siid: Int, piid: Int, tid: Int = 1): ByteArray {
        val body = byteArrayOf(siid.toByte(), (piid and 0xFF).toByte(), (piid shr 8).toByte(), 0, 0)
        val total = 6 + body.size
        return header(total, tid, op = 2, count = 1) + body
    }

    private fun buildSetFrame(siid: Int, piid: Int, typeCode: Int, value: ByteArray, tid: Int = 1): ByteArray {
        val tl = (typeCode shl 12) or value.size
        val body = byteArrayOf(siid.toByte(), (piid and 0xFF).toByte(), (piid shr 8).toByte(), (tl and 0xFF).toByte(), (tl shr 8).toByte()) + value
        val total = 6 + body.size
        return header(total, tid, op = 0, count = 1) + body
    }

    private fun header(total: Int, tid: Int, op: Int, count: Int): ByteArray {
        val lenFlag = (total or 0x2000) and 0xFFFF
        return byteArrayOf(
            (lenFlag and 0xFF).toByte(), (lenFlag shr 8).toByte(),
            (tid and 0xFF).toByte(), (tid shr 8).toByte(),
            op.toByte(), count.toByte(),
        )
    }

    /** Wraps at 16 bits since tid is packed as a u16 LE in the frame header. */
    private fun nextTid(): Int {
        val t = tidCounter
        tidCounter = if (tidCounter >= 0xFFFF) 1 else tidCounter + 1
        return t
    }

    suspend fun get(property: SpecProperty, timeoutMs: Long = 8000L): SpecReadResult {
        val frame = buildGetFrame(property.siid, property.piid, nextTid())
        val plaintext = requestWithRetry(frame, timeoutMs) ?: return SpecReadResult(property, ByteArray(0), -1)
        return parseSingleReply(property, plaintext)
    }

    suspend fun set(property: SpecProperty, value: ByteArray, timeoutMs: Long = 8000L): Int {
        val frame = buildSetFrame(property.siid, property.piid, property.type.code, value, nextTid())
        val plaintext = requestWithRetry(frame, timeoutMs) ?: return -1
        // SET reply element: [siid][piid u16][status u16] (7 bytes after the 6-byte header)
        if (plaintext.size < 11) return -1
        return (plaintext[9].toInt() and 0xFF) or ((plaintext[10].toInt() and 0xFF) shl 8)
    }

    /** The scooter occasionally goes completely silent on one request in a sequence - fully
     * acking receipt of our frame (so it's not a transmission problem on our end) and then never
     * sending any reply at all, not even a CTR frame announcing one. Confirmed live via raw BLE
     * logs: not caused by request rate (serialized, evenly-paced requests hit it too) and not a
     * parsing bug (there is nothing to parse - zero bytes come back). A single silent retry with
     * a fresh request (new AES-CCM counter value, so it's not literally a duplicate on the wire)
     * papers over that one dropped exchange without hiding a real failure - if the retry also
     * gets no reply, that's a genuine problem worth surfacing. */
    private suspend fun requestWithRetry(frame: ByteArray, timeoutMs: Long): ByteArray? =
        request(frame, timeoutMs) ?: request(frame, timeoutMs)

    private fun parseSingleReply(property: SpecProperty, pt: ByteArray): SpecReadResult {
        if (pt.size < 11) return SpecReadResult(property, ByteArray(0), -1)
        val status = (pt[9].toInt() and 0xFF) or ((pt[10].toInt() and 0xFF) shl 8)
        if (status != 0 || pt.size < 13) return SpecReadResult(property, ByteArray(0), status)
        val tl = (pt[11].toInt() and 0xFF) or ((pt[12].toInt() and 0xFF) shl 8)
        val vlen = tl and 0x0FFF
        val value = if (pt.size >= 13 + vlen) pt.copyOfRange(13, 13 + vlen) else ByteArray(0)
        return SpecReadResult(property, value, 0)
    }

    /** Sends one encrypted request and returns the decrypted device response, or null on timeout.
     * Subscribes to notifications BEFORE writing the CTR frame (via an UNDISPATCHED producer) so
     * a very fast device reply can't be emitted and lost before our own collection starts - see
     * ChannelTransport.subscribeTo for the full rationale (this was the actual login blocker).
     *
     * Wrapped in [requestMutex]: the whole write-then-wait-for-reply exchange must run to
     * completion before another one starts - see the mutex's own comment for why. */
    private suspend fun request(frame: ByteArray, timeoutMs: Long): ByteArray? = requestMutex.withLock { coroutineScope {
        val payload = MiCrypto.encryptSpecFrame(keys, appCounter, frame)
        appCounter++
        val frames = payload.toChunks(Protocol.DEFAULT_FRAME_SIZE)
        val fc = frames.size

        fun charFor(uuid: java.util.UUID) = ble.findCharacteristic(Registers.AUTH_SERVICE, uuid)
            ?: throw ProtocolException("Characteristic $uuid not found")

        suspend fun sendSeq(n: Int) {
            if (n in 1..frames.size) {
                val data = byteArrayOf((n and 0xFF).toByte(), ((n shr 8) and 0xFF).toByte()) + frames[n - 1]
                // The return value used to be silently discarded here - a failed write (e.g. the
                // GATT stack rejecting an overlapping operation) looked identical to a successful
                // one, and the scooter would just never receive that frame. Now at least visible
                // in logs instead of manifesting only as a mystery timeout minutes later.
                val ok = ble.write(charFor(Registers.SPEC_WRITE), data)
                if (!ok) android.util.Log.e("SpecClient", "sendSeq($n) write failed - frame likely never reached the device")
            }
        }

        // job must be cancelled explicitly alongside the channel - cancelling only the channel
        // leaves a leaked collector coroutine (suspended inside the upstream flow's own wait)
        // running forever, which keeps this coroutineScope from ever completing.
        val incoming = Channel<com.scooterre.client.ble.ScooterBleManager.CharacteristicUpdate>(Channel.BUFFERED)
        val incomingJob = launch(start = CoroutineStart.UNDISPATCHED) {
            ble.notificationFlow.filter {
                it.characteristicUuid == Registers.SPEC_WRITE || it.characteristicUuid == Registers.SPEC_NOTIFY
            }.collect { incoming.send(it) }
        }

        try {
        ble.write(charFor(Registers.SPEC_WRITE), byteArrayOf(0, 0, PacketType.CTR.toByte(), Protocol.SPEC_CHANNEL.toByte(), (fc and 0xFF).toByte(), ((fc shr 8) and 0xFF).toByte()))

        val deadline = System.currentTimeMillis() + timeoutMs
        var sentAll = false
        val respFrames = sortedMapOf<Int, ByteArray>()
        var respFc: Int? = null

        while (System.currentTimeMillis() < deadline) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) break
            val update = withTimeoutOrNull(remaining) { incoming.receive() } ?: break
            val b = update.value

            if (update.characteristicUuid == Registers.SPEC_WRITE && b.size >= 3 && b[0].toInt() == 0 && b[1].toInt() == 0 && (b[2].toInt() and 0xFF) == PacketType.ACK) {
                val status = if (b.size > 3) b[3].toInt() and 0xFF else -1
                when (status) {
                    0x01 -> if (!sentAll) {
                        sentAll = true
                        for (n in 1..frames.size) { sendSeq(n); delay(30) }
                    }
                    0x05 -> {
                        var i = 4
                        while (i + 1 < b.size) {
                            val seq = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
                            sendSeq(seq)
                            delay(30)
                            i += 2
                        }
                    }
                }
            } else if (update.characteristicUuid == Registers.SPEC_NOTIFY && b.size >= 3 && b[0].toInt() == 0 && b[1].toInt() == 0 && (b[2].toInt() and 0xFF) == PacketType.CTR) {
                respFc = if (b.size >= 6) (b[4].toInt() and 0xFF) or ((b[5].toInt() and 0xFF) shl 8) else (b.getOrNull(4)?.toInt()?.and(0xFF) ?: 0)
                ble.write(charFor(Registers.SPEC_NOTIFY), byteArrayOf(0, 0, PacketType.ACK.toByte(), 1))
            } else if (update.characteristicUuid == Registers.SPEC_NOTIFY && b.size >= 2) {
                val seq = (b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8)
                if (seq in 1..0xFFFF) {
                    respFrames[seq] = b.copyOfRange(2, b.size)
                    val fcNow = respFc
                    if (fcNow != null && respFrames.size >= fcNow) {
                        ble.write(charFor(Registers.SPEC_NOTIFY), byteArrayOf(0, 0, PacketType.ACK.toByte(), 0))
                        val assembled = respFrames.values.reduce { a, c -> a + c }
                        return@coroutineScope try {
                            MiCrypto.decryptSpecFrame(keys, assembled)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }
        }
        null
        } finally {
            incomingJob.cancel()
            incoming.cancel()
        }
    } }
}
