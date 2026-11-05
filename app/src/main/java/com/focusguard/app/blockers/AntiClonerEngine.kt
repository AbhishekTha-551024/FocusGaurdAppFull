package com.focusguard.app.blockers

import android.view.accessibility.AccessibilityNodeInfo

/**
 * FocusGuard Anti-Cloning & Virtual Container Engine
 * ===================================================
 * Neutralizes bypasses via:
 * 1. OEM Dual Apps & App Cloners (Realme, Xiaomi, Oppo, Vivo, OnePlus, Samsung, Huawei, Transsion)
 * 2. Virtual containers (Parallel Space, Dual Space, 2Accounts, Virtual Android, Island, Shelter)
 * 3. Mutated/Modified APK clones with spoofed package names
 * 4. Launcher clone action shortcuts
 */
object AntiClonerEngine {

    // ── Layer 1: Known OEM & Third-Party Cloners, Dual App Agents & Virtual Space Containers ──
    private val CLONER_PACKAGES = hashSetOf(
        // OEM Built-in Cloners & Dual App System Packages
        "com.oplus.multiapp",                     // Realme UI / ColorOS / OxygenOS App Cloner
        "com.coloros.oppomultiapp",              // Oppo App Cloner
        "com.oneplus.multiapp",                  // OnePlus Parallel Apps
        "com.coloros.clonemanager",              // ColorOS Clone Manager
        "com.miui.securityadd",                  // Xiaomi / Redmi / POCO Dual Apps Activity
        "com.miui.cloner",                       // Xiaomi Cloner
        "com.miui.dualapp",                      // Xiaomi Dual App Agent
        "com.miui.securitycore",                 // Xiaomi Security Core Dual App Host
        "com.vivo.doubleinstance",               // Vivo / iQOO App Clone
        "com.vivo.appclone",                     // Vivo Clone Service
        "com.vivo.xspace",                       // Vivo Dual Space / Private Space
        "com.samsung.android.da.daagent",        // Samsung Dual Messenger / Dual App Agent
        "com.samsung.android.settings.dualmessenger",
        "com.huawei.twinapp",                    // Huawei / Honor App Twin
        "com.transsion.apptwin",                 // Infinix / Tecno / itel App Twin
        "com.asus.twinapps",                     // Asus Twin Apps
        "com.zui.cloneapp",                      // Lenovo / Motorola Clone App

        // Work Profile / Managed Provisioning Container Tools
        "com.oasisfeng.island",                  // Island (Work profile isolation)
        "net.typeblog.shelter",                  // Shelter (Work profile isolation)
        "com.afwsamples.testdpc",                // TestDPC (Device policy sandbox)

        // Third-party App Cloners & Virtual Spaces
        "com.lbe.parallel.intl",
        "com.lbe.parallel.intl.arm64",
        "com.lbe.parallel.intl.arm32",
        "com.parallel.space.lite",
        "com.parallel.space.lite.arm64",
        "com.parallel.space.pro",
        "com.parallel.space.pro.arm64",
        "com.appcloner.app",
        "com.applisto.appcloner",
        "com.vapp.parallel.space",
        "com.excellendance.multiaccount",
        "com.trend.space",
        "com.cloneapp.parallelspace.dualspace",
        "com.dual.space",
        "com.dualspace.multispace.android",
        "com.multi.parallel.dual.app",
        "com.polestar.super.clone",
        "io.va.exposed",
        "com.clone.android.dual.space",
        "com.space.dual.cloneapp",
        "com.gspace.android",
        "com.multiapp.parallel",
        "com.matrix.clone",
        "com.qihoo.magic",
        "com.x8zs.sandbox",
        "com.vmos.pro",
        "com.vmos.vmos",
        "com.f1player",
        "com.twoaccounts.multipledesc",
        "com.multiple.accounts",
        "com.droi.virtual",
        "com.clone.cloneapp",
        "com.multiaccounts.dual",
        "com.clonemultiapp.parallel"
    )

    // ── Layer 3: Keywords used in OEM Dual Apps settings & Cloner UIs ──────────
    private val DUAL_APP_KEYWORDS = listOf(
        "dual app",
        "dual apps",
        "app twin",
        "clone app",
        "clone apps",
        "app cloner",
        "create clone",
        "parallel space",
        "second space",
        "dual messenger",
        "app duplication",
        "multi-user",
        "multiple users",
        "add guest",
        "add user",
        "parallel apps",
        "app clone",
        "cloned apps",
        "twin apps",
        "work profile",
        "cloner"
    )

    /**
     * Checks if the package name corresponds to a known Cloner or Virtual Space container.
     * Fast-path lookup: < 0.005ms in-memory hash set + heuristic pattern check.
     */
    fun isClonerPackage(packageName: String): Boolean {
        val lower = packageName.lowercase()
        if (CLONER_PACKAGES.contains(lower)) return true

        // Exclusion of FocusGuard itself
        if (lower.startsWith("com.focusguard.")) {
            return false
        }

        // Exclusion of official system backup, restore, and phone migration tools
        if (lower.contains("backup") ||
            lower.contains("restore") ||
            lower.contains("transfer") ||
            lower.contains("migration") ||
            lower.contains("switch")
        ) {
            return false
        }

        // Catch OEM or generic packages containing cloner signatures
        if (lower.contains("multiapp") ||
            lower.contains("cloneapp") ||
            lower.contains("appclone") ||
            lower.contains("doubleinstance") ||
            lower.contains("twinapp") ||
            lower.contains("daagent") ||
            lower.contains("parallel") ||
            lower.contains("cloner") ||
            lower.contains("dualspace") ||
            lower.contains("xspace") ||
            lower.contains("superclone")
        ) {
            return true
        }

        return false
    }

    /**
     * Layer 2: Structural UI Scan.
     * Detects if target app views (Instagram, YouTube, Facebook) are being rendered
     * inside a masked package (virtual container or modified cloned APK) ONLY IF that target app
     * is currently blocked by FocusGuard.
     */
    fun isClonedTargetViewDetected(
        rootNode: AccessibilityNodeInfo?,
        packageName: String,
        isPackageBlocked: (String) -> Boolean
    ): Boolean {
        if (rootNode == null) return false

        val lowerPkg = packageName.lowercase()
        // If the active package is already the genuine target app, normal app/reel blockers handle it!
        if (lowerPkg == "com.instagram.android" ||
            lowerPkg == "com.google.android.youtube" ||
            lowerPkg == "com.facebook.katana" ||
            lowerPkg == "com.facebook.lite"
        ) {
            return false
        }

        // Never scan system packages, launchers, or settings for cloned target views
        if (lowerPkg.contains("launcher") ||
            lowerPkg.contains("systemui") ||
            lowerPkg.contains("settings") ||
            lowerPkg == "android"
        ) {
            return false
        }

        // Only scan for signature views of target apps that are ACTUALLY BLOCKED
        val targetsToCheck = mutableListOf<Pair<String, String>>()
        if (isPackageBlocked("com.instagram.android")) {
            targetsToCheck.add("com.instagram.android" to "com.instagram.android:id/reels_viewer_container")
            targetsToCheck.add("com.instagram.android" to "com.instagram.android:id/clips_video_container")
            targetsToCheck.add("com.instagram.android" to "com.instagram.android:id/feed_tab")
        }
        if (isPackageBlocked("com.google.android.youtube")) {
            targetsToCheck.add("com.google.android.youtube" to "com.google.android.youtube:id/reel_recycler")
            targetsToCheck.add("com.google.android.youtube" to "com.google.android.youtube:id/watch_while_layout")
        }
        if (isPackageBlocked("com.facebook.katana") || isPackageBlocked("com.facebook.lite")) {
            targetsToCheck.add("com.facebook.katana" to "com.facebook.katana:id/reels_tab")
            targetsToCheck.add("com.facebook.katana" to "com.facebook.katana:id/feed_root_view")
        }

        if (targetsToCheck.isEmpty()) {
            return false
        }

        for ((targetPkg, targetViewId) in targetsToCheck) {
            try {
                val matchedNodes = rootNode.findAccessibilityNodeInfosByViewId(targetViewId)
                if (!matchedNodes.isNullOrEmpty()) {
                    var verifiedMatch = false
                    for (node in matchedNodes) {
                        val nodePkg = node.packageName?.toString()?.lowercase()
                        if (nodePkg != null && nodePkg != targetPkg && nodePkg == lowerPkg) {
                            verifiedMatch = true
                            break
                        }
                    }
                    recycleNodes(matchedNodes)
                    if (verifiedMatch) return true
                }
            } catch (e: Exception) {
                android.util.Log.d("AntiClonerEngine", "Node check error: ${e.message}")
            }
        }
        return false
    }

    /**
     * Layer 3: OEM Dual Apps & App Cloner Settings Interception.
     * Returns true if the user is attempting to configure, clone, or launch Dual Apps in settings.
     */
    fun isDualAppsSettingsScreen(
        rootNode: AccessibilityNodeInfo?,
        className: String,
        packageName: String = ""
    ): Boolean {
        val lowerPkg = packageName.lowercase()
        val lowerCls = className.lowercase()

        // Never inspect launchers or home screens for settings cloning screens
        if (lowerPkg.contains("launcher") || lowerPkg.contains("home") || lowerPkg.contains("quickstep") || lowerPkg.isEmpty()) {
            return false
        }

        // 1. Direct package match for OEM Cloners
        if (CLONER_PACKAGES.contains(lowerPkg) || lowerPkg == "com.oplus.multiapp" ||
            lowerPkg == "com.coloros.oppomultiapp" || lowerPkg == "com.vivo.doubleinstance" ||
            lowerPkg == "com.samsung.android.da.daagent" || lowerPkg == "com.miui.cloner"
        ) return true

        // 2. Class name pattern check
        if (lowerCls.contains("dualapp") || lowerCls.contains("apptwin") || lowerCls.contains("cloneapp") ||
            lowerCls.contains("appclone") || lowerCls.contains("secondspace") || lowerCls.contains("dualmessenger") ||
            lowerCls.contains("multiapp") || lowerCls.contains("doubleinstance") || lowerCls.contains("cloner") ||
            lowerCls.contains("parallelapp")
        ) return true

        if (rootNode == null) return false

        // 2.5 Exclusion of individual app details screens (e.g. Telegram / WhatsApp App Info)
        // Individual App Info screens on Vivo / Xiaomi often contain an "App clone" toggle item.
        // We must NEVER block viewing an individual third-party app's info page unless it is FocusGuard itself.
        val isAppDetailsScreen = lowerCls.contains("installedappdetails") ||
                lowerCls.contains("appinfodashboardfragment") ||
                lowerCls.contains("appinfo") ||
                lowerCls.contains("appbuttons") ||
                lowerCls.contains("appdetails") ||
                lowerCls.contains("appstoragesettings")

        if (isAppDetailsScreen) {
            val isFocusGuard = isFocusGuardAppDetails(rootNode)
            if (!isFocusGuard) {
                return false
            }
        }

        // 3. Check for Dual App keywords in settings or security center
        val isSettingsOrSystem = lowerPkg.contains("settings") || lowerPkg.contains("security") ||
                lowerPkg.contains("safecenter") || lowerPkg.contains("systemui")
        if (!isSettingsOrSystem) return false

        for (keyword in DUAL_APP_KEYWORDS) {
            try {
                val matchedNodes = rootNode.findAccessibilityNodeInfosByText(keyword)
                if (!matchedNodes.isNullOrEmpty()) {
                    recycleNodes(matchedNodes)
                    return true
                }
            } catch (e: Exception) {
                android.util.Log.d("AntiClonerEngine", "Keyword check error: ${e.message}")
            }
        }

        // 4. In settings: If FocusGuard is listed alongside cloning keywords
        try {
            val fgNodes = rootNode.findAccessibilityNodeInfosByText("FocusGuard")
            if (!fgNodes.isNullOrEmpty()) {
                recycleNodes(fgNodes)
                for (kw in listOf("Clone", "Dual", "Twin")) {
                    val kwNodes = rootNode.findAccessibilityNodeInfosByText(kw)
                    if (!kwNodes.isNullOrEmpty()) {
                        recycleNodes(kwNodes)
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.d("AntiClonerEngine", "FocusGuard check error: ${e.message}")
        }

        return false
    }

    /**
     * Checks if the active App Info screen explicitly belongs to FocusGuard.
     * Prevents false-positive cloner intercepts when viewing other apps that have an "App clone" toggle.
     */
    fun isFocusGuardAppDetails(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        val titleIds = listOf(
            "com.android.settings:id/entity_header_title",
            "android:id/title",
            "com.android.settings:id/app_title",
            ":id/entity_header_title",
            ":id/app_name",
            ":id/title"
        )
        for (id in titleIds) {
            try {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                val matches = nodes.any { node ->
                    val text = node.text?.toString() ?: ""
                    text.equals("FocusGuard", ignoreCase = true) || text.equals("com.focusguard.app", ignoreCase = true)
                }
                recycleNodes(nodes)
                if (matches) return true
            } catch (_: Exception) {}
        }
        return false
    }

    private fun recycleNodes(nodes: List<AccessibilityNodeInfo>) {
        for (node in nodes) {
            com.focusguard.app.utils.SafeNodeUtils.safeRecycle(node)
        }
    }
}
