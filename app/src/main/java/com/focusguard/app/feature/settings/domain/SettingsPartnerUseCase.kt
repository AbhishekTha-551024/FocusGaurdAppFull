package com.focusguard.app.feature.settings.domain

import com.focusguard.app.core.database.entity.PartnershipEntity
import com.focusguard.app.core.model.PartnershipStatus
import com.focusguard.app.data.repository.PartnershipRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsPartnerUseCase @Inject constructor(
    private val partnershipRepository: PartnershipRepository
) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val partnerDataFlow: Flow<Triple<FirebaseUser?, PartnershipEntity?, PartnershipEntity?>> =
        partnershipRepository.currentUser.flatMapLatest { user ->
            val userEmail = user?.email?.lowercase()
            if (user != null && !userEmail.isNullOrBlank()) {
                partnershipRepository.observePartnershipForUser(user.uid, userEmail)
                    .map { entity ->
                        val isIncoming = entity != null &&
                                entity.partnerEmail == userEmail &&
                                entity.status == PartnershipStatus.PENDING_INVITE
                        val incoming = if (isIncoming) entity else null
                        val current = if (!isIncoming) entity else null
                        Triple(user, current, incoming)
                    }
            } else {
                flowOf(Triple(null, null, null))
            }
        }

    suspend fun sendInvite(email: String): Result<String> {
        when (val validation = com.focusguard.app.core.common.validator.InputValidator.validateEmail(email)) {
            is com.focusguard.app.core.common.validator.ValidationResult.Invalid -> {
                return Result.failure(IllegalArgumentException(validation.error.message))
            }
            com.focusguard.app.core.common.validator.ValidationResult.Valid -> { /* proceed */ }
        }
        return partnershipRepository.sendInvite(email.trim().lowercase())
    }
    suspend fun cancelInvite(partnershipId: String): Result<Unit> = partnershipRepository.declineInvite(partnershipId)
    suspend fun acceptInvite(partnershipId: String): Result<Unit> = partnershipRepository.acceptInvite(partnershipId)
    suspend fun declineInvite(partnershipId: String): Result<Unit> = partnershipRepository.declineInvite(partnershipId)
    suspend fun revokePartnership(partnershipId: String): Result<Unit> = partnershipRepository.revokePartnership(partnershipId)
}
