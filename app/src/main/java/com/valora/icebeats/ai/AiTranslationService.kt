package com.valora.icebeats.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

object AiTranslationService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun translate(
        lines: List<String>,
        targetLanguageCode: String,
        targetLanguageName: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        mode: String,
        customPrompt: String? = null,
        provider: String = "OpenRouter",
        maxRetries: Int = 3,
        onLog: ((String) -> Unit)? = null
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) {
            return@withContext Result.failure(Exception("Input lines are empty"))
        }

        val effectiveApiKey = apiKey.trim()
        if (effectiveApiKey.isBlank() && provider != "Custom") {
            return@withContext Result.failure(Exception("API Key is required. Please set it in Settings > AI Integration."))
        }

        val lineCount = lines.size
        val inputText = lines.joinToString("\n")

        onLog?.invoke("Preparing translation for $lineCount lines to $targetLanguageName...")

        // Handle DeepL provider
        if (provider.equals("DeepL", ignoreCase = true)) {
            return@withContext translateWithDeepL(
                lines = lines,
                targetLangCode = targetLanguageCode,
                apiKey = effectiveApiKey,
                onLog = onLog
            )
        }

        // Standard OpenAI-compatible format (OpenRouter, OpenAI, Groq, Gemini, Claude-proxy, Custom)
        val isHinglish = targetLanguageCode == "hi-Latn" || targetLanguageName.contains("Hinglish", ignoreCase = true)

        val systemPrompt = buildString {
            append("You are an expert, award-winning music lyrics translator. Your task is to translate song lyrics line-by-line.\n")
            append("CRITICAL OUTPUT RULES:\n")
            append("1. Output MUST be ONLY a valid JSON array of strings: [\"line 1\", \"line 2\", ...]\n")
            append("2. Output EXACTLY $lineCount items in the array (one output line per input line).\n")
            append("3. If an input line is empty or purely whitespace, the output array item MUST be empty string \"\".\n")
            append("4. Maintain the rhythm, poetic flow, and emotional feeling of the song lyrics.\n")
            append("5. DO NOT wrap with markdown, DO NOT write any explanations, intro or outro text.\n")
            if (isHinglish) {
                append("6. For HINGLISH: You MUST translate into natural, conversational Hindi using ONLY English/Latin alphabet letters (e.g., 'Tum mere paas ho', 'Dil ki dhadkan', 'Zindagi ek safar hai'). NEVER use Devanagari script for Hinglish.\n")
            }
        }

        val userPrompt = buildString {
            if (isHinglish) {
                append("Translate the following $lineCount song lyrics lines into conversational HINGLISH (Hindi written with standard English/Latin alphabet letters).\n")
                append("Make it poetic, singable, and easy to read phonetically. Do not use Devanagari script.\n")
            } else when (mode) {
                "Romanized" -> {
                    append("Romanize/transliterate the following $lineCount lines into basic Latin alphabet characters (a-z, A-Z) without special diacritics.\n")
                }
                "Transcribed" -> {
                    append("Transcribe the phonetic sound of the following $lineCount lines into $targetLanguageName script.\n")
                }
                else -> {
                    append("Translate the following $lineCount song lyrics lines into $targetLanguageName.\n")
                    append("Preserve the rhyme, rhythm, and artistic meaning suitable for singing.\n")
                }
            }

            if (!customPrompt.isNullOrBlank()) {
                append("\nUser Custom Instructions:\n$customPrompt\n")
            }

            append("\nOriginal Lyrics ($lineCount lines):\n")
            append(inputText)
            append("\n\nReturn EXACTLY a JSON array of $lineCount strings.")
        }

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", userPrompt)
            })
        }

        val jsonBody = JSONObject().apply {
            if (model.isNotBlank()) {
                put("model", model.trim())
            }
            put("messages", messages)
            put("temperature", 0.3)
            put("max_tokens", (lineCount * 80).coerceAtLeast(1024))
        }

        val effectiveBaseUrl = resolveEndpoint(provider, baseUrl)

        var attempt = 0
        while (attempt < maxRetries) {
            try {
                onLog?.invoke("Connecting to $provider (Attempt ${attempt + 1})...")

                val requestBuilder = Request.Builder()
                    .url(effectiveBaseUrl)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("HTTP-Referer", "https://github.com/B7ByteMe/IceBeats")
                    .addHeader("X-Title", "IceBeats")

                if (effectiveApiKey.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $effectiveApiKey")
                }

                val request = requestBuilder.post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE)).build()
                val response = client.newCall(request).execute()
                val bodyText = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    if (response.code >= 500) {
                        attempt++
                        delay(1000L * attempt)
                        continue
                    }
                    val err = runCatching {
                        JSONObject(bodyText).optJSONObject("error")?.optString("message")
                    }.getOrNull() ?: "HTTP ${response.code}: ${response.message}"
                    return@withContext Result.failure(Exception("AI request failed: $err"))
                }

                val parsedLines = parseJsonResponse(bodyText, lineCount)
                if (parsedLines != null) {
                    onLog?.invoke("Successfully translated $lineCount lines")
                    return@withContext Result.success(parsedLines)
                } else {
                    onLog?.invoke("Retrying parsing...")
                }
            } catch (e: Exception) {
                Timber.w(e, "AI translation attempt $attempt failed")
                if (attempt == maxRetries - 1) {
                    return@withContext Result.failure(e)
                }
            }
            attempt++
            delay(1000L * attempt)
        }

        Result.failure(Exception("Failed to obtain translation from AI after $maxRetries attempts."))
    }

    private fun resolveEndpoint(provider: String, customBaseUrl: String): String {
        return when (provider) {
            "OpenRouter" -> "https://openrouter.ai/api/v1/chat/completions"
            "OpenAI" -> "https://api.openai.com/v1/chat/completions"
            "Perplexity" -> "https://api.perplexity.ai/chat/completions"
            "Claude" -> "https://api.anthropic.com/v1/messages"
            "Gemini" -> "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
            "XAi" -> "https://api.x.ai/v1/chat/completions"
            "Mistral" -> "https://api.mistral.ai/v1/chat/completions"
            "Nvidia" -> "https://integrate.api.nvidia.com/v1/chat/completions"
            "OrcaRouter" -> "https://api.orcarouter.com/v1/chat/completions"
            "Groq" -> "https://api.groq.com/openai/v1/chat/completions"
            "Puter" -> "https://api.puter.com/v1/chat/completions"
            "DeepL" -> "https://api.deepl.com/v2/translate"
            else -> customBaseUrl.ifBlank { "https://openrouter.ai/api/v1/chat/completions" }
        }
    }

    private fun parseJsonResponse(responseBody: String, expectedCount: Int): List<String>? {
        return runCatching {
            val root = JSONObject(responseBody)
            val choices = root.optJSONArray("choices") ?: return null
            if (choices.length() == 0) return null
            val message = choices.getJSONObject(0).optJSONObject("message") ?: return null
            var content = message.optString("content").trim()

            // Remove markdown code fences if present
            if (content.startsWith("```json")) {
                content = content.removePrefix("```json")
            } else if (content.startsWith("```")) {
                content = content.removePrefix("```")
            }
            if (content.endsWith("```")) {
                content = content.removeSuffix("```")
            }
            content = content.trim()

            // Try direct JSON array parse
            var list: List<String>? = runCatching {
                val array = JSONArray(content)
                (0 until array.length()).map { array.optString(it) }
            }.getOrNull()

            // Fallback: extract substring between '[' and ']'
            if (list == null) {
                val start = content.indexOf('[')
                val end = content.lastIndexOf(']')
                if (start != -1 && end != -1 && end > start) {
                    val sub = content.substring(start, end + 1)
                    list = runCatching {
                        val array = JSONArray(sub)
                        (0 until array.length()).map { array.optString(it) }
                    }.getOrNull()
                }
            }

            // Fallback: lines
            if (list == null) {
                list = content.lines()
                    .filter { it.isNotBlank() }
                    .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
            }

            // Adjust size to match expectedCount exactly
            val mutable = list.toMutableList()
            while (mutable.size < expectedCount) {
                mutable.add("")
            }
            mutable.take(expectedCount)
        }.getOrNull()
    }

    private fun translateWithDeepL(
        lines: List<String>,
        targetLangCode: String,
        apiKey: String,
        onLog: ((String) -> Unit)?
    ): Result<List<String>> {
        return runCatching {
            onLog?.invoke("Contacting DeepL API...")
            val isFree = apiKey.endsWith(":fx")
            val endpoint = if (isFree) "https://api-free.deepl.com/v2/translate" else "https://api.deepl.com/v2/translate"

            val lang = targetLangCode.uppercase().take(2)
            val payload = JSONObject().apply {
                put("text", JSONArray(lines))
                put("target_lang", lang)
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "DeepL-Auth-Key $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("DeepL error ${response.code}: $text")
            }

            val root = JSONObject(text)
            val translations = root.getJSONArray("translations")
            val result = (0 until translations.length()).map {
                translations.getJSONObject(it).getString("text")
            }
            result
        }
    }
}
