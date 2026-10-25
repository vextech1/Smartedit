package com.vextech.smart_edit.core.library

enum class OcrScript { LATIN, CHINESE, DEVANAGARI, JAPANESE, KOREAN }

data class OcrStats(val words: Int, val characters: Int, val lines: Int)

object OcrPolicies {
    /** Unifies line endings, trims line ends, limits blank runs to one blank line and trims the whole text. */
    fun clean(text: String): String = text
        .replace("\r\n", "\n").replace('\r', '\n')
        .split('\n').joinToString("\n") { it.trimEnd() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    fun stats(text: String): OcrStats {
        val cleaned = clean(text)
        if (cleaned.isEmpty()) return OcrStats(0, 0, 0)
        return OcrStats(
            words = cleaned.split(Regex("\\s+")).count { it.isNotEmpty() },
            characters = cleaned.count { !it.isWhitespace() },
            lines = cleaned.split('\n').count { it.isNotBlank() },
        )
    }

    /** First non-blank line, cut at a word boundary. Empty when there is no text. */
    fun suggestedTitle(text: String, max: Int = 60): String {
        val first = clean(text).lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return ""
        if (first.length <= max) return first
        val cut = first.lastIndexOf(' ', max).takeIf { it > max / 2 } ?: max
        return first.substring(0, cut).trimEnd() + "…"
    }

    private val reservedChars = Regex("[<>:\"/\\\\|?*\\p{Cntrl}]")

    /** Safe .txt name from a title. Falls back to [fallbackStamp]. */
    fun textFileName(title: String, fallbackStamp: String): String {
        val base = title.replace(reservedChars, " ").replace(Regex("\\s+"), " ").trim().trimEnd('.', ' ').take(60).trim()
        return (base.ifEmpty { "ocr_$fallbackStamp" }) + ".txt"
    }

    /** Script that best matches an ISO language code from language detection. */
    fun scriptForLanguage(code: String?): OcrScript = when (code?.lowercase()?.substringBefore('-')) {
        "zh" -> OcrScript.CHINESE
        "ja" -> OcrScript.JAPANESE
        "ko" -> OcrScript.KOREAN
        "hi", "mr", "ne", "sa" -> OcrScript.DEVANAGARI
        else -> OcrScript.LATIN
    }

    /** Suggest re-running with another script when the detected language belongs to a different one. */
    fun suggestedRetry(selected: OcrScript, detectedLanguage: String?): OcrScript? {
        if (detectedLanguage == null) return null
        val target = scriptForLanguage(detectedLanguage)
        return target.takeIf { it != selected }
    }
}
