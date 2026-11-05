package com.focusguard.app.feature.settings

import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.data.repository.ProtectionPermissionStatus
import com.focusguard.app.feature.settings.data.repository.ProtectionRepositoryImpl
import com.focusguard.app.feature.settings.ui.SettingsUiStateBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionRepositoryLogicTest {

    // ─────────────────────────────────────────────────────────────────────────
    // isProtectionReallyActive Unit Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `untimed protection is permanently active when enabled`() {
        val now = 1_700_000_000_000L
        val active = ProtectionRepositoryImpl.isProtectionReallyActive(
            enabled = true,
            days = 0,
            expiry = 0L,
            now = now
        )
        assertTrue("Untimed protection (days=0, expiry=0) must be active", active)
    }

    @Test
    fun `timed protection is active before expiry`() {
        val now = 1_700_000_000_000L
        val futureExpiry = now + 7 * 24 * 60 * 60 * 1000L
        val active = ProtectionRepositoryImpl.isProtectionReallyActive(
            enabled = true,
            days = 7,
            expiry = futureExpiry,
            now = now
        )
        assertTrue("Timed protection before expiry must be active", active)
    }

    @Test
    fun `timed protection is inactive after expiry`() {
        val now = 1_700_000_000_000L
        val pastExpiry = now - 1_000L
        val active = ProtectionRepositoryImpl.isProtectionReallyActive(
            enabled = true,
            days = 7,
            expiry = pastExpiry,
            now = now
        )
        assertFalse("Timed protection after expiry must be inactive", active)
    }

    @Test
    fun `protection is inactive when disabled regardless of days and expiry`() {
        val now = 1_700_000_000_000L
        val activeUntimed = ProtectionRepositoryImpl.isProtectionReallyActive(
            enabled = false,
            days = 0,
            expiry = 0L,
            now = now
        )
        assertFalse("Disabled untimed protection must be inactive", activeUntimed)

        val activeTimed = ProtectionRepositoryImpl.isProtectionReallyActive(
            enabled = false,
            days = 7,
            expiry = now + 100_000L,
            now = now
        )
        assertFalse("Disabled timed protection must be inactive", activeTimed)
    }

    @Test
    fun `timed protection with corrupt expiry is inactive`() {
        val now = 1_700_000_000_000L
        val activeZeroExpiry = ProtectionRepositoryImpl.isProtectionReallyActive(
            enabled = true,
            days = 7,
            expiry = 0L,
            now = now
        )
        assertFalse("Timed protection with 0L expiry is corrupt and must be inactive", activeZeroExpiry)

        val activeNegativeExpiry = ProtectionRepositoryImpl.isProtectionReallyActive(
            enabled = true,
            days = 7,
            expiry = -100L,
            now = now
        )
        assertFalse("Timed protection with negative expiry must be inactive", activeNegativeExpiry)
    }

    @Test
    fun `negative days treated as untimed permanent protection`() {
        val now = 1_700_000_000_000L
        val activeNegativeDaysZeroExpiry = ProtectionRepositoryImpl.isProtectionReallyActive(
            enabled = true,
            days = -1,
            expiry = 0L,
            now = now
        )
        assertTrue("Negative days with 0L expiry must be treated as untimed permanent", activeNegativeDaysZeroExpiry)

        val activeNegativeDaysWithExpiry = ProtectionRepositoryImpl.isProtectionReallyActive(
            enabled = true,
            days = -5,
            expiry = now + 100_000L,
            now = now
        )
        assertTrue("Negative days with expiry must be treated as untimed permanent", activeNegativeDaysWithExpiry)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SettingsUiStateBuilder Untimed & Timed Tests
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildArgs(snapshot: FocusGuardPreferences.SettingsSnapshot): Array<Any?> {
        return arrayOf(
            snapshot,
            ProtectionPermissionStatus(),
            null,
            false,
            false,
            false,
            "",
            null,
            false,
            false,
            null
        )
    }

    @Test
    fun `SettingsUiStateBuilder does not expire untimed protection`() {
        val untimedSnapshot = FocusGuardPreferences.SettingsSnapshot(
            isUninstallProtectionEnabled = true,
            uninstallProtectionDays = 0,
            uninstallProtectionExpiryTimestamp = 0L
        )
        val (state, isTimedLockExpired) = SettingsUiStateBuilder.buildBaseState(
            buildArgs(untimedSnapshot),
            uptimeProvedDurationElapsed = false,
            remainingUptimeMs = 0L
        )

        assertFalse("Untimed protection must not be marked as expired", isTimedLockExpired)
        assertTrue("Effective protection must remain enabled for untimed protection", state.isUninstallProtectionEnabled)
        assertEquals("Remaining protection days for untimed must be 0", 0L, state.remainingProtectionDays)
    }

    @Test
    fun `SettingsUiStateBuilder keeps active timed protection enabled`() {
        val future = System.currentTimeMillis() + 86_400_000L * 5
        val timedSnapshot = FocusGuardPreferences.SettingsSnapshot(
            isUninstallProtectionEnabled = true,
            uninstallProtectionDays = 5,
            uninstallProtectionExpiryTimestamp = future
        )
        val (state, isTimedLockExpired) = SettingsUiStateBuilder.buildBaseState(
            buildArgs(timedSnapshot),
            uptimeProvedDurationElapsed = false,
            remainingUptimeMs = 86_400_000L * 2
        )

        assertFalse("Active timed protection must not be marked expired", isTimedLockExpired)
        assertTrue("Effective protection must be enabled for active timed protection", state.isUninstallProtectionEnabled)
        assertTrue("Remaining protection days must be > 0", state.remainingProtectionDays > 0)
    }

    @Test
    fun `SettingsUiStateBuilder marks expired timed protection as expired`() {
        val past = System.currentTimeMillis() - 5_000L
        val expiredSnapshot = FocusGuardPreferences.SettingsSnapshot(
            isUninstallProtectionEnabled = true,
            uninstallProtectionDays = 3,
            uninstallProtectionExpiryTimestamp = past
        )
        val (state, isTimedLockExpired) = SettingsUiStateBuilder.buildBaseState(
            buildArgs(expiredSnapshot),
            uptimeProvedDurationElapsed = true,
            remainingUptimeMs = 0L
        )

        assertTrue("Past expiry with the full duration run must be marked expired", isTimedLockExpired)
        assertFalse("Effective protection must be disabled when expired", state.isUninstallProtectionEnabled)
    }

    @Test
    fun `SettingsUiStateBuilder keeps a clock-forwarded lock armed until the uptime ledger agrees`() {
        val past = System.currentTimeMillis() - 86_400_000L
        val clockForwardedSnapshot = FocusGuardPreferences.SettingsSnapshot(
            isUninstallProtectionEnabled = true,
            uninstallProtectionDays = 3,
            uninstallProtectionExpiryTimestamp = past
        )
        val (state, isTimedLockExpired) = SettingsUiStateBuilder.buildBaseState(
            buildArgs(clockForwardedSnapshot),
            uptimeProvedDurationElapsed = false,
            remainingUptimeMs = 86_400_000L * 2
        )

        assertFalse(
            "Moving the wall clock past the expiry must not expire a lock the ledger says is running",
            isTimedLockExpired
        )
        assertTrue("Protection stays armed against the clock move", state.isUninstallProtectionEnabled)
        assertTrue(
            "Countdown follows the monotonic ledger, not the moved clock",
            state.remainingProtectionDays >= 2L
        )
    }

    @Test
    fun `SettingsUiStateBuilder still disarms corrupt timed state without uptime proof`() {
        val corruptSnapshot = FocusGuardPreferences.SettingsSnapshot(
            isUninstallProtectionEnabled = true,
            uninstallProtectionDays = 3,
            uninstallProtectionExpiryTimestamp = 0L
        )
        val (state, isTimedLockExpired) = SettingsUiStateBuilder.buildBaseState(
            buildArgs(corruptSnapshot),
            uptimeProvedDurationElapsed = false,
            remainingUptimeMs = 0L
        )

        assertTrue("Timed state with no expiry timestamp stays corrupt and expired", isTimedLockExpired)
        assertFalse("Corrupt timed state must not report active protection", state.isUninstallProtectionEnabled)
    }
}

