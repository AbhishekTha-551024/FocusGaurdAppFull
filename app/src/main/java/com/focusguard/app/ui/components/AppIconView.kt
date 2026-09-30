package com.focusguard.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.focusguard.app.utils.AppIconLoader

private val Navy700 = Color(0xFF1F2937)
private val White60 = Color(0x99FFFFFF)

@Composable
fun AppIconView(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val context = LocalContext.current
    val cachedBitmap = remember(packageName) { AppIconLoader.getCachedIcon(packageName) }
    var iconBitmap by remember(packageName) { mutableStateOf(cachedBitmap) }

    if (iconBitmap == null) {
        LaunchedEffect(packageName) {
            val bmp = AppIconLoader.loadIcon(context, packageName)
            if (bmp != null) {
                iconBitmap = bmp
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(Navy700),
        contentAlignment = Alignment.Center
    ) {
        val bmp = iconBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.size(size * 0.82f)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Android,
                contentDescription = null,
                tint = White60,
                modifier = Modifier.size(size * 0.6f)
            )
        }
    }
}
