package com.focusguard.app.ui.protection

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.ui.protection.components.OemGuidanceAutostartCard
import com.focusguard.app.ui.protection.components.OemGuidanceRecentAppsCard
import com.focusguard.app.ui.protection.components.OemLauncherUtils
import com.focusguard.app.ui.theme.FocusGuardTheme
import com.focusguard.app.utils.BatteryOptimizationHelper

private val Navy900 = Color(0xFF07140D)
private val Navy800 = Color(0xFF0E2419)
private val Indigo400 = Color(0xFF22C55E)
private val Amber400 = Color(0xFFFBBF24)
private val Success = Color(0xFF22C55E)
private val White60 = Color(0x99FFFFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OemGuidanceScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val brand = remember { BatteryOptimizationHelper.getOemBrand() }

    var isBatteryOptimizationIgnored by remember {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false)
    }

    FocusGuardTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Background Protection Guide",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy900)
                )
            },
            containerColor = Navy900
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(
                        Brush.verticalGradient(
                            listOf(Navy900, Color(0xFF0F172A), Navy900)
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Header Card
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Navy800),
                        border = BorderStroke(1.dp, Amber400.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Amber400.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BatteryChargingFull,
                                    contentDescription = null,
                                    tint = Amber400,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Device: ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Aggressive battery savers can kill accessibility services. Follow the steps below for 100% reliable protection.",
                                    color = White60,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    // Step 1: Battery Optimization (Standard API)
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Navy800),
                        border = BorderStroke(
                            1.dp,
                            if (isBatteryOptimizationIgnored) Success.copy(alpha = 0.4f) else Indigo400.copy(alpha = 0.4f)
                        ),
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
                                        .background(if (isBatteryOptimizationIgnored) Success.copy(alpha = 0.2f) else Indigo400.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isBatteryOptimizationIgnored) Icons.Default.Check else Icons.Default.PowerSettingsNew,
                                        contentDescription = null,
                                        tint = if (isBatteryOptimizationIgnored) Success else Indigo400,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = "Step 1: Disable Battery Optimization",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "Allow FocusGuard to run unrestricted in the background so it never gets paused by Android.",
                                color = White60,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                            Button(
                                onClick = {
                                    OemLauncherUtils.requestIgnoreBatteryOptimization(context)
                                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                                    isBatteryOptimizationIgnored = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isBatteryOptimizationIgnored) Color(0x3334D399) else Indigo400,
                                    contentColor = if (isBatteryOptimizationIgnored) Success else Color.White
                                )
                            ) {
                                Text(
                                    text = if (isBatteryOptimizationIgnored) "Unrestricted Battery Active ✓" else "Allow Unrestricted Battery",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Step 2: OEM Specific Autostart
                    OemGuidanceAutostartCard(context = context, brand = brand)

                    // Step 3: Recent Apps Lock Guidance & Heartbeat
                    OemGuidanceRecentAppsCard()
                }
            }
        }
    }
}
