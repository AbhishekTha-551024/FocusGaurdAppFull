package com.focusguard.app.feature.auth.domain.usecase

import com.focusguard.app.core.model.AuthUser
import com.focusguard.app.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCurrentUserUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    val currentUser: Flow<AuthUser?> get() = repository.currentUser
    val syncCurrentUser: AuthUser? get() = repository.syncCurrentUser
    val isLoggedIn: Flow<Boolean> get() = repository.isLoggedIn
}
