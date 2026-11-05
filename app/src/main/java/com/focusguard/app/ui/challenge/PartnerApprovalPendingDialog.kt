package com.focusguard.app.ui.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val Navy900   = Color(0xFF07140D)
private val Navy800   = Color(0xFF0E2419)
private val Navy700   = Color(0xFF163827)
private val Amber400  = Color(0xFFFBBF24)
private val Emerald500 = Color(0xFF22C55E)
private val White90   = Color(0xE6FFFFFF)
private val White60   = Color(0x99FFFFFF)
private val White40   = Color(0x66FFFFFF)

@Composable
fun PartnerApprovalPendingDialog(
    actionDescription: String,
    partnerEmail: String?,
    statusMessage: String = "A request has been recorded. Your partner must approve it before this change applies.",
    onDismiss: () -> Unit
) {
    var isDialogMounted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isDialogMounted = true }

    val animScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDialogMounted) 1f else 0.92f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "partnerModalScale"
    )
    val animAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDialogMounted) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(180),
        label = "partnerModalAlpha"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .scale(animScale)
                .alpha(animAlpha)
                .clip(RoundedCornerShape(22.dp))
                .border(1.5.dp, Amber400.copy(alpha = 0.55f), RoundedCornerShape(22.dp)),
            color = Navy800
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Icon with subtle amber glow
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Amber400.copy(alpha = 0.15f))
                        .border(1.5.dp, Amber400.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = null,
                        tint = Amber400,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Title & Subtitle
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Partner Approval Required",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "True Hard Mode is active. To protect your commitment, self-unlocking is disabled.",
                        color = White60,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }

                // Action Detail Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Navy900,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Amber400.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = Amber400,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Requested Change:",
                                color = Amber400,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = actionDescription.replaceFirstChar { it.uppercase() },
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (!partnerEmail.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Partner: $partnerEmail",
                                color = White60,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Status Banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Amber400.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Amber400.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Amber400,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = statusMessage,
                            color = White90,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Dismiss Button
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber400, contentColor = Navy900),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text(
                        text = "I Understand",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
