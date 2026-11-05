package com.focusguard.app.core.datastore

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.UserManager
import android.util.Log

private const val TAG = "FG:Keys"

/**
 * DirectBootStorage
 * =================
 * The single place that decides which preference file is physically readable right now.
 *
 * After a reboot Android keeps the user's credential-encrypted (CE) storage locked until the user
 * unlocks the device for the first time. Any CE read/write inside that window throws
 * IllegalStateException, and when it happens while Hilt is building the object graph it kills the
 * entire process — including the Accessibility Service the OS is trying to bind. That crash was the
 * post-reboot bypass window: protection was still "ON" in DE storage but nothing was alive to enforce it.
 *
 * Every accessor here is exception-safe and falls back to the device-protected (DE) mirror that
 * [PreferencesCore] keeps up to date, so protection state is readable before first unlock.
 */
object DirectBootStorage {

    /** CE fast-read snapshot hydrated from DataStore. Holds every key. */
    const val CE_CACHE_FILE = "focusguard_sync_cache"

    /** DE mirror. Readable at direct boot; holds the uninstall-protection keys. */
    const val DE_CACHE_FILE = "focusguard_de_cache"

    /** True once the user has unlocked CE storage for the first time since boot. */
    fun isUserUnlocked(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true
        return try {
            val userManager = context.getSystemService(UserManager::class.java)
            userManager == null || userManager.isUserUnlocked
        } catch (e: Exception) {
            Log.w(TAG, "UserManager unlock state unavailable, assuming unlocked: ${e.message}")
            true
        }
    }

    /** CE snapshot cache, or null while credential storage is still locked. Never throws. */
    fun ceCacheOrNull(context: Context): SharedPreferences? {
        if (!isUserUnlocked(context)) return null
        return try {
            context.getSharedPreferences(CE_CACHE_FILE, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            Log.e(TAG, "CE cache unavailable: ${e.message}")
            null
        }
    }

    /**
     * DE mirror. Device-protected storage is available from early boot, so this is the source of
     * truth before the first user unlock.
     */
    fun deCache(context: Context): SharedPreferences =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                context.createDeviceProtectedStorageContext()
                    .getSharedPreferences(DE_CACHE_FILE, Context.MODE_PRIVATE)
            } catch (e: Exception) {
                Log.e(TAG, "DE cache unavailable, falling back to CE handle: ${e.message}")
                context.getSharedPreferences(CE_CACHE_FILE, Context.MODE_PRIVATE)
            }
        } else {
            context.getSharedPreferences(CE_CACHE_FILE, Context.MODE_PRIVATE)
        }

    /**
     * Best readable cache right now: the CE snapshot once unlocked, the DE mirror while locked.
     * Callers must only rely on keys that the DE mirror carries (uninstall protection) during the
     * locked window.
     */
    fun readCache(context: Context): SharedPreferences = ceCacheOrNull(context) ?: deCache(context)
}
