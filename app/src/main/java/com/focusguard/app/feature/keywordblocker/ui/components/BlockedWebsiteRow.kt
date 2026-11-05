package com.focusguard.app.feature.keywordblocker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.feature.keywordblocker.domain.model.BlockedKeyword

private val Navy800 = Color(0xFF0E2419)
private val Indigo400 = Color(0xFF22C55E)
private val Rose400 = Color(0xFFFB7185)

/**
 * =====================================================================
 * BlockedWebsiteRow.kt - Blocked Website List Item Row Component
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh Compose component LazyColumn ke andar single blocked website ya keyword
 * ka card item render karta hai. User ko blocked domain ka naam, protection status,
 * aur delete icon display karta hai.
 *
 * ## 2. Visual Elements:
 * - `Language Icon Box`: Emerald green tinted circular badge jo browser/website shield indicate karta hai.
 * - `Keyword Title`: Blocked keyword ya domain ka naam (e.g. "reddit.com").
 * - `Status Subtitle`: "Blocked in 7 browsers · 10s shield" status indicator.
 * - `Delete IconButton`: Red/Rose tinted trash can button jo unblock trigger karta hai.
 *
 * ## 3. Performance & Recomposition (Senior Dev Note):
 * - Yeh ek stateless composable hai jisme sirf immutable [BlockedKeyword] aur ek lambda
 *   `onDelete: () -> Unit` pass hota hai.
 * - LazyColumn ke items me `key = { "kw_${it.id}_${it.keyword}" }` use kiya gaya hai jisse list
 *   scroll ya update hone par unnecessary recompositions nahi hote.
 * =====================================================================
 */
@Composable
fun BlockedWebsiteRow(
    keyword: BlockedKeyword,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Navy800),
        border = androidx.compose.foundation.BorderStroke(1.dp, Indigo400.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Indigo400.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = Indigo400,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = keyword.keyword,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Blocked in 7 browsers · 10s shield",
                    color = Indigo400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete keyword",
                    tint = Rose400,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
