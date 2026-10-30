package com.focusguard.app.ui.protection.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.ui.theme.*
import com.focusguard.app.utils.BatteryOptimizationHelper
import com.focusguard.app.utils.OemBrand

@Composable
fun OemBatteryOptimizationCard(
    context: Context,
    brand: OemBrand,
    isBatteryIgnored: Boolean,
    onBatteryStatusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Forest800),
        border = BorderStroke(
            1.dp,
            if (isBatteryIgnored) Emerald500.copy(alpha = 0.5f) else Color(0xFFF59E0B).copy(alpha = 0.4f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isBatteryIgnored) Emerald500.copy(alpha = 0.2f) else Color(0x33F59E0B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isBatteryIgnored) Icons.Default.Check else Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = if (isBatteryIgnored) Emerald400 else Color(0xFFFBBF24),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (brand == OemBrand.SAMSUNG) "Step 1: Put App to Sleep OFF" else "Step 1: Battery Saver Exemption",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isBatteryIgnored) "Unrestricted Battery Active ✓" else "Currently restricted by battery saver",
                        color = if (isBatteryIgnored) Emerald400 else Color(0xFFFBBF24),
                        fontSize = 11.5.sp
                    )
                }
            }

            Text(
                text = if (brand == OemBrand.SAMSUNG) {
                    "Samsung One UI app ko sleep mode me daal deta hai. Set Battery to 'Unrestricted' so FocusGuard never gets paused."
                } else {
                    "Android battery saver ko FocusGuard ko background me sleep me daalne se rokein."
                },
                color = White60,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Button(
                onClick = {
                    BatteryOptimizationHelper.requestBatteryOptimizationExemption(context)
                    onBatteryStatusChanged(BatteryOptimizationHelper.isBatteryOptimizationIgnored(context))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isBatteryIgnored) Color(0x3322C55E) else Emerald500,
                    contentColor = if (isBatteryIgnored) Emerald400 else Forest950
                )
            ) {
                Text(
                    text = if (isBatteryIgnored) "Battery Exemption Active ✓" else "Allow Unrestricted Battery",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
