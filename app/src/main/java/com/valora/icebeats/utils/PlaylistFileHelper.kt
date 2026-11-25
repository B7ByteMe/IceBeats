package com.valora.icebeats.utils

import android.content.Context
import android.net.Uri
import com.valora.icebeats.db.MusicDatabase
import com.valora.icebeats.db.entities.PlaylistEntity
import com.valora.icebeats.db.entities.PlaylistSongMap
import com.valora.icebeats.db.entities.Song
import com.valora.icebeats.models.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object PlaylistFileHelper {
    private const val TAG = "PlaylistFileHelper"

    /**
     * Serializes a playlist and its songs (as MediaMetadata) to structured JSON text.
     */
    fun exportPlaylistToJson(playlistName: String, songs: List<MediaMetadata>): String {
        val root = JSONObject().apply {
            put("app", "IceBeats")
            put("version", 1)
            put("playlistName", playlistName)
            put("exportedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            put("songCount", songs.size)

            val songsArray = JSONArray()
            for (song in songs) {
                val songObj = JSONObject().apply {
                    put("id", song.id)
                    put("title", song.title)
                    put("duration", song.duration)
                    if (song.thumbnailUrl != null) {
                        put("thumbnailUrl", song.thumbnailUrl)
                    }
                    if (song.album != null) {
                        put("album", song.album.title)
                    }
                    val artistsArray = JSONArray()
                    for (artist in song.artists) {
                        artistsArray.put(artist.name)
                    }
                    put("artists", artistsArray)
                }
                songsArray.put(songObj)
            }
            put("songs", songsArray)
        }
        return root.toString(2)
    }

    /**
     * Overload for Room entity [Song] list.
     */
    @JvmName("exportSongPlaylistToJson")
    fun exportPlaylistToJson(playlistName: String, songs: List<Song>): String =
        exportPlaylistToJson(playlistName, songs.map { song ->
            MediaMetadata(
                id = song.id,
                title = song.title,
                artists = song.artists.map { MediaMetadata.Artist(id = it.id, name = it.name) },
                duration = song.duration,
                thumbnailUrl = song.thumbnailUrl,
                album = song.album?.let { MediaMetadata.Album(id = it.id, title = it.title) }
            )
        })

    /**
     * Writes playlist JSON text directly to an OutputStream for MediaMetadata list.
     */
    fun exportPlaylistToUri(
        context: Context,
        uri: Uri,
        playlistName: String,
        mediaList: List<MediaMetadata>
    ): Result<Unit> {
        return runCatching {
            val jsonText = exportPlaylistToJson(playlistName, mediaList)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonText.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            } ?: error("Failed to open output stream for uri: $uri")
        }
    }

    /**
     * Writes playlist JSON text directly to an OutputStream for Song list.
     */
    @JvmName("exportSongPlaylistToUri")
    fun exportPlaylistToUri(
        context: Context,
        uri: Uri,
        playlistName: String,
        songs: List<Song>
    ): Result<Unit> {
        return runCatching {
            val jsonText = exportPlaylistToJson(playlistName, songs)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonText.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            } ?: error("Failed to open output stream for uri: $uri")
        }
    }

    /**
     * Imports a playlist from a string (JSON or fallback line-based) and persists it into [database].
     * Returns a [Result] containing (Playlist Name, Songs Count).
     */
    fun importPlaylistFromString(content: String, database: MusicDatabase): Result<Pair<String, Int>> {
        return runCatching {
            val trimmed = content.trim()
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                importFromJsonString(trimmed, database)
            } else {
                importFromTextLines(trimmed, database)
            }
        }
    }

    /**
     * Imports a playlist directly from a file Uri.
     */
    fun importPlaylistFromUri(context: Context, uri: Uri, database: MusicDatabase): Result<Pair<String, Int>> {
        return runCatching {
            val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).readText()
            } ?: error("Could not open input stream for uri: $uri")

            importPlaylistFromString(content, database).getOrThrow()
        }
    }

    private fun importFromJsonString(jsonStr: String, database: MusicDatabase): Pair<String, Int> {
        var playlistName = "Imported Playlist"
        val songsList = mutableListOf<MediaMetadata>()

        if (jsonStr.startsWith("{")) {
            val root = JSONObject(jsonStr)
            playlistName = root.optString("playlistName").takeIf { it.isNotBlank() } ?: "Imported Playlist"

            val songsArray = root.optJSONArray("songs")
            if (songsArray != null) {
                parseSongsArray(songsArray, songsList)
            }
        } else if (jsonStr.startsWith("[")) {
            val array = JSONArray(jsonStr)
            parseSongsArray(array, songsList)
        }

        if (songsList.isEmpty()) {
            error("No songs found in the playlist file")
        }

        return saveToDatabase(playlistName, songsList, database)
    }

    private fun parseSongsArray(songsArray: JSONArray, destination: MutableList<MediaMetadata>) {
        for (i in 0 until songsArray.length()) {
            val obj = songsArray.optJSONObject(i) ?: continue
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
            val title = obj.optString("title").takeIf { it.isNotBlank() } ?: "Unknown Title"
            val duration = obj.optInt("duration", 0)
            val thumb = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() }

            val artists = mutableListOf<MediaMetadata.Artist>()
            val artistsArray = obj.optJSONArray("artists")
            if (artistsArray != null) {
                for (j in 0 until artistsArray.length()) {
                    val name = artistsArray.optString(j)
                    if (name.isNotBlank()) {
                        artists.add(MediaMetadata.Artist(id = null, name = name))
                    }
                }
            }

            destination.add(
                MediaMetadata(
                    id = id,
                    title = title,
                    artists = artists,
                    duration = duration,
                    thumbnailUrl = thumb
                )
            )
        }
    }

    /**
     * Fallback parser for plain text / M3U lists containing YouTube IDs or URLs.
     */
    private fun importFromTextLines(text: String, database: MusicDatabase): Pair<String, Int> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }
        val songIds = mutableListOf<String>()

        for (line in lines) {
            val id = extractYouTubeId(line)
            if (id != null) {
                songIds.add(id)
            }
        }

        if (songIds.isEmpty()) {
            error("No valid song IDs found in the file")
        }

        val songsList = songIds.distinct().map { id ->
            MediaMetadata(
                id = id,
                title = "Track $id",
                artists = emptyList(),
                duration = 0,
                thumbnailUrl = null
            )
        }

        return saveToDatabase("Imported Playlist", songsList, database)
    }

    private fun extractYouTubeId(text: String): String? {
        val clean = text.trim()
        if (clean.length == 11 && clean.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
            return clean
        }
        val match = Regex("(?:v=|youtu\\.be/|embed/|shorts/)([a-zA-Z0-9_-]{11})").find(clean)
        return match?.groupValues?.getOrNull(1)
    }

    private fun saveToDatabase(
        playlistName: String,
        songsList: List<MediaMetadata>,
        database: MusicDatabase
    ): Pair<String, Int> {
        val playlistId = PlaylistEntity.generatePlaylistId()
        val newPlaylist = PlaylistEntity(
            id = playlistId,
            name = playlistName,
            bookmarkedAt = LocalDateTime.now(),
            isEditable = true,
            lastUpdateTime = LocalDateTime.now()
        )

        // Insert synchronously so the playlist and songs exist immediately in Room DB
        database.insert(newPlaylist)

        for ((index, media) in songsList.withIndex()) {
            database.insert(media)
            database.insert(
                PlaylistSongMap(
                    songId = media.id,
                    playlistId = playlistId,
                    position = index
                )
            )
        }

        Timber.tag(TAG).d("Successfully imported playlist '${newPlaylist.name}' with ${songsList.size} tracks into library")
        return newPlaylist.name to songsList.size
    }
}
