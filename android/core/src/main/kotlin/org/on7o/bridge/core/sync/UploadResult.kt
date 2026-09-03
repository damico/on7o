package org.on7o.bridge.core.sync

/** Outcome of uploading one local capture to the server. */
sealed class UploadResult {
    data class Success(val thoughtId: String) : UploadResult()
    data class Failure(val message: String, val retryable: Boolean) : UploadResult()
}
