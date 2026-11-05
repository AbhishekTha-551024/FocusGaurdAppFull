package com.focusguard.app.feature.timelimits.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy900   = Color(0xFF07140D)
private val Purple400 = Color(0xFF22C55E)
private val White60   = Color(0x99FFFFFF)

/**
 * =====================================================================
 * TimeLimitEmptyState.kt - Empty State Visual Placeholder Component
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Jab user ne kisi bhi app par koi daily screen time limit nahi lagayi hoti, tab yeh
 * component screen ke center me ek beautiful empty state placeholder render karta hai.
 * Isme hourglass icon, informative explanation text, aur "Set Daily Limit" primary action button hota hai.
 *
 * ## 2. UX Goal:
 * User ko blank screen dekh kar confuse hone se bachana aur unhe batana ki Time Limits feature
 * kaise kaam karta hai: "Set daily caps (e.g. 30 min/day on YouTube or Instagram). Once exceeded, apps are locked until midnight."
 * =====================================================================
 */
@Composable
fun TimeLimitEmptyState(
    onAddLimitClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(Purple400.copy(alpha = 0.15f))
                .border(1.5.dp, Purple400.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.HourglassTop,
                contentDescription = null,
                tint = Purple400,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "No Daily Limits Configured",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Set daily caps (e.g. 30 min/day on YouTube or Instagram). Once exceeded, apps are locked until midnight.",
            color = White60,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onAddLimitClick,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Purple400, contentColor = Navy900),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            modifier = Modifier.height(44.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Set Daily Limit", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1)
        }
    }
}
