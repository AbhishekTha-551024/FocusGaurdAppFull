package com.focusguard.app.recovery

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.focusguard.app.core.datastore.DirectBootStorage

private const val TAG = "FG:Recovery"

/** Key in the device-protected mirror holding the last time we deliberately re-wrote the setting. */
private const val KEY_LAST_ASSERT_AT = "last_a11y_assert_at"

/**
 * Minimum gap between two deliberate re-writes of ENABLED_ACCESSIBILITY_SERVICES while the
 * setting already looks correct. The write exists only to kick a stalled system_server, and
 * repeating it sooner makes system_server re-bind (and Vivo's watchdog un-bind) in a loop.
 */
private const val ASSERT_KICK_COOLDOWN_MS = 5_000L

/**
 * Programmatic enabler for Accessibility Service using WRITE_SECURE_SETTINGS.
 * Extracted from AccessibilityRecoveryHelper to maintain single responsibility.
 */
object ProgrammaticAccessibilityEnabler {

    /**
     * LAYER 1: Attempts silent, programmatic re-enablement of FocusGuardAccessibilityService
     * using WRITE_SECURE_SETTINGS permission.
     *
     * Returns true if successfully enabled or already enabled, false otherwise.
     */
    fun tryEnableAccessibilityProgrammatically(context: Context): Boolean {
        val targetComponent = AccessibilityStatusChecker.targetComponent(context)
        val shortComponent = AccessibilityStatusChecker.shortComponent(context)
        return try {
            val contentResolver = context.contentResolver
            val currentServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""

            // A list that still contains us is a known-good snapshot of every other service the
            // user enabled; remember it so a later OEM-blanked read cannot wipe them (see EnabledServicesMirror).
            val isListed = AccessibilityStatusChecker.isComponentEnabledInSetting(
                currentServices,
                targetComponent,
                shortComponent
            )
            if (isListed) {
                EnabledServicesMirror.save(context, currentServices)
            }

            val masterEnabled = Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            ) == 1

            // Re-write only when something is actually missing, or when the cooldown expired and a
            // kick is due. Writing identical values on every recovery attempt restarts system_server
            // rebinds, which on Vivo ends with the service unbound seconds later. The cooldown is
            // measured on elapsedRealtime and discarded after a reboot (now < stored value).
            // DE storage: this runs from LOCKED_BOOT_COMPLETED, where CE SharedPreferences throw.
            val prefs = DirectBootStorage.deCache(context)
            val lastAssertAt = prefs.getLong(KEY_LAST_ASSERT_AT, 0L)
            val now = SystemClock.elapsedRealtime()
            val kickDue = lastAssertAt <= 0L ||
                now < lastAssertAt ||
                now - lastAssertAt >= ASSERT_KICK_COOLDOWN_MS

            if (isListed && masterEnabled && !kickDue) {
                Log.i(TAG, "Accessibility already asserted in Settings; skipping duplicate write.")
                return true
            }

            if (!isListed || kickDue) {
                val newServices = mergeEnabledServices(
                    currentServices = currentServices,
                    preservedServices = EnabledServicesMirror.load(context),
                    targetComponent = targetComponent
                )
                Settings.Secure.putString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    newServices
                )
            }

            // Ensure the master accessibility toggle is ON
            if (!masterEnabled) {
                Settings.Secure.putString(
                    contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    "1"
                )
            }

            prefs.edit().putLong(KEY_LAST_ASSERT_AT, now).apply()

            // A successful putString is not proof the system kept the value: Vivo's scanner can
            // re-blank the setting milliseconds later. Read back so the caller knows whether to
            // keep the binding-verification pass alive or escalate.
            val verified = AccessibilityStatusChecker.isComponentEnabledInSetting(
                Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ),
                targetComponent,
                shortComponent
            ) && Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            ) == 1

            if (verified) {
                Log.i(TAG, "Successfully asserted AccessibilityService programmatically via WRITE_SECURE_SETTINGS!")
            } else {
                Log.w(TAG, "Secure write did not stick (OEM scan or provider race); caller must verify the binding.")
            }
            verified
        } catch (e: SecurityException) {
            Log.d(TAG, "WRITE_SECURE_SETTINGS not granted: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to programmatically enable accessibility: ${e.message}", e)
            false
        }
    }

    /**
     * Pure logic helper for appending target component to existing enabled accessibility services list.
     */
    fun buildEnabledServicesString(
        currentServices: String?,
        targetComponent: String
    ): String = mergeEnabledServices(currentServices, null, targetComponent)

    /**
     * Pure logic helper that rebuilds the enabled-services list without losing the other services.
     *
     * The current secure setting only replaces the fallback while it actually contains entries: an
     * OEM tool can blank it for a few seconds, and writing that blank value back would delete every
     * other accessibility service the user enabled.
     */
    fun mergeEnabledServices(
        currentServices: String?,
        preservedServices: String?,
        targetComponent: String
    ): String {
        val current = currentServices?.trim().orEmpty()
        val source = current.ifBlank { preservedServices?.trim().orEmpty() }
        return when {
            source.contains(targetComponent) -> source
            source.isBlank() -> targetComponent
            else -> "$source:$targetComponent"
        }
    }

    /**
     * Pure logic helper: [currentServices] without FocusGuard, keeping every other entry intact.
     *
     * Blank input yields blank output. An OEM-blanked read is not evidence that the list is empty,
     * so callers must never write that blank value back (it would delete the user's other services).
     */
    fun removeComponent(
        currentServices: String?,
        targetComponent: String,
        shortComponent: String
    ): String = currentServices.orEmpty()
        .split(":")
        .map { it.trim() }
        .filter { item ->
            item.isNotEmpty() &&
                    !item.equals(targetComponent, ignoreCase = true) &&
                    !item.equals(shortComponent, ignoreCase = true)
        }
        .joinToString(":")
}
