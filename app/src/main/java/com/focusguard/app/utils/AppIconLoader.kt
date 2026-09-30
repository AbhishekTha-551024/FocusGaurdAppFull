package com.focusguard.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AppIconLoader
 * =============
 * High-performance, global in-memory cached app icon loader.
 * Uses a static LruCache of 300 icons to ensure 120 FPS scrolling with zero UI jank.
 */
@Singleton
class AppIconLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val iconCache = LruCache<String, ImageBitmap>(300)

        fun getCachedIcon(packageName: String): ImageBitmap? {
            return iconCache.get(packageName)
        }

        suspend fun loadIcon(context: Context, packageName: String): ImageBitmap? {
            iconCache.get(packageName)?.let { return it }

            return withContext(Dispatchers.IO) {
                try {
                    val pm = context.packageManager
                    val drawable = pm.getApplicationIcon(packageName)
                    val bitmap = drawableToBitmap(drawable, 96, 96)
                    val imageBitmap = bitmap.asImageBitmap()
                    iconCache.put(packageName, imageBitmap)
                    imageBitmap
                } catch (e: Exception) {
                    null
                }
            }
        }

        private fun drawableToBitmap(drawable: Drawable, width: Int, height: Int): Bitmap {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                return Bitmap.createScaledBitmap(drawable.bitmap, width, height, true)
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        }
    }

    suspend fun loadIcon(packageName: String): ImageBitmap? {
        return loadIcon(context, packageName)
    }
}
