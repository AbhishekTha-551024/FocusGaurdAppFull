package com.focusguard.app.data.repository

import android.util.Log
import com.focusguard.app.core.database.dao.PartnershipDao
import com.focusguard.app.core.database.entity.PartnershipEntity
import com.focusguard.app.core.model.Partnership
import com.focusguard.app.core.model.PartnershipStatus
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.core.model.ChallengeType

private const val TAG = "PartnershipRepo"
private const val COLLECTION_PARTNERSHIPS = "partnerships"
private const val NETWORK_TIMEOUT_MS = 15_000L

@Singleton
class PartnershipRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val dao: PartnershipDao,
    private val prefs: FocusGuardPreferences
) {
    private val partnershipExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("PartnershipRepository", "Partnership scope error: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + partnershipExceptionHandler)

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser = _currentUser.asStateFlow()

    private val isOwnerPartnershipActiveCache = AtomicBoolean(false)
    private val isPartnerPartnershipActiveCache = AtomicBoolean(false)

    private var ownerListener: ListenerRegistration? = null
    private var partnerListener: ListenerRegistration? = null

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _currentUser.value = user
            val email = user?.email?.lowercase()
            if (user != null && !email.isNullOrBlank()) {
                startPartnershipSync(user.uid, email)
            } else {
                stopPartnershipSync()
                scope.launch { dao.clearAll() }
                isOwnerPartnershipActiveCache.set(false)
                isPartnerPartnershipActiveCache.set(false)
            }
        }
    }

    val currentPartnership: Flow<PartnershipEntity?> = dao.getActivePartnership()

    fun observePartnershipForUser(userId: String, email: String): Flow<PartnershipEntity?> =
        dao.observeCurrentPartnership(userId, email)

    fun isFastPartnershipActive(): Boolean =
        isOwnerPartnershipActiveCache.get() || isPartnerPartnershipActiveCache.get()

    internal fun clearPartnershipSession() {
        _currentUser.value = null
        stopPartnershipSync()
        scope.launch { dao.clearAll() }
        isOwnerPartnershipActiveCache.set(false)
        isPartnerPartnershipActiveCache.set(false)
    }

    suspend fun sendInvite(partnerEmail: String): Result<String> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Must be logged in to send invite"))
        val normalizedPartnerEmail = partnerEmail.trim().lowercase()
        val ownerEmail = user.email?.lowercase() ?: ""

        if (normalizedPartnerEmail.isBlank()) {
            return Result.failure(IllegalArgumentException("Partner email cannot be blank"))
        }
        if (normalizedPartnerEmail == ownerEmail) {
            return Result.failure(IllegalArgumentException("You cannot invite your own email as partner"))
        }

        return try {
            withTimeout(NETWORK_TIMEOUT_MS) {
                val docRef = firestore.collection(COLLECTION_PARTNERSHIPS).document()
                val partnershipId = docRef.id

                val partnership = Partnership(
                    partnershipId = partnershipId,
                    ownerUserId = user.uid,
                    ownerEmail = ownerEmail,
                    partnerEmail = normalizedPartnerEmail,
                    partnerUserId = null,
                    status = PartnershipStatus.PENDING_INVITE.name,
                    createdAt = Timestamp.now()
                )

                docRef.set(partnership).await()

                dao.insertOrUpdate(
                    PartnershipEntity(
                        partnershipId = partnershipId,
                        ownerUserId = user.uid,
                        ownerEmail = ownerEmail,
                        partnerEmail = normalizedPartnerEmail,
                        partnerUserId = null,
                        status = PartnershipStatus.PENDING_INVITE,
                        createdAt = System.currentTimeMillis()
                    )
                )

                Result.success(partnershipId)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Send invite timed out", e)
            Result.failure(java.io.IOException("Network request timed out. Please check your connection."))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send invite: " + e.message, e)
            Result.failure(e)
        }
    }

    suspend fun acceptInvite(partnershipId: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Must be logged in to accept invite"))
        return try {
            withTimeout(NETWORK_TIMEOUT_MS) {
                firestore.collection(COLLECTION_PARTNERSHIPS).document(partnershipId)
                    .update(
                        mapOf(
                            "partnerUserId" to user.uid,
                            "status" to PartnershipStatus.ACTIVE.name
                        )
                    ).await()
                Result.success(Unit)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Accept invite timed out", e)
            Result.failure(java.io.IOException("Network request timed out. Please check your connection."))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to accept invite: " + e.message, e)
            Result.failure(e)
        }
    }

    suspend fun declineInvite(partnershipId: String): Result<Unit> {
        return try {
            withTimeout(NETWORK_TIMEOUT_MS) {
                firestore.collection(COLLECTION_PARTNERSHIPS).document(partnershipId)
                    .update("status", PartnershipStatus.REVOKED.name).await()
                dao.deleteById(partnershipId)
                Result.success(Unit)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Decline invite timed out", e)
            Result.failure(java.io.IOException("Network request timed out. Please check your connection."))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decline invite: " + e.message, e)
            Result.failure(e)
        }
    }

    suspend fun revokePartnership(partnershipId: String): Result<Unit> {
        return try {
            withTimeout(NETWORK_TIMEOUT_MS) {
                firestore.collection(COLLECTION_PARTNERSHIPS).document(partnershipId)
                    .update("status", PartnershipStatus.REVOKED.name).await()
                dao.deleteById(partnershipId)
                isOwnerPartnershipActiveCache.set(false)
                if (prefs.syncChallengeType == ChallengeType.PARTNER) {
                    prefs.setChallengeType(ChallengeType.TYPING)
                }
                Result.success(Unit)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Revoke partnership timed out", e)
            Result.failure(java.io.IOException("Network request timed out. Please check your connection."))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to revoke partnership: " + e.message, e)
            Result.failure(e)
        }
    }

    private fun startPartnershipSync(userId: String, email: String) {
        stopPartnershipSync()

        ownerListener = firestore.collection(COLLECTION_PARTNERSHIPS)
            .whereEqualTo("ownerUserId", userId)
            .whereNotEqualTo("status", PartnershipStatus.REVOKED.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error in owner listener: " + error.message)
                    return@addSnapshotListener
                }
                snapshot?.documents?.firstOrNull()?.let { doc ->
                    val statusStr = doc.getString("status") ?: PartnershipStatus.PENDING_INVITE.name
                    val status = try { PartnershipStatus.valueOf(statusStr) } catch (_: Exception) { PartnershipStatus.PENDING_INVITE }
                    val entity = PartnershipEntity(
                        partnershipId = doc.id,
                        ownerUserId = doc.getString("ownerUserId") ?: userId,
                        ownerEmail = doc.getString("ownerEmail") ?: email,
                        partnerEmail = doc.getString("partnerEmail") ?: "",
                        partnerUserId = doc.getString("partnerUserId"),
                        status = status,
                        createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
                    )
                    scope.launch { dao.insertOrUpdate(entity) }
                    isOwnerPartnershipActiveCache.set(status == PartnershipStatus.ACTIVE)
                } ?: run {
                    scope.launch {
                        val active = dao.getActivePartnershipSync()
                        if (active?.ownerUserId == userId) {
                            dao.deleteById(active.partnershipId)
                            isOwnerPartnershipActiveCache.set(false)
                            if (prefs.syncChallengeType == ChallengeType.PARTNER) {
                                prefs.setChallengeType(ChallengeType.TYPING)
                            }
                        }
                    }
                }
            }

        partnerListener = firestore.collection(COLLECTION_PARTNERSHIPS)
            .whereEqualTo("partnerEmail", email)
            .whereNotEqualTo("status", PartnershipStatus.REVOKED.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error in partner listener: " + error.message)
                    return@addSnapshotListener
                }
                snapshot?.documents?.firstOrNull()?.let { doc ->
                    val statusStr = doc.getString("status") ?: PartnershipStatus.PENDING_INVITE.name
                    val status = try { PartnershipStatus.valueOf(statusStr) } catch (_: Exception) { PartnershipStatus.PENDING_INVITE }
                    val entity = PartnershipEntity(
                        partnershipId = doc.id,
                        ownerUserId = doc.getString("ownerUserId") ?: "",
                        ownerEmail = doc.getString("ownerEmail") ?: "",
                        partnerEmail = email,
                        partnerUserId = doc.getString("partnerUserId") ?: userId,
                        status = status,
                        createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
                    )
                    scope.launch { dao.insertOrUpdate(entity) }
                    isPartnerPartnershipActiveCache.set(status == PartnershipStatus.ACTIVE)
                } ?: run {
                    isPartnerPartnershipActiveCache.set(false)
                }
            }
    }

    private fun stopPartnershipSync() {
        ownerListener?.remove()
        ownerListener = null
        partnerListener?.remove()
        partnerListener = null
    }
}
