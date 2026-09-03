package org.on7o.bridge.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.on7o.bridge.BridgeApplication
import org.on7o.bridge.core.capture.SyncState
import org.on7o.bridge.core.sync.UploadResult

/**
 * Uploads every locally captured thought that is not yet SYNCED. Runs both
 * on a periodic schedule (the offline-tolerant safety net) and on demand,
 * see [SyncScheduler].
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as BridgeApplication
        val baseUrl = app.settingsRepository.serverBaseUrl.first()
        if (baseUrl.isNullOrBlank()) {
            return@withContext Result.success()
        }

        val pending = app.captureStore.list().filter { it.syncState != SyncState.SYNCED }
        var anyTransientFailure = false

        for (capture in pending) {
            val result = app.uploadClient.upload(
                baseUrl = baseUrl,
                audioFile = app.captureStore.audioFile(capture.id),
                deviceId = capture.deviceId,
                sampleRate = capture.sampleRate,
                channels = capture.channels,
                bitsPerSample = capture.bitsPerSample,
                capturedAt = capture.capturedAt,
            )
            when (result) {
                is UploadResult.Success -> app.captureStore.markSynced(capture.id, result.thoughtId)
                is UploadResult.Failure -> {
                    app.captureStore.markFailed(capture.id, result.message)
                    if (result.retryable) anyTransientFailure = true
                }
            }
        }

        if (anyTransientFailure) Result.retry() else Result.success()
    }
}
