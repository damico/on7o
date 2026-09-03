package org.on7o.bridge.core.protocol

/**
 * One parsed unit of the bridge protocol, as read off the Bluetooth socket.
 * See PROTOCOL.md for the wire layout each variant corresponds to.
 */
sealed class Frame {

    /** Announces a new capture. Always the first frame of a capture. */
    data class CaptureHeader(
        val deviceId: String,
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        /** Null when the device has no clock; the receiver stamps its own receipt time instead. */
        val capturedAt: String?,
    ) : Frame()

    /** One block of raw PCM audio belonging to the capture in progress. */
    class AudioChunk(val payload: ByteArray) : Frame() {
        override fun equals(other: Any?): Boolean =
            other is AudioChunk && payload.contentEquals(other.payload)

        override fun hashCode(): Int = payload.contentHashCode()
    }

    /** Marks the end of the capture in progress. */
    data class CaptureEnd(val totalPcmBytes: Long) : Frame()

    /** Sent by the phone back to the device once a capture is durably stored locally. */
    data class CaptureAck(val ok: Boolean) : Frame()
}
