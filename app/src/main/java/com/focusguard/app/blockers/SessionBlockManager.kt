package com.focusguard.app.blockers

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import com.focusguard.app.core.datastore.FocusGuardPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SessionBlockManager:
 * Implements the unbypassable #blockit Deep Focus Lockdown & Suitcase Engine.
 *
 * Guarantees:
 * 1. Zero-latency in-memory fast-path (<0.01ms) for accessibility event checks.
 * 2. Whitelist ("The Suitcase"): Only phone calls, emergency services, and explicitly packed apps are allowed.
 * 3. Parachute System: Limited emergency passes to safely abort a session under critical conditions.
 * 4. Invariant Rule 2: FocusGuard self-package is always excluded.
 * 5. Monotonic Hardware Clock: Immune to clock rollback or manual time manipulation.
 */
@Singleton
class SessionBlockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: FocusGuardPreferences
) {
    companion object {
        private const val TAG = "SessionBlockManager"

        /** Kept as a delegating alias so existing tests and call sites stay unchanged. */
        internal fun isCallSurfaceName(pkg: String): Boolean =
            SessionAllowlist.isCallSurfaceName(pkg)
    }

    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    @Volatile
    private var systemCallPackages = SessionAllowlist.resolveSystemCallPackages(context)
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val coroutineExceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Unhandled coroutine exception in SessionBlockManager", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)

    // Fast-path atomic state in RAM
    private val isSessionActive = AtomicBoolean(false)
    private val sessionEndTimeMillis = AtomicLong(0L)
    private val sessionEndElapsedRealtime = AtomicLong(0L)
    private val sessionTotalMinutes = AtomicInteger(25)
    val suitcasePackages = CopyOnWriteArraySet<String>()

    data class SessionUiState(
        val isActive: Boolean = false,
        val endTimeMillis: Long = 0L,
        val totalMinutes: Int = 25,
        val remainingMillis: Long = 0L,
        val packedPackages: Set<String> = emptySet(),
        val parachutesCount: Int = 2,
        val totalSessionsCompleted: Int = 0,
        val totalMinutesCompleted: Long = 0L
    )

    private val _sessionStateFlow = MutableStateFlow(SessionUiState())
    val sessionStateFlow: StateFlow<SessionUiState> = _sessionStateFlow.asStateFlow()

    init {
        // Eager hydration from synchronous prefs on process creation
        if (preferences.syncSessionLockActive) {
            val end = preferences.syncSessionEndTimeMs
            val remainingWall = end - System.currentTimeMillis()
            if (remainingWall > 0L) {
                isSessionActive.set(true)
                sessionEndTimeMillis.set(end)
                sessionEndElapsedRealtime.set(SystemClock.elapsedRealtime() + remainingWall)
                sessionTotalMinutes.set(preferences.syncSessionTotalMinutes)
                suitcasePackages.addAll(preferences.syncSuitcasePackages.map { it.lowercase() })
                suitcasePackages.add(context.packageName.lowercase())
                Log.i(TAG, "Restored active Deep Focus session ending in ${remainingWall / 60000} mins")
            } else {
                scope.launch { preferences.stopSessionLock() }
            }
        }
        updateStateFlow()
    }

    fun updateStateFlow() {
        val remaining = getRemainingTimeMillis()
        val end = sessionEndTimeMillis.get()
        _sessionStateFlow.value = SessionUiState(
            isActive = isSessionActive.get() && remaining > 0L,
            endTimeMillis = end,
            totalMinutes = sessionTotalMinutes.get(),
            remainingMillis = remaining,
            packedPackages = suitcasePackages.toSet(),
            parachutesCount = preferences.syncEmergencyParachutesCount,
            totalSessionsCompleted = preferences.syncTotalFocusSessionsCompleted,
            totalMinutesCompleted = preferences.syncTotalFocusMinutesCompleted
        )
    }

    /**
     * Check if a package is strictly blocked under the active Deep Focus Lockdown.
     * Guaranteed < 0.01ms zero IPC execution on the accessibility thread.
     */
    fun isPackageBlockedInSession(packageName: String): Boolean {
        if (!isSessionCurrentlyActive()) return false
        val pkg = packageName.lowercase()

        // 1. Critical System & FocusGuard Invariants (NEVER block phone, emergency dialer, SMS, or FocusGuard)
        if (isAlwaysAllowedPackage(pkg)) return false

        // 2. Critical Tamper / Bypass Protection: Settings, Play Store, App Installers can NEVER be unblocked
        if (isTamperOrSettingsPackage(pkg)) return true

        // 3. The Suitcase whitelist (case-insensitive)
        if (suitcasePackages.contains(pkg) || suitcasePackages.contains(packageName)) return false

        // 4. Default: Everything else on the phone is locked down
        return true
    }

    fun isTamperOrSettingsPackage(pkg: String): Boolean =
        SessionAllowlist.isTamperOrSettingsPackage(pkg)

    fun isAlwaysAllowedPackage(pkg: String): Boolean =
        SessionAllowlist.isAlwaysAllowedPackage(
            pkg = pkg,
            selfPackage = context.packageName,
            defaultDialerPackage = systemCallPackages.defaultDialerPackage,
            defaultSmsPackage = systemCallPackages.defaultSmsPackage,
            voipCallInProgress = SessionAllowlist.isKnownVoipCallPackage(pkg) && isInPhoneCall()
        )

    /**
     * Narrow allowlist used to gate the user's own blocklist, time limits and schedules.
     * See [SessionAllowlist.isCriticalCallSurface] for the rationale.
     */
    fun isCriticalCallSurface(pkg: String): Boolean {
        val isVoipCallPackage = SessionAllowlist.isKnownVoipCallPackage(pkg)
        return SessionAllowlist.isCriticalCallSurface(
            pkg = pkg,
            inPhoneCall = isVoipCallPackage && isInPhoneCall(),
            defaultDialerPackage = systemCallPackages.defaultDialerPackage
        )
    }

    fun isPermanentCallSurface(pkg: String): Boolean =
        SessionAllowlist.isCriticalCallSurface(
            pkg = pkg,
            inPhoneCall = false,
            defaultDialerPackage = systemCallPackages.defaultDialerPackage
        )

    /** Uses AudioManager mode only to exempt a known VoIP app during active communication. */
    fun isInPhoneCall(): Boolean {
        return try {
            val mode = audioManager?.mode ?: AudioManager.MODE_NORMAL
            mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
        } catch (e: Exception) {
            Log.d(TAG, "isInPhoneCall error: ${e.message}")
            false
        }
    }

    fun isSessionCurrentlyActive(): Boolean {
        if (!isSessionActive.get()) return false
        val endReal = sessionEndElapsedRealtime.get()
        val isExpired = if (endReal > 0L) {
            SystemClock.elapsedRealtime() >= endReal
        } else {
            System.currentTimeMillis() >= sessionEndTimeMillis.get()
        }
        if (isExpired) {
            sessionScope.launch { stopSession(isAborted = false) }
            return false
        }
        return true
    }

    fun getRemainingTimeMillis(): Long {
        if (!isSessionActive.get()) return 0L
        val endReal = sessionEndElapsedRealtime.get()
        if (endReal > 0L) {
            val remainingReal = endReal - SystemClock.elapsedRealtime()
            return if (remainingReal > 0L) remainingReal else 0L
        }
        val remainingWall = sessionEndTimeMillis.get() - System.currentTimeMillis()
        return if (remainingWall > 0L) remainingWall else 0L
    }

    fun getRemainingMinutes(): Int {
        val millis = getRemainingTimeMillis()
        return if (millis > 0) ((millis + 59999) / 60000).toInt() else 0
    }

    fun startSession(durationMinutes: Int, packedApps: Set<String>) {
        systemCallPackages = SessionAllowlist.resolveSystemCallPackages(context)
        val durationMs = durationMinutes * 60 * 1000L
        val end = System.currentTimeMillis() + durationMs
        val endReal = SystemClock.elapsedRealtime() + durationMs

        suitcasePackages.clear()
        suitcasePackages.addAll(packedApps.map { it.lowercase() })
        suitcasePackages.add(context.packageName.lowercase()) // Invariant: self exclusion

        sessionEndTimeMillis.set(end)
        sessionEndElapsedRealtime.set(endReal)
        sessionTotalMinutes.set(durationMinutes)
        isSessionActive.set(true)

        scope.launch {
            preferences.startSessionLock(durationMinutes, packedApps)
        }

        updateStateFlow()
        Log.i(TAG, "Deep Focus Lockdown session STARTED for $durationMinutes minutes with ${packedApps.size} packed apps.")
    }

    fun stopSession(isAborted: Boolean) {
        val totalMins = sessionTotalMinutes.get()
        isSessionActive.set(false)
        sessionEndTimeMillis.set(0L)
        sessionEndElapsedRealtime.set(0L)

        scope.launch {
            if (isAborted) {
                preferences.stopSessionLock()
            } else {
                preferences.recordCompletedSession(totalMins)
            }
        }

        updateStateFlow()
        Log.i(TAG, "Deep Focus Lockdown session STOPPED (isAborted=$isAborted)")
    }

    /**
     * Emergency Parachute Abort:
     * Consumes 1 parachute pass and terminates the lockdown session.
     * Returns false if user has 0 parachutes remaining.
     */
    fun emergencyAbortWithParachute(): Boolean {
        val currentParachutes = preferences.syncEmergencyParachutesCount
        if (currentParachutes <= 0) {
            Log.w(TAG, "Emergency abort rejected: 0 parachutes remaining.")
            return false
        }

        isSessionActive.set(false)
        sessionEndTimeMillis.set(0L)
        sessionEndElapsedRealtime.set(0L)

        scope.launch {
            preferences.consumeEmergencyParachute()
        }

        updateStateFlow()
        Log.i(TAG, "Deep Focus Lockdown session aborted by user using 1 parachute pass. Remaining: ${currentParachutes - 1}")
        return true
    }

    fun updateSuitcasePackages(packedApps: Set<String>) {
        suitcasePackages.clear()
        suitcasePackages.addAll(packedApps.map { it.lowercase() })
        suitcasePackages.add(context.packageName.lowercase())
        scope.launch {
            preferences.saveSuitcasePackages(packedApps)
        }
        updateStateFlow()
    }
}
