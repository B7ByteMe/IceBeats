package com.valora.icebeats.recognition

import android.util.LruCache
import com.valora.icebeats.songs.IdentifiedSong
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object RecognitionCache {

    private val memoryCache = LruCache<String, IdentifiedSong>(50)

    fun get(hash: String): IdentifiedSong? {
        return memoryCache.get(hash)
    }

    fun put(hash: String, song: IdentifiedSong) {
        memoryCache.put(hash, song)
    }

    fun computeHash(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            "${file.name}_${file.length()}"
        }
    }
}
