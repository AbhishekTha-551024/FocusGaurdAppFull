package com.focusguard.app.feature.auth.domain.repository

import android.content.Context
import com.focusguard.app.core.model.AuthUser
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository contract for Authentication operations.
 */
interface AuthRepository {
    val currentUser: Flow<AuthUser?>
    val isLoggedIn: Flow<Boolean>
    val syncCurrentUser: AuthUser?

    suspend fun signInWithGoogle(activityContext: Context): Result<AuthUser>
    suspend fun signOut(activityContext: Context): Result<Unit>
    fun isSignOutLocked(): Boolean
    fun getSignOutLockReason(): String?
    suspend fun syncUserDeviceToFirestore(user: AuthUser, deviceId: String, force: Boolean = false)
}
