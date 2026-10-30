package com.focusguard.app.feature.home.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.PartnershipEntity
import com.focusguard.app.core.designsystem.theme.*
import com.focusguard.app.core.model.PartnershipStatus

/**
 * =========================================================================================
 * ARCHITECTURE COMPONENT: UI Card Composable (Home Feature - Friction Barrier)
 * =========================================================================================
 *
 * PURPOSE & RESPONSIBILITIES:
 * 1. Visual Friction Barrier State:
 *    - Displays the user's active psychological commitment mechanism on the Home Dashboard.
 *    - Renders dual states:
 *      * [isChallengeActive = true]: Displays "FRICTION ON", indicating that modifying or disabling
 *        focus rules requires solving a challenge.
 *      * [isChallengeActive = false]: Displays "FRICTION OFF" with a prominent call to action to
 *        add a barrier.
 *
 * 2. Multi-Tier Challenge Support:
 *    - Self-Commitment Tier: Offline Typing Challenge (typing a ~500-character mindful passage)
 *      or Time Delay (waiting 24 hours).
 *    - True Hard Mode Tier: Cloud Accountability Partner ([ChallengeType.PARTNER]), displaying the
 *      linked partner's email ([PartnershipEntity]) and showing whether the invite is pending or active.
 *
 * 3. Anti-Tamper Interaction:
 *    - [onRequestRemoveChallenge]: Initiates the challenge flow if the user tries to remove friction.
 * =========================================================================================
 */
@Composable
fun AccountabilityChallengeCard(
    isChallengeActive: Boolean,
    challengeType: ChallengeType,
    partnership: PartnershipEntity?,
    onRequestRemoveChallenge: () -> Unit,
    onOpenAddChallengeDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        if (isChallengeActive) {
            val isPartnerTier = challengeType == ChallengeType.PARTNER
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Forest800),
                border = androidx.compose.foundation.BorderStroke(
                    1.2.dp,
                    Success.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 15.dp, bottom = 10.dp, start = 12.dp, end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPartnerTier) "True Hard Mode" else "Self-Commitment Mode",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isPartnerTier) Amber400 else Success)
                                )
                                Text(
                                    text = if (isPartnerTier) {
                                        val pEmail = partnership?.partnerEmail
                                        if (pEmail != null) "Active · Partner ($pEmail)" else "Active · Partner Approval Required"
                                    } else {
                                        "Active · Typing barrier on (Offline)"
                                    },
                                    color = if (isPartnerTier) Amber400 else Success,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onRequestRemoveChallenge,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Rose400.copy(alpha = 0.65f)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Rose400.copy(alpha = 0.12f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = Rose400,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Remove", color = Rose400, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (isPartnerTier) {
                        val partnerEmail = partnership?.partnerEmail ?: "No partner linked"
                        val isPending = partnership?.status == PartnershipStatus.PENDING_INVITE
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Amber400.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, Amber400.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SupervisorAccount,
                                    contentDescription = null,
                                    tint = Amber400,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Partner: $partnerEmail",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                    Text(
                                        text = if (isPending) "Invite pending · Awaiting partner acceptance" else "Approval from this partner is required to unblock or modify focus rules",
                                        color = White60,
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Top-Left Badge (Active)
            Surface(
                shape = RoundedCornerShape(topStart = 10.dp, topEnd = 4.dp, bottomEnd = 10.dp, bottomStart = 4.dp),
                color = if (isPartnerTier) Color(0xFF2E2006) else Color(0xFF0F3822),
                shadowElevation = 3.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isPartnerTier) Amber400.copy(alpha = 0.5f) else Color(0x3322C55E)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isPartnerTier) Icons.Default.SupervisorAccount else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isPartnerTier) Amber400 else Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = if (isPartnerTier) "PARTNER GATED (HARD MODE)" else "FRICTION ON",
                        color = if (isPartnerTier) Amber400 else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        letterSpacing = 0.2.sp
                    )
                }
            }
        } else {
            // INACTIVE STATE: Show "Add Accountability" Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Forest800),
                border = androidx.compose.foundation.BorderStroke(
                    1.2.dp,
                    Amber400.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable { onOpenAddChallengeDialog() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp, bottom = 14.dp, start = 14.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Amber400.copy(alpha = 0.12f))
                            .border(1.dp, Amber400.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = Amber400,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Accountability Challenge",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "No friction active. Add a barrier to keep your promises.",
                            color = White60,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp
                        )
                    }

                    Button(
                        onClick = onOpenAddChallengeDialog,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = Navy900),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Navy900,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Add", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Top-Left Badge (Inactive)
            Surface(
                shape = RoundedCornerShape(topStart = 10.dp, topEnd = 4.dp, bottomEnd = 10.dp, bottomStart = 4.dp),
                color = Color(0xFF231C08),
                shadowElevation = 3.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Amber400.copy(alpha = 0.4f)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = Amber400,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "FRICTION OFF",
                        color = Amber400,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    )
                }
            }
        }
    }
}
