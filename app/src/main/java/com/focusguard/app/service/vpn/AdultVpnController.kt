package com.focusguard.app.service.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.focusguard.app.service.AdultContentVpnService

/**
 * AdultVpnController
 * ==================
 * Single owner of the Adult Shield VPN lifecycle.
 *
 * Android only lets a VpnService call Builder.establish() after the user has granted
 * consent through VpnService.prepare(). Starting the service without consent makes
 * establish() return null, so the whole DNS layer silently never runs. This controller
 * makes that consent step explicit and reports the outcome to the caller.
 *
 * All entry points are synchronized: enable(), resume re-arm and boot re-arm can otherwise
 * race into two startService() calls while the first establish() is still in flight.
 */
object AdultVpnController {

    private const val TAG = "FG:AdultVpn"

    enum class StartResult {
        /** Tunnel is already running or was just started. */
        STARTED,
        /** Feature flag is off; nothing to do. */
        DISABLED,
        /** The system consent dialog was launched; the tunnel starts on app resume. */
        CONSENT_REQUIRED,
        /** Starting the service threw; the shield is not running. */
        FAILED
    }

    /** True only when the TUN interface is actually established. */
    fun isActive(): Boolean = AdultContentVpnService.isRunning.get()

    /**
     * Enables the DNS shield. Call from a foreground user interaction so the one-time
     * VPN consent dialog can be shown (background activity starts are blocked on 10+).
     */
    @Synchronized
    fun enable(context: Context): StartResult {
        if (isActive()) return StartResult.STARTED

        val consent = try {
            VpnService.prepare(context)
        } catch (e: Exception) {
            Log.e(TAG, "VpnService.prepare failed: ${e.message}", e)
            null
        }

        if (consent != null) {
            return try {
                consent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(consent)
                Log.i(TAG, "VPN consent dialog launched — shield starts after approval")
                StartResult.CONSENT_REQUIRED
            } catch (e: Exception) {
                Log.e(TAG, "Could not launch VPN consent dialog: ${e.message}", e)
                StartResult.CONSENT_REQUIRED
            }
        }

        return startService(context, "consent already granted")
    }

    /**
     * Re-arms the shield if the feature is enabled but the tunnel is not established.
     * Call on app resume and after an unlocked boot: this is what starts the shield right
     * after the user approves the system consent dialog, and what heals a tunnel killed by
     * the OS. Consent, once granted, survives reboots until revoked; a revoked consent is
     * reported back instead of silently doing nothing.
     */
    @Synchronized
    fun ensureRunning(context: Context, featureEnabled: Boolean): StartResult {
        if (!featureEnabled) return StartResult.DISABLED
        if (isActive()) return StartResult.STARTED

        val consent = try {
            VpnService.prepare(context)
        } catch (e: Exception) {
            Log.w(TAG, "VpnService.prepare failed during re-arm: ${e.message}")
            return StartResult.FAILED
        }
        if (consent != null) {
            Log.w(TAG, "Shield re-arm skipped: VPN consent is not granted")
            return StartResult.CONSENT_REQUIRED
        }

        return startService(context, "re-armed")
    }

    /** Always dispatches the stop command so a tunnel that is still starting is also torn down. */
    @Synchronized
    fun disable(context: Context) {
        AdultContentVpnService.stop(context)
        Log.i(TAG, "Shield stop requested")
    }

    private fun startService(context: Context, reason: String): StartResult {
        return try {
            AdultContentVpnService.start(context)
            Log.i(TAG, "DNS shield starting ($reason)")
            StartResult.STARTED
        } catch (e: Exception) {
            Log.e(TAG, "Could not start DNS shield: ${e.message}", e)
            StartResult.FAILED
        }
    }
}
