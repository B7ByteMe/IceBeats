package com.valora.icebeats.ui.screens.settings

import android.app.Application
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valora.icebeats.data.local.ScrobblerPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class InstalledAppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val isKnownMusicPlayer: Boolean,
)

private val KNOWN_MUSIC_PACKAGES = setOf(
    "com.spotify.music",
    "com.google.android.apps.youtube.music",
    "com.google.android.youtube",
    "com.apple.android.music",
    "deezer.android.app",
    "com.aspiro.tidal",
    "com.amazon.mp3",
    "com.soundcloud.android",
    "com.pandora.android",
    "com.jio.media.jiobeats",
    "wynk.com.airtel",
    "com.gaana",
    "com.jiosaavn.android",
    "com.maxmpz.audioplayer",
    "com.tbig.playerpro",
    "com.simplemobiletools.musicplayer",
    "org.videolan.vlc",
    "com.frolo.muse",
    "com.github.andreyasadchy.xtra",
    "in.krosbits.musicolet",
    "com.bsplayer.bspandroid.free",
    "com.miui.player",
    "com.samsung.android.app.music.chn",
    "com.sec.android.app.music",
)

@HiltViewModel
class ScrobblerAppsViewModel @Inject constructor(
    private val application: Application,
    private val scrobblerPreferences: ScrobblerPreferences,
) : ViewModel() {

    private val _allApps = MutableStateFlow<List<InstalledAppEntry>>(emptyList())
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val selectedPackages: StateFlow<Set<String>> = scrobblerPreferences.settings
        .map { it.selectedPackages }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val apps: StateFlow<List<InstalledAppEntry>> = combine(_allApps, _query, selectedPackages) { all, q, selected ->
        val filtered = if (q.isBlank()) all else all.filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
        filtered.sortedWith(
            compareByDescending<InstalledAppEntry> { it.packageName in selected }
                .thenByDescending { it.isKnownMusicPlayer }
                .thenBy { it.label.lowercase() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        loadApps()
    }

    fun setQuery(q: String) { _query.value = q }

    private fun loadApps() {
        viewModelScope.launch {
            _loading.value = true
            val list = withContext(Dispatchers.IO) {
                try {
                    val pm = application.packageManager
                    pm.getInstalledApplications(0)
                        .asSequence()
                        .filter { it.packageName != application.packageName }
                        .filter {
                            runCatching { pm.getLaunchIntentForPackage(it.packageName) }.getOrNull() != null ||
                                (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                        }
                        .distinctBy { it.packageName }
                        .map { info ->
                            val icon = runCatching { pm.getApplicationIcon(info) }.getOrNull()?.let { drawableToBitmap(it) }
                            InstalledAppEntry(
                                packageName = info.packageName,
                                label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName),
                                icon = icon,
                                isKnownMusicPlayer = info.packageName in KNOWN_MUSIC_PACKAGES,
                            )
                        }
                        .sortedBy { it.label.lowercase() }
                        .toList()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    android.util.Log.e("ScrobblerApps", "Could not enumerate installed apps", error)
                    emptyList()
                }
            }
            _allApps.value = list
            _loading.value = false
        }
    }

    private fun drawableToBitmap(drawable: Drawable): ImageBitmap? = runCatching {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    }.getOrNull()

    fun toggle(packageName: String) {
        viewModelScope.launch {
            scrobblerPreferences.togglePackage(packageName)
        }
    }

    fun selectAllDetectedMusicPlayers() {
        viewModelScope.launch {
            val detected = _allApps.value.filter { it.isKnownMusicPlayer }.map { it.packageName }.toSet()
            if (detected.isEmpty()) return@launch
            val current = selectedPackages.value
            scrobblerPreferences.setSelectedPackages(current + detected)
        }
    }
}
