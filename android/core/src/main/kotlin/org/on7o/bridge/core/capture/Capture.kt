package org.on7o.bridge.core.capture

import kotlinx.serialization.Serializable

/**
 * Metadata sidecar for a locally captured thought, mirroring the shape of the
 * server's own Thought record. Written as capture.json next to the capture's
 * audio.wav.
 */
@Serializable
data class Capture(
    val id: String,
    val deviceId: String,
    val capturedAt: String,
    val receivedAt: String,
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val pcmBytes: Long,
    val audioBytes: Long,
    val syncState: SyncState = SyncState.PENDING,
    val syncAttempts: Int = 0,
    val lastSyncError: String? = null,
    val serverThoughtId: String? = null,
)
