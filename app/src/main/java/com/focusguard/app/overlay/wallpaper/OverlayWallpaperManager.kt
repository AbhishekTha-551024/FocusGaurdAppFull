/**
 * =====================================================================
 * FILE: OverlayWallpaperManager.kt
 * PACKAGE: com.focusguard.app.overlay
 * =====================================================================
 * 
 * 📌 OVERVIEW (YE FILE KYA KARTI HAI?):
 * Ye file FocusGuard ke Custom Overlay Wallpaper feature ka "Storage & Bitmap Cache Manager" hai.
 * User jab settings me jaakar overlay ke background ke liye koi personalized photo
 * ya wallpaper choose karta hai gallery se, toh ye manager us URI ko app ke internal
 * private storage me securely copy karta hai, format normalize karta hai (JPEG 90%),
 * aur BlockOverlayManager ke fast lookup ke liye in-memory cache maintain karta hai.
 *
 * 🏗️ ARCHITECTURE & MEMORY MANAGEMENT:
 * - Internal File Persistence: User-picked gallery URI ko context.filesDir/overlay_wallpaper.jpg
 *   par store karta hai taaki agar user gallery se original photo delete bhi kar de,
 *   tab bhi FocusGuard overlay safely display hoti rahe.
 * - In-Memory Fast Cache: cachedBitmap: Bitmap? reference rakhta hai taaki baar-baar
 *   overlay aane par disk I/O na ho aur 0ms latency me wallpaper render ho sake.
 * - Downsampling (OOM Protection): Disk se load karte waqt inSampleSize = 2 use karta hai
 *   taaki high-resolution camera photos device ki RAM exhaust (OutOfMemoryError) na karein.
 * - Cache Invalidation: Jab user wallpaper change karta hai ya delete karta hai,
 *   toh in-memory bitmap cache ko clear/invalidate karta hai.
 *
 * ⚙️ KEY FUNCTIONS:
 * 1. getBitmapOrNull(): In-memory cache check karta hai; na mile toh disk se safely decode karta hai.
 * 2. saveFromUri(uri): Gallery content URI se stream open karke internal JPEG file me compress karta hai.
 * 3. clearWallpaper(): Saved JPEG file delete karta hai aur cache reset karta hai.
 * 4. invalidateCache(): In-memory bitmap reference clear karta hai taaki fresh decode ho sake.
 * =====================================================================
 */

package com.focusguard.app.overlay.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OverlayWallpaperMgr"
private const val WALLPAPER_FILENAME = "overlay_wallpaper.jpg"

@Singleton
class OverlayWallpaperManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var cachedBitmap: Bitmap? = null
    private val wallpaperFile: File get() = File(context.filesDir, WALLPAPER_FILENAME)

    /**
     * Returns the saved bitmap if available, otherwise null (use preset gradient).
     * Loads from disk on first call, then caches in memory.
     */
    fun getBitmapOrNull(): Bitmap? {
        if (cachedBitmap != null) return cachedBitmap

        val file = wallpaperFile
        if (!file.exists()) return null

        return try {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = 2 // Downsample to avoid OOM
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)?.also {
                cachedBitmap = it
                Log.d(TAG, "Wallpaper loaded from disk: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load wallpaper bitmap", e)
            null
        }
    }

    /**
     * Returns true if a custom wallpaper is saved.
     */
    fun hasWallpaper(): Boolean = wallpaperFile.exists()

    /**
     * Copies the picked gallery URI to internal storage.
     * @return absolute path of saved file, or null on failure.
     */
    fun saveFromUri(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return null

            // Decode and re-encode as JPEG to normalize format
            val sourceBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (sourceBitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from URI")
                return null
            }

            val file = wallpaperFile
            FileOutputStream(file).use { out ->
                sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            // Invalidate cache
            cachedBitmap = sourceBitmap
            Log.i(TAG, "Wallpaper saved to internal storage: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save wallpaper from URI", e)
            null
        }
    }

    /**
     * Deletes the saved wallpaper and clears the cache.
     */
    fun clearWallpaper() {
        cachedBitmap = null
        val file = wallpaperFile
        if (file.exists()) {
            file.delete()
            Log.i(TAG, "Wallpaper cleared from internal storage.")
        }
    }

    /**
     * Invalidates the in-memory cache so next call to getBitmapOrNull() re-reads from disk.
     */
    fun invalidateCache() {
        cachedBitmap = null
    }
}
