package com.focusguard.app.ui.protection

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.focusguard.app.ui.theme.FocusGuardTheme

private val Navy900 = Color(0xFF07140D)
private val Navy800 = Color(0xFF0E2419)
private val Navy700 = Color(0xFF163827)
private val Indigo400 = Color(0xFF22C55E)
private val Amber400 = Color(0xFFFBBF24)
private val Success = Color(0xFF22C55E)
private val White90 = Color(0xE6FFFFFF)
private val White60 = Color(0x99FFFFFF)
private val White40 = Color(0x66FFFFFF)

enum class OemBrand {
    XIAOMI,
    OPPO_REALME,
    VIVO,
    SAMSUNG,
    ONEPLUS,
    HUAWEI,
    GENERIC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OemGuidanceScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val manufacturer = Build.MANUFACTURER.lowercase()
    val brand = remember(manufacturer) {
        when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> OemBrand.XIAOMI
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> OemBrand.OPPO_REALME
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> OemBrand.VIVO
            manufacturer.contains("samsung") -> OemBrand.SAMSUNG
            manufacturer.contains("oneplus") -> OemBrand.ONEPLUS
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> OemBrand.HUAWEI
            else -> OemBrand.GENERIC
        }
    }

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
                        border = androidx.compose.foundation.BorderStroke(1.dp, Amber400.copy(alpha = 0.5f)),
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
                        border = androidx.compose.foundation.BorderStroke(
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
                                    requestIgnoreBatteryOptimization(context)
                                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                                    isBatteryOptimizationIgnored = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
                                },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
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
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Navy800),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Amber400.copy(alpha = 0.4f)),
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
                                        .background(Amber400.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RocketLaunch,
                                        contentDescription = null,
                                        tint = Amber400,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = "Step 2: Enable Autostart / Auto-Launch",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Brand Specific Instructions
                            val oemInstructions = when (brand) {
                                OemBrand.XIAOMI -> listOf(
                                    "1. Tap 'Open Autostart Settings' below.",
                                    "2. Turn ON the toggle for 'FocusGuard'.",
                                    "3. Go to Battery Saver and choose 'No restrictions'."
                                )
                                OemBrand.OPPO_REALME -> listOf(
                                    "1. Tap 'Open Autostart Settings' below.",
                                    "2. Enable 'Auto-launch' and 'Allow background activity'.",
                                    "3. Lock FocusGuard in the Recent Apps overview."
                                )
                                OemBrand.VIVO -> listOf(
                                    "1. Tap 'Open Autostart Settings' below.",
                                    "2. In iManager, enable Autostart for FocusGuard.",
                                    "3. Under 'Background Power Consumption', select 'High background power consumption'."
                                )
                                OemBrand.SAMSUNG -> listOf(
                                    "1. Open App Info / Device Care.",
                                    "2. Ensure FocusGuard is NOT in 'Sleeping apps' or 'Deep sleeping apps'.",
                                    "3. Set Battery usage to 'Unrestricted'."
                                )
                                OemBrand.ONEPLUS -> listOf(
                                    "1. Tap 'Open Autostart Settings' below.",
                                    "2. Turn ON Auto-launch for FocusGuard.",
                                    "3. Set Battery Optimization to 'Don't optimize'."
                                )
                                OemBrand.HUAWEI -> listOf(
                                    "1. In Settings > Battery > App Launch.",
                                    "2. Switch FocusGuard to 'Manage manually'.",
                                    "3. Enable Auto-launch, Secondary launch, and Run in background."
                                )
                                OemBrand.GENERIC -> listOf(
                                    "1. Open App Info below.",
                                    "2. Ensure background activity and autostart are allowed.",
                                    "3. Exclude FocusGuard from any task killers or aggressive battery savers."
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                oemInstructions.forEach { step ->
                                    Text(
                                        text = step,
                                        color = White90,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }

                            Button(
                                onClick = { openOemAutostartSettings(context, brand) },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Amber400, contentColor = Navy900)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Launch,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Open Autostart Settings",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Step 3: Recent Apps Lock Guidance (Non-programmatic manual step)
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Navy800),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.45f)),
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
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF)),
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF)),
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
        }
    }
}

@SuppressLint("BatteryLife")
private fun requestIgnoreBatteryOptimization(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallbackIntent)
        } catch (ex: Exception) {
            openAppDetailsSettings(context)
        }
    }
}

private fun openOemAutostartSettings(context: Context, brand: OemBrand) {
    val intents = when (brand) {
        OemBrand.XIAOMI -> listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT)
        )
        OemBrand.OPPO_REALME -> listOf(
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity"))
        )
        OemBrand.VIVO -> listOf(
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            Intent().setComponent(ComponentName("com.vivo.abe", "com.vivo.abe.feature.power.ui.AppPowerManagerActivity")),
            Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
            Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
            Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.MainGuideActivity")),
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity"))
        )
        OemBrand.HUAWEI -> listOf(
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.bootstart.BootStartActivity"))
        )
        OemBrand.ONEPLUS -> listOf(
            Intent().setComponent(ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"))
        )
        else -> emptyList()
    }

    for (intent in intents) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            // Try next candidate
        }
    }

    // Graceful fallback to Application Details Settings
    openAppDetailsSettings(context)
}

private fun openAppDetailsSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Ignored
    }
}

private val Rose400 = Color(0xFFFB7185)
