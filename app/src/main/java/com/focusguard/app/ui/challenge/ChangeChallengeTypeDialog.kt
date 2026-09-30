package com.focusguard.app.ui.challenge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusguard.app.data.db.ChallengeType
import com.focusguard.app.data.db.PartnershipEntity
import com.focusguard.app.data.model.PartnershipStatus

private val Navy900  = Color(0xFF07140D)
private val Navy800  = Color(0xFF0E2419)
private val Navy700  = Color(0xFF163827)
private val Emerald500 = Color(0xFF22C55E)
private val Amber400 = Color(0xFFFBBF24)
private val Rose400  = Color(0xFFFB7185)
private val White90  = Color(0xE6FFFFFF)
private val White60  = Color(0x99FFFFFF)
private val White40  = Color(0x66FFFFFF)

@Composable
fun ChangeChallengeTypeDialog(
    currentType: ChallengeType,
    currentSentence: String,
    currentDelayHours: Int = 0,
    isUserLoggedIn: Boolean = false,
    currentUserEmail: String? = null,
    isAuthLoading: Boolean = false,
    partnership: PartnershipEntity? = null,
    isPartnerInviteLoading: Boolean = false,
    partnerInviteError: String? = null,
    title: String = "Select Accountability Challenge",
    confirmButtonText: String = "Activate Challenge",
    onSignInWithGoogle: () -> Unit = {},
    onSendPartnerInvite: (String) -> Unit = {},
    onConfirmChange: (ChallengeType, String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by remember {
        mutableStateOf(if (currentType == ChallengeType.PARTNER && !isUserLoggedIn) ChallengeType.TYPING else currentType)
    }
    var sentence by remember { mutableStateOf(currentSentence) }
    var delayHours by remember { mutableIntStateOf(currentDelayHours) }

    val hasPartner = partnership != null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(1.5.dp, Emerald500.copy(alpha = 0.5f), RoundedCornerShape(22.dp)),
            color = Navy800
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )



                // ── Option 1: Self-Commitment Mode (Typing Challenge · 100% Offline) ──
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selectedType == ChallengeType.TYPING) Emerald500.copy(alpha = 0.15f) else Navy700,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (selectedType == ChallengeType.TYPING) Emerald500 else Color(0x14FFFFFF)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedType = ChallengeType.TYPING }
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = selectedType == ChallengeType.TYPING,
                                onClick = { selectedType = ChallengeType.TYPING },
                                colors = RadioButtonDefaults.colors(selectedColor = Emerald500)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Self-Commitment Mode",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Emerald500.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "100% Offline",
                                            color = Emerald500,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }

                    }
                }

                // ── Option 2: True Hard Mode (Accountability Partner Gated) ──
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selectedType == ChallengeType.PARTNER) Amber400.copy(alpha = 0.15f) else Navy700,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (selectedType == ChallengeType.PARTNER) Amber400 else Color(0x14FFFFFF)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isUserLoggedIn) {
                                selectedType = ChallengeType.PARTNER
                            } else {
                                onSignInWithGoogle()
                            }
                        }
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = selectedType == ChallengeType.PARTNER,
                                onClick = {
                                    if (isUserLoggedIn) {
                                        selectedType = ChallengeType.PARTNER
                                    } else {
                                        onSignInWithGoogle()
                                    }
                                },
                                enabled = isUserLoggedIn,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Amber400,
                                    unselectedColor = if (isUserLoggedIn) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.25f)
                                )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Accountability Partner",
                                        color = if (isUserLoggedIn) Color.White else White60,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (!isUserLoggedIn) Rose400.copy(alpha = 0.2f) else Amber400.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = if (!isUserLoggedIn) "Login Required" else "True Hard Mode",
                                            color = if (!isUserLoggedIn) Rose400 else Amber400,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Action Buttons (Cancel / Confirm) ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = White60, fontSize = 12.5.sp)
                    }

                    val canConfirm = when (selectedType) {
                        ChallengeType.TYPING -> true
                        ChallengeType.PARTNER -> hasPartner
                    }

                    Button(
                        onClick = { onConfirmChange(selectedType, sentence, delayHours) },
                        enabled = canConfirm,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedType == ChallengeType.PARTNER) Amber400 else Emerald500,
                            contentColor = Navy900
                        )
                    ) {
                        Text(
                            text = if (selectedType == ChallengeType.PARTNER && !hasPartner) "Link Partner First" else confirmButtonText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
