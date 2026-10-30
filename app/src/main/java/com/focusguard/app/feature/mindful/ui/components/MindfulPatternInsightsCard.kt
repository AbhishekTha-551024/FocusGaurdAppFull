package com.focusguard.app.feature.mindful.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.core.database.entity.MindfulIntentLogEntity

private val CardBg     = Color(0xFF0D2117)
private val Emerald400 = Color(0xFF4ADE80)
private val TextMuted  = Color(0x99FFFFFF)

@Composable
fun MindfulPatternInsightsCard(
    logs: List<MindfulIntentLogEntity>,
    consciousOpensCount: Int,
    modifier: Modifier = Modifier
) {
    if (logs.isEmpty()) return

    val repeatCount = logs.count {
        it.intentCategory == "REFLEX" || it.intentCategory == "WAITING_REPLY" || it.intentCategory == "BOREDOM"
    }
    val topCategory = logs
        .filter { !it.wasWalkedAway }
        .groupBy { it.intentCategory }
        .maxByOrNull { it.value.size }?.key ?: "WORK"

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        border = BorderStroke(1.dp, Color(0x2222C55E)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Insights, contentDescription = null, tint = Emerald400, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("MIND PATTERNS & INSIGHTS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald400, letterSpacing = 0.8.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Top Intent", fontSize = 10.sp, color = TextMuted)
                    Text(topCategory, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Column {
                    Text("Rebound Opens", fontSize = 10.sp, color = TextMuted)
                    Text("$repeatCount today", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (repeatCount > 0) Color(0xFFFBBF24) else Emerald400)
                }
                Column {
                    Text("Conscious Opens", fontSize = 10.sp, color = TextMuted)
                    Text("$consciousOpensCount sessions", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
