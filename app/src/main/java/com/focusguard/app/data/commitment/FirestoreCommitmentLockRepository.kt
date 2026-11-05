package com.focusguard.app.data.commitment

import android.util.Log
import com.focusguard.app.core.di.IoDispatcher
import com.focusguard.app.domain.model.CommitmentLockState
import com.focusguard.app.domain.policy.CommitmentRestorePolicy
import com.focusguard.app.domain.repository.CommitmentLockRepository
import com.focusguard.app.domain.repository.CommitmentRead
import com.focusguard.app.domain.repository.CommitmentReadFailure
import com.focusguard.app.domain.repository.CommitmentWrite
import com.focusguard.app.sync.SyncPaths
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.Lazy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FG:Commitment"

/**
 * FirestoreCommitmentLockRepository
 * ================================
 * Device-keyed durable copy of the timed uninstall lock: `device_locks/{reinstallStableId}`.
 *
 * This collection is the ONLY state that outlives an uninstall, so it is written with monotonic
 * merge semantics inside a transaction: a stored commitment can be extended or refreshed, never
 * lowered, and never deleted by this code path. A client that edits its own copy of the app cannot
 * use this class to talk the server out of a lock — and the matching Firestore rules (see
 * docs/firestore-device-locks-rules.md) enforce the same invariant on the server.
 *
 * [firestore] is injected lazily: touching Firestore initialises FirebaseAuth, whose preferences live
 * in credential-encrypted storage, so the graph must be constructable during a direct-boot process
 * that can never reach this class.
 */
@Singleton
class FirestoreCommitmentLockRepository @Inject constructor(
    private val firestore: Lazy<FirebaseFirestore>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CommitmentLockRepository {

    private fun locks(): CollectionReference =
        firestore.get().collection(SyncPaths.COLLECTION_DEVICE_LOCKS)

    override suspend fun read(deviceId: String): CommitmentRead {
        if (deviceId.isBlank()) return CommitmentRead.NoRecord
        return try {
            val snapshot = withContext(ioDispatcher) {
                locks().document(deviceId).get().await()
            }
            if (!snapshot.exists()) CommitmentRead.NoRecord
            else CommitmentRead.Found(snapshot.toLockState(deviceId))
        } catch (e: Exception) {
            val failure = e.toFailure()
            Log.w(TAG, "Commitment read unavailable: ${e.javaClass.simpleName} ($failure)")
            CommitmentRead.Unavailable(failure)
        }
    }

    override suspend fun publishStronger(state: CommitmentLockState): CommitmentWrite {
        if (!state.hasUsableDeviceId) return CommitmentWrite.Failed(CommitmentReadFailure.UNKNOWN)
        return try {
            val wrote = withContext(ioDispatcher) {
                firestore.get().runTransaction { transaction ->
                    val docRef = locks().document(state.deviceId)
                    // Transaction.get() is the blocking, already-resolved read inside a transaction.
                    val current = transaction.get(docRef).toLockStateOrNull(state.deviceId)
                    if (CommitmentRestorePolicy.isStrongerThan(state, current)) {
                        transaction.set(docRef, state.toServerFields())
                        true
                    } else {
                        false
                    }
                }.await()
            }
            if (wrote) {
                Log.i(TAG, "Commitment mirrored to server: days=${state.days}, active=${state.isTimedLockActive}")
                CommitmentWrite.Written
            } else {
                Log.i(TAG, "Server commitment already covers local state; write skipped")
                CommitmentWrite.AlreadyCurrent
            }
        } catch (e: Exception) {
            val failure = e.toFailure()
            Log.w(TAG, "Commitment mirror failed: ${e.javaClass.simpleName} ($failure)")
            CommitmentWrite.Failed(failure)
        }
    }

    private fun CommitmentLockState.toServerFields(): Map<String, Any> = mapOf(
        SyncPaths.FIELD_DEVICE_ID to deviceId,
        SyncPaths.FIELD_IS_TIMED_LOCK_ACTIVE to isTimedLockActive,
        SyncPaths.FIELD_LOCK_DAYS to days,
        SyncPaths.FIELD_EXPIRY_WALL_MS to expiryWallMs,
        SyncPaths.FIELD_RECORDED_AT_MS to recordedAtMs
    )

    private fun DocumentSnapshot.toLockState(deviceId: String): CommitmentLockState = CommitmentLockState(
        deviceId = deviceId,
        isTimedLockActive = getBoolean(SyncPaths.FIELD_IS_TIMED_LOCK_ACTIVE) ?: false,
        days = getLong(SyncPaths.FIELD_LOCK_DAYS)?.toInt() ?: 0,
        expiryWallMs = getLong(SyncPaths.FIELD_EXPIRY_WALL_MS) ?: 0L,
        recordedAtMs = getLong(SyncPaths.FIELD_RECORDED_AT_MS) ?: 0L
    )

    /** Missing documents read as null so the transaction can tell "no record" from "empty record". */
    private fun DocumentSnapshot?.toLockStateOrNull(deviceId: String): CommitmentLockState? =
        this?.takeIf { it.exists() }?.toLockState(deviceId)

    /** Coarse, non-leaking failure class — never surface raw messages (they can carry service URLs). */
    private fun Throwable.toFailure(): CommitmentReadFailure = when (this) {
        is FirebaseFirestoreException -> when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> CommitmentReadFailure.PERMISSION
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> CommitmentReadFailure.PERMISSION
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> CommitmentReadFailure.TIMEOUT
            FirebaseFirestoreException.Code.UNAVAILABLE -> CommitmentReadFailure.NETWORK
            else -> CommitmentReadFailure.UNKNOWN
        }
        is TimeoutException -> CommitmentReadFailure.TIMEOUT
        is IOException -> CommitmentReadFailure.NETWORK
        else -> CommitmentReadFailure.UNKNOWN
    }
}
