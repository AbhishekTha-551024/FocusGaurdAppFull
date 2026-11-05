package com.focusguard.app.feature.auth.domain.usecase

import android.content.Context
import com.focusguard.app.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject

class SignOutUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(activityContext: Context): Result<Unit> {
        return repository.signOut(activityContext)
    }

    fun isSignOutLocked(): Boolean = repository.isSignOutLocked()

    fun getSignOutLockReason(): String? = repository.getSignOutLockReason()
}
