package org.on7o.server.ingest;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Minimal 44-byte canonical WAV/RIFF header for uncompressed PCM.
 *
 * <p>The device streams raw PCM with an unknown total length, so ingestion writes
 * a placeholder header first and rewrites it with the real sizes once the stream
 * ends. Keeping the header on the server keeps the firmware free of any container
 * bookkeeping.
 */
final class WavHeader {

    static final int SIZE = 44;
    private static final int PCM_FORMAT_TAG = 1;

    private WavHeader() {
    }

    static byte[] of(PcmFormat format, long dataBytes) {
        int byteRate = format.sampleRate() * format.bytesPerFrame();
        ByteBuffer buf = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(new byte[] {'R', 'I', 'F', 'F'});
        buf.putInt((int) Math.min(Integer.MAX_VALUE, dataBytes + SIZE - 8));
        buf.put(new byte[] {'W', 'A', 'V', 'E'});
        buf.put(new byte[] {'f', 'm', 't', ' '});
        buf.putInt(16);
        buf.putShort((short) PCM_FORMAT_TAG);
        buf.putShort((short) format.channels());
        buf.putInt(format.sampleRate());
        buf.putInt(byteRate);
        buf.putShort((short) format.bytesPerFrame());
        buf.putShort((short) format.bitsPerSample());
        buf.put(new byte[] {'d', 'a', 't', 'a'});
        buf.putInt((int) Math.min(Integer.MAX_VALUE, dataBytes));
        return buf.array();
    }

    /**
     * Reads the format out of a canonical WAV header, for captures that arrive
     * already wrapped. Returns {@code null} when the bytes are not a WAV we can
     * read, in which case the caller falls back to the declared format.
     */
    static PcmFormat parse(byte[] header) {
        if (header.length < SIZE) {
            return null;
        }
        ByteBuffer buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        if (buf.getInt(0) != 0x46464952 || buf.getInt(8) != 0x45564157) { // "RIFF", "WAVE"
            return null;
        }
        try {
            return new PcmFormat(buf.getInt(24), buf.getShort(22), buf.getShort(34));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
