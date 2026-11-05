package com.focusguard.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.core.designsystem.theme.*

enum class MainTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    BLOCK("Block", Icons.Default.Block),
    LIMITS("Limits", Icons.Default.HourglassTop),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun MainBottomBar(
    currentTab: MainTab,
    isSessionActive: Boolean,
    onTabSelected: (MainTab) -> Unit
) {
    NavigationBar(
        containerColor = Forest900,
        contentColor = Color.White
    ) {
        MainTab.entries.forEach { tab ->
            val isSelected = currentTab == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    if (tab == MainTab.HOME && isSessionActive) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = Emerald500,
                                    contentColor = Forest950
                                ) {
                                    Text("ACTIVE", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        ) {
                            Icon(imageVector = tab.icon, contentDescription = tab.title)
                        }
                    } else {
                        Icon(imageVector = tab.icon, contentDescription = tab.title)
                    }
                },
                label = {
                    Text(text = tab.title, fontSize = 12.sp)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Emerald500,
                    selectedTextColor = Emerald500,
                    unselectedIconColor = White60,
                    unselectedTextColor = White60,
                    indicatorColor = Emerald500.copy(alpha = 0.15f)
                )
            )
        }
    }
}
