package com.focusguard.app.ui.capability

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.capability.Capability
import com.focusguard.app.capability.CapabilityDetails
import com.focusguard.app.capability.CapabilityState
import com.focusguard.app.capability.ProtectionHealthSummary
import com.focusguard.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectionHealthSheet(
    summary: ProtectionHealthSummary,
    onRequestCapability: (Capability) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Forest900,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = White40
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Protection Health",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${summary.activeCount} active · ${summary.neededCount} needed · ${summary.notNeededCount} optional",
                        color = White60,
                        fontSize = 12.5.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (summary.isCoreProtectionActive) Color(0x2222C55E) else Color(0x22FBBF24),
                    border = BorderStroke(
                        1.dp,
                        if (summary.isCoreProtectionActive) Emerald500.copy(alpha = 0.5f) else Amber400.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = if (summary.isCoreProtectionActive) "SHIELD ACTIVE" else "READY",
                        color = if (summary.isCoreProtectionActive) Emerald400 else Amber400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Capabilities List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(summary.items, key = { it.capability.name }) { details ->
                    CapabilityHealthCard(
                        details = details,
                        onEnable = { onRequestCapability(details.capability) }
                    )
                }
            }
        }
    }
}

@Composable
fun CapabilityHealthCard(
    details: CapabilityDetails,
    onEnable: () -> Unit
) {
    val state = details.state
    val borderColor = when (state) {
        CapabilityState.ACTIVE -> Color(0x3322C55E)
        CapabilityState.NEEDS_ACCESS -> Color(0x66FBBF24)
        else -> Forest700
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Forest800,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        when (state) {
                            CapabilityState.ACTIVE -> Color(0x2222C55E)
                            CapabilityState.NEEDS_ACCESS -> Color(0x22FBBF24)
                            else -> Color(0x11FFFFFF)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getCapabilityIcon(details.capability),
                    contentDescription = null,
                    tint = when (state) {
                        CapabilityState.ACTIVE -> Emerald400
                        CapabilityState.NEEDS_ACCESS -> Amber400
                        else -> White40
                    },
                    modifier = Modifier.size(22.dp)
                )
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = details.title,
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Status Indicator
                    when (state) {
                        CapabilityState.ACTIVE -> {
                            Text(
                                text = "● Active",
                                color = Emerald400,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        CapabilityState.NEEDS_ACCESS -> {
                            Text(
                                text = "● Needed",
                                color = Amber400,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        else -> {
                            Text(
                                text = "○ Not needed yet",
                                color = White40,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = when (state) {
                        CapabilityState.ACTIVE -> "Connected and operational."
                        CapabilityState.NEEDS_ACCESS -> "Required for active rules. Tap to enable."
                        else -> "Enable when you use ${details.featureName}."
                    },
                    color = White60,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            // Action button if needed
            if (state == CapabilityState.NEEDS_ACCESS) {
                Button(
                    onClick = onEnable,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Amber400,
                        contentColor = Forest950
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = "Enable",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (state == CapabilityState.ACTIVE) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = Emerald500,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
