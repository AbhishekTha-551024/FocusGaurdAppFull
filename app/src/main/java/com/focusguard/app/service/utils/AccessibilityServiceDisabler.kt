package com.focusguard.app.service.utils

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.focusguard.app.recovery.AccessibilityRecoveryHelper
import com.focusguard.app.recovery.AccessibilityStatusChecker
import com.focusguard.app.recovery.EnabledServicesMirror
import com.focusguard.app.recovery.ProgrammaticAccessibilityEnabler

/**
 * Deliberate removal path used only for a clean uninstall.
 *
 * Recovery never calls this; it exists so the app can hand the accessibility setting back to the
 * user instead of leaving a dead component behind after the APK is gone.
 */
object AccessibilityServiceDisabler {

    private const val TAG = "FocusGuardService"

    fun removeFromEnabledServices(context: Context, serviceClassName: String) {
        try {
            if (!AccessibilityRecoveryHelper.canWriteSecureSettings(context)) {
                Log.w(TAG, "Cannot remove accessibility component: WRITE_SECURE_SETTINGS is not granted.")
                return
            }

            val contentResolver = context.contentResolver
            val current = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()

            // Blank is ambiguous (OEM scan in progress vs genuinely empty). Writing it back would
            // delete every other accessibility service the user enabled, so treat it as no-op.
            if (current.isBlank()) {
                Log.d(TAG, "Enabled-services setting is blank; nothing to remove.")
                return
            }

            val targetComponent = "${context.packageName}/$serviceClassName"
            val shortComponent = AccessibilityStatusChecker.shortComponent(context)
            if (!AccessibilityStatusChecker.isComponentEnabledInSetting(
                    current,
                    targetComponent,
                    shortComponent
                )
            ) {
                Log.d(TAG, "FocusGuard is not listed in enabled accessibility services; nothing to remove.")
                return
            }

            val updated = ProgrammaticAccessibilityEnabler.removeComponent(
                currentServices = current,
                targetComponent = targetComponent,
                shortComponent = shortComponent
            )
            Settings.Secure.putString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                updated
            )

            // Forget our component in the fallback list too, otherwise a later re-assert would
            // resurrect a service the user deliberately removed.
            EnabledServicesMirror.save(context, updated)

            // The master toggle is global: switch it off only when no service is left at all.
            if (updated.isEmpty()) {
                Settings.Secure.putString(
                    contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    "0"
                )
            }
            Log.i(TAG, "Removed FocusGuard from ENABLED_ACCESSIBILITY_SERVICES programmatically")
        } catch (e: Exception) {
            Log.w(TAG, "Could not remove from Secure.Settings: ${e.message}")
        }
    }
}
