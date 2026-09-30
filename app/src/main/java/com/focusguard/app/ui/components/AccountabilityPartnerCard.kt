package com.focusguard.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.data.db.PartnershipEntity
import com.focusguard.app.data.model.AuthUser
import com.focusguard.app.data.model.PartnershipStatus

private val Forest800   = Color(0xFF0E2419)
private val Forest700   = Color(0xFF163827)
private val Emerald500  = Color(0xFF22C55E)
private val Amber400    = Color(0xFFFBBF24)
private val Rose400     = Color(0xFFFB7185)
private val White90     = Color(0xE6FFFFFF)
private val White60     = Color(0x99FFFFFF)
private val White40     = Color(0x66FFFFFF)

@Composable
fun AccountabilityPartnerCard(
    authUser: AuthUser?,
    isAuthLoading: Boolean,
    partnership: PartnershipEntity?,
    isInviteLoading: Boolean,
    inviteError: String?,
    onSignInWithGoogle: () -> Unit,
    onSignOut: () -> Unit,
    onSendInvite: (partnerEmail: String) -> Unit,
    onCancelInvite: (partnershipId: String) -> Unit,
    onRequestRevokePartner: (partnershipId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var partnerEmailInput by remember { mutableStateOf("") }
    val isUserLoggedIn = authUser != null
    val currentUserEmail = authUser?.email

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Forest800),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                partnership?.status == PartnershipStatus.ACTIVE -> Emerald500.copy(alpha = 0.5f)
                partnership?.status == PartnershipStatus.PENDING_INVITE -> Amber400.copy(alpha = 0.5f)
                else -> Color.White.copy(alpha = 0.12f)
            }
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                partnership?.status == PartnershipStatus.ACTIVE -> Emerald500.copy(alpha = 0.15f)
                                partnership?.status == PartnershipStatus.PENDING_INVITE -> Amber400.copy(alpha = 0.15f)
                                else -> Color.White.copy(alpha = 0.08f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SupervisorAccount,
                        contentDescription = null,
                        tint = when {
                            partnership?.status == PartnershipStatus.ACTIVE -> Emerald500
                            partnership?.status == PartnershipStatus.PENDING_INVITE -> Amber400
                            else -> White90
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Accountability Partner",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        if (partnership?.status == PartnershipStatus.ACTIVE) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Emerald500.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    color = Emerald500,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else if (partnership?.status == PartnershipStatus.PENDING_INVITE) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Amber400.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "PENDING",
                                    color = Amber400,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (partnership?.status == PartnershipStatus.ACTIVE) "True Hard Mode Unlocked"
                               else "Required for True Hard Mode",
                        color = White60,
                        fontSize = 12.sp
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            when {
                // Case 1: Not logged in -> Clean Google Sign-In button!
                !isUserLoggedIn -> {
                    Text(
                        text = "Connect your Google Account to link an Accountability Partner. Once linked, they hold the key to approve or deny override requests.",
                        color = White60,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.05f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = Emerald500, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Core protection (app blocking, website filtering, time limits) is 100% offline & private. Sign-In is only used for partner accountability.",
                                color = White60,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Button(
                        onClick = onSignInWithGoogle,
                        enabled = !isAuthLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (isAuthLoading) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign In with Google", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }

                // Case 2: Active Partnership
                partnership?.status == PartnershipStatus.ACTIVE -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF07140D),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Emerald500.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Emerald500, modifier = Modifier.size(18.dp))
                                Text(text = "Linked Partner", color = White60, fontSize = 12.sp)
                            }
                            val displayEmail = if (currentUserEmail == partnership.partnerEmail) {
                                partnership.ownerEmail
                            } else {
                                partnership.partnerEmail
                            }
                            Text(
                                text = displayEmail,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    Text(
                        text = "Your partner holds the key to unlock protected actions. Revoking your partner requires completing the commitment challenge.",
                        color = White60,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    OutlinedButton(
                        onClick = { onRequestRevokePartner(partnership.partnershipId) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose400),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Rose400, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Revoke Partner (Challenge Gated)")
                    }
                }

                // Case 3: Pending Invite
                partnership?.status == PartnershipStatus.PENDING_INVITE -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF07140D),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Amber400.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = Amber400, modifier = Modifier.size(18.dp))
                                Text(text = "Waiting for Acceptance", color = Amber400, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "Waiting for " + partnership.partnerEmail + " to accept your invitation.",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Text(
                        text = "Ask your partner to install FocusGuard, sign in with " + partnership.partnerEmail + ", and accept your invitation.",
                        color = White60,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    OutlinedButton(
                        onClick = { onCancelInvite(partnership.partnershipId) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = White60),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel Invite")
                    }
                }

                // Case 4: Logged in, no partner configured
                else -> {
                    // Profile Info Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF07140D),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Emerald500, modifier = Modifier.size(24.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = authUser.displayName ?: "Google User",
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = authUser?.email ?: "",
                                    color = White60,
                                    fontSize = 11.sp
                                )
                            }
                            TextButton(onClick = onSignOut) {
                                Text("Sign Out", color = Rose400, fontSize = 12.sp)
                            }
                        }
                    }

                    Text(
                        text = "Invite your Accountability Partner by their Google email address:",
                        color = White60,
                        fontSize = 13.sp
                    )

                    if (!inviteError.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Rose400.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = inviteError,
                                color = Rose400,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = partnerEmailInput,
                        onValueChange = { partnerEmailInput = it },
                        label = { Text("Partner's Google Email") },
                        placeholder = { Text("partner@gmail.com") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = White60) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Emerald500,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedLabelColor = Emerald500,
                            unfocusedLabelColor = White60,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = White90
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (partnerEmailInput.isNotBlank()) {
                                onSendInvite(partnerEmailInput.trim())
                            }
                        },
                        enabled = !isInviteLoading && partnerEmailInput.contains("@"),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isInviteLoading) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Send Partner Invite", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
