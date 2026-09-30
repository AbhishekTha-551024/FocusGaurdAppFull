package com.focusguard.app.security

import java.util.Locale

sealed class IntentValidationResult {
    object Valid : IntentValidationResult()
    data class TooShort(val currentChars: Int, val minRequired: Int = 15) : IntentValidationResult()
    data class TooFewWords(val currentWords: Int, val minRequired: Int = 3) : IntentValidationResult()
    data class GibberishDetected(val message: String) : IntentValidationResult()
}

data class PresetIntent(
    val id: String,
    val label: String,
    val defaultText: String,
    val iconName: String
)

object IntentValidationEngine {

    const val MIN_CHARACTERS = 15
    const val MIN_WORDS = 3

    val PRESET_INTENTS = listOf(
        PresetIntent(
            id = "WORK",
            label = "💼 Urgent Work Message",
            defaultText = "Need to check or respond to an urgent work message",
            iconName = "Work"
        ),
        PresetIntent(
            id = "QUICK_REPLY",
            label = "💬 Quick Family / Friend Reply",
            defaultText = "Replying to an important personal message from family or friend",
            iconName = "Chat"
        ),
        PresetIntent(
            id = "OTP_DELIVERY",
            label = "📦 OTP / Ticket / Delivery",
            defaultText = "Checking an urgent OTP code, ticket confirmation, or delivery update",
            iconName = "Receipt"
        )
    )

    val REPEAT_PRESET_INTENTS = listOf(
        PresetIntent(
            id = "REFLEX",
            label = "🔁 Reflex / Habitual Tap",
            defaultText = "Opened reflexively out of habit or muscle memory",
            iconName = "Loop"
        ),
        PresetIntent(
            id = "WAITING_REPLY",
            label = "⏳ Checking for Reply",
            defaultText = "Checking if someone replied to my previous message",
            iconName = "Hourglass"
        ),
        PresetIntent(
            id = "FORGOT_TASK",
            label = "⚡ Urgent Detail I Forgot",
            defaultText = "Quickly checking an urgent detail I forgot during previous session",
            iconName = "Flash"
        ),
        PresetIntent(
            id = "BOREDOM",
            label = "🥱 Bored / Passing Time",
            defaultText = "Taking a quick break or passing time intentionally",
            iconName = "Coffee"
        )
    )

    private val KEYBOARD_MASH_PATTERNS = listOf(
        Regex("(?i)asdf|qwer|zxcv|hjkl|poiuy"),
        Regex("(?i)12345|67890"),
        Regex("(?i)(.)\\1{3,}"), // 4 or more identical characters in a row (e.g. "aaaa")
        Regex("(?i)[bcdfghjklmnpqrstvwxyz]{6,}") // 6 or more consecutive consonants without vowels
    )

    fun validateIntent(text: String): IntentValidationResult {
        val trimmed = text.trim()

        // 1. Minimum character length check
        if (trimmed.length < MIN_CHARACTERS) {
            return IntentValidationResult.TooShort(
                currentChars = trimmed.length,
                minRequired = MIN_CHARACTERS
            )
        }

        // 2. Minimum word count check (at least 3 words separated by spaces)
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size < MIN_WORDS) {
            return IntentValidationResult.TooFewWords(
                currentWords = words.size,
                minRequired = MIN_WORDS
            )
        }

        // 3. Anti-gibberish / Keyboard mash detection
        for (pattern in KEYBOARD_MASH_PATTERNS) {
            if (pattern.containsMatchIn(trimmed)) {
                return IntentValidationResult.GibberishDetected(
                    "Please write a real, meaningful intention rather than random characters."
                )
            }
        }

        // 4. Repeated identical words check (e.g. "test test test test")
        val uniqueWords = words.map { it.lowercase(Locale.ROOT) }.toSet()
        if (uniqueWords.size == 1 && words.size >= 3) {
            return IntentValidationResult.GibberishDetected(
                "Please describe a specific purpose instead of repeating the same word."
            )
        }

        return IntentValidationResult.Valid
    }
}
