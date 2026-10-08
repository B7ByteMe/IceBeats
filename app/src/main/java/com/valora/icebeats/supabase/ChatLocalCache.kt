package com.valora.icebeats.supabase

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cache Lokal Persisten untuk Riwayat Chat & Daftar Percakapan IceBeats.
 * Menjamin obrolan dan pesan TIDAK PERNAH hilang meskipun aplikasi ditutup lama atau offline.
 */
object ChatLocalCache {
    private const val PREFS_NAME = "icebeats_chat_cache"
    private const val KEY_PREFIX_MESSAGES = "cache_msgs_"
    private const val KEY_PREFIX_CONVERSATIONS = "cache_convs_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Simpan seluruh daftar percakapan untuk pengguna tertentu
     */
    fun saveConversations(context: Context, userId: String, conversations: List<ChatConversation>) {
        if (userId.isBlank() || conversations.isEmpty()) return
        runCatching {
            val jsonArr = JSONArray()
            conversations.forEach { conv ->
                val obj = JSONObject().apply {
                    put("id", conv.id)
                    put("last_message", conv.lastMessage)
                    put("unread_count", conv.unreadCount)

                    val userObj = JSONObject().apply {
                        put("id", conv.otherUser.id)
                        put("name", conv.otherUser.name)
                        put("profile_url", conv.otherUser.profileUrl.orEmpty())
                        put("total_listen_ms", conv.otherUser.totalListenMs)
                        put("border_style", conv.otherUser.borderStyle.orEmpty())
                        put("verification_badge", conv.otherUser.verificationBadge.orEmpty())
                        put("banner_url", conv.otherUser.bannerUrl.orEmpty())
                    }
                    put("other_user", userObj)
                }
                jsonArr.put(obj)
            }
            getPrefs(context).edit().putString(KEY_PREFIX_CONVERSATIONS + userId, jsonArr.toString()).apply()
        }
    }

    /**
     * Dapatkan daftar percakapan tersimpan dari cache lokal
     */
    fun getConversations(context: Context, userId: String): List<ChatConversation> {
        if (userId.isBlank()) return emptyList()
        return runCatching {
            val raw = getPrefs(context).getString(KEY_PREFIX_CONVERSATIONS + userId, null) ?: return emptyList()
            val jsonArr = JSONArray(raw)
            val list = mutableListOf<ChatConversation>()
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val lastMsg = obj.optString("last_message")
                val unread = obj.optInt("unread_count", 0)

                val uObj = obj.optJSONObject("other_user") ?: JSONObject()
                val uid = uObj.optString("id")
                val name = uObj.optString("name", "User")
                val profileUrl = uObj.optString("profile_url").takeIf { it.isNotBlank() }
                val totalMs = uObj.optLong("total_listen_ms", 0L)
                val hours = (totalMs / (1000 * 3600)).toInt()
                val rank = if (hours >= 1) com.valora.icebeats.ui.component.icebeatsRank.fromHours(hours) else null
                val bStyle = uObj.optString("border_style").takeIf { it.isNotBlank() }
                val vBadge = uObj.optString("verification_badge").takeIf { it.isNotBlank() }
                val bannerUrl = uObj.optString("banner_url").takeIf { it.isNotBlank() }

                val otherUser = ChatUser(
                    id = uid,
                    name = name,
                    profileUrl = profileUrl,
                    totalListenMs = totalMs,
                    rank = rank,
                    borderStyle = bStyle,
                    verificationBadge = vBadge,
                    bannerUrl = bannerUrl
                )

                list.add(
                    ChatConversation(
                        id = id,
                        otherUser = otherUser,
                        lastMessage = lastMsg,
                        unreadCount = unread
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    /**
     * Simpan seluruh daftar riwayat pesan untuk percakapan tertentu
     */
    fun saveMessages(context: Context, conversationId: String, messages: List<ChatMessage>) {
        if (conversationId.isBlank() || messages.isEmpty()) return
        runCatching {
            val jsonArr = JSONArray()
            messages.forEach { msg ->
                val obj = JSONObject().apply {
                    put("id", msg.id)
                    put("conversation_id", msg.conversationId)
                    put("sender_id", msg.senderId)
                    put("receiver_id", msg.receiverId)
                    put("message_type", msg.messageType)
                    put("content", msg.content)
                    put("is_read", msg.isRead)
                    put("created_at", msg.createdAt)

                    if (msg.mediaData != null) {
                        val mObj = JSONObject().apply {
                            put("song_id", msg.mediaData.songId)
                            put("title", msg.mediaData.title)
                            put("artist_name", msg.mediaData.artistName)
                            put("album_name", msg.mediaData.albumName.orEmpty())
                            put("thumbnail_url", msg.mediaData.thumbnailUrl.orEmpty())
                            put("duration", msg.mediaData.duration)
                        }
                        put("media_data", mObj)
                    }
                }
                jsonArr.put(obj)
            }
            getPrefs(context).edit().putString(KEY_PREFIX_MESSAGES + conversationId, jsonArr.toString()).apply()
        }
    }

    /**
     * Dapatkan daftar riwayat pesan tersimpan dari cache lokal
     */
    fun getMessages(context: Context, conversationId: String): List<ChatMessage> {
        if (conversationId.isBlank()) return emptyList()
        return runCatching {
            val raw = getPrefs(context).getString(KEY_PREFIX_MESSAGES + conversationId, null) ?: return emptyList()
            val jsonArr = JSONArray(raw)
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val convId = obj.optString("conversation_id")
                val senderId = obj.optString("sender_id")
                val receiverId = obj.optString("receiver_id")
                val type = obj.optString("message_type", "text")
                val content = obj.optString("content")
                val isRead = obj.optBoolean("is_read", false)
                val createdAt = obj.optString("created_at")

                var mediaData: ChatSharedMedia? = null
                val mObj = obj.optJSONObject("media_data")
                if (mObj != null) {
                    mediaData = ChatSharedMedia(
                        songId = mObj.optString("song_id"),
                        title = mObj.optString("title"),
                        artistName = mObj.optString("artist_name"),
                        albumName = mObj.optString("album_name").takeIf { it.isNotBlank() },
                        thumbnailUrl = mObj.optString("thumbnail_url").takeIf { it.isNotBlank() },
                        duration = mObj.optInt("duration", 0)
                    )
                }

                list.add(
                    ChatMessage(
                        id = id,
                        conversationId = convId,
                        senderId = senderId,
                        receiverId = receiverId,
                        messageType = type,
                        content = content,
                        mediaData = mediaData,
                        isRead = isRead,
                        createdAt = createdAt
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    /**
     * Tambahkan satu pesan ke cache pesan lokal
     */
    fun appendMessage(context: Context, conversationId: String, message: ChatMessage) {
        if (conversationId.isBlank()) return
        val current = getMessages(context, conversationId).toMutableList()
        if (current.none { it.id == message.id }) {
            current.add(message)
            saveMessages(context, conversationId, current)
        }
    }
}
