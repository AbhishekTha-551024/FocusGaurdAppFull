package com.focusguard.app.ui.protection.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy800 = Color(0xFF0E2419)
private val Navy900 = Color(0xFF07140D)
private val Amber400 = Color(0xFFFBBF24)
private val Success = Color(0xFF22C55E)
private val White90 = Color(0xE6FFFFFF)
private val White60 = Color(0x99FFFFFF)
private val Rose400 = Color(0xFFFB7185)

@Composable
fun OemGuidanceRecentAppsCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Step 3: Recent Apps Lock Guidance
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Navy800),
            border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
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
                            .background(Color(0xFF818CF8).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Step 3: Lock FocusGuard in Recent Apps",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "Most OEM skins (Xiaomi, Realme, Vivo, Samsung) close all background apps when you tap 'Clear All' in Recent Apps unless you lock the app card.",
                    color = White60,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Navy900,
                    border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = Amber400,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "How to Lock FocusGuard:",
                                color = Amber400,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "1. Swipe up / tap button to open Recent Apps overview.",
                            color = White90,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "2. Locate the FocusGuard app card.",
                            color = White90,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "3. Tap the 🔒 Lock or Pin icon at the top of the card (or long-press the card and select 'Lock').",
                            color = Success,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Periodic Heartbeat info
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0x14FFFFFF),
            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Rose400,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "FocusGuard includes a 15-minute background heartbeat self-check that alerts you with a notification if protection is ever disabled.",
                    color = White60,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
