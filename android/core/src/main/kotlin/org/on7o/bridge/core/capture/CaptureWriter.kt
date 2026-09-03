package org.on7o.bridge.core.capture

import java.io.File
import java.io.RandomAccessFile

/**
 * Streams one capture's audio to disk as it arrives, without ever buffering
 * the whole thing in memory. Mirrors the RandomAccessFile approach in the
 * server's ThoughtStore.store(): a placeholder WAV header is written first
 * and patched with the real size once the capture ends.
 */
class CaptureWriter internal constructor(
    private val audioFile: File,
    private val sampleRate: Int,
    private val channels: Int,
    private val bitsPerSample: Int,
) {
    private val out = RandomAccessFile(audioFile, "rw")
    private var pcmBytes = 0L
    private var finished = false

    init {
        out.write(WavHeader.of(sampleRate, channels, bitsPerSample, 0))
    }

    /** Appends one chunk of raw PCM. */
    fun append(payload: ByteArray) {
        check(!finished) { "capture already finished" }
        out.write(payload)
        pcmBytes += payload.size
    }

    /** Patches the header with the real size and closes the file. Returns the final PCM byte count. */
    fun finish(): Long {
        check(!finished) { "capture already finished" }
        out.seek(0)
        out.write(WavHeader.of(sampleRate, channels, bitsPerSample, pcmBytes))
        out.close()
        finished = true
        return pcmBytes
    }

    /** Drops the partial file, for a capture whose connection dropped before CAPTURE_END. */
    fun abort() {
        if (finished) return
        out.close()
        finished = true
        audioFile.delete()
    }
}
