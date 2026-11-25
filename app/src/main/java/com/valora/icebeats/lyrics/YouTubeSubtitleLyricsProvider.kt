package com.valora.icebeats.lyrics

import android.content.Context
import com.valora.icebeats.constants.EnableYouTubeSubtitleLyricsKey
import com.valora.icebeats.innertube.YouTube
import com.valora.icebeats.utils.dataStore
import com.valora.icebeats.utils.get

object YouTubeSubtitleLyricsProvider : LyricsProvider {
    override val name = "YouTube Subtitle"

    override fun isEnabled(context: Context) = context.dataStore[EnableYouTubeSubtitleLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = YouTube.transcript(id)
}
