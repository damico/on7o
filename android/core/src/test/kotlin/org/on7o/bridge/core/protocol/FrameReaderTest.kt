package org.on7o.bridge.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class FrameReaderTest {

    @Test
    fun `reads a full capture roundtrip`() {
        val out = ByteArrayOutputStream()
        FrameWriter.writeCaptureHeader(out, "sticks3-01", 16000, 1, 16, "2026-09-02T14:03:00Z")
        FrameWriter.writeAudioChunk(out, byteArrayOf(1, 2, 3, 4))
        FrameWriter.writeAudioChunk(out, byteArrayOf(5, 6))
        FrameWriter.writeCaptureEnd(out, 6)
        FrameWriter.writeCaptureAck(out, true)

        val reader = FrameReader(ByteArrayInputStream(out.toByteArray()))

        val header = reader.readFrame() as Frame.CaptureHeader
        assertEquals("sticks3-01", header.deviceId)
        assertEquals(16000, header.sampleRate)
        assertEquals(1, header.channels)
        assertEquals(16, header.bitsPerSample)
        assertEquals("2026-09-02T14:03:00Z", header.capturedAt)

        val chunk1 = reader.readFrame() as Frame.AudioChunk
        assertEquals(listOf<Byte>(1, 2, 3, 4), chunk1.payload.toList())

        val chunk2 = reader.readFrame() as Frame.AudioChunk
        assertEquals(listOf<Byte>(5, 6), chunk2.payload.toList())

        val end = reader.readFrame() as Frame.CaptureEnd
        assertEquals(6L, end.totalPcmBytes)

        val ack = reader.readFrame() as Frame.CaptureAck
        assertEquals(true, ack.ok)

        assertNull(reader.readFrame())
    }

    @Test
    fun `capturedAt is absent when the device has no clock`() {
        val out = ByteArrayOutputStream()
        FrameWriter.writeCaptureHeader(out, "sticks3-01", 16000, 1, 16, null)

        val header = FrameReader(ByteArrayInputStream(out.toByteArray())).readFrame() as Frame.CaptureHeader
        assertNull(header.capturedAt)
    }

    @Test
    fun `resynchronizes past garbage bytes before the next magic sequence`() {
        val out = ByteArrayOutputStream()
        // Bytes chosen to contain none of the magic sequence's characters, so
        // there is no ambiguity about where the real frame starts.
        out.write(byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()))
        FrameWriter.writeAudioChunk(out, byteArrayOf(9, 9, 9))

        val chunk = FrameReader(ByteArrayInputStream(out.toByteArray())).readFrame() as Frame.AudioChunk
        assertEquals(listOf<Byte>(9, 9, 9), chunk.payload.toList())
    }

    @Test
    fun `an oversized declared chunk length is rejected and the reader resynchronizes`() {
        val out = ByteArrayOutputStream()
        out.write(BridgeProtocol.MAGIC)
        out.write(BridgeProtocol.VERSION.toInt())
        out.write(BridgeProtocol.TYPE_AUDIO_CHUNK.toInt())
        out.write(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F)) // huge length, little-endian
        FrameWriter.writeAudioChunk(out, byteArrayOf(7))

        val chunk = FrameReader(ByteArrayInputStream(out.toByteArray())).readFrame() as Frame.AudioChunk
        assertEquals(listOf<Byte>(7), chunk.payload.toList())
    }

    @Test
    fun `a stream that ends mid-frame yields null instead of throwing`() {
        val out = ByteArrayOutputStream()
        out.write(BridgeProtocol.MAGIC)
        out.write(BridgeProtocol.VERSION.toInt())
        out.write(BridgeProtocol.TYPE_AUDIO_CHUNK.toInt())
        out.write(byteArrayOf(0x10, 0x00, 0x00, 0x00)) // declares 16 bytes, the stream has none

        assertNull(FrameReader(ByteArrayInputStream(out.toByteArray())).readFrame())
    }
}
