package com.valora.icebeats.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.valora.icebeats.BuildConfig
import com.valora.icebeats.db.InternalDatabase
import com.valora.icebeats.db.MusicDatabase
import com.valora.icebeats.extensions.div
import com.valora.icebeats.extensions.tryOrNull
import com.valora.icebeats.extensions.zipInputStream
import com.valora.icebeats.extensions.zipOutputStream
import com.valora.icebeats.playback.MusicService
import com.valora.icebeats.playback.MusicService.Companion.PERSISTENT_QUEUE_FILE
import com.valora.icebeats.ui.component.NamePreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import kotlin.system.exitProcess

object AutoBackupManager {

    const val BACKUP_FILENAME = "icebeats_backup.backup"
    private const val PREFS_NAME = "auto_backup_state"
    const val KEY_LAST_BACKUP_TIME = "last_android_os_backup_time"
    private const val KEY_LAST_RESTORED_SIG = "last_restored_sig"
    private const val KEY_RESTART_ATTEMPTS = "restart_attempts"

    const val SETTINGS_FILENAME = "settings.preferences_pb"
    const val USER_NAME_PREFS_FILENAME = "user_name_preferences.preferences_pb"
    const val GOOGLE_ACCOUNT_FILENAME = "google_account.json"
    const val GLOBAL_STATS_FILENAME = "icebeats_global_stats.xml"
    const val PLAYLIST_IMAGES_PREFS_FILENAME = "playlist_images.xml"
    const val PLAYLIST_IMAGES_DIR = "playlist_images"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun getDeviceId(context: Context): String {
        return runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown_device"
    }

    fun getDeviceCloudFilePath(context: Context): String {
        val deviceId = getDeviceId(context)
        return "icebeats/devices/$deviceId/$BACKUP_FILENAME"
    }

    fun getAutoBackupFile(context: Context): File = File(context.filesDir, BACKUP_FILENAME)

    fun getBackupSize(context: Context): Long {
        val file = getAutoBackupFile(context)
        if (file.exists() && file.length() > 0) {
            return file.length()
        }
        val legacyFile = File(context.filesDir, "os_backup/latest.backup")
        if (legacyFile.exists() && legacyFile.length() > 0) {
            return legacyFile.length()
        }
        var totalBytes = 0L
        val dbFile = context.getDatabasePath(InternalDatabase.DB_NAME)
        if (dbFile.exists()) totalBytes += dbFile.length()
        val datastoreDir = context.filesDir / "datastore"
        if (datastoreDir.exists()) {
            datastoreDir.listFiles()?.forEach { totalBytes += it.length() }
        }
        val playlistImagesDir = context.filesDir / PLAYLIST_IMAGES_DIR
        if (playlistImagesDir.exists()) {
            playlistImagesDir.listFiles()?.forEach { totalBytes += it.length() }
        }
        val parentFile = context.filesDir.parentFile
        if (parentFile != null) {
            val stats = parentFile / "shared_prefs" / GLOBAL_STATS_FILENAME
            if (stats.exists()) totalBytes += stats.length()
        }
        return totalBytes
    }

    fun getLastBackupTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_BACKUP_TIME, 0L)
    }

    fun setLastBackupTime(context: Context, time: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_BACKUP_TIME, time)
            .commit()
    }

    fun resetRestartAttempts(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_RESTART_ATTEMPTS, 0)
            .apply()
    }

    fun createBackupZip(context: Context, database: MusicDatabase?, rawStream: OutputStream) {
        // Ensure Room database WAL is fully flushed to song.db
        runCatching {
            val db = database ?: runCatching { com.valora.icebeats.App.instance.database }.getOrNull()
            db?.checkpoint()
        }.onFailure { e ->
            Timber.w(e, "Database checkpoint failed during backup creation")
        }

        // Synchronously commit relevant SharedPreferences to disk
        runCatching {
            context.getSharedPreferences("icebeats_global_stats", Context.MODE_PRIVATE).edit().commit()
            context.getSharedPreferences("playlist_images", Context.MODE_PRIVATE).edit().commit()
            context.getSharedPreferences("backup_settings", Context.MODE_PRIVATE).edit().commit()
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().commit()
        }

        rawStream.buffered().zipOutputStream().use { outputStream ->
            // 1. Comprehensive datastore backup: all preferences (name, settings, avatar, ranks, etc.)
            val datastoreDir = context.filesDir / "datastore"
            if (datastoreDir.exists() && datastoreDir.isDirectory) {
                datastoreDir.listFiles()?.forEach { dsFile ->
                    if (dsFile.isFile) {
                        dsFile.inputStream().buffered().use { inputStream ->
                            outputStream.putNextEntry(ZipEntry("datastore/${dsFile.name}"))
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            }

            // Legacy standalone entries for maximum backwards compatibility
            val settingsFile = context.filesDir / "datastore" / SETTINGS_FILENAME
            if (settingsFile.exists()) {
                settingsFile.inputStream().buffered().use { inputStream ->
                    outputStream.putNextEntry(ZipEntry(SETTINGS_FILENAME))
                    inputStream.copyTo(outputStream)
                }
            }

            val namePrefsFile = context.filesDir / "datastore" / USER_NAME_PREFS_FILENAME
            if (namePrefsFile.exists()) {
                namePrefsFile.inputStream().buffered().use { inputStream ->
                    outputStream.putNextEntry(ZipEntry(USER_NAME_PREFS_FILENAME))
                    inputStream.copyTo(outputStream)
                }
            }

            val accountEmail = runCatching {
                runBlocking { NamePreferenceManager(context).accountEmail.first() }
            }.getOrDefault("")
            if (accountEmail.isNotBlank()) {
                outputStream.putNextEntry(ZipEntry(GOOGLE_ACCOUNT_FILENAME))
                outputStream.write(
                    JSONObject()
                        .put("email", accountEmail)
                        .put("previouslyLoggedIn", true)
                        .toString()
                        .toByteArray()
                )
            }

            val parentFile = context.filesDir.parentFile
            if (parentFile != null) {
                val statsPrefsFile = parentFile / "shared_prefs" / GLOBAL_STATS_FILENAME
                if (statsPrefsFile.exists()) {
                    statsPrefsFile.inputStream().buffered().use { inputStream ->
                        outputStream.putNextEntry(ZipEntry(GLOBAL_STATS_FILENAME))
                        inputStream.copyTo(outputStream)
                    }
                }

                val playlistImagesPrefs = parentFile / "shared_prefs" / PLAYLIST_IMAGES_PREFS_FILENAME
                if (playlistImagesPrefs.exists()) {
                    playlistImagesPrefs.inputStream().buffered().use { inputStream ->
                        outputStream.putNextEntry(ZipEntry(PLAYLIST_IMAGES_PREFS_FILENAME))
                        inputStream.copyTo(outputStream)
                    }
                }
            }

            val playlistImagesDir = context.filesDir / PLAYLIST_IMAGES_DIR
            if (playlistImagesDir.exists() && playlistImagesDir.isDirectory) {
                playlistImagesDir.listFiles()?.forEach { imgFile ->
                    if (imgFile.isFile) {
                        imgFile.inputStream().buffered().use { inputStream ->
                            outputStream.putNextEntry(ZipEntry("$PLAYLIST_IMAGES_DIR/${imgFile.name}"))
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            }

            val dbFile = context.getDatabasePath(InternalDatabase.DB_NAME)
            if (dbFile.exists()) {
                FileInputStream(dbFile).use { inputStream ->
                    outputStream.putNextEntry(ZipEntry(InternalDatabase.DB_NAME))
                    inputStream.copyTo(outputStream)
                }
            }
        }
    }

    fun createAutoBackup(context: Context, database: MusicDatabase?, notifyBackupManager: Boolean = true): Boolean {
        return try {
            val targetFile = getAutoBackupFile(context)
            val tmpFile = File(context.filesDir, "$BACKUP_FILENAME.tmp")

            FileOutputStream(tmpFile).use { fos ->
                createBackupZip(context, database, fos)
            }

            if (tmpFile.exists() && tmpFile.length() > 0) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                tmpFile.renameTo(targetFile)

                val now = System.currentTimeMillis()
                val currentSig = "${targetFile.length()}_${targetFile.lastModified()}"

                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_LAST_BACKUP_TIME, now)
                    .putString(KEY_LAST_RESTORED_SIG, currentSig)
                    .commit()

                if (notifyBackupManager) {
                    runCatching {
                        android.app.backup.BackupManager(context).dataChanged()
                    }
                }

                Timber.i("AutoBackupManager: auto_backup created successfully (${targetFile.length()} bytes)")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "AutoBackupManager: createAutoBackup failed")
            false
        }
    }

    fun restoreFromInputStream(context: Context, rawStream: InputStream, shouldRestart: Boolean = true): Boolean {
        return try {
            rawStream.zipInputStream().use { inputStream ->
                var entry = tryOrNull { inputStream.nextEntry }
                while (entry != null) {
                    when {
                        entry.name.startsWith("datastore/") -> {
                            val relName = entry.name.removePrefix("datastore/")
                            if (relName.isNotBlank()) {
                                val destFile = context.filesDir / "datastore" / relName
                                destFile.parentFile?.mkdirs()
                                destFile.outputStream().use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        }

                        entry.name == SETTINGS_FILENAME -> {
                            val destFile = context.filesDir / "datastore" / SETTINGS_FILENAME
                            destFile.parentFile?.mkdirs()
                            destFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }

                        entry.name == USER_NAME_PREFS_FILENAME -> {
                            val destFile = context.filesDir / "datastore" / USER_NAME_PREFS_FILENAME
                            destFile.parentFile?.mkdirs()
                            destFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }

                        entry.name == GLOBAL_STATS_FILENAME -> {
                            val parentFile = context.filesDir.parentFile
                            if (parentFile != null) {
                                val destFile = parentFile / "shared_prefs" / GLOBAL_STATS_FILENAME
                                destFile.parentFile?.mkdirs()
                                destFile.outputStream().use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        }

                        entry.name == GOOGLE_ACCOUNT_FILENAME -> {
                            val email = inputStream.readBytes()
                                .toString(Charsets.UTF_8)
                                .let { JSONObject(it).optString("email") }
                                .trim()
                            if (email.isNotBlank()) {
                                runBlocking {
                                    NamePreferenceManager(context).rememberGoogleLoginEmail(email)
                                }
                            }
                        }

                        entry.name == InternalDatabase.DB_NAME -> {
                            val dbFile = context.getDatabasePath(InternalDatabase.DB_NAME)
                            dbFile.parentFile?.mkdirs()
                            context.getDatabasePath("${InternalDatabase.DB_NAME}-wal").delete()
                            context.getDatabasePath("${InternalDatabase.DB_NAME}-shm").delete()
                            FileOutputStream(dbFile).use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }

                        entry.name == PLAYLIST_IMAGES_PREFS_FILENAME -> {
                            val parentFile = context.filesDir.parentFile
                            if (parentFile != null) {
                                val destFile = parentFile / "shared_prefs" / PLAYLIST_IMAGES_PREFS_FILENAME
                                destFile.parentFile?.mkdirs()
                                destFile.outputStream().use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        }

                        entry.name.startsWith("$PLAYLIST_IMAGES_DIR/") -> {
                            val relName = entry.name.removePrefix("$PLAYLIST_IMAGES_DIR/")
                            if (relName.isNotBlank()) {
                                val destFile = context.filesDir / PLAYLIST_IMAGES_DIR / relName
                                destFile.parentFile?.mkdirs()
                                destFile.outputStream().use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        }
                    }
                    entry = tryOrNull { inputStream.nextEntry }
                }
            }
            context.filesDir.resolve(PERSISTENT_QUEUE_FILE).delete()

            if (shouldRestart) {
                restartApp(context)
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "AutoBackupManager: restoreFromInputStream failed")
            reportException(e)
            false
        }
    }

    fun restoreAutoBackup(context: Context, shouldRestart: Boolean = true): Boolean {
        var file = getAutoBackupFile(context)
        if (!file.exists() || file.length() == 0L) {
            file = File(context.filesDir, "os_backup/latest.backup")
        }
        if (!file.exists() || file.length() == 0L) {
            Timber.w("AutoBackupManager: No backup file available to restore")
            return false
        }
        return FileInputStream(file).use { stream ->
            restoreFromInputStream(context, stream, shouldRestart)
        }
    }

    fun checkAndRestoreOnOpen(context: Context) {
        try {
            var backupFile = getAutoBackupFile(context)
            if (!backupFile.exists() || backupFile.length() == 0L) {
                val legacyFile = File(context.filesDir, "os_backup/latest.backup")
                if (legacyFile.exists() && legacyFile.length() > 0L) {
                    legacyFile.copyTo(backupFile, overwrite = true)
                }
            }

            if (!backupFile.exists() || backupFile.length() == 0L) {
                return
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastRestoredSig = prefs.getString(KEY_LAST_RESTORED_SIG, null)
            val currentSig = "${backupFile.length()}_${backupFile.lastModified()}"

            // Safeguard: Do not unpack if already restored
            if (lastRestoredSig == currentSig) {
                return
            }

            // Loop prevention: Max 2 restart attempts
            val restartAttempts = prefs.getInt(KEY_RESTART_ATTEMPTS, 0)
            if (restartAttempts >= 2) {
                Timber.w("AutoBackupManager: Aborting auto-restore to prevent restart loop ($restartAttempts attempts)")
                return
            }

            Timber.i("AutoBackupManager: Discovered un-restored backup file (${backupFile.length()} bytes). Restoring on app open...")
            val success = FileInputStream(backupFile).use { stream ->
                restoreFromInputStream(context, stream, shouldRestart = false)
            }

            if (success) {
                prefs.edit()
                    .putString(KEY_LAST_RESTORED_SIG, currentSig)
                    .putInt(KEY_RESTART_ATTEMPTS, restartAttempts + 1)
                    .putLong(KEY_LAST_BACKUP_TIME, backupFile.lastModified())
                    .commit()

                Timber.i("AutoBackupManager: Unpack on open succeeded. Restarting app with restored state...")
                restartApp(context)
            }
        } catch (t: Throwable) {
            Timber.e(t, "AutoBackupManager: checkAndRestoreOnOpen encountered error")
        }
    }

    suspend fun uploadToCloud(context: Context, backupFile: File): Boolean = withContext(Dispatchers.IO) {
        if (!backupFile.exists() || backupFile.length() == 0L) {
            Timber.w("AutoBackupManager: Cannot upload non-existent or empty backup file")
            return@withContext false
        }
        return@withContext try {
            val cloudFile = getDeviceCloudFilePath(context)
            val url = "${BuildConfig.STATS_BASE_URL}/upload?file=${URLEncoder.encode(cloudFile, "UTF-8")}"
            val mediaType = "application/octet-stream".toMediaTypeOrNull()
            val requestBody = backupFile.asRequestBody(mediaType)
            val request = Request.Builder()
                .url(url)
                .header("X-API-Key", BuildConfig.STATS_API_KEY)
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val isSuccess = response.isSuccessful
                Timber.i("AutoBackupManager: Cloud upload response code=${response.code}, success=$isSuccess")
                isSuccess
            }
        } catch (e: Exception) {
            Timber.e(e, "AutoBackupManager: Cloud upload failed")
            false
        }
    }

    suspend fun downloadFromCloud(context: Context, destinationFile: File): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val cloudFile = getDeviceCloudFilePath(context)
            val url = "${BuildConfig.STATS_BASE_URL}/download?file=${URLEncoder.encode(cloudFile, "UTF-8")}"
            val request = Request.Builder()
                .url(url)
                .header("X-API-Key", BuildConfig.STATS_API_KEY)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    Timber.i("AutoBackupManager: No cloud backup found for this device on server (404)")
                    return@use false
                }
                if (!response.isSuccessful) {
                    Timber.w("AutoBackupManager: Cloud download failed with code=${response.code}")
                    return@use false
                }
                val body = response.body
                val tmpFile = File(destinationFile.parentFile ?: context.filesDir, "${destinationFile.name}.download")
                tmpFile.outputStream().use { fos ->
                    body.byteStream().copyTo(fos)
                }
                if (tmpFile.length() > 0L) {
                    if (destinationFile.exists()) destinationFile.delete()
                    tmpFile.renameTo(destinationFile)
                    Timber.i("AutoBackupManager: Cloud backup downloaded successfully (${destinationFile.length()} bytes)")
                    true
                } else {
                    tmpFile.delete()
                    false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "AutoBackupManager: Cloud download failed")
            false
        }
    }

    suspend fun deleteFromCloud(context: Context): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val cloudFile = getDeviceCloudFilePath(context)
            val url = "${BuildConfig.STATS_BASE_URL}/delete?file=${URLEncoder.encode(cloudFile, "UTF-8")}"
            val request = Request.Builder()
                .url(url)
                .header("X-API-Key", BuildConfig.STATS_API_KEY)
                .post("".toRequestBody(null))
                .build()

            httpClient.newCall(request).execute().use { response ->
                Timber.i("AutoBackupManager: Cloud delete response code=${response.code}")
                response.isSuccessful
            }
        } catch (e: Exception) {
            Timber.e(e, "AutoBackupManager: Cloud delete failed")
            false
        }
    }

    suspend fun checkAndRestoreDeviceCloudBackup(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val restartAttempts = prefs.getInt(KEY_RESTART_ATTEMPTS, 0)
        if (restartAttempts >= 2) {
            Timber.w("AutoBackupManager: Aborting cloud restore to prevent restart loop ($restartAttempts attempts)")
            return@withContext false
        }

        val targetFile = getAutoBackupFile(context)
        val downloadSuccess = downloadFromCloud(context, targetFile)
        if (!downloadSuccess || !targetFile.exists() || targetFile.length() == 0L) {
            return@withContext false
        }

        val currentSig = "${targetFile.length()}_${targetFile.lastModified()}"
        val lastRestoredSig = prefs.getString(KEY_LAST_RESTORED_SIG, null)
        if (lastRestoredSig == currentSig) {
            Timber.d("AutoBackupManager: Cloud backup already matches last restored signature, skipping duplicate unpack")
            return@withContext false
        }

        Timber.i("AutoBackupManager: Cloud backup downloaded for device. Restoring local state...")
        val success = FileInputStream(targetFile).use { stream ->
            restoreFromInputStream(context, stream, shouldRestart = false)
        }

        if (success) {
            prefs.edit()
                .putString(KEY_LAST_RESTORED_SIG, currentSig)
                .putInt(KEY_RESTART_ATTEMPTS, restartAttempts + 1)
                .putLong(KEY_LAST_BACKUP_TIME, targetFile.lastModified())
                .commit()

            Timber.i("AutoBackupManager: Cloud backup unpacked successfully! Restarting app with full restored profile...")
            withContext(Dispatchers.Main) {
                restartApp(context)
            }
            return@withContext true
        }
        return@withContext false
    }

    fun deleteBackup(context: Context): Boolean {
        return try {
            val file = getAutoBackupFile(context)
            if (file.exists()) {
                file.delete()
            }
            val legacyFile = File(context.filesDir, "os_backup/latest.backup")
            if (legacyFile.exists()) {
                legacyFile.delete()
            }

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()

            runCatching {
                android.app.backup.BackupManager(context).dataChanged()
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "AutoBackupManager: deleteBackup failed")
            false
        }
    }

    fun restartApp(context: Context) {
        try {
            context.stopService(Intent(context, MusicService::class.java))
        } catch (_: Exception) {}

        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        if (intent != null) {
            try {
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    24601,
                    intent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                alarmManager?.set(
                    AlarmManager.RTC,
                    System.currentTimeMillis() + 400,
                    pendingIntent
                )
            } catch (_: Exception) {}
            try {
                context.startActivity(intent)
            } catch (_: Exception) {}
        }

        try {
            Thread.sleep(350)
        } catch (_: InterruptedException) {}

        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(0)
    }
}

