package com.focusguard.app.ui.protection

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusguard.app.ui.theme.*
import com.focusguard.app.utils.BatteryOptimizationHelper
import com.focusguard.app.utils.OemBrand

@Composable
fun OemProtectionDialog(
    onDismiss: () -> Unit,
    onAcknowledge: () -> Unit = onDismiss
) {
    val context = LocalContext.current
    val brand = remember { BatteryOptimizationHelper.getOemBrand() }
    val shortName = remember { BatteryOptimizationHelper.getOemShortName() }
    val headline = remember { BatteryOptimizationHelper.getOemDialogHeadline() }
    val whyNeeded = remember { BatteryOptimizationHelper.getOemWhyNeededText() }
    val stepInstructions = remember { BatteryOptimizationHelper.getOemStepInstructions() }

    var isBatteryIgnored by remember {
        mutableStateOf(BatteryOptimizationHelper.isBatteryOptimizationIgnored(context))
    }

    var showRecentAppsGuide by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(26.dp)),
            color = Forest950,
            border = BorderStroke(1.5.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Forest950, Forest900, Forest950)
                        )
                    )
            ) {
                // ── HEADER ────────────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Brand Icon Badge
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // Device Detection Badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "📱 ${Build.MANUFACTURER.uppercase()} ${Build.MODEL.uppercase()} DETECTED",
                                color = Color(0xFFFBBF24),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // Dynamic Headline (e.g. "Vivo detected: Auto-start allow karein")
                        Text(
                            text = headline,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp
                        )
                    }

                    // Close icon top-right
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = White60,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = Forest700, thickness = 1.dp)

                // ── SCROLLABLE BODY ────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Kyun Zaroori Hai Banner
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, Color(0x33F59E0B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(top = 2.dp)
                            )
                            Column {
                                Text(
                                    text = "Kyun Zaroori Hai?",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = whyNeeded,
                                    color = White90,
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }

                    // ── STEP 1: Battery Optimization Exemption ──────────────────────
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Forest800),
                        border = BorderStroke(
                            1.dp,
                            if (isBatteryIgnored) Emerald500.copy(alpha = 0.5f) else Color(0xFFF59E0B).copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(if (isBatteryIgnored) Emerald500.copy(alpha = 0.2f) else Color(0x33F59E0B)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isBatteryIgnored) Icons.Default.Check else Icons.Default.PowerSettingsNew,
                                        contentDescription = null,
                                        tint = if (isBatteryIgnored) Emerald400 else Color(0xFFFBBF24),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (brand == OemBrand.SAMSUNG) "Step 1: Put App to Sleep OFF" else "Step 1: Battery Saver Exemption",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isBatteryIgnored) "Unrestricted Battery Active ✓" else "Currently restricted by battery saver",
                                        color = if (isBatteryIgnored) Emerald400 else Color(0xFFFBBF24),
                                        fontSize = 11.5.sp
                                    )
                                }
                            }

                            Text(
                                text = if (brand == OemBrand.SAMSUNG) {
                                    "Samsung One UI app ko sleep mode me daal deta hai. Set Battery to 'Unrestricted' so FocusGuard never gets paused."
                                } else {
                                    "Android battery saver ko FocusGuard ko background me sleep me daalne se rokein."
                                },
                                color = White60,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )

                            Button(
                                onClick = {
                                    BatteryOptimizationHelper.requestBatteryOptimizationExemption(context)
                                    isBatteryIgnored = BatteryOptimizationHelper.isBatteryOptimizationIgnored(context)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isBatteryIgnored) Color(0x3322C55E) else Emerald500,
                                    contentColor = if (isBatteryIgnored) Emerald400 else Forest950
                                )
                            ) {
                                Text(
                                    text = if (isBatteryIgnored) "Battery Exemption Active ✓" else "Allow Unrestricted Battery",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // ── STEP 2: OEM Auto-Start & Background Launch ──────────────────
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Forest800),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x3338BDF8)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RocketLaunch,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Step 2: $shortName Auto-Start & Launch",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Guarantees app survives reboots & screen-off",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.5.sp
                                    )
                                }
                            }

                            // Steps checklist
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                stepInstructions.forEachIndexed { idx, step ->
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "•",
                                            color = Color(0xFF38BDF8),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = step,
                                            color = White90,
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            // 1-Click Action Button to open OEM settings
                            Button(
                                onClick = {
                                    BatteryOptimizationHelper.openAutostartSettings(context)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF38BDF8),
                                    contentColor = Color(0xFF0F172A)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Launch,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Open $shortName Autostart Settings (1-Tap)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // ── STEP 3: Recent Apps Lock (Collapsible) ─────────────────────
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0x1A818CF8),
                        border = BorderStroke(1.dp, Color(0x33818CF8)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Optional Bonus: Lock in Recent Apps",
                                    color = Color(0xFF818CF8),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { showRecentAppsGuide = !showRecentAppsGuide },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = if (showRecentAppsGuide) "Hide" else "How?",
                                        color = Color(0xFF818CF8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            AnimatedVisibility(visible = showRecentAppsGuide) {
                                Text(
                                    text = "Recent apps overview me FocusGuard card par tap karke 🔒 Lock/Pin icon tap karein taaki 'Clear All' tap karne par FocusGuard background se band na ho.",
                                    color = White60,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                // ── FOOTER ACTIONS ─────────────────────────────────────────────────
                HorizontalDivider(color = Forest700, thickness = 1.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Forest700),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = White60)
                    ) {
                        Text("Later", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            onAcknowledge()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(2f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Emerald500,
                            contentColor = Forest950
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "I've Allowed Auto-Start ✓",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
