package com.focusguard.app.service.vpn

import android.util.Log
import kotlinx.coroutines.delay
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SafeSearchEnforcer
 * ==================
 * Manages live DNS resolution of SafeSearch hostnames (Google, YouTube, Bing, DuckDuckGo)
 * and holds the in-memory cache of dynamically resolved IPs for query redirection.
 */
@Singleton
class SafeSearchEnforcer @Inject constructor() {

    companion object {
        private const val TAG = "SafeSearchEnforcer"

        // SafeSearch canonical hostnames — IPs resolved LIVE at runtime, never hardcoded.
        val SAFESEARCH_HOSTS = mapOf(
            "google.com" to "forcesafesearch.google.com",
            "www.google.com" to "forcesafesearch.google.com",
            "youtube.com" to "restrict.youtube.com",
            "www.youtube.com" to "restrict.youtube.com",
            "bing.com" to "strict.bing.com",
            "www.bing.com" to "strict.bing.com",
            "duckduckgo.com" to "safe.duckduckgo.com",
            "www.duckduckgo.com" to "safe.duckduckgo.com"
        )
    }

    // Live-resolved SafeSearch IPs: populated at startup and refreshed every 6 hours.
    // Key = query hostname, Value = InetAddress list from live DNS lookup.
    private val safeSearchIpCache = ConcurrentHashMap<String, List<InetAddress>>()

    fun getSafeSearchIps(hostname: String): List<InetAddress>? =
        safeSearchIpCache[hostname.lowercase().trimEnd('.')]

    fun refreshSafeSearchIps() {
        Log.d(TAG, "Refreshing SafeSearch IPs via live DNS lookup…")
        val uniqueTargets = SAFESEARCH_HOSTS.values.toSet()
        for (target in uniqueTargets) {
            try {
                val addrs = InetAddress.getAllByName(target)
                // Map back: all query domains that point to this SafeSearch target get these IPs
                SAFESEARCH_HOSTS.forEach { (queryDomain, ssHost) ->
                    if (ssHost == target) {
                        safeSearchIpCache[queryDomain] = addrs.toList()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve SafeSearch host: ${e.message}")
            }
        }
        Log.d(TAG, "SafeSearch IPs refreshed (${safeSearchIpCache.size} endpoints mapped)")
    }

    suspend fun scheduleSafeSearchRefresh(isActive: () -> Boolean) {
        while (isActive()) {
            delay(6 * 60 * 60 * 1000L) // 6 hours
            if (isActive()) {
                refreshSafeSearchIps()
            }
        }
    }
}

