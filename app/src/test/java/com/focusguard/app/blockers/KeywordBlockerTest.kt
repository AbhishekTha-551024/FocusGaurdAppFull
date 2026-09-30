package com.focusguard.app.blockers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class KeywordBlockerTest {

    private lateinit var blocker: KeywordBlocker

    @Before
    fun setUp() {
        blocker = KeywordBlocker()
    }

    @Test
    fun testWordBoundary_BankDoesNotMatchEmbankment() {
        blocker.updateKeywords(setOf("bank"))

        // False positive check: "bank" inside "embankment" must NOT trigger a block
        val resultEmbankment = blocker.isViolationDetectedSync("embankment")
        assertNull("Expected 'embankment' to NOT match keyword 'bank'", resultEmbankment)

        val resultBanker = blocker.isViolationDetectedSync("banker")
        assertNull("Expected 'banker' to NOT match keyword 'bank'", resultBanker)

        val resultSnowbank = blocker.isViolationDetectedSync("snowbank")
        assertNull("Expected 'snowbank' to NOT match keyword 'bank'", resultSnowbank)

        // Valid match check: "check my bank account" must trigger a block
        val resultValid = blocker.isViolationDetectedSync("check my bank account")
        assertEquals("bank", resultValid)

        // Exact match
        val resultExact = blocker.isViolationDetectedSync("bank")
        assertEquals("bank", resultExact)

        // With punctuation boundaries
        val resultPunctuation = blocker.isViolationDetectedSync("Visiting the bank.")
        assertEquals("bank", resultPunctuation)

        val resultUrl = blocker.isViolationDetectedSync("https://bank.com/login")
        assertEquals("bank", resultUrl)
    }

    @Test
    fun testWordBoundary_ShortKeywords() {
        blocker.updateKeywords(setOf("ass", "car"))

        // Should NOT match embedded words
        assertNull(blocker.isViolationDetectedSync("class"))
        assertNull(blocker.isViolationDetectedSync("classic"))
        assertNull(blocker.isViolationDetectedSync("career"))

        // SHOULD match standalone words
        assertEquals("ass", blocker.isViolationDetectedSync("kick ass"))
        assertEquals("car", blocker.isViolationDetectedSync("red car"))
    }

    @Test
    fun testUnicodeAndObfuscationMatching() {
        blocker.updateKeywords(setOf("porn", "reels"))

        // Diacritics
        assertEquals("porn", blocker.isViolationDetectedSync("pórn video"))
        assertEquals("reels", blocker.isViolationDetectedSync("rëëls"))

        // Leetspeak & spaces
        assertEquals("porn", blocker.isViolationDetectedSync("p 0 r n"))
        assertEquals("reels", blocker.isViolationDetectedSync("r33ls"))
    }
}
