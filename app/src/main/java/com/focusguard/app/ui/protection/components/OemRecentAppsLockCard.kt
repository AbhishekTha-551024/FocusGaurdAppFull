package com.focusguard.app.ui.protection.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.ui.theme.White60

@Composable
fun OemRecentAppsLockCard(modifier: Modifier = Modifier) {
    var showRecentAppsGuide by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0x1A818CF8),
        border = BorderStroke(1.dp, Color(0x33818CF8)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFF818CF8),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Optional Bonus: Lock in Recent Apps",
                    color = Color(0xFF818CF8),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { showRecentAppsGuide = !showRecentAppsGuide },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = if (showRecentAppsGuide) "Hide" else "How?",
                        color = Color(0xFF818CF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            AnimatedVisibility(visible = showRecentAppsGuide) {
                Text(
                    text = "Recent apps overview me FocusGuard card par tap karke 🔒 Lock/Pin icon tap karein taaki 'Clear All' tap karne par FocusGuard background se band na ho.",
                    color = White60,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
