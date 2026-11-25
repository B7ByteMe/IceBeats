package com.valora.icebeats.appicon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil.decode.SvgDecoder
import coil.imageLoader
import coil.request.ImageRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import timber.log.Timber
import java.util.concurrent.TimeUnit

object AppIconRepository {

    private const val PREFS_NAME = "icebeats_app_icon_prefs"
    private const val KEY_SELECTED_ICON = "selected_app_icon_id"

    private const val PACKAGE_NAME = "com.valora.icebeats"

    const val GITHUB_COMMUNITY_ICONS_URL =
        "https://raw.githubusercontent.com/B7ByteMe/IceBeats/main/assets/icons/community_icons.json"

    val DEFAULT_ICON = AppIcon(
        id = "default",
        title = "Default Brand",
        subtitle = "Original vibrant IceBeats gradient",
        author = "IceBeats Team",
        aliasName = "$PACKAGE_NAME.launcher.DefaultIcon",
        bgColors = listOf(Color(0xFFFFFFFF), Color(0xFFF2F4F7)),
        fgTint = null, // null means use original multi-color gradient
        isDefault = true,
        inApp = true
    )

    val WINTER_ICON = AppIcon(
        id = "icebeats_winters",
        title = "IceBeats Winters",
        subtitle = "Winter season frosted cabin & glowing headphones",
        author = "@Dark",
        aliasName = "$PACKAGE_NAME.launcher.AirbeatsWinters",
        bgColors = listOf(Color(0xFF061838), Color(0xFF0F386E)),
        fgTint = null,
        isDefault = false,
        isCommunity = false,
        inApp = true,
        svgUrl = "https://raw.githubusercontent.com/B7ByteMe/IceBeats/main/assets/icons/IceBeats_Winters.png"
    )

    val MELODY_HEARTS_ICON = AppIcon(
        id = "melody_hearts",
        title = "Melody Hearts",
        subtitle = "Romantic anime youth duo glowing under warm crimson sunset melody",
        author = "@Dark",
        aliasName = "$PACKAGE_NAME.launcher.MelodyHearts",
        bgColors = listOf(Color(0xFF3B0505), Color(0xFFC62828)),
        fgTint = null,
        isDefault = false,
        isCommunity = false,
        inApp = true
    )

    val GTA6_ICON = AppIcon(
        id = "gta_6",
        title = "Vice City VI",
        subtitle = "Neon synthwave Vice City sunset, palm waterfront & retro night drive",
        author = "@Dark",
        aliasName = "$PACKAGE_NAME.launcher.Gta6",
        bgColors = listOf(Color(0xFF1A032A), Color(0xFFFF007F)),
        fgTint = null,
        isDefault = false,
        isCommunity = false,
        inApp = true
    )

    val BUILT_IN_ICONS: List<AppIcon> = listOf(
        DEFAULT_ICON,
        WINTER_ICON,
        MELODY_HEARTS_ICON,
        GTA6_ICON
    )

    /**
     * Returns all available built-in styles for the app.
     */
    fun getAvailableIcons(): List<AppIcon> = BUILT_IN_ICONS.filter { !it.isCommunity }

    /**
     * Determines which icon is currently active on the device.
     */
    fun getActiveIconId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedId = prefs.getString(KEY_SELECTED_ICON, null)
        if (!savedId.isNullOrEmpty() && BUILT_IN_ICONS.any { it.id == savedId }) {
            return savedId
        }

        // Check PackageManager state if not in prefs
        try {
            val pm = context.packageManager
            for (icon in BUILT_IN_ICONS) {
                val componentName = ComponentName(context, icon.aliasName)
                val state = pm.getComponentEnabledSetting(componentName)
                if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    return icon.id
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking active icon via PackageManager")
        }

        return DEFAULT_ICON.id
    }

    /**
     * Changes the active launcher app icon.
     * Uses DONT_KILL_APP to prevent killing the current process.
     */
    fun setActiveIcon(context: Context, targetIconId: String): Boolean {
        val targetIcon = BUILT_IN_ICONS.find { it.id == targetIconId } ?: DEFAULT_ICON
        val pm = context.packageManager

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ atomic batch update
                val settings = mutableListOf<PackageManager.ComponentEnabledSetting>()
                for (icon in BUILT_IN_ICONS) {
                    val componentName = ComponentName(context, icon.aliasName)
                    val newState = if (icon.id == targetIcon.id) {
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    } else {
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    }
                    settings.add(
                        PackageManager.ComponentEnabledSetting(
                            componentName,
                            newState,
                            PackageManager.DONT_KILL_APP
                        )
                    )
                }
                pm.setComponentEnabledSettings(settings)
            } else {
                // API 26-32: Enable target FIRST, then disable others
                val targetComponent = ComponentName(context, targetIcon.aliasName)
                pm.setComponentEnabledSetting(
                    targetComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )

                for (icon in BUILT_IN_ICONS) {
                    if (icon.id != targetIcon.id) {
                        val otherComponent = ComponentName(context, icon.aliasName)
                        pm.setComponentEnabledSetting(
                            otherComponent,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    }
                }
            }

            // Save preference
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SELECTED_ICON, targetIcon.id)
                .apply()

            Timber.i("IceBeats app icon changed to: ${targetIcon.title}")
            return true
        } catch (e: Exception) {
            Timber.e(e, "Failed to change app icon to: ${targetIcon.id}")
            return false
        }
    }

    /**
     * Applies a community SVG icon dynamically to the Android launcher by pinning/updating a high-res home screen shortcut.
     */
    suspend fun applyCommunityIcon(context: Context, icon: AppIcon): Boolean = withContext(Dispatchers.IO) {
        try {
            // Save preference so app remembers selection
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SELECTED_ICON, icon.id)
                .apply()

            if (!icon.svgUrl.isNullOrBlank()) {
                val loader = context.imageLoader
                val reqBuilder = ImageRequest.Builder(context)
                    .data(icon.svgUrl)
                    .size(512, 512)
                    .allowHardware(false)

                if (icon.svgUrl.endsWith(".svg", ignoreCase = true)) {
                    reqBuilder.decoderFactory(SvgDecoder.Factory())
                }

                val req = reqBuilder.build()
                val result = loader.execute(req)
                val bitmap = result.drawable?.toBitmap(512, 512, Bitmap.Config.ARGB_8888)

                if (bitmap != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val shortcutManager = context.getSystemService(ShortcutManager::class.java)
                    if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                        val launchIntent = (context.packageManager.getLaunchIntentForPackage(context.packageName)
                            ?: Intent(context, Class.forName("$PACKAGE_NAME.MainActivity"))).apply {
                                action = Intent.ACTION_MAIN
                                addCategory(Intent.CATEGORY_LAUNCHER)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                            }

                        val pinShortcutInfo = ShortcutInfo.Builder(context, "icebeats_icon_${icon.id}")
                            .setIcon(android.graphics.drawable.Icon.createWithBitmap(bitmap))
                            .setShortLabel(context.getString(com.valora.icebeats.R.string.app_name))
                            .setLongLabel("${context.getString(com.valora.icebeats.R.string.app_name)} - ${icon.title}")
                            .setIntent(launchIntent)
                            .build()

                        shortcutManager.requestPinShortcut(pinShortcutInfo, null)
                        return@withContext true
                    }
                }
            }
            return@withContext true
        } catch (e: Exception) {
            Timber.e(e, "Failed to apply community icon: ${icon.id}")
            return@withContext false
        }
    }

    /**
     * Generates a GitHub Issue template URL for users to submit custom SVG icons.
     */
    fun buildCommunitySubmissionUrl(iconTitle: String, authorName: String, svgUrl: String): String {
        val titleEncoded = java.net.URLEncoder.encode("[Icon Submission] $iconTitle", "UTF-8")
        val bodyContent = """
            ### Custom Icon Submission for IceBeats
            - **Icon Name:** $iconTitle
            - **Designer / Author:** $authorName
            - **Direct SVG Link:** $svgUrl
            
            *Submitted from IceBeats App Icon Customizer*
        """.trimIndent()
        val bodyEncoded = java.net.URLEncoder.encode(bodyContent, "UTF-8")
        return "https://github.com/B7ByteMe/IceBeats/issues/new?title=$titleEncoded&body=$bodyEncoded"
    }

    /**
     * Fetches community SVG icons hosted in the GitHub repository catalog.
     * File: assets/icons/community_icons.json
     */
    private fun parseCommunityJson(jsonStr: String, destination: MutableList<AppIcon>) {
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", "community_$i")
                val title = obj.optString("title", obj.optString("name", "Community Icon"))
                val author = obj.optString("author", "Community Designer")
                val subtitle = obj.optString("subtitle", "Designed by $author")
                val inApp = obj.optBoolean("inApp", id == "icebeats_winters")
                var svgUrl = obj.optString("svgUrl", obj.optString("imageUrl", obj.optString("url", "")))
                if (svgUrl.startsWith("http://")) {
                    svgUrl = "https://" + svgUrl.substring(7)
                }
                if (svgUrl.isBlank() && id == "icebeats_winters") {
                    svgUrl = "https://raw.githubusercontent.com/B7ByteMe/IceBeats/main/assets/icons/IceBeats_Winters.png"
                }

                val matchingBuiltIn = BUILT_IN_ICONS.find { it.id == id }
                val aliasName = matchingBuiltIn?.aliasName ?: if (id == "icebeats_winters") {
                    WINTER_ICON.aliasName
                } else {
                    DEFAULT_ICON.aliasName
                }

                if (svgUrl.isNotBlank()) {
                    destination.add(
                        AppIcon(
                            id = id,
                            title = title,
                            subtitle = subtitle,
                            author = author,
                            aliasName = aliasName,
                            bgColors = listOf(Color(0xFF061838), Color(0xFF0F386E)),
                            fgTint = null,
                            isCommunity = !inApp,
                            inApp = inApp,
                            svgUrl = svgUrl
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing community icons JSON")
        }
    }

    /**
     * Fetches community SVG icons hosted in the GitHub repository catalog and bundled assets.
     */
    suspend fun fetchCommunityIcons(context: Context? = null): List<AppIcon> = withContext(Dispatchers.IO) {
        val list = mutableListOf<AppIcon>()

        // 1. Load locally bundled assets/icons/community_icons.json first for instant display
        if (context != null) {
            try {
                val assetJson = context.assets.open("icons/community_icons.json").bufferedReader().use { it.readText() }
                parseCommunityJson(assetJson, list)
            } catch (e: Exception) {
                Timber.d("No bundled community icons: ${e.message}")
            }
        }

        // 2. Fallback guarantee for bundled icons if not yet parsed
        if (list.none { it.id == "icebeats_winters" }) {
            list.add(WINTER_ICON)
        }
        if (list.none { it.id == "melody_hearts" }) {
            list.add(MELODY_HEARTS_ICON)
        }
        if (list.none { it.id == "gta_6" }) {
            list.add(GTA6_ICON)
        }

        // 3. Fetch latest remote icons from GitHub
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
            val cacheBusterUrl = "$GITHUB_COMMUNITY_ICONS_URL?_t=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(cacheBusterUrl)
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body.string()
                if (!body.isNullOrBlank()) {
                    val remoteList = mutableListOf<AppIcon>()
                    parseCommunityJson(body, remoteList)
                    for (remoteIcon in remoteList) {
                        val existingIndex = list.indexOfFirst { it.id == remoteIcon.id }
                        if (existingIndex >= 0) {
                            list[existingIndex] = remoteIcon
                        } else {
                            list.add(remoteIcon)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.d("No remote community icons loaded: ${e.message}")
        }

        list
    }
}
