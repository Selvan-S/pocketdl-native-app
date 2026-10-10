package com.pocketdl.app.download.service

/**
 * Sealed result representing the outcome of a foreground service promotion attempt.
 */
sealed interface ServiceForegroundResult {
    data object Idle : ServiceForegroundResult
    data class Starting(val sessionId: Long) : ServiceForegroundResult
    data class Success(val sessionId: Long) : ServiceForegroundResult
    data class Failed(val sessionId: Long, val exception: Throwable) : ServiceForegroundResult
}
