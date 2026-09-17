package com.valora.icebeats.supabase

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.valora.icebeats.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SupabaseAuthManager(private val context: Context) {
    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("supabase_access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("supabase_refresh_token")
        private val KEY_USER_ID = stringPreferencesKey("supabase_user_id")
        private val KEY_USER_EMAIL = stringPreferencesKey("supabase_user_email")
        private val KEY_USER_NAME = stringPreferencesKey("supabase_user_name")
        private val KEY_LAST_SYNC_TIME = stringPreferencesKey("supabase_last_sync_time")
        private val KEY_AUTH_PROVIDER = stringPreferencesKey("supabase_auth_provider")

        @Volatile
        private var INSTANCE: SupabaseAuthManager? = null

        fun getInstance(context: Context): SupabaseAuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SupabaseAuthManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userEmail = MutableStateFlow("")
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userId = MutableStateFlow("")
    val userId: StateFlow<String> = _userId.asStateFlow()

    private val _lastSyncTime = MutableStateFlow("")
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()

    private val _authProvider = MutableStateFlow("Email")
    val authProvider: StateFlow<String> = _authProvider.asStateFlow()

    var accessToken: String? = null
        private set

    var refreshToken: String? = null
        private set

    init {
        CoroutineScope(Dispatchers.IO).launch {
            loadSession()
        }
    }

    suspend fun loadSession() {
        val prefs = context.dataStore.data.first()
        val token = prefs[KEY_ACCESS_TOKEN]
        val email = prefs[KEY_USER_EMAIL].orEmpty()
        val name = prefs[KEY_USER_NAME].orEmpty()
        val uid = prefs[KEY_USER_ID].orEmpty()
        val syncTime = prefs[KEY_LAST_SYNC_TIME].orEmpty()
        val provider = prefs[KEY_AUTH_PROVIDER] ?: "Email"

        accessToken = token
        refreshToken = prefs[KEY_REFRESH_TOKEN]

        _userId.value = uid
        _userEmail.value = email
        _userName.value = name
        _lastSyncTime.value = syncTime
        _authProvider.value = provider
        _isLoggedIn.value = !token.isNullOrBlank() && email.isNotBlank()
    }

    suspend fun saveSession(
        token: String,
        refreshTokenStr: String?,
        uid: String,
        email: String,
        name: String,
        provider: String = "Email"
    ) {
        accessToken = token
        refreshToken = refreshTokenStr
        _userId.value = uid
        _userEmail.value = email
        _userName.value = name
        _authProvider.value = provider
        _isLoggedIn.value = true

        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = token
            if (refreshTokenStr != null) prefs[KEY_REFRESH_TOKEN] = refreshTokenStr
            prefs[KEY_USER_ID] = uid
            prefs[KEY_USER_EMAIL] = email
            prefs[KEY_USER_NAME] = name
            prefs[KEY_AUTH_PROVIDER] = provider
        }
    }

    suspend fun updateLastSyncTime(formattedTime: String) {
        _lastSyncTime.value = formattedTime
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_SYNC_TIME] = formattedTime
        }
    }

    suspend fun clearSession() {
        accessToken = null
        refreshToken = null
        _userId.value = ""
        _userEmail.value = ""
        _userName.value = ""
        _authProvider.value = "Email"
        _isLoggedIn.value = false

        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_USER_EMAIL)
            prefs.remove(KEY_USER_NAME)
            prefs.remove(KEY_AUTH_PROVIDER)
        }
    }
}
