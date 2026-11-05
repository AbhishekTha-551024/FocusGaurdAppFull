package com.focusguard.app.recovery

import android.content.Context
import android.util.Log
import com.focusguard.app.core.datastore.DirectBootStorage

private const val TAG = "FG:Recovery"
private const val MIRROR_KEY = "a11y_enabled_services_mirror"

/**
 * EnabledServicesMirror
 * ====================
 * Remembers the last complete ENABLED_ACCESSIBILITY_SERVICES list that still contained FocusGuard.
 *
 * Vivo's security scanner (com.vivo.imanager) transiently blanks that secure setting for 2-5s while
 * Settings opens or while it "cleans" background services. Re-asserting during that window from the
 * blanked value alone would silently delete every other accessibility service the user enabled
 * (TalkBack, Alarmy, ...) — collateral damage that also gets the app flagged as a settings abuser.
 * The mirror lets [ProgrammaticAccessibilityEnabler] rebuild the full list instead.
 *
 * Kept in DE storage: readable before first unlock and survives a reboot.
 */
object EnabledServicesMirror {

    /**
     * Persists [enabledServices]. A blank value clears the mirror so the deliberate removal path
     * cannot leave our own component behind in the fallback list.
     */
    fun save(context: Context, enabledServices: String?) {
        val value = enabledServices?.trim().orEmpty()
        try {
            val editor = DirectBootStorage.deCache(context).edit()
            if (value.isEmpty()) editor.remove(MIRROR_KEY) else editor.putString(MIRROR_KEY, value)
            editor.apply()
        } catch (e: Exception) {
            Log.d(TAG, "Could not persist enabled-services mirror: ${e.message}")
        }
    }

    fun load(context: Context): String? = try {
        DirectBootStorage.deCache(context).getString(MIRROR_KEY, null)
    } catch (e: Exception) {
        Log.d(TAG, "Could not read enabled-services mirror: ${e.message}")
        null
    }
}
