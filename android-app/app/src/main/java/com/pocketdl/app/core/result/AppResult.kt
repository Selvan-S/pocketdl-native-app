package com.pocketdl.app.core.result

/**
 * Sealed class representing domain/data operation outcomes.
 */
sealed interface AppResult<out T> {
    data class Success<out T>(val data: T) : AppResult<T>
    data class Error(val exception: Throwable, val message: String? = null) : AppResult<Nothing>
    object Loading : AppResult<Nothing>
}
