package com.focusguard.app.feature.auth.domain.usecase

import android.content.Context
import com.focusguard.app.core.model.AuthUser
import com.focusguard.app.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject

class SignInWithGoogleUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(activityContext: Context): Result<AuthUser> {
        return repository.signInWithGoogle(activityContext)
    }
}
