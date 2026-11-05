package com.focusguard.app.core.common.privacy

import java.security.MessageDigest

/**
 * LogPrivacy
 * ==========
 * Turns an identity into something safe to leave in `adb logcat`, a bug report or a crash tool.
 *
 * FocusGuard ships to a website audience and its logs are the main way field problems get diagnosed,
 * so log lines inevitably travel outside the phone. The account email and the full device id are the
 * two values that identify a person, and neither belongs in a file the user will attach to a message.
 *
 * The replacements are correlation handles, not anonymity: a prefix of a salt-free hash still matches
 * across logs, and a value an attacker can guess (their own email) can be confirmed by hashing it.
 * That is exactly what a diagnostic needs — and it no longer hands over the identity itself.
 */
object LogPrivacy {

    /** Hex characters kept from the digest: enough to group log lines, too short to be useful offline. */
    private const val HASH_PREFIX_CHARS = 8

    /** Tail of an opaque device id that may be shown. */
    private const val ID_SUFFIX_CHARS = 4

    /** `"mail:3f2a81c0"` — never the address itself. */
    fun emailRef(email: String): String {
        val normalized = email.trim().lowercase()
        if (normalized.isEmpty()) return "mail:unknown"
        return "mail:" + sha256Hex(normalized).substring(0, HASH_PREFIX_CHARS)
    }

    /** `"dev:…F3A2"` — the tail only, so the id cannot be replayed against the server. */
    fun deviceRef(deviceId: String): String {
        val normalized = deviceId.trim()
        if (normalized.length <= ID_SUFFIX_CHARS) return "dev:hidden"
        return "dev:…" + normalized.takeLast(ID_SUFFIX_CHARS)
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
