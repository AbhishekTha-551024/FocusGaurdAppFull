package com.focusguard.app.ui.challenge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.core.designsystem.theme.*

private val TimerRed = Color(0xFFEF4444)
private val TimerOrange = Color(0xFFF97316)

@Composable
fun SentenceTypingHeader(
    actionDescription: String,
    secondsLeft: Int,
    timerStarted: Boolean,
    isPenaltyActive: Boolean,
    penaltyLeft: Int,
    scale: Float
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Rose400.copy(alpha = 0.15f))
                        .border(1.dp, Rose400.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Rose400,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column {
                    Text(
                        text = "Accountability Challenge",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Sentence Typing Requirement",
                        color = Rose400,
                        fontSize = 11.sp
                    )
                }
            }

            val timerColor = when {
                isPenaltyActive -> TimerRed
                secondsLeft <= 30 && timerStarted -> TimerRed
                secondsLeft <= 60 && timerStarted -> TimerOrange
                else -> Success
            }
            val min = secondsLeft / 60
            val sec = secondsLeft % 60
            val timerText = "%02d:%02d".format(min, sec)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(timerColor.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .scale(if (secondsLeft <= 15 && timerStarted) scale else 1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = timerColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = timerText,
                    color = timerColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Text(
            text = "Type the sentence below to $actionDescription.",
            color = White60,
            fontSize = 11.5.sp,
            lineHeight = 16.sp
        )

        AnimatedVisibility(visible = isPenaltyActive, enter = fadeIn(), exit = fadeOut()) {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = TimerRed.copy(alpha = 0.15f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, TimerRed.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = TimerRed, modifier = Modifier.size(14.dp))
                    Text(
                        text = "App-switch detected! New paragraph assigned. Wait ${penaltyLeft}s",
                        color = TimerRed,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
