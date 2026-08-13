package org.on7o.server.ingest;

/**
 * Audio format of a capture. The device records linear PCM; the server is
 * responsible for turning that into a playable WAV file.
 */
public record PcmFormat(int sampleRate, int channels, int bitsPerSample) {

    public PcmFormat {
        if (sampleRate < 4000 || sampleRate > 192000) {
            throw new IllegalArgumentException("unsupported sampleRate: " + sampleRate);
        }
        if (channels < 1 || channels > 2) {
            throw new IllegalArgumentException("unsupported channels: " + channels);
        }
        if (bitsPerSample != 8 && bitsPerSample != 16 && bitsPerSample != 24 && bitsPerSample != 32) {
            throw new IllegalArgumentException("unsupported bitsPerSample: " + bitsPerSample);
        }
    }

    public int bytesPerFrame() {
        return channels * (bitsPerSample / 8);
    }

    public long durationMs(long pcmBytes) {
        return pcmBytes * 1000L / ((long) sampleRate * bytesPerFrame());
    }
}
