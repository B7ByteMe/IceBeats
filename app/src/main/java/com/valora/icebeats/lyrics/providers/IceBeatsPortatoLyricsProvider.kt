package com.valora.icebeats.lyrics.providers

import android.content.Context
import com.valora.icebeats.constants.EnablePortatoLyricsKey
import com.valora.icebeats.lyrics.LyricsProvider
import com.valora.icebeats.utils.dataStore
import com.valora.icebeats.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object IceBeatsPortatoLyricsProvider : LyricsProvider {
    override val name: String = "BetterLyrics (Portato)"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnablePortatoLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val encTitle = URLEncoder.encode(title.trim(), "UTF-8")
            val encArtist = URLEncoder.encode(artist.trim(), "UTF-8")
            val url = "https://lyrics.portato.app/api/v1/lyrics?title=$encTitle&artist=$encArtist"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "IceBeats/1.0")
                .header("Accept", "application/json")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")

            val body = resp.body?.string() ?: throw IllegalStateException("Empty response")
            val json = JSONObject(body)
            val lrc = json.optString("lyrics", json.optString("lrc", "")).trim()
            if (lrc.isBlank()) throw IllegalStateException("No lyrics found on Portato")

            lrc
        }
    }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        getLyrics(id, title, artist, duration).onSuccess(callback)
    }
}
