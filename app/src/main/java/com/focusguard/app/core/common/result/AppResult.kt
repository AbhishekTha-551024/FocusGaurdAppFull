package com.focusguard.app.core.common.result

/**
 * Structured App Error hierarchy.
 * Every error has a unique diagnostic code and message.
 */
sealed class AppError(
    val code: String,
    val message: String,
    val cause: Throwable? = null
) {
    // Database errors (DB-xxx)
    data class DatabaseError(
        val errorCode: String = "DB-001",
        val errorMessage: String,
        val throwable: Throwable? = null
    ) : AppError(errorCode, errorMessage, throwable)

    // Permission errors (PERM-xxx)
    data class PermissionDenied(
        val permission: String,
        val errorCode: String = "PERM-001",
        val errorMessage: String = "Required permission was denied: $permission"
    ) : AppError(errorCode, errorMessage)

    // Blocking rule errors (BLK-xxx)
    data class BlockingError(
        val errorCode: String = "BLK-001",
        val errorMessage: String,
        val throwable: Throwable? = null
    ) : AppError(errorCode, errorMessage, throwable)

    // Network / Sync errors (NET-xxx)
    data class NetworkError(
        val errorCode: String = "NET-001",
        val errorMessage: String,
        val throwable: Throwable? = null
    ) : AppError(errorCode, errorMessage, throwable)

    // Auth errors (AUTH-xxx)
    data class AuthError(
        val errorCode: String = "AUTH-001",
        val errorMessage: String,
        val throwable: Throwable? = null
    ) : AppError(errorCode, errorMessage, throwable)

    // Security / Tamper errors (SEC-xxx)
    data class SecurityError(
        val errorCode: String = "SEC-001",
        val errorMessage: String,
        val throwable: Throwable? = null
    ) : AppError(errorCode, errorMessage, throwable)

    // Validation errors (VAL-xxx)
    data class ValidationError(
        val errorMessage: String,
        val errorCode: String = "VAL-001",
        val throwable: Throwable? = null
    ) : AppError(errorCode, errorMessage, throwable)

    // Generic / Unknown errors (GEN-xxx)
    data class UnknownError(
        val errorCode: String = "GEN-999",
        val errorMessage: String = "An unexpected error occurred",
        val throwable: Throwable? = null
    ) : AppError(errorCode, errorMessage, throwable)
}

/**
 * Standard Result wrapper for Repository and UseCase layer boundaries.
 */
sealed interface AppResult<out T> {
    data class Success<out T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    fun errorOrNull(): AppError? = when (this) {
        is Success -> null
        is Failure -> error
    }
}

/**
 * Helper to execute a suspending block safely and map any exceptions to AppResult.
 */
inline fun <T> runCatchingAppResult(
    errorCode: String = "GEN-001",
    errorMessage: String = "Operation failed",
    block: () -> T
): AppResult<T> {
    return try {
        AppResult.Success(block())
    } catch (e: Exception) {
        AppResult.Failure(AppError.UnknownError(errorCode = errorCode, errorMessage = errorMessage, throwable = e))
    }
}
