package com.focusguard.app.feature.auth.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.focusguard.app.blockers.SessionBlockManager
import com.focusguard.app.core.common.privacy.LogPrivacy
import com.focusguard.app.core.datastore.DirectBootStorage
import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.core.model.AuthUser
import com.focusguard.app.data.repository.PartnershipRepository
import com.focusguard.app.feature.auth.domain.repository.AuthRepository
import com.focusguard.app.security.AuthConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

private const val TAG = "AuthRepo"

@Singleton
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: FocusGuardPreferences,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val sessionBlockManagerProvider: Provider<SessionBlockManager>,
    private val partnershipRepositoryProvider: Provider<PartnershipRepository>
) : AuthRepository {

    private val authScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val syncCache: SharedPreferences
        get() = DirectBootStorage.readCache(context)

    override val currentUser: Flow<AuthUser?> = prefs.currentAuthUser
    override val isLoggedIn: Flow<Boolean> = prefs.currentAuthUser.map { it != null }

    override val syncCurrentUser: AuthUser?
        get() = prefs.syncAuthUser

    init {
        // Keep local preferences synchronized with Firebase Auth state
        firebaseAuth.addAuthStateListener { auth ->
            val firebaseUser = auth.currentUser
            if (firebaseUser != null) {
                val current = prefs.syncAuthUser
                if (current == null || current.id != firebaseUser.uid) {
                    val user = AuthUser(
                        id = firebaseUser.uid,
                        displayName = firebaseUser.displayName,
                        email = firebaseUser.email,
                        photoUrl = firebaseUser.photoUrl?.toString()
                    )
                    authScope.launch {
                        prefs.saveAuthUser(user)
                    }
                }
            } else {
                if (prefs.syncIsLoggedIn) {
                    authScope.launch {
                        prefs.clearAuthUser()
                    }
                }
            }
        }

        val user = prefs.syncAuthUser
        if (user != null && !user.email.isNullOrBlank()) {
            authScope.launch {
                val deviceId = prefs.getOrCreateDeviceId()
                syncUserDeviceToFirestore(user, deviceId, force = false)
            }
        }
    }

    override suspend fun syncUserDeviceToFirestore(user: AuthUser, deviceId: String, force: Boolean) {
        val userEmail = user.email?.trim()?.lowercase()
        if (userEmail.isNullOrBlank() || !userEmail.contains("@")) return

        val now = System.currentTimeMillis()
        val lastSync = syncCache.getLong("last_device_sync_time", 0L)
        // Throttle automatic cold-start sync to at most once every 6 hours
        if (!force && (now - lastSync < 6 * 3600_000L)) {
            Log.d(TAG, "Device sync to Firestore throttled (last synced ${(now - lastSync) / 60000} mins ago)")
            return
        }

        try {
            val appVersion = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
            }.getOrDefault("1.0.0")

            val userMap = hashMapOf(
                "uid" to user.id,
                "email" to userEmail,
                "displayName" to (user.displayName ?: ""),
                "photoUrl" to (user.photoUrl ?: ""),
                "deviceId" to deviceId,
                "deviceModel" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "androidVersion" to Build.VERSION.RELEASE,
                "appVersion" to appVersion,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            firestore.collection("users").document(user.id)
                .set(userMap, SetOptions.merge())
                .await()

            firestore.collection("users_by_email").document(userEmail)
                .set(userMap, SetOptions.merge())
                .await()

            val deviceDoc = hashMapOf<String, Any>(
                "id" to deviceId,
                "deviceId" to deviceId,
                "model" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "osVersion" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                "appVersion" to appVersion,
                "isOnline" to true,
                "primaryEmail" to userEmail,
                "lastActive" to FieldValue.serverTimestamp(),
                "linkedEmails" to FieldValue.arrayUnion(userEmail),
                "uninstallProtection" to prefs.syncUninstallProtectionEnabled,
                "blockedAppsCount" to prefs.syncBlockedAppsCount,
                "blockedKeywordsCount" to prefs.syncBlockedKeywordsCount,
                "timedLockDays" to prefs.getTimedUninstallProtectionRemainingDays()
            )

            firestore.collection("devices").document(deviceId)
                .set(deviceDoc, SetOptions.merge())
                .await()

            syncCache.edit().putLong("last_device_sync_time", now).apply()
            Log.i(
                TAG,
                "Successfully synced user device to Firestore: " +
                    "user=${LogPrivacy.emailRef(userEmail)} device=${LogPrivacy.deviceRef(deviceId)}"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync user device to Firestore: ${e.message}")
        }
    }

    override suspend fun signInWithGoogle(activityContext: Context): Result<AuthUser> {
        val credentialManager = CredentialManager.create(activityContext)

        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(AuthConfig.GOOGLE_WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                context = activityContext,
                request = request
            )

            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken

                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
                val firebaseUser = authResult.user

                val resolvedEmail = firebaseUser?.email ?: if (googleIdTokenCredential.id.contains("@")) googleIdTokenCredential.id else null

                val user = AuthUser(
                    id = firebaseUser?.uid ?: googleIdTokenCredential.id,
                    displayName = firebaseUser?.displayName ?: googleIdTokenCredential.displayName,
                    email = resolvedEmail,
                    photoUrl = firebaseUser?.photoUrl?.toString() ?: googleIdTokenCredential.profilePictureUri?.toString(),
                    idToken = idToken
                )
                prefs.saveAuthUser(user)

                val deviceId = prefs.getOrCreateDeviceId()
                syncUserDeviceToFirestore(user, deviceId, force = true)

                Result.success(user)
            } else {
                Result.failure(IllegalStateException("Unsupported credential type: " + credential.type))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "Google Sign-In cancelled by user")
            Result.failure(e)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google Sign-In Credential error: " + e.message, e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In error: " + e.message, e)
            Result.failure(e)
        }
    }

    override fun isSignOutLocked(): Boolean = getSignOutLockReason() != null

    override fun getSignOutLockReason(): String? {
        val sessionActive = try {
            sessionBlockManagerProvider.get().isSessionCurrentlyActive()
        } catch (_: Throwable) {
            false
        }
        if (sessionActive) {
            return "Cannot log out while an active Focus Session is in progress."
        }

        val isProtectionActive = prefs.syncUninstallProtectionEnabled
        if (isProtectionActive) {
            val remainingDays = prefs.getTimedUninstallProtectionRemainingDays()
            return if (remainingDays > 0) {
                "Cannot log out while Uninstall Protection is active ($remainingDays days remaining in timed lock). Complete challenge to turn it off first."
            } else {
                "Cannot log out while Uninstall Protection is active. Complete challenge to turn it off first."
            }
        }

        val isPartnerActive = try {
            partnershipRepositoryProvider.get().isFastPartnershipActive()
        } catch (_: Throwable) {
            false
        }
        if (isPartnerActive) {
            return "Cannot log out while an Accountability Partner is actively linked."
        }

        return null
    }

    override suspend fun signOut(activityContext: Context): Result<Unit> {
        val lockReason = getSignOutLockReason()
        if (lockReason != null) {
            Log.w(TAG, "Sign out blocked by Anti-Tamper Shield: $lockReason")
            return Result.failure(IllegalStateException(lockReason))
        }

        val deviceId = prefs.getOrCreateDeviceId()
        try {
            firestore.collection("devices").document(deviceId)
                .update(
                    mapOf(
                        "isOnline" to false,
                        "lastActive" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (e: Exception) {
            Log.d(TAG, "Could not update device offline status in Firestore: ${e.message}")
        }

        return try {
            val credentialManager = CredentialManager.create(activityContext)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            firebaseAuth.signOut()
            prefs.clearAuthUser()
            Result.success(Unit)
        } catch (e: Exception) {
            firebaseAuth.signOut()
            prefs.clearAuthUser()
            Result.success(Unit)
        }
    }
}
