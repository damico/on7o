package org.on7o.bridge.core.protocol

/**
 * Constants for the provisional on7o Bluetooth bridge framing protocol.
 * See PROTOCOL.md at the root of the android project for the full spec.
 */
object BridgeProtocol {

    val MAGIC: ByteArray = byteArrayOf('O'.code.toByte(), 'N'.code.toByte(), '7'.code.toByte(), 'O'.code.toByte())
    const val VERSION: Byte = 1

    const val TYPE_CAPTURE_HEADER: Byte = 0x01
    const val TYPE_AUDIO_CHUNK: Byte = 0x02
    const val TYPE_CAPTURE_END: Byte = 0x03
    const val TYPE_CAPTURE_ACK: Byte = 0x04

    /** Sanity ceiling for a declared chunk length, far above the ~1 KB blocks expected in practice. */
    const val MAX_CHUNK_BYTES: Int = 64 * 1024

    const val MAX_DEVICE_ID_BYTES: Int = 255
    const val MAX_CAPTURED_AT_BYTES: Int = 255

    const val ACK_STATUS_OK: Byte = 0x00
    const val ACK_STATUS_ERROR: Byte = 0x01
}
