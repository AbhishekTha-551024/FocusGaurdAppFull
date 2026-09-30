package com.focusguard.app.data.repository

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.focusguard.app.data.datastore.FocusGuardPreferences
import com.focusguard.app.data.model.AuthUser
import com.focusguard.app.security.AuthConfig
import android.os.Build
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import com.focusguard.app.blockers.SessionBlockManager
import com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver
import javax.inject.Provider
import javax.inject.Singleton

private const val TAG = "AuthRepo"

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: FocusGuardPreferences,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val sessionBlockManagerProvider: Provider<SessionBlockManager>,
    private val partnershipRepositoryProvider: Provider<PartnershipRepository>
) {
    // FIX NC4: Class-level scope with SupervisorJob instead of a raw CoroutineScope().
    // This associates in-flight Firestore writes with a named, cancellable scope,
    // preventing leaked coroutines after sign-out or DI rebind in tests.
    private val authScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val currentUser: Flow<AuthUser?> = prefs.currentAuthUser
    val isLoggedIn: Flow<Boolean> = prefs.currentAuthUser.map { it != null }

    val syncCurrentUser: AuthUser?
        get() = prefs.syncAuthUser

    init {
        // Automatically sync registered email & deviceId to Firestore on startup if logged in
        val user = prefs.syncAuthUser
        if (user != null && !user.email.isNullOrBlank()) {
            authScope.launch {   // FIX NC4: replaced raw CoroutineScope(Dispatchers.IO)
                val deviceId = prefs.getOrCreateDeviceId()
                syncUserDeviceToFirestore(user, deviceId)
            }
        }
    }

    /**
     * Stores the user profile, deviceId, and device hardware info in Firestore
     * under both 'users/{uid}' and 'users_by_email/{email}' for instant admin lookups by Gmail.
     */
    suspend fun syncUserDeviceToFirestore(user: AuthUser, deviceId: String) {
        if (user.email.isNullOrBlank()) return
        try {
            val userMap = hashMapOf(
                "uid" to user.id,
                "email" to user.email.lowercase().trim(),
                "displayName" to (user.displayName ?: ""),
                "photoUrl" to (user.photoUrl ?: ""),
                "deviceId" to deviceId,
                "deviceModel" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "androidVersion" to Build.VERSION.RELEASE,
                "appVersion" to (runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
                }.getOrDefault("unknown")), // FIX NM1: actual installed version
                "updatedAt" to FieldValue.serverTimestamp()
            )

            // 1. Store in users collection by UID
            firestore.collection("users").document(user.id)
                .set(userMap, SetOptions.merge())
                .await()

            // 2. Store in users_by_email collection by lowercase email (Instant search by Gmail for Admin)
            firestore.collection("users_by_email").document(user.email.lowercase().trim())
                .set(userMap, SetOptions.merge())
                .await()

            Log.i(TAG, "Successfully synced user device to Firestore: email=${user.email} deviceId=$deviceId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync user device to Firestore: ${e.message}")
        }
    }

    /**
     * Executes Google Sign-In via Android Credential Manager and links to Firebase Auth.
     */
    suspend fun signInWithGoogle(activityContext: Context): Result<AuthUser> {
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

                // Authenticate with Firebase Auth
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
                val firebaseUser = authResult.user

                val user = AuthUser(
                    id = firebaseUser?.uid ?: googleIdTokenCredential.id,
                    displayName = firebaseUser?.displayName ?: googleIdTokenCredential.displayName,
                    email = firebaseUser?.email ?: googleIdTokenCredential.id,
                    photoUrl = firebaseUser?.photoUrl?.toString() ?: googleIdTokenCredential.profilePictureUri?.toString(),
                    idToken = idToken
                )
                prefs.saveAuthUser(user)

                // Sync deviceId and user info to Firestore for seamless Gmail-based support lookup
                val deviceId = prefs.getOrCreateDeviceId()
                syncUserDeviceToFirestore(user, deviceId)

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

    /**
     * Anti-Tamper: Checks if signing out is currently locked by an active session,
     * armed uninstall protection, or an active accountability partnership.
     */
    fun isSignOutLocked(): Boolean = getSignOutLockReason() != null

    fun getSignOutLockReason(): String? {
        val sessionActive = try {
            sessionBlockManagerProvider.get().isSessionCurrentlyActive()
        } catch (_: Throwable) {
            false
        }
        if (sessionActive) {
            return "Cannot log out while an active Focus Session is in progress."
        }

        val isProtectionActive = prefs.syncUninstallProtectionEnabled ||
                (FocusGuardDeviceAdminReceiver.isDeviceAdminActive(context) && prefs.syncUninstallProtectionEnabled)
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

    suspend fun signOut(activityContext: Context): Result<Unit> {
        val lockReason = getSignOutLockReason()
        if (lockReason != null) {
            Log.w(TAG, "Sign out blocked by Anti-Tamper Shield: $lockReason")
            return Result.failure(IllegalStateException(lockReason))
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
