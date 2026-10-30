package com.focusguard.app.feature.settings.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.core.designsystem.theme.*
import com.focusguard.app.feature.settings.ui.SettingsUiState
import com.focusguard.app.overlay.standard.OVERLAY_PRESETS

private val Violet500 = Color(0xFF22C55E)

@Composable
fun OverlayWallpaperThemeDialog(
    uiState: SettingsUiState,
    onSetPreset: (Int) -> Unit,
    onPickGallery: () -> Unit,
    onClearWallpaper: () -> Unit,
    onSetDimAlpha: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Wallpaper & Scrim Darkness", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Background Theme", color = White60, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OVERLAY_PRESETS.forEach { preset ->
                        val isSelected = !uiState.hasCustomWallpaper && uiState.overlayPresetId == preset.id
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) Violet500.copy(alpha = 0.25f) else Navy700,
                            border = BorderStroke(1.dp, if (isSelected) Violet500 else Color(0x22FFFFFF)),
                            modifier = Modifier.clickable { onSetPreset(preset.id) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(Brush.horizontalGradient(preset.gradient))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = preset.name,
                                    color = if (isSelected) Color.White else White60,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPickGallery,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Violet500.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(15.dp), tint = Violet500)
                        Spacer(Modifier.width(6.dp))
                        Text("Custom Photo", color = Color.White, fontSize = 12.sp)
                    }

                    if (uiState.hasCustomWallpaper) {
                        Button(
                            onClick = onClearWallpaper,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFFEF4444))
                            Spacer(Modifier.width(6.dp))
                            Text("Reset Preset", color = Color(0xFFEF4444), fontSize = 12.sp)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Dim Darkness (Scrim)", color = White60, fontSize = 12.sp)
                        Text("${(uiState.overlayDimAlpha * 100).toInt()}%", color = Violet500, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = uiState.overlayDimAlpha,
                        onValueChange = onSetDimAlpha,
                        valueRange = 0.1f..0.95f,
                        colors = SliderDefaults.colors(
                            thumbColor = Violet500,
                            activeTrackColor = Violet500,
                            inactiveTrackColor = Navy700
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = Violet500, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Forest800,
        shape = RoundedCornerShape(18.dp)
    )
}
