package com.valora.icebeats

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import com.valora.icebeats.utils.AutoBackupManager
import android.widget.Toast
import com.valora.icebeats.ui.component.LocaleAwareApplication
import com.valora.icebeats.utils.dataStore
import com.valora.icebeats.utils.initializeCache
import android.widget.Toast.LENGTH_SHORT
import androidx.datastore.preferences.core.edit
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.request.CachePolicy
import com.valora.icebeats.innertube.YouTube
import com.valora.icebeats.innertube.models.YouTubeLocale
import com.valora.icebeats.kugou.KuGou
import com.valora.icebeats.constants.AccountChannelHandleKey
import com.valora.icebeats.constants.AccountEmailKey
import com.valora.icebeats.constants.AccountNameKey
import com.valora.icebeats.constants.ContentCountryKey
import com.valora.icebeats.constants.ContentLanguageKey
import com.valora.icebeats.constants.CountryCodeToName
import com.valora.icebeats.constants.DataSyncIdKey
import com.valora.icebeats.constants.InnerTubeCookieKey
import com.valora.icebeats.constants.LanguageCodeToName
import com.valora.icebeats.constants.MaxImageCacheSizeKey
import com.valora.icebeats.constants.ProxyEnabledKey
import com.valora.icebeats.constants.ProxyTypeKey
import com.valora.icebeats.constants.ProxyUrlKey
import com.valora.icebeats.constants.SYSTEM_DEFAULT
import com.valora.icebeats.constants.SpotifyCookieKey
import com.valora.icebeats.constants.UseLoginForBrowse
import com.valora.icebeats.constants.VisitorDataKey
import com.valora.icebeats.db.MusicDatabase
import com.valora.icebeats.extensions.toEnum
import com.valora.icebeats.extensions.toInetSocketAddress
import com.valora.icebeats.extensions.tryOrNull
import com.valora.icebeats.ui.component.NamePreferenceManager
import com.valora.icebeats.utils.IceBeatsStatsCloudSync
import com.valora.icebeats.utils.dataStore
import com.valora.icebeats.utils.get
import com.valora.icebeats.utils.reportException
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.Proxy
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class App : LocaleAwareApplication(), ImageLoaderFactory {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var namePreferenceManager: NamePreferenceManager

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this
        // Prevent SQLiteBlobTooBigException (default Android CursorWindow is only 2MB)
        runCatching {
            val field = android.database.CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
            field.isAccessible = true
            field.set(null, 50 * 1024 * 1024)
        }
        com.valora.icebeats.utils.RemoteConfigManager.initialize(this)
        kotlinx.coroutines.runBlocking {
            runCatching { dataStore.initializeCache() }
        }
        Timber.plant(com.valora.icebeats.utils.GlobalLogTree())

        // Sanitize and heal any corrupted or fragmented playback events
        GlobalScope.launch(Dispatchers.IO) {
            com.valora.icebeats.db.DatabaseSanitizer.sanitizeDatabase(database)
        }

        // Ensure active FCM subscription to personal numeric topic (e.g. "1", "2", "10")
        val assignedUserNum = IceBeatsStatsCloudSync.getUserNumber(this)
        if (!assignedUserNum.isNullOrBlank()) {
            runCatching {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic(assignedUserNum)
            }
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivities = 0
            override fun onActivityStarted(activity: Activity) {
                if (startedActivities == 0) {
                    // App opened or brought to foreground: fetch fresh URLs and developer news from Firebase immediately
                    com.valora.icebeats.utils.RemoteConfigManager.refresh()
                    com.valora.icebeats.utils.DeveloperNewsManager.refresh()

                    // Auto backup on every app open to Documents/IceBeats if enabled
                    GlobalScope.launch(Dispatchers.IO) {
                        tryOrNull {
                            AutoBackupManager.performAutoBackupToStorageIfEnabled(this@App, database)
                        }
                    }
                }
                startedActivities++
            }
            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                if (startedActivities <= 0) {
                    GlobalScope.launch(Dispatchers.IO) {
                        tryOrNull {
                            val success = AutoBackupManager.createAutoBackup(this@App, database)
                            if (success) {
                                val file = AutoBackupManager.getAutoBackupFile(this@App)
                                AutoBackupManager.uploadToCloud(this@App, file)
                            }
                        }
                    }
                }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // Initialize Developer News & Announcements (Firebase Realtime Database)
        try {
            com.valora.icebeats.utils.DeveloperNewsManager.init(this)
        } catch (_: Exception) {}

        try {
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                // Ignore benign Navigation Compose back-stack transition race condition
                val isNavRace = throwable is java.lang.IllegalStateException &&
                    throwable.message?.contains("Cannot transition entry that is not in the back stack") == true
                if (isNavRace) {
                    Timber.w(throwable, "Ignored NavHost back-stack transition race condition")
                    return@setDefaultUncaughtExceptionHandler
                }

                try {
                    val sw = java.io.StringWriter()
                    val pw = java.io.PrintWriter(sw)
                    throwable.printStackTrace(pw)
                    val stack = sw.toString()

                    val intent = android.content.Intent(this@App, com.valora.icebeats.ui.activities.DebugActivity::class.java).apply {
                        putExtra(com.valora.icebeats.ui.activities.DebugActivity.EXTRA_STACK_TRACE, stack)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    startActivity(intent)
                    try { Thread.sleep(500) } catch (_: InterruptedException) {}
                } catch (e: Exception) {
                    Timber.e(e, "UncaughtExceptionHandler failure")
                } finally {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(2)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to register UncaughtExceptionHandler")
        }

        val locale = Locale.getDefault()
        val languageTag = locale.toLanguageTag().replace("-Hant", "") // replace zh-Hant-* to zh-*
        YouTube.locale = YouTubeLocale(
            gl = dataStore[ContentCountryKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.country.takeIf { it in CountryCodeToName }
                ?: "US",
            hl = dataStore[ContentLanguageKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.language.takeIf { it in LanguageCodeToName }
                ?: languageTag.takeIf { it in LanguageCodeToName }
                ?: "en"
        )
        if (languageTag == "zh-TW") {
            KuGou.useTraditionalChinese = true
        }

        if (dataStore[ProxyEnabledKey] == true) {
            try {
                YouTube.proxy = Proxy(
                    dataStore[ProxyTypeKey].toEnum(defaultValue = Proxy.Type.HTTP),
                    dataStore[ProxyUrlKey]!!.toInetSocketAddress()
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to parse proxy url.", LENGTH_SHORT).show()
                reportException(e)
            }
        }

        if (dataStore[UseLoginForBrowse] != false) {
            YouTube.useLoginForBrowse = true
        }

        GlobalScope.launch {
            IceBeatsStatsCloudSync.syncDaily(
                context = this@App,
                database = database,
                namePreferenceManager = namePreferenceManager,
            )?.onFailure(::reportException)
        }
        GlobalScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[SpotifyCookieKey] }
                .distinctUntilChanged()
                .collect { spDc ->
                    if (!spDc.isNullOrBlank() && com.valora.icebeats.spotify.Spotify.accessToken.isNullOrBlank()) {
                        tryOrNull {
                            com.valora.icebeats.spotify.SpotifyAuth.fetchAccessToken(spDc).onSuccess { token ->
                                com.valora.icebeats.spotify.Spotify.accessToken = token.accessToken
                            }
                        }
                    }
                }
        }
        GlobalScope.launch {
            dataStore.data
                .map { it[VisitorDataKey] }
                .distinctUntilChanged()
                .collect { visitorData ->
                    YouTube.visitorData = visitorData
                        ?.takeIf { it != "null" } // Previously visitorData was sometimes saved as "null" due to a bug
                        ?: YouTube.visitorData().onFailure {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@App, "Failed to get visitorData.", LENGTH_SHORT)
                                    .show()
                            }
                            reportException(it)
                        }.getOrNull()?.also { newVisitorData ->
                            dataStore.edit { settings ->
                                settings[VisitorDataKey] = newVisitorData
                            }
                        }
                }
        }
        GlobalScope.launch {
            dataStore.data
                .map { it[DataSyncIdKey] }
                .distinctUntilChanged()
                .collect { dataSyncId ->
                    YouTube.dataSyncId = dataSyncId?.let {
                        /*
                         * Workaround to avoid breaking older installations that have a dataSyncId
                         * that contains "||" in it.
                         * If the dataSyncId ends with "||" and contains only one id, then keep the
                         * id before the "||".
                         * If the dataSyncId contains "||" and is not at the end, then keep the
                         * second id.
                         * This is needed to keep using the same account as before.
                         */
                        it.takeIf { !it.contains("||") }
                            ?: it.takeIf { it.endsWith("||") }?.substringBefore("||")
                            ?: it.substringAfter("||")
                    }
                }
        }
        GlobalScope.launch {
            dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .collect { cookie ->
                    try {
                        YouTube.cookie = cookie
                    } catch (e: Exception) {
                        // we now allow user input now, here be the demons. This serves as a last ditch effort to avoid a crash loop
                        Timber.e("Could not parse cookie. Clearing existing cookie. %s", e.message)
                        forgetAccount(this@App)
                    }
                }
        }
    }

    override fun newImageLoader(): ImageLoader {
        val cacheSize = dataStore[MaxImageCacheSizeKey]

        val builder = ImageLoader.Builder(this)
            .crossfade(true)
            .respectCacheHeaders(false)
            .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)

        if (cacheSize == 0) {
            return builder
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        }

        return builder
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil"))
                    .maxSizeBytes((cacheSize ?: 512) * 1024 * 1024L)
                    .build()
            }
            .build()
    }

    override fun startForegroundService(service: android.content.Intent?): android.content.ComponentName? {
        return try {
            super.startForegroundService(service)
        } catch (e: Exception) {
            Timber.e(e, "ForegroundServiceStartNotAllowedException caught and suppressed in App.startForegroundService")
            null
        }
    }

    companion object {
        lateinit var instance: App
            private set

        fun forgetAccount(context: Context) {
            runBlocking {
                context.dataStore.edit { settings ->
                    settings.remove(InnerTubeCookieKey)
                    settings.remove(VisitorDataKey)
                    settings.remove(DataSyncIdKey)
                    settings.remove(AccountNameKey)
                    settings.remove(AccountEmailKey)
                    settings.remove(AccountChannelHandleKey)
                }
            }
        }
    }
}
