package org.on7o.bridge.core.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.io.path.createTempDirectory

class CaptureStoreTest {

    private fun newStore(): CaptureStore = CaptureStore(createTempDirectory("captures").toFile())

    @Test
    fun `a completed capture is written to disk and listed as pending`() {
        val store = newStore()
        val capturedAt = Instant.parse("2026-09-02T14:03:00Z")

        val (id, writer) = store.begin("sticks3-01", 16000, 1, 16, capturedAt)
        writer.append(byteArrayOf(1, 2, 3, 4))
        writer.append(byteArrayOf(5, 6))
        store.complete(id, writer)

        val capture = store.find(id)
        assertEquals(6L, capture?.pcmBytes)
        assertEquals(WavHeader.SIZE + 6L, capture?.audioBytes)
        assertEquals(SyncState.PENDING, capture?.syncState)
        assertEquals("sticks3-01", capture?.deviceId)

        val audio = store.audioFile(id)
        assertTrue(audio.isFile)
        assertEquals(WavHeader.SIZE + 6L, audio.length())

        assertEquals(listOf(id), store.list().map { it.id })
    }

    @Test
    fun `a discarded capture leaves nothing behind`() {
        val store = newStore()
        val (id, writer) = store.begin("sticks3-01", 16000, 1, 16, Instant.now())
        writer.append(byteArrayOf(1, 2, 3))

        store.discard(id, writer)

        assertNull(store.find(id))
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `sync outcomes update the capture's metadata`() {
        val store = newStore()
        val (id, writer) = store.begin("sticks3-01", 16000, 1, 16, Instant.now())
        store.complete(id, writer)

        store.markSynced(id, "server-thought-id")
        assertEquals(SyncState.SYNCED, store.find(id)?.syncState)
        assertEquals("server-thought-id", store.find(id)?.serverThoughtId)

        store.markFailed(id, "boom")
        val failed = store.find(id)
        assertEquals(SyncState.FAILED, failed?.syncState)
        assertEquals("boom", failed?.lastSyncError)
        assertEquals(1, failed?.syncAttempts)
    }
}
