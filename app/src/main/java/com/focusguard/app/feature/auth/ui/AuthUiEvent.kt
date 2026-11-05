package com.focusguard.app.feature.auth.ui

import android.content.Context

/**
 * UI Events for Authentication operations.
 */
sealed interface AuthUiEvent {
    data class SignInWithGoogle(val context: Context) : AuthUiEvent
    data class SignOut(val context: Context) : AuthUiEvent
    data object ClearError : AuthUiEvent
}
