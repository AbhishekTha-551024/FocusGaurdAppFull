package com.focusguard.app.feature.timelimits.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.feature.timelimits.ui.AppTimeLimitUiModel
import com.focusguard.app.ui.components.AppIconView

private val Navy800   = Color(0xFF0E2419)
private val Navy700   = Color(0xFF163827)
private val Purple400 = Color(0xFF22C55E)
private val Rose400   = Color(0xFFFB7185)
private val White90   = Color(0xE6FFFFFF)
private val White40   = Color(0x66FFFFFF)

/**
 * =====================================================================
 * CompactTimeLimitRow.kt - Active Time Cap List Item Component
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh Compose component active limited apps ki list ka single row card draw karta hai.
 * Isme app icon, app name, kitna time use ho chuka hai out of daily cap,
 * locked minutes badge, delete trash icon, aur linear progress bar hoti hai.
 *
 * ## 2. Visual & Dynamic States:
 * - `Normal State`: Green/Emerald color accent (`Purple400`), smooth progress bar.
 * - `Over-Limit State (Limit Exceeded)`: Jab `usedMinutesToday >= dailyLimitMinutes` ho jata hai,
 *   toh card ka background aur progress bar Red/Rose tint (`Rose400`) me shift ho jati hai,
 *   jisse user ko instantly visual cue milta hai ki yeh app lock ho chuka hai.
 *
 * ## 3. Performance & Stability (Senior Dev Note):
 * - `maxOf(1, model.dailyLimitMinutes)` guard use kiya gaya hai taaki zero-division se NaN/crash na ho.
 * - Progress ko `.coerceIn(0f, 1f)` me lock kiya gaya hai taaki bar card se bahar na nikle.
 * =====================================================================
 */
@Composable
fun CompactTimeLimitRow(
    model: AppTimeLimitUiModel,
    onDelete: () -> Unit
) {
    val denom = maxOf(1, model.dailyLimitMinutes)
    val progress = (model.usedMinutesToday.toFloat() / denom.toFloat()).coerceIn(0f, 1f)
    val isOverLimit = model.usedMinutesToday >= model.dailyLimitMinutes

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverLimit) Color(0x2EFB7185) else Navy800
        ),
        border = BorderStroke(
            1.dp,
            if (isOverLimit) Rose400.copy(alpha = 0.6f) else Color(0x14FFFFFF)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppIconView(packageName = model.appInfo.packageName, size = 36.dp)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.appInfo.appName,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${model.usedMinutesToday}m used of ${model.dailyLimitMinutes}m cap",
                        color = if (isOverLimit) Rose400 else Purple400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Navy700,
                    border = BorderStroke(1.dp, Color(0x20FFFFFF))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = White40,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "${model.dailyLimitMinutes}m",
                            color = White90,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Daily Limit",
                        tint = Rose400,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (isOverLimit) Rose400 else Purple400,
                trackColor = Color(0x1AFFFFFF)
            )
        }
    }
}
