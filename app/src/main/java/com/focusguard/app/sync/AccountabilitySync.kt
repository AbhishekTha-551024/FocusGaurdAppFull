package com.focusguard.app.sync

import android.util.Log
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.core.datastore.DEFAULT_COMMITMENT_SENTENCE
import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.domain.policy.ChallengePolicy
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

private const val TAG = "AccountabilitySync"

private data class AccountabilityConfig(
    val isChallengeActive: Boolean,
    val challengeType: ChallengeType,
    val commitmentSentence: String,
    val timeDelayHours: Int
)

@Singleton
class AccountabilitySync @Inject constructor(
    private val prefs: FocusGuardPreferences,
    private val firestore: FirebaseFirestore
) {

    suspend fun restore(uid: String): Boolean {
        return try {
            val accountDoc = firestore.collection(SyncPaths.COLLECTION_USERS)
                .document(uid)
                .collection(SyncPaths.COLLECTION_ACCOUNTABILITY)
                .document(SyncPaths.DOCUMENT_ACCOUNTABILITY_SETTINGS)
                .get()
                .await()

            if (accountDoc.exists()) {
                val cloudIsChallengeActive = accountDoc.getBoolean(SyncPaths.FIELD_IS_CHALLENGE_ACTIVE) ?: false
                val typeStr = accountDoc.getString(SyncPaths.FIELD_CHALLENGE_TYPE) ?: ChallengeType.TYPING.name
                val cloudChallengeType = try { ChallengeType.valueOf(typeStr) } catch (_: Exception) { ChallengeType.TYPING }
                val cloudSentence = accountDoc.getString(SyncPaths.FIELD_COMMITMENT_SENTENCE) ?: DEFAULT_COMMITMENT_SENTENCE
                val cloudDelayHours = accountDoc.getLong(SyncPaths.FIELD_TIME_DELAY_HOURS)?.toInt() ?: 24

                val localIsChallengeActive = prefs.syncIsChallengeActive
                val localChallengeType = prefs.syncChallengeType
                val localCommitmentSentence = prefs.syncCommitmentSentence
                val localTimeDelayHours = prefs.syncTimeDelayHours

                if (localIsChallengeActive) {
                    if (!cloudIsChallengeActive) {
                        Log.w(TAG, "Cloud challenge is inactive while local challenge is ACTIVE. Preserving local active state.")
                    }
                    // Stricter type wins (PARTNER over TYPING). Preserving the local type verbatim was
                    // wrong in one direction and cloud-adopting it was wrong in the other: either way a
                    // sync could quietly downgrade a partner challenge to a self-typed sentence.
                    val resolvedChallengeType = ChallengePolicy.stricter(localChallengeType, cloudChallengeType)
                    if (cloudChallengeType != localChallengeType) {
                        Log.w(TAG, "Cloud challengeType ($cloudChallengeType) differs from local ($localChallengeType). Keeping the stricter: $resolvedChallengeType.")
                    }
                    if (cloudSentence != localCommitmentSentence) {
                        Log.w(TAG, "Cloud commitmentSentence differs from local sentence. Preserving local commitment sentence.")
                    }
                    val resolvedDelayHours = max(localTimeDelayHours, cloudDelayHours)
                    if (cloudDelayHours != localTimeDelayHours) {
                        Log.w(TAG, "Cloud timeDelayHours ($cloudDelayHours) differs from local ($localTimeDelayHours). Enforcing max: $resolvedDelayHours hours.")
                    }

                    prefs.setChallengeType(resolvedChallengeType)
                    prefs.setCommitmentSentence(localCommitmentSentence)
                    prefs.setTimeDelayHours(resolvedDelayHours)
                    prefs.setChallengeActive(true)
                    Log.i(TAG, "Restored accountability with local-stronger rule: active=true, type=$resolvedChallengeType, delayHours=$resolvedDelayHours")
                } else {
                    prefs.setChallengeType(cloudChallengeType)
                    prefs.setCommitmentSentence(cloudSentence)
                    prefs.setTimeDelayHours(cloudDelayHours)
                    prefs.setChallengeActive(cloudIsChallengeActive)
                    Log.i(TAG, "Restored accountability settings from cloud: isChallengeActive=$cloudIsChallengeActive, type=$cloudChallengeType, delayHours=$cloudDelayHours")
                }
            } else {
                Log.i(TAG, "No remote accountability document found.")
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed restoring accountability settings: ${e.message}")
            false
        }
    }

    suspend fun backup(uid: String) {
        syncAccountabilityToCloud(
            uid = uid,
            isChallengeActive = prefs.syncIsChallengeActive,
            challengeType = prefs.syncChallengeType,
            commitmentSentence = prefs.syncCommitmentSentence,
            timeDelayHours = prefs.syncTimeDelayHours
        )
    }

    suspend fun syncAccountabilityToCloud(
        uid: String,
        isChallengeActive: Boolean,
        challengeType: ChallengeType,
        commitmentSentence: String,
        timeDelayHours: Int
    ) {
        try {
            val docRef = firestore.collection(SyncPaths.COLLECTION_USERS)
                .document(uid)
                .collection(SyncPaths.COLLECTION_ACCOUNTABILITY)
                .document(SyncPaths.DOCUMENT_ACCOUNTABILITY_SETTINGS)

            val data = hashMapOf(
                SyncPaths.FIELD_IS_CHALLENGE_ACTIVE to isChallengeActive,
                SyncPaths.FIELD_CHALLENGE_TYPE to challengeType.name,
                SyncPaths.FIELD_COMMITMENT_SENTENCE to commitmentSentence,
                SyncPaths.FIELD_TIME_DELAY_HOURS to timeDelayHours,
                SyncPaths.FIELD_UPDATED_AT to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
            Log.i(TAG, "Synced accountability settings to cloud: isChallengeActive=$isChallengeActive, type=${challengeType.name}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync accountability to cloud: ${e.message}")
        }
    }

    fun watchAndSync(
        scope: CoroutineScope,
        uid: String,
        isRestoring: () -> Boolean,
        onSynced: () -> Unit
    ): Job {
        return scope.launch {
            combine(
                prefs.isChallengeActive,
                prefs.challengeType,
                prefs.commitmentSentence,
                prefs.timeDelayHours
            ) { active, type, sentence, delay ->
                AccountabilityConfig(active, type, sentence, delay)
            }.drop(1).collect { config ->
                if (!isRestoring()) {
                    syncAccountabilityToCloud(
                        uid = uid,
                        isChallengeActive = config.isChallengeActive,
                        challengeType = config.challengeType,
                        commitmentSentence = config.commitmentSentence,
                        timeDelayHours = config.timeDelayHours
                    )
                    onSynced()
                }
            }
        }
    }
}

