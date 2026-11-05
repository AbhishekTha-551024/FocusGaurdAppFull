package com.focusguard.app.data.identity

import android.content.Context
import android.provider.Settings
import com.focusguard.app.domain.repository.DeviceIdentityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FG:Keys"
private const val MIN_STABLE_ID_LENGTH = 8
private const val MAX_STABLE_ID_LENGTH = 64
private val STABLE_ID_PATTERN = Regex("[a-z0-9]{$MIN_STABLE_ID_LENGTH,$MAX_STABLE_ID_LENGTH}")

/**
 * AndroidIdDeviceIdentityRepository
 * =================================
 * Adapter for [DeviceIdentityRepository] backed by `Settings.Secure.ANDROID_ID`.
 *
 * Why this id: since Android 8 it is generated per (device, app signing key) and lives in
 * `Settings.Secure`, i.e. outside the app's own storage. It therefore survives
 * uninstall → reinstall, which is exactly the property the app's own `FG-####-####` id lacks (that
 * one is regenerated blank and cannot key anything durable). It is reset by a factory reset and some
 * OEM dual-space features — which is why it is used only as a lock-restore hint, never as proof of
 * identity or as a credential.
 *
 * Returns blank rather than a guess when the platform has no usable value: a fallback id would write
 * a server record that can never be found again after a reinstall, which is worse than not writing
 * one.
 */
@Singleton
class AndroidIdDeviceIdentityRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : DeviceIdentityRepository {

    /** Read once per process: the value cannot change without a factory reset. */
    private val cachedId: String by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { readAndroidId() }

    override fun reinstallStableId(): String = cachedId

    private fun readAndroidId(): String {
        val raw = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "ANDROID_ID lookup failed: ${e.javaClass.simpleName}")
            null
        }
        val normalized = raw?.trim()?.lowercase().orEmpty()
        return if (STABLE_ID_PATTERN.matches(normalized)) {
            normalized
        } else {
            android.util.Log.w(TAG, "ANDROID_ID unusable (length=${normalized.length}); restore disabled")
            ""
        }
    }
}
