package com.focusguard.app.core.model

/**
 * Model representing an authenticated user session in FocusGuard.
 */
data class AuthUser(
    val id: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
    val idToken: String? = null
) {
    val displayInitial: String
        get() = displayName?.trim()?.firstOrNull()?.uppercase()
            ?: email?.trim()?.firstOrNull()?.uppercase()
            ?: "U"
}
