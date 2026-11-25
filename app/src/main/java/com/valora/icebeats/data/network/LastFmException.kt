package com.valora.icebeats.data.network

import java.io.IOException

class LastFmException(
    override val message: String,
    val errorCode: Int? = null,
    cause: Throwable? = null,
) : IOException(message, cause)

object LastFmErrors {

    private val FRIENDLY_MESSAGES = mapOf(
        2 to "Service unavailable — Last.fm is experiencing issues.",
        3 to "Invalid method — the requested operation does not exist.",
        4 to "Authentication failed — please check your credentials.",
        6 to "Invalid parameters — please verify the provided details.",
        7 to "Invalid resource specified.",
        8 to "There was a temporary problem contacting Last.fm.",
        9 to "Invalid session key — please reconnect your Last.fm account.",
        10 to "Invalid API key — please check your Last.fm API credentials.",
        11 to "Service offline — Last.fm is temporarily down for maintenance.",
        13 to "Invalid signature — please check your API secret.",
        14 to "Unauthorized token — web authentication was cancelled or expired.",
        16 to "There was a temporary error — please try again.",
        26 to "Suspended API key — contact Last.fm support.",
        29 to "Rate limit exceeded — slowing down requests to Last.fm.",
    )

    fun friendlyMessage(code: Int?, rawMessage: String?): String {
        if (code != null) {
            val friendly = FRIENDLY_MESSAGES[code]
            if (friendly != null) return friendly
        }
        if (!rawMessage.isNullOrBlank()) return rawMessage
        return if (code != null) "Last.fm error $code" else "Could not reach Last.fm"
    }
}
