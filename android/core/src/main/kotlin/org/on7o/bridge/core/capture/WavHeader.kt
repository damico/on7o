package org.on7o.bridge.core.capture

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal 44-byte canonical WAV/RIFF header for uncompressed PCM. A direct
 * port of server/src/main/java/org/on7o/server/ingest/WavHeader.java so a
 * capture written on the phone is byte-for-byte the same shape the server
 * already knows how to read.
 */
object WavHeader {

    const val SIZE = 44
    private const val PCM_FORMAT_TAG = 1

    fun of(sampleRate: Int, channels: Int, bitsPerSample: Int, dataBytes: Long): ByteArray {
        val bytesPerFrame = channels * (bitsPerSample / 8)
        val byteRate = sampleRate * bytesPerFrame
        val buf = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte()))
        buf.putInt(minOf(Int.MAX_VALUE.toLong(), dataBytes + SIZE - 8).toInt())
        buf.put(byteArrayOf('W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte()))
        buf.put(byteArrayOf('f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte()))
        buf.putInt(16)
        buf.putShort(PCM_FORMAT_TAG.toShort())
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort(bytesPerFrame.toShort())
        buf.putShort(bitsPerSample.toShort())
        buf.put(byteArrayOf('d'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte()))
        buf.putInt(minOf(Int.MAX_VALUE.toLong(), dataBytes).toInt())
        return buf.array()
    }
}
