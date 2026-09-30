package com.focusguard.app.overlay

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

/**
 * Phase 7: Overlay Wallpaper Manager
 *
 * Handles:
 * - Copying user-picked gallery URI to internal storage (safe from deletion)
 * - Loading the saved bitmap for use in BlockOverlayManager
 * - Cache invalidation
 */
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
