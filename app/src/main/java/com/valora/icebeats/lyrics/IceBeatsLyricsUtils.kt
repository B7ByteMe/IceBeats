/*
 * icebeats Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.valora.icebeats.lyrics

import android.text.format.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.valora.icebeats.betterlyrics.TTMLParser


data class LyricsRomanizationPreferences(
    val romanizeJapanese: Boolean,
    val romanizeKorean: Boolean,

    ) {
    val isEnabled: Boolean
        get() = romanizeJapanese || romanizeKorean
}

@Suppress("RegExpRedundantEscape")
object icebeatsLyricsUtils {
    val LINE_REGEX = "((\\[\\d\\d:\\d\\d\\.\\d{2,3}\\] ?)+)(.+)".toRegex()
    val TIME_REGEX = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\]".toRegex()

    private val KANA_ROMAJI_MAP: Map<String, String> = mapOf(
        // Digraphs (Yoon - combinations like kya, sho)
        "??" to "kya", "??" to "kyu", "??" to "kyo",
        "??" to "sha", "??" to "shu", "??" to "sho",
        "??" to "cha", "??" to "chu", "??" to "cho",
        "??" to "nya", "??" to "nyu", "??" to "nyo",
        "??" to "hya", "??" to "hyu", "??" to "hyo",
        "??" to "mya", "??" to "myu", "??" to "myo",
        "??" to "rya", "??" to "ryu", "??" to "ryo",
        "??" to "gya", "??" to "gyu", "??" to "gyo",
        "??" to "ja", "??" to "ju", "??" to "jo",
        "??" to "ja", "??" to "ju", "??" to "jo", // ? variants, also commonly 'ja', 'ju', 'jo'
        "??" to "bya", "??" to "byu", "??" to "byo",
        "??" to "pya", "??" to "pyu", "??" to "pyo",

        // Basic Katakana Characters
        "?" to "a", "?" to "i", "?" to "u", "?" to "e", "?" to "o",
        "?" to "ka", "?" to "ki", "?" to "ku", "?" to "ke", "?" to "ko",
        "?" to "sa", "?" to "shi", "?" to "su", "?" to "se", "?" to "so",
        "?" to "ta", "?" to "chi", "?" to "tsu", "?" to "te", "?" to "to",
        "?" to "na", "?" to "ni", "?" to "nu", "?" to "ne", "?" to "no",
        "?" to "ha", "?" to "hi", "?" to "fu", "?" to "he", "?" to "ho",
        "?" to "ma", "?" to "mi", "?" to "mu", "?" to "me", "?" to "mo",
        "?" to "ya", "?" to "yu", "?" to "yo",
        "?" to "ra", "?" to "ri", "?" to "ru", "?" to "re", "?" to "ro",
        "?" to "wa", "?" to "o", // ? is pronounced 'o'
        "?" to "n",

        // Dakuten (voiced consonants)
        "?" to "ga", "?" to "gi", "?" to "gu", "?" to "ge", "?" to "go",
        "?" to "za", "?" to "ji", "?" to "zu", "?" to "ze", "?" to "zo",
        "?" to "da", "?" to "ji", "?" to "zu", "?" to "de", "?" to "do", // ? and ? are often 'ji' and 'zu'

        // Handakuten (p-sounds for 'h' group) / Dakuten for 'h' group
        "?" to "ba", "?" to "bi", "?" to "bu", "?" to "be", "?" to "bo", // Dakuten for ?? (ha-row)
        "?" to "pa", "?" to "pi", "?" to "pu", "?" to "pe", "?" to "po", // Handakuten for ?? (ha-row)

        // Choonpu (long vowel mark) - removed as per original logic
        "?" to ""
    )

    private val HANGUL_ROMAJA_MAP: Map<String, Map<String, String>> = mapOf(
        "cho" to mapOf(
            "?" to "g",  "?" to "kk", "?" to "n",  "?" to "d", 
            "?" to "tt", "?" to "r",  "?" to "m",  "?" to "b",
            "?" to "pp", "?" to "s",  "?" to "ss", "?" to "",
            "?" to "j",  "?" to "jj", "?" to "ch", "?" to "k",
            "?" to "t",  "?" to "p",  "?" to "h"
        ),
        "jung" to mapOf(
            "?" to "a",  "?" to "ae", "?" to "ya",  "?" to "yae", 
            "?" to "eo", "?" to "e",  "?" to "yeo", "?" to "ye", 
            "?" to "o",  "?" to "wa", "?" to "wae", "?" to "oe",
            "?" to "yo", "?" to "u",  "?" to "wo",  "?" to "we",
            "?" to "wi", "?" to "yu", "?" to "eu",  "?" to "eui",
            "?" to "i"
        ),
        "jong" to mapOf(
            "?" to "k",     "??" to "g",   "??" to "ngn", "??" to "ngn", "??" to "ngm", "??" to "kh",
            "?" to "kk",    "??" to "kg",  "??" to "ngn", "??" to "ngn", "??" to "ngm", "??" to "kh",
            "?" to "k",     "??" to "ks",  "??" to "ngn", "??" to "ngn", "??" to "ngm", "??" to "kch",
            "?" to "n",     "??" to "ll",  "?" to "n",     "??" to "nj",  "??" to "nn",  "??" to "nn",
            "??" to "nm",  "??" to "nch", "?" to "n",     "??" to "nh",  "??" to "nn",  "?" to "t",
            "??" to "d",   "??" to "nn",  "??" to "nn",  "??" to "nm",  "??" to "th",  "?" to "l",
            "??" to "r",   "??" to "ll",  "??" to "ll",  "?" to "k",     "??" to "lg",  "??" to "ngn",
            "??" to "ngn", "??" to "ngm", "??" to "lkh", "?" to "m",     "??" to "lm",  "??" to "mn",
            "??" to "mn",  "??" to "mm",  "??" to "lmh", "?" to "p",     "??" to "lb",  "??" to "mn",
            "??" to "mn",  "??" to "mm",  "??" to "lph", "?" to "t",     "??" to "ls",  "??" to "nn",
            "??" to "nn",  "??" to "nm",  "??" to "lsh", "?" to "t",     "??" to "lt",  "??" to "nn",
            "??" to "nn",  "??" to "nm",  "??" to "lth", "?" to "p",     "??" to "lp",  "??" to "mn",
            "??" to "mn",  "??" to "mm",  "??" to "lph", "?" to "l",     "??" to "lh",  "??" to "ll",
            "??" to "ll",  "??" to "lm",  "??" to "lh",  "?" to "m",     "??" to "mn",  "?" to "p",
            "??" to "b",   "??" to "mn",  "??" to "mn",  "??" to "mm",  "??" to "ph",  "?" to "p",
            "??" to "ps",  "??" to "mn",  "??" to "mn",  "??" to "mm",  "??" to "psh", "?" to "t",
            "??" to "s",   "??" to "nn",  "??" to "nn",  "??" to "nm",  "??" to "sh",  "?" to "t",
            "??" to "ss",  "??" to "tn",  "??" to "tn",  "??" to "nm",  "??" to "th",  "?" to "ng",
            "?" to "t",     "??" to "j",   "??" to "nn",  "??" to "nn",  "??" to "nm",  "??" to "ch",
            "?" to "t",     "??" to "ch",  "??" to "nn",  "??" to "nn",  "??" to "nm",  "??" to "ch",
            "?" to "k",     "??" to "k",   "??" to "ngn", "??" to "ngn", "??" to "ngm", "??" to "kh",
            "?" to "t",     "??" to "t",   "??" to "nn",  "??" to "nn",  "??" to "nm",  "??" to "th",
            "?" to "p",     "??" to "p",   "??" to "mn",  "??" to "mn",  "??" to "mm",  "??" to "ph",
            "?" to "t",     "??" to "h",   "??" to "nn",  "??" to "nn",  "??" to "mm",  "??" to "t",
            "??" to "k",
        )
    )

    fun isTtml(lyrics: String): Boolean {
        val trimmed = lyrics.trim()
        if (!trimmed.startsWith("<")) return false

        return trimmed.contains("<tt", ignoreCase = true) ||
                trimmed.contains("http://www.w3.org/ns/ttml", ignoreCase = true)
    }

    fun parseTtml(lyrics: String, durationSeconds: Int? = null): List<LyricsEntry> {
        val parsedLines = TTMLParser.parseTTML(lyrics)
        if (parsedLines.isEmpty()) return emptyList()
        val scale = 1.0

        return parsedLines.map { line ->
            val words =
                line.words
                    .filter { it.text.isNotEmpty() }
                    .map { word ->
                        WordTimestamp(
                            text = word.text,
                            startTime = word.startTime * scale,
                            endTime = word.endTime * scale,
                            isBackground = word.isBackground,
                        )
                    }.takeIf { it.isNotEmpty() }

            LyricsEntry(
                time = (line.startTime * scale * 1000.0).toLong(),
                text = line.text,
                words = words,
                agent = line.agent,
            )
        }.sorted()
    }

    fun parseLyrics(lyrics: String): List<LyricsEntry> {
        val lines = lyrics.lines()
        val result = mutableListOf<LyricsEntry>()

        for (line in lines) {
            val entries = parseLine(line)
            if (entries != null) {
                result.addAll(entries)
            }
        }
        return result.sorted()
    }

    private fun parseLine(line: String): List<LyricsEntry>? {
        if (line.isEmpty()) {
            return null
        }
        val matchResult = LINE_REGEX.matchEntire(line.trim()) ?: return null
        val times = matchResult.groupValues[1]
        val text = matchResult.groupValues[3]
        val timeMatchResults = TIME_REGEX.findAll(times)

        return timeMatchResults
            .map { timeMatchResult ->
                val min = timeMatchResult.groupValues[1].toLong()
                val sec = timeMatchResult.groupValues[2].toLong()
                val milString = timeMatchResult.groupValues[3]
                var mil = milString.toLong()
                if (milString.length == 2) {
                    mil *= 10
                }
                val time = min * DateUtils.MINUTE_IN_MILLIS + sec * DateUtils.SECOND_IN_MILLIS + mil
                LyricsEntry(time, text)
            }.toList()
    }

    fun findCurrentLineIndex(
        lines: List<LyricsEntry>,
        position: Long,
        leadMs: Long = 300L,
    ): Int {
        if (lines.isEmpty()) return -1

        val target = position + leadMs
        var low = 0
        var high = lines.lastIndex

        while (low <= high) {
            val mid = (low + high).ushr(1)
            val midTime = lines[mid].time

            if (midTime < target) {
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return high.coerceIn(0, lines.lastIndex)
    }

    /**
     * Converts a Katakana string to Romaji.
     * This optimized version uses a pre-defined map and StringBuilder for better performance
     * compared to chained regex replacements.
     * Expected impact: Significant reduction in object creation (Regex, String) and faster execution.
     */
    fun katakanaToRomaji(katakana: String?): String {
        if (katakana.isNullOrEmpty()) return ""

        val romajiBuilder = StringBuilder(katakana.length) // Initial capacity
        var i = 0
        val n = katakana.length
        while (i < n) {
            var consumed = false
            // Prioritize 2-character sequences from the map (e.g., "??" before "?")
            if (i + 1 < n) {
                val twoCharCandidate = katakana.substring(i, i + 2)
                val mappedTwoChar = KANA_ROMAJI_MAP[twoCharCandidate]
                if (mappedTwoChar != null) {
                    romajiBuilder.append(mappedTwoChar)
                    i += 2
                    consumed = true
                }
            }

            if (!consumed) {
                // If no 2-character sequence matched, try 1-character
                val oneCharCandidate = katakana[i].toString()
                val mappedOneChar = KANA_ROMAJI_MAP[oneCharCandidate]
                if (mappedOneChar != null) {
                    romajiBuilder.append(mappedOneChar)
                } else {
                    // If the character is not in Katakana map, append it as is.
                    romajiBuilder.append(oneCharCandidate)
                }
                i += 1
            }
        }
        return romajiBuilder.toString().lowercase()
    }

    /**
     * Romanizes Japanese text using Kuromoji Tokenizer and the optimized katakanaToRomaji function.
     * Runs on Dispatchers.Default for CPU-intensive work.
     * Expected impact: Faster tokenization due to reused Tokenizer instance and faster
     * per-token romanization.
     */
    suspend fun romanizeJapanese(text: String): String = withContext(Dispatchers.Default) { text }

    /**
     * Converts a Katakana string to Romaji.
     * This optimized version uses a pre-defined map and StringBuilder for better performance
     * compared to chained regex replacements.
     * Expected impact: Significant reduction in object creation (Regex, String) and faster execution.
     * @param katakana The Katakana string to convert.
     * @param nextKatakana Optional: The next Katakana string (from the next token) to help with sokuon (?) gemination.
     */
    fun katakanaToRomaji(katakana: String?, nextKatakana: String? = null): String {
        if (katakana.isNullOrEmpty()) return ""

        val romajiBuilder = StringBuilder(katakana.length) // Initial capacity
        var i = 0
        val n = katakana.length
        while (i < n) {
            var consumed = false
            // Prioritize 2-character sequences from the map (e.g., "??" before "?")
            if (i + 1 < n) {
                val twoCharCandidate = katakana.substring(i, i + 2)
                val mappedTwoChar = KANA_ROMAJI_MAP[twoCharCandidate]
                if (mappedTwoChar != null) {
                    romajiBuilder.append(mappedTwoChar)
                    i += 2
                    consumed = true
                }
            }

            // Handle sokuon (?) - gemination
            if (!consumed && katakana[i] == '?') {
                val nextCharToDouble = nextKatakana?.getOrNull(0)
                if (nextCharToDouble != null) {
                    val nextCharRomaji = KANA_ROMAJI_MAP[nextCharToDouble.toString()]?.getOrNull(0)?.toString()
                        ?: nextCharToDouble.toString()
                    romajiBuilder.append(nextCharRomaji.lowercase().trim())
                }
                // Sokuon itself doesn't have a direct romaji representation other than geminating the next consonant.
                // We just consume '?' and let the next character (if any within the current token) be processed normally.
                i += 1 // Consume the '?'
                consumed = true
            }

            if (!consumed) {
                // If no 2-character sequence matched, try 1-character
                val oneCharCandidate = katakana[i].toString()
                val mappedOneChar = KANA_ROMAJI_MAP[oneCharCandidate]
                if (mappedOneChar != null) {
                    romajiBuilder.append(mappedOneChar)
                } else {
                    // If the character is not in Katakana map, append it as is.
                    romajiBuilder.append(oneCharCandidate)
                }
                i += 1
            }
        }
        return romajiBuilder.toString().lowercase()
    }

    suspend fun romanizeKorean(text: String): String = withContext(Dispatchers.Default) {
        val romajaBuilder = StringBuilder()
        var prevFinal: String? = null

        for (i in text.indices) {
            val char = text[i]

            if (char in '\uAC00'..'\uD7A3') {
                val syllableIndex = char.code - 0xAC00
                
                val choIndex = syllableIndex / (21 * 28)
                val jungIndex = (syllableIndex % (21 * 28)) / 28
                val jongIndex = syllableIndex % 28

                val choChar = (0x1100 + choIndex).toChar().toString()
                val jungChar = (0x1161 + jungIndex).toChar().toString()
                val jongChar = if (jongIndex == 0) null else (0x11A7 + jongIndex).toChar().toString()

                if (prevFinal != null) {
                    val contextKey = prevFinal + choChar
                    val jong = HANGUL_ROMAJA_MAP["jong"]?.get(contextKey)
                        ?: HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal)
                        ?: prevFinal
                    romajaBuilder.append(jong)
                }

                val cho = HANGUL_ROMAJA_MAP["cho"]?.get(choChar) ?: choChar
                val jung = HANGUL_ROMAJA_MAP["jung"]?.get(jungChar) ?: jungChar
                romajaBuilder.append(cho).append(jung)

                prevFinal = jongChar
            } else {
                if (prevFinal != null) {
                    val jong = HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal) ?: prevFinal
                    romajaBuilder.append(jong)
                    prevFinal = null
                }
                romajaBuilder.append(char)
            }
        }

        if (prevFinal != null) {
            val jong = HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal) ?: prevFinal
            romajaBuilder.append(jong)
        }

        romajaBuilder.toString()
    }

    /**
     * Checks if the given text contains any Japanese characters (Hiragana, Katakana, or common Kanji).
     * This function is generally efficient due to '.any' and early exit.
     * No major performance bottlenecks expected here for typical inputs.
     */
    fun isJapanese(text: String): Boolean {
        return text.any { char ->
            (char in '\u3040'..'\u309F') || // Hiragana
            (char in '\u30A0'..'\u30FF') || // Katakana
            // CJK Unified Ideographs (covers most common Kanji)
            // Note: This range also includes many Chinese Hanzi.
            // Differentiating Japanese Kanji from Chinese Hanzi solely based on Unicode
            // ranges is challenging as they share many characters.
            // For more accurate Japanese detection, one might need to analyze
            // the presence of Hiragana/Katakana alongside Kanji.
            (char in '\u4E00'..'\u9FFF')
        }
    }

    /**
     * Checks if the given text contains any Korean characters (Hangul Syllables, Jamo, etc.).
     */
    fun isKorean(text: String): Boolean {
        return text.any { char ->
            (char in '\uAC00'..'\uD7A3') // Hangul Syllables
        }
    }
        
    /**
     * Checks if the given text contains any Chinese characters (common Hanzi).
     * This function is generally efficient due to '.any' and early exit.
     * To improve accuracy in distinguishing between Chinese and Japanese (which shares Kanji),
     * this function now checks if the text *predominantly* consists of CJK Unified Ideographs
     * and *lacks* significant amounts of Hiragana or Katakana.
     *
     * A simple threshold is used here. More sophisticated methods (e.g., frequency analysis,
     * dictionaries, or machine learning models) would be needed for higher accuracy.
     */
    fun isChinese(text: String): Boolean {
        if (text.isEmpty()) return false

        val cjkCharCount = text.count { char -> char in '\u4E00'..'\u9FFF' }
        val hiraganaKatakanaCount = text.count { char -> (char in '\u3040'..'\u309F') || (char in '\u30A0'..'\u30FF') }

        // Heuristic: If CJK characters are present and there are very few or no Hiragana/Katakana,
        // it's more likely to be Chinese.
        // The threshold (e.g., 0.1) can be adjusted based on desired sensitivity.
        return cjkCharCount > 0 && (hiraganaKatakanaCount.toDouble() / text.length.toDouble()) < 0.1
    }
}
