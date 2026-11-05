package com.focusguard.app.feature.settings.domain

import android.content.Context
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.core.model.ChangeType
import com.focusguard.app.core.model.ActiveChallengeState
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.data.repository.PartnershipRepository
import com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver
import com.focusguard.app.recovery.UninstallTeardownGuard
import com.focusguard.app.security.SupportVerificationResult
import com.focusguard.app.utils.UninstallHelper
import javax.inject.Inject
import javax.inject.Singleton

/** Challenge payloads this use case branches on. */
private const val PAYLOAD_LAUNCH_UNINSTALL = "LAUNCH_UNINSTALL"
private const val PAYLOAD_REMOVE_CHALLENGE = "REMOVE_CHALLENGE"
private const val PAYLOAD_REVOKE_PARTNER = "REVOKE_PARTNER"

/**
 * Shown as "Type the sentence below to …" in the challenge dialog, so it reads as an instruction to
 * retry. Deliberately fixed wording: the underlying network exception can carry a document path.
 */
private const val PARTNER_REVOKE_RETRY_HINT =
    "retry revoking your Accountability Partner — it is unreachable right now, so nothing was changed"

@Singleton
class SettingsProtectionUseCase @Inject constructor(
    private val repository: FocusGuardRepository,
    private val partnershipRepository: PartnershipRepository,
    private val uninstallTeardownGuard: UninstallTeardownGuard
) {
    suspend fun startTimedProtection(days: Int) {
        val durationDays = when (val validation = com.focusguard.app.core.common.validator.InputValidator.validateUninstallDays(days)) {
            is com.focusguard.app.core.common.validator.ValidationResult.Invalid -> throw IllegalArgumentException(validation.error.message)
            com.focusguard.app.core.common.validator.ValidationResult.Valid -> days
        }
        repository.startTimedUninstallProtection(durationDays)
    }

    suspend fun disableProtection() {
        // A deliberate disable is final: drop any restore owed by an earlier cancelled uninstall,
        // otherwise resumeIfPending re-arms protection the user just switched off.
        uninstallTeardownGuard.discardPendingTeardown()
        repository.setUninstallProtectionEnabled(false)
    }

    suspend fun handleChallengeSuccess(
        challenge: ActiveChallengeState,
        context: Context
    ): ChallengeApplyResult {
        when (challenge.changeType) {
            ChangeType.UNINSTALL_REQUEST -> {
                val launchesUninstall = challenge.targetPayload == PAYLOAD_LAUNCH_UNINSTALL
                if (launchesUninstall) {
                    // Captured before the disarm, which erases the flag, the days and the deadline: this
                    // is the only moment the state a cancelled uninstall must restore is still readable.
                    uninstallTeardownGuard.armBeforeTeardown()
                } else {
                    // "Turn off Uninstall Protection" is not a teardown. Arming here made the toggle
                    // self-defeating: the marker survived, and the next app start re-armed protection.
                    uninstallTeardownGuard.discardPendingTeardown()
                }
                repository.setUninstallProtectionEnabled(false, challengeAuthorized = true)
                FocusGuardDeviceAdminReceiver.deactivateDeviceAdmin(context)
                if (launchesUninstall) {
                    UninstallHelper.performCleanUninstall(context)
                }
            }
            ChangeType.CHANGE_CHALLENGE_TYPE -> {
                if (challenge.targetPayload == PAYLOAD_REMOVE_CHALLENGE) {
                    repository.setChallengeActive(false)
                } else if (challenge.targetPayload == PAYLOAD_REVOKE_PARTNER) {
                    // Nothing below this line can be undone, so it must not run unless the partner was
                    // actually released. Offline or timeout: keep the challenge, the protection and the
                    // Device Admin exactly as they are and let the user retry the same challenge.
                    partnershipRepository.revokePartnership(challenge.targetId)
                        .onFailure { return ChallengeApplyResult.Retryable(PARTNER_REVOKE_RETRY_HINT) }
                    repository.setChallengeActive(false)
                    repository.setUninstallProtectionEnabled(false, challengeAuthorized = true)
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
        return ChallengeApplyResult.Applied
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
