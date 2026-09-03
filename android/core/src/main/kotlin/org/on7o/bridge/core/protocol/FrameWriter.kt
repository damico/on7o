package org.on7o.bridge.core.protocol

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Encodes protocol frames onto an OutputStream. Used by the phone to send
 * CaptureAck back to the device, and by tests to build fixture streams for
 * FrameReader without a real Bluetooth socket.
 */
object FrameWriter {

    fun writeCaptureHeader(
        out: OutputStream,
        deviceId: String,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        capturedAt: String?,
    ) {
        val deviceIdBytes = deviceId.toByteArray(StandardCharsets.UTF_8)
        require(deviceIdBytes.size <= BridgeProtocol.MAX_DEVICE_ID_BYTES) { "deviceId too long" }
        val capturedAtBytes = capturedAt?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
        require(capturedAtBytes.size <= BridgeProtocol.MAX_CAPTURED_AT_BYTES) { "capturedAt too long" }

        val body = ByteBuffer
            .allocate(1 + deviceIdBytes.size + 4 + 1 + 1 + 1 + capturedAtBytes.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        body.put(deviceIdBytes.size.toByte())
        body.put(deviceIdBytes)
        body.putInt(sampleRate)
        body.put(channels.toByte())
        body.put(bitsPerSample.toByte())
        body.put(capturedAtBytes.size.toByte())
        body.put(capturedAtBytes)

        writeFrame(out, BridgeProtocol.TYPE_CAPTURE_HEADER, body.array())
    }

    fun writeAudioChunk(out: OutputStream, payload: ByteArray) {
        require(payload.size <= BridgeProtocol.MAX_CHUNK_BYTES) { "chunk too large" }
        val body = ByteBuffer.allocate(4 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        body.putInt(payload.size)
        body.put(payload)
        writeFrame(out, BridgeProtocol.TYPE_AUDIO_CHUNK, body.array())
    }

    /** [totalPcmBytes] is written as an unsigned 32-bit field; captures never come close to that ceiling. */
    fun writeCaptureEnd(out: OutputStream, totalPcmBytes: Long) {
        val body = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        body.putInt(totalPcmBytes.toInt())
        writeFrame(out, BridgeProtocol.TYPE_CAPTURE_END, body.array())
    }

    fun writeCaptureAck(out: OutputStream, ok: Boolean) {
        val body = byteArrayOf(if (ok) BridgeProtocol.ACK_STATUS_OK else BridgeProtocol.ACK_STATUS_ERROR)
        writeFrame(out, BridgeProtocol.TYPE_CAPTURE_ACK, body)
    }

    private fun writeFrame(out: OutputStream, type: Byte, body: ByteArray) {
        out.write(BridgeProtocol.MAGIC)
        out.write(BridgeProtocol.VERSION.toInt())
        out.write(type.toInt())
        out.write(body)
    }
}
