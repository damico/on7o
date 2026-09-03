package org.on7o.bridge.core.protocol

import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Thrown internally for a frame whose declared shape is invalid. Callers never
 * see this: [FrameReader] catches it and resynchronizes on the stream instead.
 */
private class MalformedFrameException(message: String) : Exception(message)

/**
 * Reads on7o bridge protocol frames off a byte stream, a Bluetooth RFCOMM
 * socket's InputStream in production, anything InputStream-shaped in tests.
 * See PROTOCOL.md for the wire format.
 *
 * <p>Because there is no real firmware to validate this protocol against yet,
 * the reader is deliberately defensive: a malformed frame does not throw, it
 * resynchronizes by scanning forward for the next magic sequence, using a
 * proper KMP search so a partial magic sequence occurring inside audio
 * payload bytes cannot cause the scan to miss the real one that follows it.
 */
class FrameReader(private val input: InputStream) {

    private val magicFailure = kmpFailureFunction(BridgeProtocol.MAGIC)

    /**
     * Reads the next well-formed frame. Returns null once the stream has
     * cleanly ended, no more bytes where a new frame would start.
     */
    fun readFrame(): Frame? {
        while (true) {
            if (!syncToMagic()) return null
            val version = readByteOrNull() ?: return null
            if (version != BridgeProtocol.VERSION) continue
            val type = readByteOrNull() ?: return null
            try {
                return readBody(type)
            } catch (e: MalformedFrameException) {
                continue
            } catch (e: EOFException) {
                return null
            }
        }
    }

    private fun readBody(type: Byte): Frame = when (type) {
        BridgeProtocol.TYPE_CAPTURE_HEADER -> readCaptureHeader()
        BridgeProtocol.TYPE_AUDIO_CHUNK -> readAudioChunk()
        BridgeProtocol.TYPE_CAPTURE_END -> readCaptureEnd()
        BridgeProtocol.TYPE_CAPTURE_ACK -> readCaptureAck()
        else -> throw MalformedFrameException("unknown message type: $type")
    }

    private fun readCaptureHeader(): Frame.CaptureHeader {
        val deviceIdLen = readByteOrThrow().toInt() and 0xFF
        val deviceId = readFully(deviceIdLen).toString(StandardCharsets.UTF_8)
        val sampleRate = readInt32LE()
        val channels = readByteOrThrow().toInt() and 0xFF
        val bitsPerSample = readByteOrThrow().toInt() and 0xFF
        val capturedAtLen = readByteOrThrow().toInt() and 0xFF
        val capturedAt = if (capturedAtLen == 0) null else readFully(capturedAtLen).toString(StandardCharsets.UTF_8)
        return Frame.CaptureHeader(deviceId, sampleRate, channels, bitsPerSample, capturedAt)
    }

    private fun readAudioChunk(): Frame.AudioChunk {
        val length = readInt32LE()
        if (length < 0 || length > BridgeProtocol.MAX_CHUNK_BYTES) {
            throw MalformedFrameException("chunk length out of range: $length")
        }
        return Frame.AudioChunk(readFully(length))
    }

    private fun readCaptureEnd(): Frame.CaptureEnd {
        val totalPcmBytes = readInt32LE().toLong() and 0xFFFFFFFFL
        return Frame.CaptureEnd(totalPcmBytes)
    }

    private fun readCaptureAck(): Frame.CaptureAck {
        val status = readByteOrThrow()
        return Frame.CaptureAck(status == BridgeProtocol.ACK_STATUS_OK)
    }

    private fun syncToMagic(): Boolean {
        val magic = BridgeProtocol.MAGIC
        var matched = 0
        while (true) {
            val b = readByteOrNull() ?: return false
            while (matched > 0 && b != magic[matched]) matched = magicFailure[matched - 1]
            if (b == magic[matched]) matched++
            if (matched == magic.size) return true
        }
    }

    private fun readByteOrNull(): Byte? {
        val value = input.read()
        return if (value == -1) null else value.toByte()
    }

    private fun readByteOrThrow(): Byte = readByteOrNull() ?: throw EOFException()

    private fun readFully(length: Int): ByteArray {
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(buffer, offset, length - offset)
            if (read == -1) throw EOFException()
            offset += read
        }
        return buffer
    }

    private fun readInt32LE(): Int {
        val bytes = readFully(4)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
    }
}

/** Standard KMP prefix function, used to resynchronize the scan correctly past partial matches. */
private fun kmpFailureFunction(pattern: ByteArray): IntArray {
    val failure = IntArray(pattern.size)
    var k = 0
    for (i in 1 until pattern.size) {
        while (k > 0 && pattern[i] != pattern[k]) k = failure[k - 1]
        if (pattern[i] == pattern[k]) k++
        failure[i] = k
    }
    return failure
}
