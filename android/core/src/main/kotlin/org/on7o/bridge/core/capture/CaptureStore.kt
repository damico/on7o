package org.on7o.bridge.core.capture

import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.random.Random

private val ID_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
private const val AUDIO_FILE = "audio.wav"
private const val META_FILE = "capture.json"
private val json = Json { ignoreUnknownKeys = true }

/**
 * Local, filesystem-backed queue of captured thoughts, one directory per
 * capture: {root}/{id}/audio.wav + capture.json. Deliberately no database:
 * mirrors the same "the filesystem is the whole storage layer" approach the
 * server's own ThoughtStore takes.
 */
class CaptureStore(private val root: File) {

    init {
        root.mkdirs()
    }

    /** Begins a new capture, creating its directory and placeholder audio file. */
    fun begin(
        deviceId: String,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        capturedAt: Instant,
        receivedAt: Instant = Instant.now(),
    ): Pair<String, CaptureWriter> {
        val id = newId(receivedAt)
        val dir = File(root, id).apply { mkdirs() }
        val writer = CaptureWriter(File(dir, AUDIO_FILE), sampleRate, channels, bitsPerSample)
        save(
            Capture(
                id = id,
                deviceId = deviceId,
                capturedAt = capturedAt.toString(),
                receivedAt = receivedAt.toString(),
                sampleRate = sampleRate,
                channels = channels,
                bitsPerSample = bitsPerSample,
                pcmBytes = 0,
                audioBytes = 0,
            ),
        )
        return id to writer
    }

    /** Finalizes a capture after its writer reports CAPTURE_END. */
    fun complete(id: String, writer: CaptureWriter) {
        val pcmBytes = writer.finish()
        val audioBytes = audioFile(id).length()
        val capture = requireNotNull(find(id)) { "unknown capture: $id" }
        save(capture.copy(pcmBytes = pcmBytes, audioBytes = audioBytes))
    }

    /** Discards a capture whose connection dropped before CAPTURE_END. */
    fun discard(id: String, writer: CaptureWriter) {
        writer.abort()
        dirFor(id).deleteRecursively()
    }

    fun audioFile(id: String): File = File(dirFor(id), AUDIO_FILE)

    fun find(id: String): Capture? {
        val file = File(dirFor(id), META_FILE)
        if (!file.isFile) return null
        return runCatching { json.decodeFromString(Capture.serializer(), file.readText()) }.getOrNull()
    }

    /** All local captures, most recently received first. */
    fun list(): List<Capture> {
        val dirs = root.listFiles { f -> f.isDirectory } ?: emptyArray()
        return dirs.mapNotNull { find(it.name) }.sortedByDescending { it.id }
    }

    fun markSynced(id: String, serverThoughtId: String) {
        val capture = find(id) ?: return
        save(capture.copy(syncState = SyncState.SYNCED, serverThoughtId = serverThoughtId, lastSyncError = null))
    }

    fun markFailed(id: String, error: String) {
        val capture = find(id) ?: return
        save(capture.copy(syncState = SyncState.FAILED, syncAttempts = capture.syncAttempts + 1, lastSyncError = error))
    }

    private fun save(capture: Capture) {
        File(dirFor(capture.id), META_FILE).writeText(json.encodeToString(Capture.serializer(), capture))
    }

    private fun dirFor(id: String): File = File(root, id)

    private fun newId(receivedAt: Instant): String {
        val suffix = Random.nextLong(1L shl 32).toString(16)
        return "${ID_TIME.format(receivedAt)}-$suffix"
    }
}
