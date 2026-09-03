package org.on7o.bridge.core.capture

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavHeaderTest {

    @Test
    fun `produces a canonical 44-byte PCM WAV header`() {
        val header = WavHeader.of(sampleRate = 16000, channels = 1, bitsPerSample = 16, dataBytes = 1000)
        assertEquals(WavHeader.SIZE, header.size)

        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(header, 0, 4, Charsets.US_ASCII))
        assertEquals(1000 + WavHeader.SIZE - 8, buf.getInt(4))
        assertEquals("WAVE", String(header, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(header, 12, 4, Charsets.US_ASCII))
        assertEquals(16, buf.getInt(16))
        assertEquals(1, buf.getShort(20).toInt())
        assertEquals(1, buf.getShort(22).toInt())
        assertEquals(16000, buf.getInt(24))
        assertEquals(32000, buf.getInt(28))
        assertEquals(2, buf.getShort(32).toInt())
        assertEquals(16, buf.getShort(34).toInt())
        assertEquals("data", String(header, 36, 4, Charsets.US_ASCII))
        assertEquals(1000, buf.getInt(40))
    }
}
