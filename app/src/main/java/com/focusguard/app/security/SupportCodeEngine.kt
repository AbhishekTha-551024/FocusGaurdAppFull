package com.focusguard.app.security

import com.focusguard.app.BuildConfig
import java.nio.ByteBuffer
import java.security.MessageDigest
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
        val description: String,
        /** The date string the code actually matched — feeds the single-use ledger. */
        val matchedDate: String,
        /** One-way fingerprint of the matched payload. Never the code itself. */
        val codeFingerprint: String
    ) : SupportVerificationResult()

    /** Code is cryptographically valid but was already applied on this device. */
    data object AlreadyUsed : SupportVerificationResult()

    data object InvalidCode : SupportVerificationResult()
    data class RateLimited(val remainingMinutes: Long) : SupportVerificationResult()
}

/**
 * SupportCodeEngine
 * =================
 * Offline, stateless HMAC-SHA256 verification of the 6-digit Emergency Support
 * Passcode. The mirrored generator lives in admin-panel/src/crypto/codeGenerator.js
 * and MUST stay byte-compatible (see SupportCodeEngineTest for the parity vectors).
 *
 * Secret handling (ECC rules/kotlin/security.md — "never hardcode secrets"):
 * the master secret is injected at build time from local.properties / CI into
 * BuildConfig.SUPPORT_MASTER_SECRET. It is deliberately NOT stored as a literal
 * here any more, not even XOR-obfuscated: XOR over a constant array next to a
 * comment naming the plaintext provides zero protection against a decompiler.
 */
@Singleton
class SupportCodeEngine @Inject constructor() {

    companion object {
        private const val PIN_MODULO = 900_000
        private const val PIN_OFFSET = 100_000
        private const val POSITIVE_INT_MASK = 0x7FFFFFFF
        private const val SUPPORTED_CODE_LENGTH = 6

        /** Device IDs are alphanumeric plus hyphen; anything else is stripped, mirroring the panel. */
        private val NON_DEVICE_ID_CHARS = Regex("[^A-Z0-9-]")

        const val ACTION_DISABLE_UNINSTALL = "101"
        const val ACTION_RESET_CHALLENGE = "102"
        const val ACTION_RESET_TIME_TAMPER = "103"
        const val ACTION_UNBLOCK_APP = "201"
        const val ACTION_DELETE_KEYWORD = "202"
        const val ACTION_DISABLE_FEED = "203"
        const val ACTION_RESET_LIMIT = "301"
        const val ACTION_FULL_RESET = "999"

        private val PRESET_FEEDS = listOf("instagram", "youtube", "facebook", "all")

        /** Build-time injected master secret. Blank means "not configured" and every call rejects. */
        val DEFAULT_MASTER_SECRET: String get() = BuildConfig.SUPPORT_MASTER_SECRET

        /**
         * Canonical Device ID used for both signing and verification so that a
         * copy-paste or a spoken "FG 8821 4902" cannot silently change the HMAC input.
         */
        fun canonicalizeDeviceId(raw: String): String =
            raw.trim().uppercase(Locale.US).replace(NON_DEVICE_ID_CHARS, "")

        /** One-way, non-secret-storing identity of a code, used for the local single-use ledger. */
        fun fingerprintOf(deviceId: String, dateStr: String, actionCode: String, targetPayload: String): String {
            val canonical = "${canonicalizeDeviceId(deviceId)}|${dateStr.trim()}|" +
                "${actionCode.trim()}|${targetPayload.trim().lowercase(Locale.US)}"
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.substring(0, 32)
        }
    }

    /**
     * Calculates the exact 6-digit numeric PIN for one (device, date, action, target) tuple.
     * Throws when the master secret is unconfigured — the caller must never treat that as "invalid code".
     */
    fun calculateCode(
        deviceId: String,
        dateStr: String,
        actionCode: String,
        targetPayload: String = "",
        secretKey: String = DEFAULT_MASTER_SECRET
    ): String {
        val secret = secretKey.trim()
        check(secret.isNotEmpty()) { "Support master secret is not configured (see local.properties)." }

        val rawPayload = "${canonicalizeDeviceId(deviceId)}|${dateStr.trim()}|" +
            "${actionCode.trim()}|${targetPayload.trim().lowercase(Locale.US)}"

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hashBytes = mac.doFinal(rawPayload.toByteArray(Charsets.UTF_8))

        // First 4 bytes as a big-endian 32-bit int, forced positive, folded into the 100000..999999 range.
        val intVal = ByteBuffer.wrap(hashBytes.copyOfRange(0, 4)).int and POSITIVE_INT_MASK
        return ((intVal % PIN_MODULO) + PIN_OFFSET).toString()
    }

    /**
     * Verifies the entered PIN against every candidate action across the multi-day grace window
     * (local today, UTC today, yesterday, tomorrow). Destructive actions are tested first.
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
        if (cleanCode.length != SUPPORTED_CODE_LENGTH || !cleanCode.all { it.isDigit() }) {
            return SupportVerificationResult.InvalidCode
        }

        val candidates = buildCandidateActions(
            activeBlockedPackages = activeBlockedPackages,
            activeBlockedKeywords = activeBlockedKeywords,
            activeLimitPackages = activeLimitPackages
        )

        for (date in getCandidateDates()) {
            for (candidate in candidates) {
                val expected = calculateCode(
                    deviceId = deviceId,
                    dateStr = date,
                    actionCode = candidate.actionCode,
                    targetPayload = candidate.target,
                    secretKey = secretKey
                )
                if (expected == cleanCode) {
                    return SupportVerificationResult.Success(
                        actionCode = candidate.actionCode,
                        actionName = candidate.actionName,
                        targetPayload = candidate.target,
                        description = candidate.description,
                        matchedDate = date,
                        codeFingerprint = fingerprintOf(deviceId, date, candidate.actionCode, candidate.target)
                    )
                }
            }
        }

        return SupportVerificationResult.InvalidCode
    }

    private data class Candidate(
        val actionCode: String,
        val actionName: String,
        val target: String,
        val description: String
    )

    private fun buildCandidateActions(
        activeBlockedPackages: Set<String>,
        activeBlockedKeywords: Set<String>,
        activeLimitPackages: Set<String>
    ): List<Candidate> = buildList {
        add(
            Candidate(
                ACTION_DISABLE_UNINSTALL, "Disable Uninstall Protection", "",
                "Turn off local Uninstall Protection and request Device Admin removal. " +
                    "A mirrored timed commitment may return after reinstall."
            )
        )
        add(
            Candidate(
                ACTION_RESET_CHALLENGE, "Reset Challenge Lock", "",
                "Turn off the accountability challenge and clear pending changes. " +
                    "Uninstall Protection is unaffected."
            )
        )
        add(
            Candidate(
                ACTION_RESET_TIME_TAMPER, "Reset Time Tamper Lock", "",
                "Clear system clock manipulation flag and restore normal access"
            )
        )
        add(
            Candidate(
                ACTION_FULL_RESET, "Master Emergency Reset", "",
                "Reset local restrictions and disable local Uninstall Protection. " +
                    "A mirrored timed commitment may return after reinstall."
            )
        )
        PRESET_FEEDS.forEach { feed ->
            add(
                Candidate(
                    ACTION_DISABLE_FEED, "Disable Distraction Feed", feed,
                    "Unblock short videos / reels for $feed"
                )
            )
        }
        activeBlockedPackages.forEach { pkg ->
            add(
                Candidate(
                    ACTION_UNBLOCK_APP, "Unblock App", pkg,
                    "Remove app ($pkg) from the blocked apps list"
                )
            )
        }
        activeBlockedKeywords.forEach { keyword ->
            add(
                Candidate(
                    ACTION_DELETE_KEYWORD, "Delete Blocked Keyword", keyword,
                    "Unblock website keyword or domain \"$keyword\""
                )
            )
        }
        activeLimitPackages.forEach { pkg ->
            add(
                Candidate(
                    ACTION_RESET_LIMIT, "Reset App Daily Limit", pkg,
                    "Reset today's elapsed usage counter for $pkg"
                )
            )
        }
    }

    private fun getCandidateDates(): List<String> {
        val localFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val utcFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val calendar = Calendar.getInstance()
        val localToday = localFormat.format(calendar.time)
        val utcToday = utcFormat.format(Date())

        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = localFormat.format(calendar.time)

        calendar.add(Calendar.DAY_OF_YEAR, 2)
        val tomorrow = localFormat.format(calendar.time)

        return linkedSetOf(localToday, utcToday, yesterday, tomorrow).toList()
    }
}
