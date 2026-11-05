package com.focusguard.app.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UninstallTeardownPolicyTest
 * ===========================
 * Unit tests for [UninstallTeardownPolicy] / [OwedTeardown].
 *
 * Uninstalling FocusGuard requires stripping its Device Administrator and Accessibility Service
 * BEFORE the system dialog is shown, and that dialog reports nothing back. The rules below decide
 * when "we are still alive" means "the user cancelled", and therefore when the stripped privileges
 * have to be given back.
 */
class UninstallTeardownPolicyTest {

    private val graceMs = 45_000L

    private fun pending(
        enabled: Boolean = true,
        days: Int = 0,
        expiryWallMs: Long = 0L,
        requestedAtElapsedMs: Long = 1_000L
    ) = OwedTeardown(enabled, days, expiryWallMs, requestedAtElapsedMs)

    @Test
    fun noMarkerMeansNoDecision() {
        assertEquals(
            "Nothing was torn down, so nothing may be re-armed",
            TeardownVerdict.NOTHING_PENDING,
            UninstallTeardownPolicy.decide(pending = null, nowElapsedMs = 999_999L, graceMs = graceMs)
        )
    }

    @Test
    fun protectionThatWasOffIsHonoured() {
        assertFalse(
            "A user with Uninstall Protection OFF declared that end state; a cancelled uninstall must not re-arm it",
            UninstallTeardownPolicy.shouldArm(protectionWasEnabled = false)
        )
        assertEquals(
            TeardownVerdict.NOTHING_PENDING,
            UninstallTeardownPolicy.decide(
                pending = pending(enabled = false),
                nowElapsedMs = 9_999_999L,
                graceMs = graceMs
            )
        )
    }

    @Test
    fun protectionThatWasOnIsWorthWatching() {
        assertTrue(UninstallTeardownPolicy.shouldArm(protectionWasEnabled = true))
    }

    @Test
    fun theCheckWaitsWhileTheUninstallerMayStillBeOpen() {
        assertEquals(
            "Inside the grace window the user could still be reading the dialog",
            TeardownVerdict.WAIT_FOR_UNINSTALLER,
            UninstallTeardownPolicy.decide(pending = pending(), nowElapsedMs = 1_000L + graceMs - 1L, graceMs = graceMs)
        )
    }

    @Test
    fun survivingTheWholeGraceWindowProvesACancel() {
        assertEquals(
            "A real uninstall kills this process, so surviving the window means the package stayed",
            TeardownVerdict.RESTORE,
            UninstallTeardownPolicy.decide(pending = pending(), nowElapsedMs = 1_000L + graceMs, graceMs = graceMs)
        )
    }

    @Test
    fun aPendingRestoreCanBeRetriedUntilItsMarkerIsCleared() {
        val retainedMarker = pending()

        repeat(2) {
            assertEquals(
                "A failed restoration must leave the same marker eligible for another attempt",
                TeardownVerdict.RESTORE,
                UninstallTeardownPolicy.decide(
                    pending = retainedMarker,
                    nowElapsedMs = 1_000L + graceMs,
                    graceMs = graceMs
                )
            )
        }
    }

    @Test
    fun aMarkerFromAPreviousBootRestoresImmediately() {
        assertEquals(
            "elapsedRealtime restarted, and a reboot is itself proof the uninstall never completed",
            TeardownVerdict.RESTORE,
            UninstallTeardownPolicy.decide(
                pending = pending(requestedAtElapsedMs = 800_000L),
                nowElapsedMs = 30_000L,
                graceMs = graceMs
            )
        )
    }

    @Test
    fun aZeroGraceNeverStrandsAMarker() {
        assertEquals(
            TeardownVerdict.RESTORE,
            UninstallTeardownPolicy.decide(pending = pending(), nowElapsedMs = 1_000L, graceMs = 0L)
        )
    }

    @Test
    fun aTimedLockKeepsItsOwnDeadline() {
        assertTrue(
            "Days plus a real expiry is the only shape that has a deadline to restore",
            pending(days = 7, expiryWallMs = 5_000L).isTimedLock
        )
        assertFalse(
            "Untimed protection is a flag alone: there is no deadline to carry back",
            pending(days = 0, expiryWallMs = 0L).isTimedLock
        )
        assertFalse(
            "Corrupt timed state (days without expiry) must not be restored as a timed lock",
            pending(days = 7, expiryWallMs = 0L).isTimedLock
        )
        assertFalse(
            "Protection that was off has nothing owed, timed or not",
            pending(enabled = false, days = 7, expiryWallMs = 5_000L).isTimedLock
        )
    }

    @Test
    fun crossBootDetectionOnlyTriggersOnAReboot() {
        assertTrue(UninstallTeardownPolicy.armedInEarlierBoot(requestedAtElapsedMs = 60_000L, nowElapsedMs = 59_999L))
        assertFalse(UninstallTeardownPolicy.armedInEarlierBoot(requestedAtElapsedMs = 60_000L, nowElapsedMs = 60_000L))
        assertFalse(UninstallTeardownPolicy.armedInEarlierBoot(requestedAtElapsedMs = 60_000L, nowElapsedMs = 60_001L))
    }
}
