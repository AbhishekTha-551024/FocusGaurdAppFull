package com.focusguard.app.feature.settings.ui

import android.content.Context
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.ChangeType
import com.focusguard.app.core.model.ActiveChallengeState
import com.focusguard.app.feature.settings.domain.SettingsFeedUseCase
import com.focusguard.app.feature.settings.domain.SettingsOverlayUseCase
import com.focusguard.app.feature.settings.domain.SettingsProtectionUseCase

object SettingsChallengeHandler {

    suspend fun resolveChallengeSuccess(
        challenge: ActiveChallengeState,
        context: Context,
        feedUseCase: SettingsFeedUseCase,
        overlayUseCase: SettingsOverlayUseCase,
        protectionUseCase: SettingsProtectionUseCase
    ) {
        when (challenge.changeType) {
            ChangeType.DISABLE_DISTRACTION_FEED -> feedUseCase.disableFeed(challenge.targetId)
            ChangeType.LOWER_COUNTDOWN -> {
                val targetSec = challenge.targetPayload?.toIntOrNull() ?: 5
                overlayUseCase.setBlockCountdownSeconds(targetSec)
            }
            else -> protectionUseCase.handleChallengeSuccess(challenge, context)
        }
    }

    fun createRevokePartnerChallenge(partnershipId: String, remainingDays: Long): ActiveChallengeState {
        val desc = if (remainingDays > 0) {
            "revoke your Accountability Partner ($remainingDays days remaining in timed lock) and disable protection"
        } else {
            "revoke your Accountability Partner and disable protection"
        }
        return ActiveChallengeState(
            changeType = ChangeType.CHANGE_CHALLENGE_TYPE,
            targetId = partnershipId,
            targetName = "Accountability Partner",
            targetPayload = "REVOKE_PARTNER",
            description = desc
        )
    }

    fun createUninstallChallenge(remainingDays: Long): ActiveChallengeState {
        val desc = if (remainingDays > 0) {
            "turn off protection ($remainingDays days remaining) and permanently uninstall FocusGuard"
        } else {
            "turn off protection and permanently uninstall FocusGuard"
        }
        return ActiveChallengeState(
            changeType = ChangeType.UNINSTALL_REQUEST,
            targetId = "uninstall_app",
            targetName = "Uninstall FocusGuard",
            targetPayload = "LAUNCH_UNINSTALL",
            description = desc
        )
    }

    fun createLowerCountdownChallenge(currentSeconds: Int, targetSeconds: Int): ActiveChallengeState {
        return ActiveChallengeState(
            changeType = ChangeType.LOWER_COUNTDOWN,
            targetId = "countdown_seconds",
            targetName = "Overlay Countdown",
            targetPayload = targetSeconds.toString(),
            description = "decrease block screen countdown from ${currentSeconds}s to ${targetSeconds}s"
        )
    }

    fun createRemoveChallengeState(remainingDays: Long): ActiveChallengeState {
        val desc = if (remainingDays > 0) {
            "disable Sentence Typing Challenge ($remainingDays days remaining in timed lock) and remove friction barrier"
        } else {
            "disable Sentence Typing Challenge and remove friction barrier"
        }
        return ActiveChallengeState(
            changeType = ChangeType.CHANGE_CHALLENGE_TYPE,
            targetId = "remove_challenge",
            targetName = "Sentence Typing Challenge",
            targetPayload = "REMOVE_CHALLENGE",
            description = desc
        )
    }

    fun createChangeChallengeState(type: ChallengeType, sentence: String, delayHours: Int): ActiveChallengeState {
        val payload = "${type.name}|$sentence|$delayHours"
        return ActiveChallengeState(
            changeType = ChangeType.CHANGE_CHALLENGE_TYPE,
            targetId = "challenge_type",
            targetName = "Accountability Challenge",
            targetPayload = payload,
            description = "change accountability challenge to ${type.name}"
        )
    }
}
