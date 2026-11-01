package com.focusguard.app.tracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BrowserPackageRegistry
 * ======================
 * Registry of supported and unsupported browser package names, YouTube packages,
 * messaging/chat packages, and known omnibox resource IDs per browser.
 */
@Singleton
class BrowserPackageRegistry @Inject constructor() {

    val browserResourceIdMap: Map<String, List<String>> = mapOf(
        "com.android.chrome" to listOf("com.android.chrome:id/url_bar", "com.android.chrome:id/search_box_text"),
        "org.mozilla.firefox" to listOf("org.mozilla.firefox:id/url_bar_title", "org.mozilla.firefox:id/toolbar", "org.mozilla.firefox:id/mozac_browser_toolbar_edit_url_view"),
        "com.microsoft.emmx" to listOf("com.microsoft.emmx:id/url_bar", "com.microsoft.emmx:id/search_box_text"),
        "com.brave.browser" to listOf("com.brave.browser:id/url_bar", "com.brave.browser:id/search_box_text"),
        "com.sec.android.app.sbrowser" to listOf("com.sec.android.app.sbrowser:id/location_bar_edit_text", "com.sec.android.app.sbrowser:id/url_bar"),
        "com.opera.browser" to listOf("com.opera.browser:id/url_field"),
        "com.opera.mini.native" to listOf("com.opera.mini.native:id/url_field"),
        "com.vivaldi.browser" to listOf("com.vivaldi.browser:id/url_bar"),
        "com.duckduckgo.mobile.android" to listOf("com.duckduckgo.mobile.android:id/omnibarTextInput", "com.duckduckgo.mobile.android:id/search_edit_text"),
        "com.kiwibrowser.browser" to listOf("com.kiwibrowser.browser:id/url_bar"),
        "com.heytap.browser" to listOf("com.heytap.browser:id/search_hint", "com.heytap.browser:id/url_bar", "com.heytap.browser:id/address_bar"),
        "com.mi.globalbrowser" to listOf("com.mi.globalbrowser:id/url_bar"),
        "org.mozilla.focus" to listOf("org.mozilla.focus:id/display_url", "org.mozilla.focus:id/urlview"),
        "com.opera.gx" to listOf("com.opera.gx:id/url_field", "com.opera.gx:id/search_edit_text"),
        "com.aloha.browser" to listOf("com.aloha.browser:id/url_edit_text", "com.aloha.browser:id/search_view"),
        "org.torproject.torbrowser" to listOf("org.torproject.torbrowser:id/url_bar_title", "org.torproject.torbrowser:id/toolbar"),
        "mark.via.gp" to listOf("mark.via.gp:id/b7", "mark.via.gp:id/search_box"),
        "com.UCMobile.intl" to listOf("com.UCMobile.intl:id/address_bar", "com.UCMobile.intl:id/url_bar"),
        "org.mozilla.rocket" to listOf("org.mozilla.rocket:id/url_bar_title", "org.mozilla.rocket:id/display_url", "org.mozilla.rocket:id/url_edit_text"),
        "org.torproject.torbrowser_alpha" to listOf("org.torproject.torbrowser_alpha:id/url_bar_title", "org.torproject.torbrowser_alpha:id/toolbar"),
        "com.nationaledtech.spinbrowser" to listOf("com.nationaledtech.spinbrowser:id/url_bar", "com.nationaledtech.spinbrowser:id/address_bar", "com.nationaledtech.spinbrowser:id/url", "com.nationaledtech.spinbrowser:id/search_box"),
        "com.nationaledtech.spinbrowserplus" to listOf("com.nationaledtech.spinbrowserplus:id/url_bar", "com.nationaledtech.spinbrowserplus:id/address_bar", "com.nationaledtech.spinbrowserplus:id/url", "com.nationaledtech.spinbrowserplus:id/search_box"),
        "com.ecosia.android" to listOf("com.ecosia.android:id/url_bar", "com.ecosia.android:id/search_box_text"),
        "com.hsv.freeadblockerbrowser" to listOf("com.hsv.freeadblockerbrowser:id/url_bar", "com.hsv.freeadblockerbrowser:id/search_box_text", "com.hsv.freeadblockerbrowser:id/address_bar"),
        "idm.internet.download.manager" to listOf("idm.internet.download.manager:id/address_bar", "idm.internet.download.manager:id/url_bar", "idm.internet.download.manager:id/search_box"),
        "idm.internet.download.manager.plus" to listOf("idm.internet.download.manager.plus:id/address_bar", "idm.internet.download.manager.plus:id/url_bar", "idm.internet.download.manager.plus:id/search_box"),
        "idm.internet.download.manager.lite" to listOf("idm.internet.download.manager.lite:id/address_bar", "idm.internet.download.manager.lite:id/url_bar", "idm.internet.download.manager.lite:id/search_box"),
        "com.instantbits.cast.webvideo" to listOf("com.instantbits.cast.webvideo:id/address_bar", "com.instantbits.cast.webvideo:id/url", "com.instantbits.cast.webvideo:id/search_view", "com.instantbits.cast.webvideo:id/web_view_url"),
        "com.vivaldi.browser.snapshot" to listOf("com.vivaldi.browser.snapshot:id/url_bar")
    )

    companion object {
        val YOUTUBE_PACKAGES = setOf(
            "com.google.android.youtube",
            "com.google.android.apps.youtube.kids",
            "app.revanced.android.youtube"
        )

        val VERIFIED_SUPPORTED_BROWSERS = setOf(
            // Chrome
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            // Firefox & Firefox Lite
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.fenix",
            "org.mozilla.rocket",
            // Microsoft Edge
            "com.microsoft.emmx",
            // Brave
            "com.brave.browser",
            "com.brave.browser_beta",
            "com.brave.browser_nightly",
            // Samsung Internet
            "com.sec.android.app.sbrowser",
            "com.sec.android.app.sbrowser.beta",
            // Opera & Opera Mini & Opera GX
            "com.opera.browser",
            "com.opera.mini.native",
            "com.opera.touch",
            "com.opera.gx",
            // DuckDuckGo
            "com.duckduckgo.mobile.android",
            // Vivaldi
            "com.vivaldi.browser",
            "com.vivaldi.browser.snapshot",
            // Spin Safe Browser
            "com.nationaledtech.spinbrowser",
            "com.nationaledtech.spinbrowserplus",
            // Ecosia
            "com.ecosia.android",
            // Free Adblocker Browser
            "com.hsv.freeadblockerbrowser",
            // 1DM / 1DM+ Browser
            "idm.internet.download.manager",
            "idm.internet.download.manager.plus",
            "idm.internet.download.manager.lite",
            // Web Video Cast
            "com.instantbits.cast.webvideo"
        )

        val KNOWN_UNSUPPORTED_BROWSERS = setOf(
            // ── Tor / Onion Browsers (bypass DNS via encrypted relay — auto-block entirely) ──
            "org.torproject.torbrowser",        // Tor Browser (stable)
            "org.torproject.torbrowser_alpha",  // Tor Browser (alpha)
            "org.torproject.android",           // Orbot / legacy Tor
            "onion.torsearch",                  // Onion Search
            // ── Puffin (cloud-proxy, bypasses local DNS) ──
            "com.cloudmosa.puffinfree",
            "com.cloudmosa.puffin",
            // ── Firefox Focus (no URL-bar accessibility node exposure) ──
            "org.mozilla.focus",
            // ── Other unsupported / obscure browsers ──
            "com.aloha.browser",
            "mark.via.gp",
            "com.ucmobile.intl",
            "com.uc.browser.en",
            "com.transsion.phoenix",
            "com.mi.globalbrowser",
            "com.heytap.browser",
            "com.vivo.browser",
            "com.kiwibrowser.browser",
            "mobi.mgeek.tunnybrowser",
            "com.ghostery.android.ghostery",
            "com.qwant.liberty",
            "org.bromite.bromite",
            "de.baumann.browser",
            "com.stoutner.privacybrowser.standard",
            "acr.browser.barebones",
            "com.android.browser",
            "com.z20.browser",
            "com.pure.browser",
            "com.cake.browser",
            "com.xbrowser.play",
            "com.mycompany.app.soulbrowser"
        )
    }

    private val unsupportedBrowserCache = ConcurrentHashMap<String, Boolean>()

    fun isSupportedBrowser(pkg: String): Boolean {
        val lower = pkg.lowercase()
        if (VERIFIED_SUPPORTED_BROWSERS.contains(lower)) return true
        if (browserResourceIdMap.containsKey(lower)) return true
        return lower.contains("browser") ||
                lower.contains("chrome") ||
                lower.contains("firefox")
    }

    fun isUnsupportedBrowser(pkg: String, context: Context? = null): Boolean {
        val lower = pkg.lowercase()
        if (lower.startsWith("com.focusguard.app")) return false
        if (VERIFIED_SUPPORTED_BROWSERS.contains(lower)) return false
        if (isYouTubePackage(lower) || isMessagingOrChatPackage(lower)) return false

        unsupportedBrowserCache[lower]?.let { return it }

        if (KNOWN_UNSUPPORTED_BROWSERS.contains(lower) ||
            lower.contains("puffin") ||
            lower.contains("aloha.browser") ||
            lower.contains("soulbrowser") ||
            lower.contains("xbrowser") ||
            lower.contains("pure.browser") ||
            lower.contains("dolphin") ||
            lower.contains("ghostery") ||
            (lower.contains("browser") && !VERIFIED_SUPPORTED_BROWSERS.contains(lower))
        ) {
            unsupportedBrowserCache[lower] = true
            return true
        }

        if (context != null) {
            try {
                val pm = context.packageManager
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    setPackage(lower)
                }
                val matches = pm.queryIntentActivities(webIntent, 0)
                val isBrowser = matches.isNotEmpty()
                unsupportedBrowserCache[lower] = isBrowser
                return isBrowser
            } catch (_: Exception) {
                // ignore
            }
        }

        return false
    }

    fun isYouTubePackage(pkg: String): Boolean {
        return YOUTUBE_PACKAGES.contains(pkg) || pkg.contains("youtube", ignoreCase = true)
    }

    fun isMessagingOrChatPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower == "com.whatsapp" ||
                lower == "com.whatsapp.w4b" ||
                lower.contains("telegram") ||
                lower.contains("signal") ||
                lower == "com.google.android.apps.messaging" ||
                lower == "com.samsung.android.messaging" ||
                lower.contains("mms") ||
                lower.contains("sms") ||
                lower == "com.facebook.orca"
    }
}
