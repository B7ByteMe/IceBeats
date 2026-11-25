package com.valora.icebeats.data.network

import java.security.MessageDigest

/**
 * Last.fm signature generator according to official Last.fm API auth specification:
 * 1. Filter out parameters: format, callback, api_sig
 * 2. Sort parameter keys alphabetically
 * 3. Concatenate name + value pairs with no separator
 * 4. Append API secret
 * 5. Compute MD5 hash and return lowercase hex
 */
object LastFmSigner {

    private val NON_HEX = Regex("[^a-fA-F0-9]")
    private val WHITESPACE = Regex("[\\s\\u00A0\\u200B\\u200C\\u200D\\uFEFF]")

    /**
     * Cleans whitespace and non-hex characters from pasted keys.
     */
    fun normalizeKey(raw: String): String =
        raw.replace(WHITESPACE, "").replace(NON_HEX, "").lowercase()

    private val SKIP = setOf("format", "callback", "api_sig")

    fun sign(params: Map<String, String>, secret: String): String {
        val base = params
            .filterKeys { it !in SKIP }
            .toSortedMap()
            .entries
            .joinToString(separator = "") { (k, v) -> k + v } + secret
        return md5(base)
    }

    fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
