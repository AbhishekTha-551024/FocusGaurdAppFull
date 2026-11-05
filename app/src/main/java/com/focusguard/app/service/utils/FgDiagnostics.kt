package com.focusguard.app.service.utils

import android.Manifest
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * FgDiagnostics
 * =============
 * Diagnostics helper logging service lifecycle intervals, system security settings,
 * and historical process exit reasons under the tag [TAG].
 *
 * Guarantees:
 * - All Settings.Secure queries and exit reason checks execute off the accessibility thread (Dispatchers.IO).
 * - Timings use monotonic [SystemClock.elapsedRealtime].
 * - Zero logging of sensitive data (URLs, passcodes, secrets, or third-party apps).
 */
object FgDiagnostics {

    const val TAG = "FG:Diag"

    private const val MAX_HISTORICAL_EXIT_REASONS = 3
    private const val DEFAULT_PID = 0
    private const val NO_TIMESTAMP = -1L

    @Volatile
    private var lastConnectRealtimeMs: Long = NO_TIMESTAMP

    private val diagScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Reads and logs historical process exit reasons from ActivityManager on API 30+.
     * Must be called on application start on a background dispatcher.
     */
    fun logHistoricalExitReasons(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        diagScope.launch {
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return@launch
                val reasons = am.getHistoricalProcessExitReasons(
                    context.packageName,
                    DEFAULT_PID,
                    MAX_HISTORICAL_EXIT_REASONS
                )

                if (reasons.isEmpty()) {
                    Log.i(TAG, "[ProcessExitHistory] No prior historical exit reasons recorded.")
                    return@launch
                }

                reasons.forEachIndexed { index, exitInfo ->
                    val reasonCode = exitInfo.reason
                    val reasonName = exitReasonToString(reasonCode)
                    val importance = exitInfo.importance
                    val timestamp = exitInfo.timestamp
                    val description = exitInfo.description ?: "none"

                    Log.i(
                        TAG,
                        "[ProcessExitHistory #$index] reasonCode=$reasonCode ($reasonName) | " +
                                "importance=$importance | timestamp=$timestamp | description=$description"
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read historical process exit reasons: ${e.message}")
            }
        }
    }

    /**
     * Logs lifecycle events for FocusGuardAccessibilityService.
     * Captures elapsed realtime synchronously, then queries Settings.Secure off-thread.
     *
     * [lastNavigationActionElapsed] is read from [GlobalActionGate], or 0 when it has sent nothing.
     * It is passed in rather than read from a static so the gate stays the single owner of that stamp.
     */
    fun logLifecycleEvent(
        context: Context,
        eventName: String,
        lastNavigationActionElapsed: Long
    ) {
        val nowRealtime = SystemClock.elapsedRealtime()

        val msSinceLastConnect = if (lastConnectRealtimeMs > 0L) {
            nowRealtime - lastConnectRealtimeMs
        } else {
            NO_TIMESTAMP
        }

        if (eventName == "onServiceConnected") {
            lastConnectRealtimeMs = nowRealtime
        }

        val msSinceLastGlobalAction = if (lastNavigationActionElapsed > 0L) {
            nowRealtime - lastNavigationActionElapsed
        } else {
            NO_TIMESTAMP
        }

        val appContext = context.applicationContext

        diagScope.launch {
            try {
                val contentResolver = appContext.contentResolver
                val enabledServices = Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""

                val a11yEnabledValue = Settings.Secure.getInt(
                    contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    0
                )

                val targetComponent = "${appContext.packageName}/com.focusguard.app.service.FocusGuardAccessibilityService"
                val shortComponent = "${appContext.packageName}/.service.FocusGuardAccessibilityService"

                val isOurComponentInEnabledList = enabledServices.split(":").any { item ->
                    val trimmed = item.trim()
                    trimmed.equals(targetComponent, ignoreCase = true) ||
                            trimmed.equals(shortComponent, ignoreCase = true)
                }

                val isWriteSecureGranted = appContext.checkSelfPermission(
                    Manifest.permission.WRITE_SECURE_SETTINGS
                ) == PackageManager.PERMISSION_GRANTED

                Log.i(
                    TAG,
                    "[$eventName] msSinceLastConnect=$msSinceLastConnect | " +
                            "msSinceLastGlobalAction=$msSinceLastGlobalAction | " +
                            "inEnabledServices=$isOurComponentInEnabledList | " +
                            "a11yEnabled=$a11yEnabledValue | " +
                            "writeSecureGranted=$isWriteSecureGranted"
                )
            } catch (e: Exception) {
                Log.w(TAG, "[$eventName] Error reading diagnostics state: ${e.message}")
            }
        }
    }

    private fun exitReasonToString(reason: Int): String {
        return when (reason) {
            ApplicationExitInfo.REASON_ANR -> "REASON_ANR"
            ApplicationExitInfo.REASON_CRASH -> "REASON_CRASH"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "REASON_CRASH_NATIVE"
            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "REASON_DEPENDENCY_DIED"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "REASON_EXCESSIVE_RESOURCE_USAGE"
            ApplicationExitInfo.REASON_EXIT_SELF -> "REASON_EXIT_SELF"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "REASON_INITIALIZATION_FAILURE"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "REASON_LOW_MEMORY"
            ApplicationExitInfo.REASON_OTHER -> "REASON_OTHER"
            ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "REASON_PERMISSION_CHANGE"
            ApplicationExitInfo.REASON_SIGNALED -> "REASON_SIGNALED"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "REASON_USER_REQUESTED"
            ApplicationExitInfo.REASON_USER_STOPPED -> "REASON_USER_STOPPED"
            else -> "REASON_UNKNOWN($reason)"
        }
    }
}

