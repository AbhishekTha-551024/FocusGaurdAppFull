/**
 * =====================================================================
 * FILE: DeepFocusLockWidgets.kt
 * PACKAGE: com.focusguard.app.overlay
 * =====================================================================
 * 
 * 📌 OVERVIEW (YE FILE KYA KARTI HAI?):
 * Ye file DeepFocusLockOverlayContent ke reusable modular UI sub-components aur
 * helper functions contain karti hai. Iska maqsad main overlay screen code ko
 * clean, readable, aur modular rakhna hai.
 *
 * 🏗️ UI SUB-COMPONENTS & WIDGETS:
 * 1. TimeDigitBox:
 *    - Monospace digital timer box jo two-digit numbers (HRS, MIN, SEC) ko
 *      emerald neon borders aur dark background ke saath render karta hai.
 * 2. DeepFocusAllowedSlots:
 *    - User ki suitcase me packed 3 apps ke slots ko render karta hai.
 *    - Agar slot filled hai toh AppIconView + App name clickable card banta hai.
 *    - Agar slot khali hai toh subtle "Empty Slot" placeholder render hota hai.
 * 3. DeepFocusActionControls:
 *    - Cyan colored "EMERGENCY PHONE CALL" action pill jo system phone dialer ko launch karta hai.
 *    - Strict Lockdown status text jo session end time ka exact timestamp dikhata hai.
 * 4. getAppNameSafely:
 *    - Robust PackageManager query helper jo crash-proof way me app label nikalta hai
 *      (agar app uninstalled ya missing ho toh safe fallback package suffix use karta hai).
 *
 * ⚠️ SENIOR DEVELOPER PERFORMANCE NOTE:
 * - getAppNameSafely() PackageManager IPC call karta hai; DeepFocusAllowedSlots me
 *   remember(pkg) use karke ensure kiya gaya hai ki har recomposition par dobara
 *   system binder IPC call na ho.
 * =====================================================================
 */

package com.focusguard.app.overlay.deepfocus

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.ui.components.AppIconView
import java.util.Locale

internal val DarkCardBg = Color(0xFF09140E)
internal val DarkCardBorder = Color(0x3322C55E)
internal val EmeraldNeonGlow = Color(0xFF4ADE80)
internal val TextDim = Color(0x55FFFFFF)

@Composable
fun TimeDigitBox(
    digits: String,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DarkCardBg,
        border = BorderStroke(1.dp, DarkCardBorder),
        modifier = Modifier.width(88.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = digits,
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                color = EmeraldNeonGlow,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
fun DeepFocusAllowedSlots(
    context: Context,
    displayPackedApps: List<String>,
    onLaunchPackage: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = TextDim,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = "ALLOWED APPS (${displayPackedApps.size}/3 SLOTS)",
                color = TextDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (slotIndex in 0 until 3) {
                val pkg = displayPackedApps.getOrNull(slotIndex)
                if (pkg != null) {
                    val appName = remember(pkg) { getAppNameSafely(context, pkg) }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = DarkCardBg,
                        border = BorderStroke(1.dp, DarkCardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(90.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onLaunchPackage(pkg) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            AppIconView(packageName = pkg, size = 40.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = appName,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0x0AFFFFFF),
                        border = BorderStroke(1.dp, Color(0x14FFFFFF)),
                        modifier = Modifier
                            .weight(1f)
                            .height(90.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Empty",
                                color = TextDim,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            )
                            Text(
                                text = "Slot ${slotIndex + 1}",
                                color = TextDim.copy(alpha = 0.5f),
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeepFocusActionControls(
    endSessionTimeString: String,
    onLaunchDialer: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .clickable { onLaunchDialer() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Open Phone Dialer",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "EMERGENCY PHONE CALL",
                    color = Color(0xFF38BDF8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0x1422C55E),
            border = BorderStroke(1.dp, Color(0x3322C55E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFF22C55E),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Strict Lockdown · Auto-unlocks at $endSessionTimeString",
                    color = Color(0xFF86EFAC),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

fun getAppNameSafely(context: Context, packageName: String): String {
    return try {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(appInfo).toString()
    } catch (_: Exception) {
        packageName.substringAfterLast('.').replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
        }
    }
}
