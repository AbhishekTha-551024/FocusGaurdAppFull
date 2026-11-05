package com.focusguard.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * SupportCodeEngine unit tests.
 *
 * Two jobs:
 *  1. Lock the wire format. The admin panel (admin-panel/src/crypto/codeGenerator.js) signs the
 *     same payload with CryptoJS; these golden vectors are generated from that implementation, so
 *     a refactor on either side that changes bytes turns a test red instead of silently breaking
 *     every support code already handed to users.
 *  2. Cover the behaviours the emergency unlock path depends on: Device ID canonicalization,
 *     target normalization, the reject-when-unconfigured contract, and the single-use fingerprint.
 *
 * The vectors below are computed with a dedicated test secret — the production master secret never
 * appears in tracked source (it is injected into BuildConfig from local.properties / CI).
 */
class SupportCodeEngineTest {

    private val engine = SupportCodeEngine()

    private companion object {
        const val TEST_SECRET = "SupportCodeEngineTest#LocalSecret#0000"
        const val DEVICE_ID = "FG-8821-4902"
        const val FIXED_DATE = "2026-10-30"

        fun localToday(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(System.currentTimeMillis())
    }

    // ── 1. Golden parity vectors (Kotlin ⇄ panel JS) ───────────────────────

    @Test
    fun `action 101 matches the panel generated code`() {
        assertEquals("158390", engine.calculateCode(DEVICE_ID, FIXED_DATE, "101", "", TEST_SECRET))
    }

    @Test
    fun `action 102 matches the panel generated code`() {
        assertEquals("980134", engine.calculateCode(DEVICE_ID, FIXED_DATE, "102", "", TEST_SECRET))
    }

    @Test
    fun `action 103 matches the panel generated code`() {
        assertEquals("135228", engine.calculateCode(DEVICE_ID, FIXED_DATE, "103", "", TEST_SECRET))
    }

    @Test
    fun `action 999 matches the panel generated code`() {
        assertEquals("338343", engine.calculateCode(DEVICE_ID, FIXED_DATE, "999", "", TEST_SECRET))
    }

    @Test
    fun `feed action 203 with target all matches the panel generated code`() {
        assertEquals("831630", engine.calculateCode(DEVICE_ID, FIXED_DATE, "203", "all", TEST_SECRET))
    }

    @Test
    fun `feed action 203 with target youtube matches the panel generated code`() {
        assertEquals("441501", engine.calculateCode(DEVICE_ID, FIXED_DATE, "203", "youtube", TEST_SECRET))
    }

    @Test
    fun `app action 201 with instagram package matches the panel generated code`() {
        assertEquals(
            "455699",
            engine.calculateCode(DEVICE_ID, FIXED_DATE, "201", "com.instagram.android", TEST_SECRET)
        )
    }

    @Test
    fun `keyword action 202 with gambling matches the panel generated code`() {
        assertEquals("851192", engine.calculateCode(DEVICE_ID, FIXED_DATE, "202", "gambling", TEST_SECRET))
    }

    @Test
    fun `limit action 301 with youtube package matches the panel generated code`() {
        assertEquals(
            "406500",
            engine.calculateCode(DEVICE_ID, FIXED_DATE, "301", "com.google.android.youtube", TEST_SECRET)
        )
    }

    // ── 2. Canonicalization — the panel strips non [A-Z0-9-] before signing ─

    @Test
    fun `device id with spaces canonicalizes to the hyphen stripped form`() {
        assertEquals("FG88214902", SupportCodeEngine.canonicalizeDeviceId("fg 8821 4902"))
        assertEquals("FG88214902", SupportCodeEngine.canonicalizeDeviceId("FG.8821#4902"))
        assertEquals("FG-8821-4902", SupportCodeEngine.canonicalizeDeviceId("  FG-8821-4902  "))
    }

    @Test
    fun `surrounding whitespace on the device id cannot change the code`() {
        val spaced = engine.calculateCode("  FG-8821-4902  ", FIXED_DATE, "101", "", TEST_SECRET)
        assertEquals("158390", spaced)
    }

    @Test
    fun `device id written without hyphens matches the panel vector for that form`() {
        // The panel normalizes "fg-88214902" to "FG-88214902" (hyphen kept is a different string),
        // so a user reading "FG 8821 4902" aloud and an agent typing "FG88214902" must agree —
        // but a hyphenless ID is genuinely a different signing input, and this pins that.
        assertEquals("370119", engine.calculateCode("fg-88214902", FIXED_DATE, "101", "", TEST_SECRET))
        assertEquals("558197", engine.calculateCode("FG88214902", FIXED_DATE, "101", "", TEST_SECRET))
    }

    @Test
    fun `target payload is lowercased and trimmed before signing`() {
        val upper = engine.calculateCode(DEVICE_ID, FIXED_DATE, "201", "  COM.Instagram.Android ", TEST_SECRET)
        assertEquals("455699", upper)
    }

    // ── 3. Shape and determinism of the PIN ────────────────────────────────

    @Test
    fun `code is always six digits`() {
        for (action in listOf("101", "102", "103", "201", "202", "203", "301", "999")) {
            val code = engine.calculateCode(DEVICE_ID, FIXED_DATE, action, "target", TEST_SECRET)
            assertEquals(action, 6, code.length)
            assertTrue(action, code.all(Char::isDigit))
        }
    }

    @Test
    fun `same inputs produce the same code`() {
        val first = engine.calculateCode(DEVICE_ID, FIXED_DATE, "101", "", TEST_SECRET)
        val second = engine.calculateCode(DEVICE_ID, FIXED_DATE, "101", "", TEST_SECRET)
        assertEquals(first, second)
    }

    @Test
    fun `different dates produce different codes for the same action`() {
        val today = engine.calculateCode(DEVICE_ID, FIXED_DATE, "101", "", TEST_SECRET)
        val tomorrow = engine.calculateCode(DEVICE_ID, "2026-10-31", "101", "", TEST_SECRET)
        assertNotEquals(today, tomorrow)
    }

    @Test
    fun `a blank master secret is rejected loudly instead of verifying`() {
        try {
            engine.calculateCode(DEVICE_ID, FIXED_DATE, "101", "", "")
            fail("Expected IllegalStateException when the master secret is unconfigured")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("master secret"))
        }
    }

    // ── 4. verifySupportCode round-trip inside the grace window ────────────

    @Test
    fun `verifies a freshly generated uninstall protection code`() {
        val today = localToday()
        val code = engine.calculateCode(DEVICE_ID, today, SupportCodeEngine.ACTION_DISABLE_UNINSTALL, "", TEST_SECRET)

        val result = engine.verifySupportCode(code, DEVICE_ID, secretKey = TEST_SECRET)

        assertTrue(result.toString(), result is SupportVerificationResult.Success)
        val success = result as SupportVerificationResult.Success
        assertEquals(SupportCodeEngine.ACTION_DISABLE_UNINSTALL, success.actionCode)
        assertEquals(today, success.matchedDate)
        assertEquals("Disable Uninstall Protection", success.actionName)
    }

    @Test
    fun `verifies a time tamper reset code`() {
        val today = localToday()
        val code = engine.calculateCode(DEVICE_ID, today, SupportCodeEngine.ACTION_RESET_TIME_TAMPER, "", TEST_SECRET)

        val result = engine.verifySupportCode(code, DEVICE_ID, secretKey = TEST_SECRET)

        assertEquals(SupportCodeEngine.ACTION_RESET_TIME_TAMPER, (result as SupportVerificationResult.Success).actionCode)
    }

    @Test
    fun `verifies a master emergency reset code`() {
        val today = localToday()
        val code = engine.calculateCode(DEVICE_ID, today, SupportCodeEngine.ACTION_FULL_RESET, "", TEST_SECRET)

        val result = engine.verifySupportCode(code, DEVICE_ID, secretKey = TEST_SECRET)

        assertEquals(SupportCodeEngine.ACTION_FULL_RESET, (result as SupportVerificationResult.Success).actionCode)
    }

    @Test
    fun `verifies a feed unlock without knowing any installed package`() {
        val today = localToday()
        val code = engine.calculateCode(DEVICE_ID, today, SupportCodeEngine.ACTION_DISABLE_FEED, "all", TEST_SECRET)

        val result = engine.verifySupportCode(code, DEVICE_ID, secretKey = TEST_SECRET)

        val success = result as SupportVerificationResult.Success
        assertEquals(SupportCodeEngine.ACTION_DISABLE_FEED, success.actionCode)
        assertEquals("all", success.targetPayload)
    }

    @Test
    fun `verifies an app unlock only when that package is currently blocked`() {
        val today = localToday()
        val target = "com.instagram.android"
        val code = engine.calculateCode(DEVICE_ID, today, SupportCodeEngine.ACTION_UNBLOCK_APP, target, TEST_SECRET)

        val unmatched = engine.verifySupportCode(code, DEVICE_ID, secretKey = TEST_SECRET)
        assertEquals(SupportVerificationResult.InvalidCode, unmatched)

        val matched = engine.verifySupportCode(
            inputCode = code,
            deviceId = DEVICE_ID,
            activeBlockedPackages = setOf(target),
            secretKey = TEST_SECRET
        )
        val success = matched as SupportVerificationResult.Success
        assertEquals(SupportCodeEngine.ACTION_UNBLOCK_APP, success.actionCode)
        assertEquals(target, success.targetPayload)
    }

    @Test
    fun `verifies a keyword unlock against the active keyword list`() {
        val today = localToday()
        val keyword = "gambling"
        val code = engine.calculateCode(DEVICE_ID, today, SupportCodeEngine.ACTION_DELETE_KEYWORD, keyword, TEST_SECRET)

        val result = engine.verifySupportCode(
            inputCode = code,
            deviceId = DEVICE_ID,
            activeBlockedKeywords = setOf(keyword),
            secretKey = TEST_SECRET
        )

        assertEquals(SupportCodeEngine.ACTION_DELETE_KEYWORD, (result as SupportVerificationResult.Success).actionCode)
    }

    @Test
    fun `verifies a daily limit reset against the active limit list`() {
        val today = localToday()
        val target = "com.google.android.youtube"
        val code = engine.calculateCode(DEVICE_ID, today, SupportCodeEngine.ACTION_RESET_LIMIT, target, TEST_SECRET)

        val result = engine.verifySupportCode(
            inputCode = code,
            deviceId = DEVICE_ID,
            activeLimitPackages = setOf(target),
            secretKey = TEST_SECRET
        )

        assertEquals(SupportCodeEngine.ACTION_RESET_LIMIT, (result as SupportVerificationResult.Success).actionCode)
    }

    // ── 5. Rejection paths ────────────────────────────────────────────────

    @Test
    fun `non numeric input is invalid without touching the mac`() {
        assertEquals(SupportVerificationResult.InvalidCode, engine.verifySupportCode("abcdef", DEVICE_ID, secretKey = TEST_SECRET))
    }

    @Test
    fun `short and long inputs are invalid`() {
        assertEquals(SupportVerificationResult.InvalidCode, engine.verifySupportCode("12345", DEVICE_ID, secretKey = TEST_SECRET))
        assertEquals(SupportVerificationResult.InvalidCode, engine.verifySupportCode("1234567", DEVICE_ID, secretKey = TEST_SECRET))
        assertEquals(SupportVerificationResult.InvalidCode, engine.verifySupportCode("", DEVICE_ID, secretKey = TEST_SECRET))
    }

    @Test
    fun `zero padded code can never be produced by the scheme`() {
        // calculateCode maps into 100000..999999, so "000000" is structurally unreachable.
        assertEquals(SupportVerificationResult.InvalidCode, engine.verifySupportCode("000000", DEVICE_ID, secretKey = TEST_SECRET))
    }

    @Test
    fun `a valid code for another device does not verify here`() {
        val today = localToday()
        val code = engine.calculateCode("FG-1111-2222", today, SupportCodeEngine.ACTION_FULL_RESET, "", TEST_SECRET)

        assertEquals(SupportVerificationResult.InvalidCode, engine.verifySupportCode(code, DEVICE_ID, secretKey = TEST_SECRET))
    }

    // ── 6. Single-use ledger fingerprint ──────────────────────────────────

    @Test
    fun `fingerprint is stable and shared with the verification result`() {
        val expected = SupportCodeEngine.fingerprintOf(DEVICE_ID, FIXED_DATE, "101", "")
        assertEquals("aea78a5b0f1d9f00b6e01902273092ad", expected)

        val code = engine.calculateCode(DEVICE_ID, localToday(), "101", "", TEST_SECRET)
        val success = engine.verifySupportCode(code, DEVICE_ID, secretKey = TEST_SECRET) as SupportVerificationResult.Success
        assertEquals(
            SupportCodeEngine.fingerprintOf(DEVICE_ID, success.matchedDate, "101", ""),
            success.codeFingerprint
        )
    }

    @Test
    fun `fingerprint differs per action so one consumed code cannot mark another consumed`() {
        val unlock = SupportCodeEngine.fingerprintOf(DEVICE_ID, FIXED_DATE, "101", "")
        val reset = SupportCodeEngine.fingerprintOf(DEVICE_ID, FIXED_DATE, "999", "")
        assertEquals("2371462cd5b837edfcdaf90c46584d65", reset)
        assertNotEquals(unlock, reset)
    }

    @Test
    fun `fingerprint is a 32 char lowercase hex digest and never carries the payload`() {
        val fingerprint = SupportCodeEngine.fingerprintOf(DEVICE_ID, FIXED_DATE, "201", "com.instagram.android")

        assertEquals(32, fingerprint.length)
        assertTrue(fingerprint.all { it in '0'..'9' || it in 'a'..'f' })
        assertTrue(!fingerprint.contains("instagram"))
    }

    @Test
    fun `fingerprint normalizes device id casing and padding like the code does`() {
        val clean = SupportCodeEngine.fingerprintOf(DEVICE_ID, FIXED_DATE, "101", "")
        val messy = SupportCodeEngine.fingerprintOf("  fg-8821-4902 ", FIXED_DATE, "101", "")
        assertEquals(clean, messy)
    }
}
