package com.focusguard.app.service.handlers

import com.focusguard.app.overlay.manager.OverlayBlockType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockTriggerHoldPolicyTest {

    @Test
    fun hardAppBlocksDoNotReleaseBeforeCountdownWhenRedirectingHome() {
        val hardBlockTypes = listOf(
            OverlayBlockType.APP_BLOCK,
            OverlayBlockType.TIME_LIMIT_BLOCK,
            OverlayBlockType.SCHEDULE_BLOCK,
            OverlayBlockType.CLONER_BLOCK
        )

        hardBlockTypes.forEach { blockType ->
            assertFalse(BlockTrigger.shouldHoldWhileForeground(blockType, exitsToHome = true))
            assertTrue(BlockTrigger.shouldHoldWhileForeground(blockType, exitsToHome = false))
        }
    }

    @Test
    fun transientContentAndTamperBlocksStillAutoDismiss() {
        val transientBlockTypes = listOf(
            OverlayBlockType.KEYWORD_BLOCK,
            OverlayBlockType.SHORT_OR_REEL_BLOCK,
            OverlayBlockType.INCOGNITO_BLOCK,
            OverlayBlockType.UNINSTALL_ATTEMPT,
            OverlayBlockType.SETTINGS_TAMPER,
            OverlayBlockType.SESSION_LOCK_BLOCK
        )

        transientBlockTypes.forEach { blockType ->
            assertFalse(BlockTrigger.shouldHoldWhileForeground(blockType, exitsToHome = false))
        }
    }

    @Test
    fun allNonSessionBlocksStepBackThenExitHomeAfterOverlayAttach() {
        val exitTypes = listOf(
            OverlayBlockType.APP_BLOCK,
            OverlayBlockType.TIME_LIMIT_BLOCK,
            OverlayBlockType.SCHEDULE_BLOCK,
            OverlayBlockType.SHORT_OR_REEL_BLOCK,
            OverlayBlockType.KEYWORD_BLOCK,
            OverlayBlockType.INCOGNITO_BLOCK,
            OverlayBlockType.UNINSTALL_ATTEMPT,
            OverlayBlockType.SETTINGS_TAMPER,
            OverlayBlockType.CLONER_BLOCK
        )

        exitTypes.forEach { blockType ->
            assertTrue(BlockTrigger.shouldStepBackBeforeOverlaySurface(blockType))
            assertTrue(BlockTrigger.shouldExitToHomeAfterAttach(blockType))
        }
    }

    @Test
    fun sessionLockDoesNotStepBackOrAutoExitHome() {
        assertFalse(BlockTrigger.shouldStepBackBeforeOverlaySurface(OverlayBlockType.SESSION_LOCK_BLOCK))
        assertFalse(BlockTrigger.shouldExitToHomeAfterAttach(OverlayBlockType.SESSION_LOCK_BLOCK))
    }
}
