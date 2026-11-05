package com.focusguard.app.blockers

import android.content.Context
import android.provider.Telephony
import android.telecom.TelecomManager
import android.util.Log
import com.focusguard.app.domain.policy.CallSurfacePolicy

/**
 * SessionAllowlist:
 * Shared package classification for Deep Focus and regular blockers.
 *
 * Extracted from SessionBlockManager to keep that file under the 300-line limit.
 */
object SessionAllowlist {
    private const val TAG = "SessionBlockManager"

    data class SystemCallPackages(
        val defaultDialerPackage: String?,
        val defaultSmsPackage: String?
    )

    /**
     * Pure call-surface check (no context, no IPC) used on the accessibility hot path.
     *
     * Deliberately narrow: dialer / in-call UI / telephony / caller-ID only. It must NOT
     * use the broad contains("phone") / clock / alarm rules from the Deep Focus allowlist,
     * because those silent exemptions caused user-blocklisted apps to never block.
     */
    internal fun isCallSurfaceName(pkg: String): Boolean {
        return CallSurfacePolicy.isNamedCallSurface(pkg)
    }

    fun isKnownVoipCallPackage(pkg: String): Boolean =
        CallSurfacePolicy.isKnownVoipCallPackage(pkg)

    fun resolveSystemCallPackages(context: Context): SystemCallPackages {
        val defaultDialer = try {
            (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)
                ?.defaultDialerPackage
                ?.lowercase()
        } catch (e: SecurityException) {
            Log.w(TAG, "Unable to resolve the default dialer", e)
            null
        }
        val defaultSms = try {
            Telephony.Sms.getDefaultSmsPackage(context)?.lowercase()
        } catch (e: SecurityException) {
            Log.w(TAG, "Unable to resolve the default SMS app", e)
            null
        }
        return SystemCallPackages(defaultDialer, defaultSms)
    }

    /**
     * Narrow allowlist used to gate the user's own blocklist, time limits and schedules.
     *
     * [isAlwaysAllowedPackage] is the Deep Focus suitcase allowlist. Reusing it for ordinary
     * blocking silently exempted clock / alarm / wellbeing apps, every package whose name
     * happens to contain "phone", and EVERY foreground app while a call or communication-mode
     * audio session was active (isInPhoneCall). A user blocklist must only step aside for the
     * real call surface.
     */
    fun isCriticalCallSurface(
        pkg: String,
        inPhoneCall: Boolean,
        defaultDialerPackage: String?
    ): Boolean = CallSurfacePolicy.isCriticalCallSurface(
        packageName = pkg,
        defaultDialerPackage = defaultDialerPackage,
        callInProgress = inPhoneCall
    )

    fun isTamperOrSettingsPackage(pkg: String): Boolean {
        val p = pkg.lowercase()
        if (p == "com.android.settings" || p.endsWith(".settings") || p.contains(".settings.")) return true
        if (p == "com.android.vending") return true
        return p.contains("packageinstaller") || p.contains("safecenter") || p.contains("securitycenter")
    }

    fun isAlwaysAllowedPackage(
        pkg: String,
        selfPackage: String,
        defaultDialerPackage: String?,
        defaultSmsPackage: String?,
        voipCallInProgress: Boolean
    ): Boolean {
        if (CallSurfacePolicy.isAlwaysAllowedDuringSession(
                packageName = pkg,
                selfPackage = selfPackage,
                defaultDialerPackage = defaultDialerPackage,
                defaultSmsPackage = defaultSmsPackage,
                voipCallInProgress = voipCallInProgress
            )
        ) return true

        // Clock / Alarm
        val lower = pkg.lowercase()
        if (lower.contains("deskclock") || lower.contains("alarm") || lower.contains("google.android.deskclock")) return true

        // Emergency alerts & SIM toolkit
        return lower.contains("cellbroadcastreceiver") || lower.contains("stk")
    }
}
