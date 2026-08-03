package com.valora.icebeats.utils

import com.google.firebase.database.database
import com.google.firebase.Firebase
import com.valora.icebeats.models.MediaMetadata
import kotlinx.coroutines.tasks.await
import java.util.UUID

object ListenTogetherClient {
    private val database = Firebase.database.reference.child("sessions")

    private fun generateCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    suspend fun createSession(
        displayName: String,
        state: ListenTogetherPlaybackState,
    ): ListenTogetherSession {
        val code = generateCode()
        val participantId = UUID.randomUUID().toString()
        
        val hostParticipant = ListenTogetherParticipant(
            id = participantId,
            name = displayName,
            isHost = true
        )
        
        val session = ListenTogetherSession(
            code = code,
            participantId = participantId,
            joinUrl = "https://icebeats.pages.dev/join?code=$code",
            participants = 1,
            participantList = listOf(hostParticipant),
            hostName = displayName,
            controllerId = participantId,
            controllerName = displayName,
            stateVersion = 1L,
            serverNow = System.currentTimeMillis(),
            state = state.copy(updatedAt = System.currentTimeMillis())
        )
        
        database.child(code).setValue(session).await()
        return session
    }

    suspend fun joinSession(
        code: String,
        displayName: String,
    ): ListenTogetherSession {
        val upperCode = code.trim().uppercase()
        val snapshot = database.child(upperCode).get().await()
        if (!snapshot.exists()) throw IllegalStateException("Session not found")
        
        val session = snapshot.getValue(ListenTogetherSession::class.java) 
            ?: throw IllegalStateException("Invalid session data")
            
        val participantId = UUID.randomUUID().toString()
        val newParticipant = ListenTogetherParticipant(
            id = participantId,
            name = displayName,
            isHost = false
        )
        
        val updatedList = session.participantList.toMutableList()
        updatedList.add(newParticipant)
        
        val updatedSession = session.copy(
            participantId = participantId, // local participant ID
            participants = updatedList.size,
            participantList = updatedList
        )
        
        // Save the updated participants to the server
        database.child(upperCode).child("participants").setValue(updatedSession.participants).await()
        database.child(upperCode).child("participantList").setValue(updatedSession.participantList).await()
        
        return updatedSession
    }

    suspend fun getSession(code: String): ListenTogetherSession {
        val snapshot = database.child(code.trim().uppercase()).get().await()
        if (!snapshot.exists()) throw IllegalStateException("Session not found")
        val session = snapshot.getValue(ListenTogetherSession::class.java) 
            ?: throw IllegalStateException("Invalid session data")
        return session.copy(serverNow = System.currentTimeMillis())
    }

    suspend fun updateState(
        code: String,
        participantId: String,
        state: ListenTogetherPlaybackState,
    ): ListenTogetherSession {
        val upperCode = code.trim().uppercase()
        val snapshot = database.child(upperCode).get().await()
        if (!snapshot.exists()) throw IllegalStateException("Session not found")
        
        val session = snapshot.getValue(ListenTogetherSession::class.java) 
            ?: throw IllegalStateException("Invalid session data")
            
        // Check if user is controller
        if (session.controllerId != participantId) {
            return session
        }
        
        val updatedSession = session.copy(
            stateVersion = session.stateVersion + 1,
            state = state.copy(updatedAt = System.currentTimeMillis())
        )
        
        // Update state on firebase
        database.child(upperCode).child("stateVersion").setValue(updatedSession.stateVersion).await()
        database.child(upperCode).child("state").setValue(updatedSession.state).await()
        
        return updatedSession
    }

    suspend fun leaveSession(
        code: String,
        participantId: String,
    ) {
        val upperCode = code.trim().uppercase()
        val snapshot = database.child(upperCode).get().await()
        if (!snapshot.exists()) return
        
        val session = snapshot.getValue(ListenTogetherSession::class.java) ?: return
        
        val updatedList = session.participantList.filter { it.id != participantId }
        
        if (updatedList.isEmpty()) {
            // Delete session if empty
            database.child(upperCode).removeValue().await()
        } else {
            // Update list and participants count
            database.child(upperCode).child("participants").setValue(updatedList.size).await()
            database.child(upperCode).child("participantList").setValue(updatedList).await()
        }
    }
}

// Ensure data classes have default values so Firebase can deserialize them
data class ListenTogetherSession(
    val code: String = "",
    val participantId: String = "",
    val joinUrl: String = "",
    val participants: Int = 1,
    val participantList: List<ListenTogetherParticipant> = emptyList(),
    val hostName: String = "",
    val controllerId: String = "",
    val controllerName: String = "",
    val stateVersion: Long = 0L,
    val serverNow: Long = 0L,
    val state: ListenTogetherPlaybackState? = null,
)

data class ListenTogetherParticipant(
    val id: String = "",
    val name: String = "",
    val isHost: Boolean = false,
)

data class ListenTogetherPlaybackState(
    val songId: String = "",
    val title: String = "",
    val artists: List<String> = emptyList(),
    val thumbnailUrl: String? = null,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val updatedAt: Long = 0L,
) {
    fun toMediaMetadata() =
        MediaMetadata(
            id = songId,
            title = title,
            artists = artists.map { MediaMetadata.Artist(id = null, name = it) },
            duration = -1,
            thumbnailUrl = thumbnailUrl,
        )
}
