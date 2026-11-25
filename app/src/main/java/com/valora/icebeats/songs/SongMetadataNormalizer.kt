package com.valora.icebeats.songs

import com.valora.icebeats.recognition.RecognitionResult

object SongMetadataNormalizer {

    private val VIDEO_NOISE_REGEX = Regex("(?i)\\[(official (music )?video|lyrics? video|audio|official)\\]")

    fun normalize(result: RecognitionResult): IdentifiedSong {
        val rawTitle = result.title.orEmpty().trim()
        val cleanedTitle = rawTitle.replace(VIDEO_NOISE_REGEX, "").trim()
            .replace("\\s+".toRegex(), " ")

        val rawArtist = result.artist.orEmpty().trim()
            .replace("\\s+".toRegex(), " ")

        val rawAlbum = result.album?.trim()?.takeIf { it.isNotBlank() }

        val identifiers = mutableListOf<String>()
        result.externalId?.takeIf { it.isNotBlank() }?.let { identifiers.add(it) }

        return IdentifiedSong(
            title = if (cleanedTitle.isNotBlank()) cleanedTitle else rawTitle,
            artist = rawArtist.takeIf { it.isNotBlank() },
            album = rawAlbum,
            albumArtUrl = result.albumArtUrl,
            durationMs = result.durationMs,
            identifiers = identifiers
        )
    }
}
