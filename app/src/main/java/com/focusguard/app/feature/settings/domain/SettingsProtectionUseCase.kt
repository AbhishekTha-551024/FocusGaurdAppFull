package com.focusguard.app.feature.settings.domain

import android.content.Context
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.ChangeType
import com.focusguard.app.core.model.ActiveChallengeState
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.data.repository.PartnershipRepository
import com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver
import com.focusguard.app.security.SupportVerificationResult
import com.focusguard.app.utils.UninstallHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsProtectionUseCase @Inject constructor(
    private val repository: FocusGuardRepository,
    private val partnershipRepository: PartnershipRepository
) {
    suspend fun startTimedProtection(days: Int) {
        val durationDays = when (val validation = com.focusguard.app.core.common.validator.InputValidator.validateUninstallDays(days)) {
            is com.focusguard.app.core.common.validator.ValidationResult.Invalid -> throw IllegalArgumentException(validation.error.message)
            com.focusguard.app.core.common.validator.ValidationResult.Valid -> days
        }
        repository.startTimedUninstallProtection(durationDays)
    }

    suspend fun disableProtection() {
        repository.setUninstallProtectionEnabled(false)
    }

    suspend fun handleChallengeSuccess(challenge: ActiveChallengeState, context: Context) {
        when (challenge.changeType) {
            ChangeType.UNINSTALL_REQUEST -> {
                repository.setUninstallProtectionEnabled(false)
                FocusGuardDeviceAdminReceiver.deactivateDeviceAdmin(context)
                if (challenge.targetPayload == "LAUNCH_UNINSTALL") {
                    UninstallHelper.performCleanUninstall(context)
                }
            }
            ChangeType.CHANGE_CHALLENGE_TYPE -> {
                if (challenge.targetPayload == "REMOVE_CHALLENGE") {
                    repository.setChallengeActive(false)
                    repository.setUninstallProtectionEnabled(false)
                    FocusGuardDeviceAdminReceiver.deactivateDeviceAdmin(context)
                } else if (challenge.targetPayload == "REVOKE_PARTNER") {
                    partnershipRepository.revokePartnership(challenge.targetId)
                    repository.setChallengeActive(false)
                    repository.setUninstallProtectionEnabled(false)
                    FocusGuardDeviceAdminReceiver.deactivateDeviceAdmin(context)
                } else {
                    val parts = challenge.targetPayload?.split("|") ?: emptyList()
                    if (parts.size >= 3) {
                        val newType = ChallengeType.valueOf(parts[0])
                        val sentence = parts[1]
                        val delay = parts[2].toIntOrNull() ?: 1
                        repository.setChallengeType(newType)
                        repository.setCommitmentSentence(sentence)
                        repository.setTimeDelayHours(delay)
                    }
                }
            }
            else -> {}
        }
    }

    suspend fun activateChallenge(type: ChallengeType, sentence: String, delayHours: Int) {
        repository.setChallengeType(type)
        repository.setCommitmentSentence(sentence)
        repository.setTimeDelayHours(delayHours)
        repository.setChallengeActive(true)
    }

    fun verifySupportCode(code: String): SupportVerificationResult =
        repository.verifySupportCode(code)

    suspend fun executeSupportAction(verified: SupportVerificationResult.Success): Boolean =
        repository.executeSupportAction(verified)
}
