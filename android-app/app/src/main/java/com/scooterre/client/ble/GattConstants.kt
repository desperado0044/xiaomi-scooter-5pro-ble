package com.scooterre.client.ble

import java.util.UUID

/**
 * CONFIRMED 2026-09-13 against the real device (KuziaMother/SCOOTER_5_PRO reference,
 * live-verified with core/dreame_auth.py from this same project). Everything lives under
 * the single FE95 service - no separate AVDTP/UART service split.
 */
object Registers {
    val AUTH_SERVICE: UUID = UUID.fromString("0000fe95-0000-1000-8000-00805f9b34fb")

    val VERSION: UUID = UUID.fromString("00000004-0000-1000-8000-00805f9b34fb") // read, no login
    val CONTROL: UUID = UUID.fromString("00000010-0000-1000-8000-00805f9b34fb") // A4 handshake, login-start/result
    val LOGIN: UUID = UUID.fromString("00000016-0000-1000-8000-00805f9b34fb")   // login channel transport
    val DFU_CMD: UUID = UUID.fromString("00000017-0000-1000-8000-00805f9b34fb")
    val DFU_DATA: UUID = UUID.fromString("00000018-0000-1000-8000-00805f9b34fb")
    val SPEC_WRITE: UUID = UUID.fromString("0000001a-0000-1000-8000-00805f9b34fb") // MIoT property GET/SET
    val SPEC_NOTIFY: UUID = UUID.fromString("0000001b-0000-1000-8000-00805f9b34fb")
    val MCU_INFO: UUID = UUID.fromString("0000001c-0000-1000-8000-00805f9b34fb") // no login needed

    val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

/** Channel-transport packet type byte (offset 2 in every non-DATA frame). */
object PacketType {
    const val CTR = 0x00
    const val ACK = 0x01
    const val SINGLE = 0x02
    const val SINGLE_ACK = 0x03
    const val MNG = 0x04
    const val MNG_ACK = 0x05
}

object Protocol {
    const val A4: Byte = 0xA4.toByte()
    const val PUBKEY_CHANNEL = 3     // channel used to send our ECDH public key on LOGIN char
    const val CONFIRM_CHANNEL = 5    // channel used to send the AES-CCM confirmation on LOGIN char
    const val SPEC_CHANNEL = 0       // channel used for MIoT property GET/SET on SPEC_WRITE/NOTIFY
    const val DEFAULT_FRAME_SIZE = 18

    val LOGIN_START = byteArrayOf(0x20, 0x00, 0x00)
    val LOGIN_OK_PREFIX: Byte = 0x21
    val LOGIN_FAIL_PREFIX: Byte = 0x22

    /** Fixed 12-byte nonce for the AES-CCM login confirmation - same constant found in both
     * this device's firmware and the unrelated macbury/m365 project, apparently a shared
     * convention across Xiaomi's "mible"-family BLE security chip implementations. */
    val CCM_LOGIN_NONCE: ByteArray = byteArrayOf(
        0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x1b
    )

    val HKDF_SALT: ByteArray = "smartcfg-login-salt".toByteArray(Charsets.US_ASCII)
    val HKDF_INFO: ByteArray = "smartcfg-login-info".toByteArray(Charsets.US_ASCII)

    /** AES-128-CBC IV used to decrypt a PIN-protected ltmk returned by the cloud (encrypt_type=1). */
    val LTMK_ENCRYPT_IV: ByteArray = byteArrayOf(
        0x7a, 0xa4.toByte(), 0xc6.toByte(), 0x8c.toByte(), 0x59, 0x0d, 0x40, 0x31,
        0xb9.toByte(), 0x80.toByte(), 0xd9.toByte(), 0x8b.toByte(), 0x41, 0x02, 0x38, 0x00
    )
}
