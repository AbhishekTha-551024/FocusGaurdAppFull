package com.focusguard.app.ui.protection.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.utils.OemBrand

private val Navy800 = Color(0xFF0E2419)
private val Navy900 = Color(0xFF07140D)
private val Amber400 = Color(0xFFFBBF24)
private val White90 = Color(0xE6FFFFFF)

@Composable
fun OemGuidanceAutostartCard(
    context: Context,
    brand: OemBrand,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Navy800),
        border = BorderStroke(1.dp, Amber400.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Amber400.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = null,
                        tint = Amber400,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "Step 2: Enable Autostart / Auto-Launch",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val oemInstructions = when (brand) {
                OemBrand.XIAOMI -> listOf(
                    "1. Tap 'Open Autostart Settings' below.",
                    "2. Turn ON the toggle for 'FocusGuard'.",
                    "3. Go to Battery Saver and choose 'No restrictions'."
                )
                OemBrand.OPPO_REALME -> listOf(
                    "1. Tap 'Open Autostart Settings' below.",
                    "2. Enable 'Auto-launch' and 'Allow background activity'.",
                    "3. Lock FocusGuard in the Recent Apps overview."
                )
                OemBrand.VIVO -> listOf(
                    "1. Tap 'Open Autostart Settings' below.",
                    "2. In iManager, enable Autostart for FocusGuard.",
                    "3. Under 'Background Power Consumption', select 'High background power consumption'."
                )
                OemBrand.SAMSUNG -> listOf(
                    "1. Open App Info / Device Care.",
                    "2. Ensure FocusGuard is NOT in 'Sleeping apps' or 'Deep sleeping apps'.",
                    "3. Set Battery usage to 'Unrestricted'."
                )
                OemBrand.ONEPLUS -> listOf(
                    "1. Tap 'Open Autostart Settings' below.",
                    "2. Turn ON Auto-launch for FocusGuard.",
                    "3. Set Battery Optimization to 'Don't optimize'."
                )
                OemBrand.HUAWEI -> listOf(
                    "1. In Settings > Battery > App Launch.",
                    "2. Switch FocusGuard to 'Manage manually'.",
                    "3. Enable Auto-launch, Secondary launch, and Run in background."
                )
                OemBrand.GENERIC -> listOf(
                    "1. Open App Info below.",
                    "2. Ensure background activity and autostart are allowed.",
                    "3. Exclude FocusGuard from any task killers or aggressive battery savers."
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                oemInstructions.forEach { step ->
                    Text(
                        text = step,
                        color = White90,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Button(
                onClick = { OemLauncherUtils.openOemAutostartSettings(context, brand) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Amber400, contentColor = Navy900)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Launch,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Open Autostart Settings",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
