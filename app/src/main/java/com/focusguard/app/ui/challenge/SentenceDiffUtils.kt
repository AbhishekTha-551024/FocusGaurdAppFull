package com.focusguard.app.ui.challenge

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.focusguard.app.core.designsystem.theme.*

object SentenceDiffUtils {

    fun buildAnnotatedDiff(typedText: String, activeSentence: String): AnnotatedString {
        return buildAnnotatedString {
            for (i in activeSentence.indices) {
                when {
                    i < typedText.length && typedText[i] == activeSentence[i] ->
                        withStyle(SpanStyle(color = Success, fontWeight = FontWeight.SemiBold)) { append(activeSentence[i]) }
                    i < typedText.length ->
                        withStyle(SpanStyle(color = Rose400, fontWeight = FontWeight.SemiBold)) { append(activeSentence[i]) }
                    else ->
                        withStyle(SpanStyle(color = Amber400)) { append(activeSentence[i]) }
                }
            }
            if (typedText.length > activeSentence.length) {
                val overflow = typedText.substring(activeSentence.length)
                if (overflow.trim().isNotEmpty()) {
                    withStyle(SpanStyle(color = Rose400, fontWeight = FontWeight.SemiBold)) { append(overflow) }
                }
            }
        }
    }
}
