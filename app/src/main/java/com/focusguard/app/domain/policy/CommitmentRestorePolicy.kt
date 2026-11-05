package com.focusguard.app.domain.policy

import com.focusguard.app.domain.model.CommitmentLockState

/**
 * Upper bound for a restored timed lock, in days. A corrupt or absurd server value would otherwise
 * overflow the uptime requirement (days * oneDayMs) and leave a lock that can never lapse — which is
 * fail-closed but unusable. Ten years is beyond any commitment the app offers.
 */
private const val MAX_REARM_DAYS = 3650

/**
 * What the cold-start restore pass decided to do about a timed uninstall lock.
 * Exactly one action per pass, so the effect on local state and on the server record is auditable.
 */
sealed interface CommitmentRestoreDecision {

    /** Neither side owes a running timed lock: nothing to restore, nothing to mirror. */
    data object NothingToRestore : CommitmentRestoreDecision

    /**
     * Local protection already runs at least as long as the server record.
     * The idempotency guard: without it every cold start would re-pin the monotonic anchor and
     * silently extend a lock that is already counting down.
     */
    data object LocalAlreadyCoversServer : CommitmentRestoreDecision

    /**
     * The server owes this device a lock that local state does not cover — write it locally.
     *
     * @param days             whole days left, rounded UP so a partial day is never shortened.
     * @param expiryWallMs     the server's own deadline, NOT now + days, so a restore can never
     *                         lengthen the commitment beyond what was promised.
     * @param repinUptimeAnchor true only when local state had no live lock: an in-progress lock
     *                          keeps its earlier monotonic anchor so earned uptime is not lost.
     */
    data class ReArmLocal(
        val days: Int,
        val expiryWallMs: Long,
        val repinUptimeAnchor: Boolean
    ) : CommitmentRestoreDecision

    /** Local state is the stronger record: push it up so a reinstall can find it again. */
    data class MirrorLocalToServer(
        val state: CommitmentLockState
    ) : CommitmentRestoreDecision
}

/**
 * CommitmentRestorePolicy
 * =======================
 * Pure rule that reconciles the local timed-uninstall-lock state with the server record keyed by a
 * reinstall-stable device identity.
 *
 * Why it exists: uninstalling wipes DataStore, SharedPreferences and device-protected storage, so
 * the only durable proof of a commitment is the server copy. Signing back in restores blocklists and
 * the challenge config but NOT the timed lock, which left a reinstalls-with-signin path with zero
 * time-locked protection.
 *
 * Rules, in the fail-closed direction:
 * - The server can only ever ADD protection. An absent, stale, inactive or unreachable server record
 *   NEVER disarms a local lock — absence of proof is not proof of release.
 * - Both `days` and the wall deadline are compared; local must cover BOTH to count as sufficient.
 * - Re-arm keeps the server deadline (max semantics), so restoring can shorten a locally inflated
 *   deadline but never extend the original promise.
 * - Repeated cold starts are a no-op once local covers the server ([CommitmentRestoreDecision.LocalAlreadyCoversServer]).
 */
object CommitmentRestorePolicy {

    /** True when this state still owes a timed lock at [nowMs] (wall-clock view only). */
    fun isOwedAt(state: CommitmentLockState?, nowMs: Long): Boolean {
        if (state == null) return false
        if (!state.isTimedWithDeadline) return false
        return state.expiryWallMs > nowMs
    }

    /** Local runs at least as long, and for at least as many days, as [server]. */
    fun localCoversServer(local: CommitmentLockState, server: CommitmentLockState): Boolean =
        local.expiryWallMs >= server.expiryWallMs && local.days >= server.days

    /**
     * The single "may this write replace what is already stored?" rule, used by the device record so
     * a stored commitment can only ever GROW. Kept here rather than inside the Firestore adapter so
     * it is unit-testable and so the client and the published rules enforce the same ordering.
     *
     * Ordering, strongest first: permanent protection > longer timed deadline > more days > equal
     * (equal is a no-op, which keeps repeated cold starts from re-writing the same record).
     *
     * @param stored the record already on the server, or null when this device has none yet
     */
    fun isStrongerThan(candidate: CommitmentLockState, stored: CommitmentLockState?): Boolean {
        // An inactive candidate must never deactivate a live commitment, and never creates a record.
        if (!candidate.isTimedLockActive) return false
        if (stored == null) return true
        if (stored.isPermanentLock) return false
        if (candidate.isPermanentLock) return true
        if (!stored.isTimedLockActive) return true
        if (candidate.expiryWallMs != stored.expiryWallMs) return candidate.expiryWallMs > stored.expiryWallMs
        return candidate.days > stored.days
    }

    /**
     * Whole days needed to cover [remainingMs], rounded up and floored at one day so a live lock is
     * never written with a zero duration (which would read as untimed protection). Capped at
     * [MAX_REARM_DAYS] so a corrupt server value cannot overflow the uptime requirement.
     */
    fun reArmDays(remainingMs: Long, oneDayMs: Long): Int {
        if (oneDayMs <= 0L) return 0
        if (remainingMs <= 0L) return 1
        val wholeDays = remainingMs / oneDayMs
        val hasPartialDay = remainingMs % oneDayMs != 0L
        val days = if (hasPartialDay) wholeDays + 1 else wholeDays
        return days.coerceIn(1L, MAX_REARM_DAYS.toLong()).toInt()
    }

    /**
     * Single reconciliation entry point.
     *
     * @param local   state read from this install's storage (inactive on a fresh reinstall)
     * @param server  state read from the device-keyed server record, or null when there is none
     * @param nowMs   current wall clock; the monotonic gate is applied by the caller, locally
     * @param oneDayMs day length to divide the remaining time by
     */
    fun decide(
        local: CommitmentLockState,
        server: CommitmentLockState?,
        nowMs: Long,
        oneDayMs: Long
    ): CommitmentRestoreDecision {
        val serverOwesLock = isOwedAt(server, nowMs)
        val localOwesLock = isOwedAt(local, nowMs)

        if (serverOwesLock) {
            // Permanent local protection outranks any timed record: restoring must not turn it into a
            // lock that can expire.
            if (local.isPermanentLock) return CommitmentRestoreDecision.LocalAlreadyCoversServer
            val remote = server!!
            if (localOwesLock && localCoversServer(local, remote)) return CommitmentRestoreDecision.LocalAlreadyCoversServer
            return CommitmentRestoreDecision.ReArmLocal(
                days = reArmDays(remote.expiryWallMs - nowMs, oneDayMs),
                expiryWallMs = remote.expiryWallMs,
                repinUptimeAnchor = !localOwesLock
            )
        }
        if (localOwesLock) return CommitmentRestoreDecision.MirrorLocalToServer(local)
        return CommitmentRestoreDecision.NothingToRestore
    }
}
