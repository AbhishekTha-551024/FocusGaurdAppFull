package com.focusguard.app.security

import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

sealed class SupportVerificationResult {
    data class Success(
        val actionCode: String,
        val actionName: String,
        val targetPayload: String,
        val description: String
    ) : SupportVerificationResult()

    object InvalidCode : SupportVerificationResult()
    data class RateLimited(val remainingMinutes: Long) : SupportVerificationResult()
}

@Singleton
class SupportCodeEngine @Inject constructor() {

    companion object {
        // Runtime XOR obfuscation to prevent static decompiler string extraction
        // Deobfuscates with XOR 0x5A to: "FocusGuard#MasterSecret#2026@Key"
        private val OBFUSCATED_SECRET_BYTES = intArrayOf(
            28, 53, 57, 47, 41, 29, 47, 59, 40, 62, 121,
            23, 59, 41, 46, 63, 40, 9, 63, 57, 40, 63, 46, 121,
            104, 106, 104, 108, 26, 17, 63, 35
        )
        private const val XOR_KEY = 0x5A

        fun getMasterSecret(): String {
            val chars = CharArray(OBFUSCATED_SECRET_BYTES.size) { i ->
                (OBFUSCATED_SECRET_BYTES[i] xor XOR_KEY).toChar()
            }
            return String(chars)
        }

        val DEFAULT_MASTER_SECRET: String get() = getMasterSecret()

        const val ACTION_DISABLE_UNINSTALL = "101"
        const val ACTION_RESET_CHALLENGE = "102"
        const val ACTION_RESET_TIME_TAMPER = "103"
        const val ACTION_UNBLOCK_APP = "201"
        const val ACTION_DELETE_KEYWORD = "202"
        const val ACTION_DISABLE_FEED = "203"
        const val ACTION_RESET_LIMIT = "301"
        const val ACTION_FULL_RESET = "999"

        private val PRESET_FEEDS = listOf("instagram", "youtube", "facebook", "all")
    }

    /**
     * Calculates the exact 6-digit numeric PIN using HMAC-SHA256
     */
    fun calculateCode(
        deviceId: String,
        dateStr: String,
        actionCode: String,
        targetPayload: String = "",
        secretKey: String = getMasterSecret()
    ): String {
        val normDevice = deviceId.trim().uppercase(Locale.US)
        val normDate = dateStr.trim()
        val normAction = actionCode.trim()
        val normTarget = targetPayload.trim().lowercase(Locale.US)
        val secret = secretKey.trim()

        val rawPayload = "$normDevice|$normDate|$normAction|$normTarget"

        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(keySpec)
        val hashBytes = mac.doFinal(rawPayload.toByteArray(Charsets.UTF_8))

        // Read first 4 bytes as 32-bit big-endian integer, mask to positive
        val intVal = ByteBuffer.wrap(hashBytes.copyOfRange(0, 4)).int and 0x7FFFFFFF
        val pinNum = (intVal % 900000) + 100000

        return pinNum.toString()
    }

    /**
     * Verifies the entered 6-digit PIN against all potential candidate actions
     * across a multi-day grace window (Today local, UTC, Yesterday, Tomorrow).
     */
    fun verifySupportCode(
        inputCode: String,
        deviceId: String,
        activeBlockedPackages: Set<String> = emptySet(),
        activeBlockedKeywords: Set<String> = emptySet(),
        activeLimitPackages: Set<String> = emptySet(),
        secretKey: String = DEFAULT_MASTER_SECRET
    ): SupportVerificationResult {
        val cleanCode = inputCode.trim()
        if (cleanCode.length != 6 || !cleanCode.all { it.isDigit() }) {
            return SupportVerificationResult.InvalidCode
        }

        // Test candidate dates to support timezone variations
        val candidateDates = getCandidateDates()

        for (date in candidateDates) {
            // 1. Check Action 101: Disable Uninstall Protection
            if (calculateCode(deviceId, date, ACTION_DISABLE_UNINSTALL, "", secretKey) == cleanCode) {
                return SupportVerificationResult.Success(
                    actionCode = ACTION_DISABLE_UNINSTALL,
                    actionName = "Disable Uninstall Protection",
                    targetPayload = "",
                    description = "Turn off Device Admin and allow FocusGuard modifications or uninstallation"
                )
            }

            // 2. Check Action 102: Reset Challenge Lock
            if (calculateCode(deviceId, date, ACTION_RESET_CHALLENGE, "", secretKey) == cleanCode) {
                return SupportVerificationResult.Success(
                    actionCode = ACTION_RESET_CHALLENGE,
                    actionName = "Reset Challenge Lock",
                    targetPayload = "",
                    description = "Reset active Sentence Typing or 24-hour time delay challenges"
                )
            }

            // 2.5 Check Action 103: Reset Time Tamper Flag
            if (calculateCode(deviceId, date, ACTION_RESET_TIME_TAMPER, "", secretKey) == cleanCode) {
                return SupportVerificationResult.Success(
                    actionCode = ACTION_RESET_TIME_TAMPER,
                    actionName = "Reset Time Tamper Lock",
                    targetPayload = "",
                    description = "Clear system clock manipulation flag and restore normal access"
                )
            }

            // 3. Check Action 999: Master Emergency Reset
            if (calculateCode(deviceId, date, ACTION_FULL_RESET, "", secretKey) == cleanCode) {
                return SupportVerificationResult.Success(
                    actionCode = ACTION_FULL_RESET,
                    actionName = "Master Emergency Reset",
                    targetPayload = "",
                    description = "Disable all protections, unblock all apps, and clear all restrictions"
                )
            }

            // 4. Check Action 203: Disable Distraction Feeds (Instagram, YouTube, Facebook, all)
            for (feed in PRESET_FEEDS) {
                if (calculateCode(deviceId, date, ACTION_DISABLE_FEED, feed, secretKey) == cleanCode) {
                    return SupportVerificationResult.Success(
                        actionCode = ACTION_DISABLE_FEED,
                        actionName = "Disable Distraction Feed",
                        targetPayload = feed,
                        description = "Unblock short videos / reels for $feed"
                    )
                }
            }

            // 5. Check Action 201: Unblock specific app
            for (pkg in activeBlockedPackages) {
                if (calculateCode(deviceId, date, ACTION_UNBLOCK_APP, pkg, secretKey) == cleanCode) {
                    return SupportVerificationResult.Success(
                        actionCode = ACTION_UNBLOCK_APP,
                        actionName = "Unblock App",
                        targetPayload = pkg,
                        description = "Remove app ($pkg) from the blocked apps list"
                    )
                }
            }

            // 6. Check Action 202: Delete specific keyword
            for (kw in activeBlockedKeywords) {
                if (calculateCode(deviceId, date, ACTION_DELETE_KEYWORD, kw, secretKey) == cleanCode) {
                    return SupportVerificationResult.Success(
                        actionCode = ACTION_DELETE_KEYWORD,
                        actionName = "Delete Blocked Keyword",
                        targetPayload = kw,
                        description = "Unblock website keyword or domain \"$kw\""
                    )
                }
            }

            // 7. Check Action 301: Reset Daily Usage Limit
            for (pkg in activeLimitPackages) {
                if (calculateCode(deviceId, date, ACTION_RESET_LIMIT, pkg, secretKey) == cleanCode) {
                    return SupportVerificationResult.Success(
                        actionCode = ACTION_RESET_LIMIT,
                        actionName = "Reset App Daily Limit",
                        targetPayload = pkg,
                        description = "Reset today's elapsed usage counter for $pkg"
                    )
                }
            }
        }

        return SupportVerificationResult.InvalidCode
    }

    private fun getCandidateDates(): List<String> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val now = Calendar.getInstance()

        val dates = mutableSetOf<String>()
        // Local Today
        dates.add(sdf.format(now.time))

        // UTC Today
        val utcSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        dates.add(utcSdf.format(Date()))

        // Yesterday
        now.add(Calendar.DAY_OF_YEAR, -1)
        dates.add(sdf.format(now.time))

        // Tomorrow
        now.add(Calendar.DAY_OF_YEAR, 2)
        dates.add(sdf.format(now.time))

        return dates.toList()
    }
}
