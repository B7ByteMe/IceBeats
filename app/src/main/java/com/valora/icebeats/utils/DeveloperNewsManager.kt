package com.valora.icebeats.utils

import android.content.Context
import android.content.SharedPreferences
import com.valora.icebeats.models.DeveloperNewsItem
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

object DeveloperNewsManager {
    private const val PREFS_NAME = "airbeats_developer_news"
    private const val KEY_CACHED_NEWS = "cached_news_json"
    private const val KEY_DISMISSED_POPUPS = "dismissed_popup_ids"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefs: SharedPreferences? = null

    private val _newsList = MutableStateFlow<List<DeveloperNewsItem>>(emptyList())
    val newsList: StateFlow<List<DeveloperNewsItem>> = _newsList.asStateFlow()

    private val _activePopup = MutableStateFlow<DeveloperNewsItem?>(null)
    val activePopup: StateFlow<DeveloperNewsItem?> = _activePopup.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun init(context: Context) {
        val appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Load cached announcements for instant offline display
        loadCachedNews()

        // Trigger background fetch from Firebase Realtime Database
        refresh()
    }

    fun refresh() {
        scope.launch {
            _isSyncing.value = true
            try {
                fetchFromRestApi()
            } catch (e: Exception) {
                Timber.e(e, "DeveloperNewsManager: Failed to refresh news from RTDB")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private suspend fun fetchFromRestApi() = withContext(Dispatchers.IO) {
        try {
            val app = runCatching { FirebaseApp.getInstance() }.getOrNull() ?: return@withContext
            val options = app.options
            val projectId = options.projectId?.takeIf { it.isNotBlank() } ?: "airbeats-54c06"
            val baseUrl = (options.databaseUrl?.takeIf { it.isNotBlank() } ?: "https://$projectId-default-rtdb.firebaseio.com").trimEnd('/')

            val directUrl = "$baseUrl/developer_news.json"
            Timber.d("DeveloperNewsManager: Fetching news from $directUrl")

            val request = Request.Builder()
                .url(directUrl)
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            var fetchedSuccessfully = false
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    if (body.isNotBlank() && body != "null" && !body.contains("\"error\"")) {
                        parseNewsJson(body)
                        fetchedSuccessfully = true
                    }
                } else {
                    Timber.w("DeveloperNewsManager: Direct news fetch failed with code ${response.code}")
                }
            }

            if (fetchedSuccessfully) return@withContext

            // Fallback with Firebase Auth token if direct read fails
            var token: String? = null
            try {
                val auth = FirebaseAuth.getInstance()
                val user = auth.currentUser ?: auth.signInAnonymously().await().user
                token = user?.getIdToken(false)?.await()?.token
            } catch (e: Exception) {
                Timber.d("DeveloperNewsManager: Firebase anonymous auth skipped: ${e.message}")
            }

            if (!token.isNullOrBlank()) {
                val authUrl = "$baseUrl/developer_news.json?auth=$token"
                val authRequest = Request.Builder()
                    .url(authUrl)
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()

                httpClient.newCall(authRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        if (body.isNotBlank() && body != "null" && !body.contains("\"error\"")) {
                            parseNewsJson(body)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "DeveloperNewsManager: Error fetching news from Firebase")
        }
    }

    private fun parseNewsJson(jsonString: String) {
        try {
            val root = JSONObject(jsonString)
            val items = mutableListOf<DeveloperNewsItem>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = root.optJSONObject(key) ?: continue
                val item = DeveloperNewsItem(
                    id = obj.optString("id", key),
                    title = obj.optString("title", ""),
                    message = obj.optString("message", ""),
                    author = obj.optString("author", "").takeIf { it.isNotBlank() }
                        ?: obj.optString("authorName", "").takeIf { it.isNotBlank() },
                    imageUrl = obj.optString("imageUrl", "").takeIf { it.isNotBlank() },
                    actionUrl = obj.optString("actionUrl", "").takeIf { it.isNotBlank() },
                    actionText = obj.optString("actionText", "").takeIf { it.isNotBlank() },
                    tag = obj.optString("tag", "").takeIf { it.isNotBlank() },
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    showPopup = obj.optBoolean("showPopup", false),
                    priority = obj.optInt("priority", 0),
                    active = obj.optBoolean("active", true)
                )
                if (item.active) {
                    items.add(item)
                }
            }
            updateNews(items)
            cacheNewsJson(jsonString)
        } catch (e: Exception) {
            Timber.e(e, "DeveloperNewsManager: Error parsing REST JSON")
        }
    }

    private fun updateNews(items: List<DeveloperNewsItem>) {
        val sorted = items.sortedWith(
            compareByDescending<DeveloperNewsItem> { it.priority }
                .thenByDescending { it.timestamp }
        )
        _newsList.value = sorted
        recalculateActivePopup(sorted)
    }

    private fun recalculateActivePopup(items: List<DeveloperNewsItem>) {
        val dismissed = getDismissedPopupIds().toMutableSet()
        val currentPopup = _activePopup.value

        // If user is currently viewing a popup that got dismissed, clear it
        if (currentPopup != null && dismissed.contains(currentPopup.id)) {
            _activePopup.value = null
        }

        // Find the single highest priority / latest candidate popup
        val candidate = items.firstOrNull { it.showPopup && it.id.isNotBlank() && !dismissed.contains(it.id) }

        if (candidate != null) {
            // Only show this single highest priority/latest popup.
            // Mark all other items with showPopup as dismissed so they do not chain-pop one by one!
            var changed = false
            items.forEach { item ->
                if (item.id != candidate.id && item.id.isNotBlank() && !dismissed.contains(item.id)) {
                    dismissed.add(item.id)
                    changed = true
                }
            }
            if (changed) {
                prefs?.edit()?.putStringSet(KEY_DISMISSED_POPUPS, dismissed)?.apply()
            }

            // Only replace current popup if none is showing or candidate has strictly higher priority
            if (_activePopup.value == null || candidate.priority > (_activePopup.value?.priority ?: 0)) {
                _activePopup.value = candidate
            }
        } else {
            _activePopup.value = null
        }
    }

    fun dismissPopup(newsId: String) {
        if (newsId.isBlank()) return
        val dismissed = getDismissedPopupIds().toMutableSet()
        dismissed.add(newsId)
        // Mark all current items as dismissed to guarantee no subsequent popups appear
        _newsList.value.forEach { item ->
            if (item.id.isNotBlank()) {
                dismissed.add(item.id)
            }
        }
        prefs?.edit()?.putStringSet(KEY_DISMISSED_POPUPS, dismissed)?.apply()
        _activePopup.value = null
    }

    private fun getDismissedPopupIds(): Set<String> {
        return prefs?.getStringSet(KEY_DISMISSED_POPUPS, emptySet()) ?: emptySet()
    }

    private fun cacheNewsJson(jsonString: String) {
        prefs?.edit()?.putString(KEY_CACHED_NEWS, jsonString)?.apply()
    }

    private fun loadCachedNews() {
        val cached = prefs?.getString(KEY_CACHED_NEWS, null)
        if (!cached.isNullOrBlank()) {
            parseNewsJson(cached)
        }
    }
}
