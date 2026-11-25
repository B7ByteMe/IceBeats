package com.valora.icebeats.ui.sharedmusic

import com.valora.icebeats.providers.ProviderSong
import com.valora.icebeats.songs.IdentifiedSong
import com.valora.icebeats.usecases.IdentificationStep

sealed class SharedMusicUiState {
    data object Idle : SharedMusicUiState()
    data class Processing(val step: IdentificationStep) : SharedMusicUiState()
    data class Success(
        val identifiedSong: IdentifiedSong,
        val airBeatsMatch: ProviderSong?,
        val candidates: List<ProviderSong>
    ) : SharedMusicUiState()
    data object NoMusicFound : SharedMusicUiState()
    data object NoAudio : SharedMusicUiState()
    data class UnsupportedMedia(val message: String) : SharedMusicUiState()
    data class UrlShared(val url: String) : SharedMusicUiState()
    data object NetworkError : SharedMusicUiState()
    data class Error(val message: String) : SharedMusicUiState()
}
