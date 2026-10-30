package com.focusguard.app.feature.auth.ui

import com.focusguard.app.core.model.AuthUser

/**
 * Immutable UI State for Authentication operations.
 */
data class AuthUiState(
    val user: AuthUser? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAuthResolved: Boolean = false
)
