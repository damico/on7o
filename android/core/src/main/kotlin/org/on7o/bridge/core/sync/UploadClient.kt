package org.on7o.bridge.core.sync

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

private val WAV_MEDIA_TYPE = "audio/wav".toMediaType()

/**
 * Uploads a finished local capture to the on7o server's existing audio
 * ingestion endpoint (server's IngestController). The capture is always
 * uploaded as a complete WAV file (format=wav): unlike the StickS3's live
 * HTTP-chunked stream, the phone already has the whole file by sync time, so
 * there is no need to reimplement chunked transfer encoding here.
 */
class UploadClient(private val client: OkHttpClient) {

    fun upload(
        baseUrl: String,
        audioFile: File,
        deviceId: String,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        capturedAt: String,
    ): UploadResult {
        val base = baseUrl.trimEnd('/').toHttpUrlOrNull()
            ?: return UploadResult.Failure("invalid server URL: $baseUrl", retryable = false)

        val url = base.newBuilder()
            .addPathSegments("api/thoughts/audio")
            .addQueryParameter("device", deviceId)
            .addQueryParameter("sampleRate", sampleRate.toString())
            .addQueryParameter("channels", channels.toString())
            .addQueryParameter("bits", bitsPerSample.toString())
            .addQueryParameter("format", "wav")
            .addQueryParameter("capturedAt", capturedAt)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(audioFile.asRequestBody(WAV_MEDIA_TYPE))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val thoughtId = response.header("Location")?.substringAfterLast('/')
                    if (thoughtId.isNullOrBlank()) {
                        UploadResult.Failure("${response.code} response with no Location header", retryable = false)
                    } else {
                        UploadResult.Success(thoughtId)
                    }
                } else {
                    UploadResult.Failure("server responded ${response.code}", retryable = response.code >= 500)
                }
            }
        } catch (e: IOException) {
            UploadResult.Failure(e.message ?: "network error", retryable = true)
        }
    }
}
