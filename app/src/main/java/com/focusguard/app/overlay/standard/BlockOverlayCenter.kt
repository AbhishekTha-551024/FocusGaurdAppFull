package com.focusguard.app.overlay.standard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.overlay.manager.OverlayBlockType

/**
 * Center stack of the standard block shield: accent badge, countdown ring, target name,
 * context tag and description. Split out of [BlockOverlayContent] to keep both files
 * within the project's 300-line rule.
 */
@Composable
internal fun BlockOverlayCenter(
    heading: OverlayHeading,
    blockType: OverlayBlockType,
    targetName: String,
    resolvedDescription: String,
    secondsLeft: Int,
    progressProvider: () -> Float,
    compact: Boolean
) {
    val accentColor = heading.accentColor
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 20.dp)
    ) {
        // Accent Badge
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = accentColor.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(
                    imageVector = heading.icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = heading.headline,
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // Countdown Ring
        CountdownRing(
            secondsLeft = secondsLeft,
            progressProvider = progressProvider,
            accentColor = accentColor
        )

        // Target name + description
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (blockType == OverlayBlockType.KEYWORD_BLOCK ||
                blockType == OverlayBlockType.INCOGNITO_BLOCK ||
                blockType == OverlayBlockType.CLONER_BLOCK ||
                blockType == OverlayBlockType.SESSION_LOCK_BLOCK
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = when (blockType) {
                            OverlayBlockType.INCOGNITO_BLOCK -> "PRIVATE BROWSING"
                            OverlayBlockType.CLONER_BLOCK -> "CLONED APP DETECTED"
                            OverlayBlockType.SESSION_LOCK_BLOCK -> "PHONE LOCKED DOWN"
                            else -> "BLOCKED KEYWORD"
                        },
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = if (blockType == OverlayBlockType.KEYWORD_BLOCK) "“$targetName”" else targetName,
                color = Color.White,
                fontSize = if (compact) 20.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = resolvedDescription,
                color = White90,
                fontSize = if (compact) 13.sp else 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = if (compact) 18.sp else 20.sp
            )
        }
    }
}
