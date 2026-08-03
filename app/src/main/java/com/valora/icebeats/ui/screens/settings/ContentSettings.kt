package com.valora.icebeats.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.valora.icebeats.NotificationPermissionPreference
import com.valora.icebeats.R
import com.valora.icebeats.innertube.YouTube
import com.valora.icebeats.constants.ContentCountryKey
import com.valora.icebeats.constants.MusicProviderKey
import com.valora.icebeats.constants.ContentLanguageKey
import com.valora.icebeats.constants.CountryCodeToName
import com.valora.icebeats.constants.EnableKugouKey
import com.valora.icebeats.constants.EnableLrcLibKey
import com.valora.icebeats.constants.HideExplicitKey
import com.valora.icebeats.constants.HistoryDuration
import com.valora.icebeats.constants.LanguageCodeToName
import com.valora.icebeats.constants.PreferredLyricsProvider
import com.valora.icebeats.constants.PreferredLyricsProviderKey
import com.valora.icebeats.constants.ProxyEnabledKey
import com.valora.icebeats.constants.ProxyTypeKey
import com.valora.icebeats.constants.ProxyUrlKey
import com.valora.icebeats.constants.QuickPicks
import com.valora.icebeats.constants.QuickPicksKey
import com.valora.icebeats.constants.SYSTEM_DEFAULT
import com.valora.icebeats.constants.TopSize
import com.valora.icebeats.ui.component.EditTextPreference
import com.valora.icebeats.ui.component.ListPreference
import com.valora.icebeats.ui.component.SettingsGeneralCategory
import com.valora.icebeats.ui.component.SettingsPage
import com.valora.icebeats.ui.component.SliderPreference
import com.valora.icebeats.ui.component.SwitchPreference
import com.valora.icebeats.utils.rememberEnumPreference
import com.valora.icebeats.utils.rememberPreference
import java.net.Proxy
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (contentLanguage, onContentLanguageChange) = rememberPreference(
        key = ContentLanguageKey,
        defaultValue = SYSTEM_DEFAULT
    )
    val (musicProvider, onMusicProviderChange) = rememberPreference(
        key = MusicProviderKey,
        defaultValue = "YT"
    )
    val (contentCountry, onContentCountryChange) = rememberPreference(
        key = ContentCountryKey,
        defaultValue = SYSTEM_DEFAULT
    )
    val (hideExplicit, onHideExplicitChange) = rememberPreference(
        key = HideExplicitKey,
        defaultValue = false
    )
    val (proxyEnabled, onProxyEnabledChange) = rememberPreference(
        key = ProxyEnabledKey,
        defaultValue = false
    )
    val (proxyType, onProxyTypeChange) = rememberEnumPreference(
        key = ProxyTypeKey,
        defaultValue = Proxy.Type.HTTP
    )
    val (proxyUrl, onProxyUrlChange) = rememberPreference(
        key = ProxyUrlKey,
        defaultValue = "host:port"
    )
    val (lengthTop, onLengthTopChange) = rememberPreference(
        key = TopSize,
        defaultValue = "50"
    )
    val (historyDuration, onHistoryDurationChange) = rememberPreference(
        key = HistoryDuration,
        defaultValue = 30f
    )
    val (quickPicks, onQuickPicksChange) = rememberEnumPreference(
        key = QuickPicksKey,
        defaultValue = QuickPicks.QUICK_PICKS
    )
    val (enableKugou, onEnableKugouChange) = rememberPreference(
        key = EnableKugouKey,
        defaultValue = true
    )
    val (enableLrclib, onEnableLrclibChange) = rememberPreference(
        key = EnableLrcLibKey,
        defaultValue = true
    )
    val (preferredProvider, onPreferredProviderChange) = rememberEnumPreference(
        key = PreferredLyricsProviderKey,
        defaultValue = PreferredLyricsProvider.LRCLIB
    )


    SettingsPage(
        title = stringResource(R.string.content),
        navController = navController,
        scrollBehavior = scrollBehavior
    ) {
        // General settings
        SettingsGeneralCategory(
            title = stringResource(R.string.general),
            items = listOf(
                {ListPreference(
                    title = { Text(stringResource(R.string.module)) },
                    icon = { Icon(painterResource(R.drawable.music_note), null) },
                    selectedValue = musicProvider,
                    values = listOf("YT", "JIOSAAVN"),
                    valueText = {
                        if (it == "YT") "YT (Compatible)" else "Jio Saavn (Incompatible Supported VPN)"
                    },
                    onValueSelected = onMusicProviderChange,
                )},
                {ListPreference(
                    title = { Text(stringResource(R.string.content_language)) },
                    icon = { Icon(painterResource(R.drawable.language), null) },
                    selectedValue = contentLanguage,
                    values = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
                    valueText = {
                        LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
                    },
                    onValueSelected = { selectedLanguage ->
                        onContentLanguageChange(selectedLanguage)
                        // The request client reads this value at request time. Updating it
                        // here makes Content settings take effect immediately, instead of
                        // waiting for the next full app process restart.
                        val effectiveLanguage = selectedLanguage
                            .takeIf { it != SYSTEM_DEFAULT }
                            ?: Locale.getDefault().language.takeIf { it in LanguageCodeToName }
                            ?: "en"
                        YouTube.locale = YouTube.locale.copy(hl = effectiveLanguage)

                    },
                )},
                {ListPreference(
                    title = { Text(stringResource(R.string.content_country)) },
                    icon = { Icon(painterResource(R.drawable.location_on), null) },
                    selectedValue = contentCountry,
                    values = listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList(),
                    valueText = {
                        CountryCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
                    },
                    onValueSelected = { selectedCountry ->
                        onContentCountryChange(selectedCountry)
                        val effectiveCountry = selectedCountry
                            .takeIf { it != SYSTEM_DEFAULT }
                            ?: Locale.getDefault().country.takeIf { it in CountryCodeToName }
                            ?: "US"
                        YouTube.locale = YouTube.locale.copy(gl = effectiveCountry)
                    },
                )},

                // Hide explicit content
                {SwitchPreference(
                    title = { Text(stringResource(R.string.hide_explicit)) },
                    icon = { Icon(painterResource(R.drawable.explicit), null) },
                    checked = hideExplicit,
                    onCheckedChange = onHideExplicitChange,
                )},

                {NotificationPermissionPreference()},
            )
        )

        // Proxy settings
        SettingsGeneralCategory(
            title = stringResource(R.string.proxy),
            items = listOf(
                {SwitchPreference(
                    title = { Text(stringResource(R.string.enable_proxy)) },
                    icon = { Icon(painterResource(R.drawable.wifi_proxy), null) },
                    checked = proxyEnabled,
                    onCheckedChange = onProxyEnabledChange,
                )},
                {if (proxyEnabled) {
                    Column {
                        ListPreference(
                            title = { Text(stringResource(R.string.proxy_type)) },
                            selectedValue = proxyType,
                            values = listOf(Proxy.Type.HTTP, Proxy.Type.SOCKS),
                            valueText = { it.name },
                            onValueSelected = onProxyTypeChange,
                        )
                        EditTextPreference(
                            title = { Text(stringResource(R.string.proxy_url)) },
                            value = proxyUrl,
                            onValueChange = onProxyUrlChange,
                        )
                    }
                }}
            )
        )

        // Lyrics settings
        SettingsGeneralCategory(
            title = stringResource(R.string.lyrics),
            items = listOf(
                {SwitchPreference(
                    title = { Text(stringResource(R.string.enable_lrclib)) },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = enableLrclib,
                    onCheckedChange = onEnableLrclibChange,
                )},
                {SwitchPreference(
                    title = { Text(stringResource(R.string.enable_kugou)) },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    checked = enableKugou,
                    onCheckedChange = onEnableKugouChange,
                )},
            )
        )

        // Misc settings
        SettingsGeneralCategory(
            title = stringResource(R.string.misc),
            items = listOf(
                {EditTextPreference(
                    title = { Text(stringResource(R.string.top_length)) },
                    icon = { Icon(painterResource(R.drawable.trending_up), null) },
                    value = lengthTop,
                    isInputValid = { it.toIntOrNull()?.let { num -> num > 0 } == true },
                    onValueChange = onLengthTopChange,
                )},
                {ListPreference(
                    title = { Text(stringResource(R.string.set_quick_picks)) },
                    icon = { Icon(painterResource(R.drawable.home_outlined), null) },
                    selectedValue = quickPicks,
                    values = listOf(QuickPicks.QUICK_PICKS, QuickPicks.LAST_LISTEN),
                    valueText = {
                        when (it) {
                            QuickPicks.QUICK_PICKS -> stringResource(R.string.quick_picks)
                            QuickPicks.LAST_LISTEN -> stringResource(R.string.last_song_listened)
                        }
                    },
                    onValueSelected = onQuickPicksChange,
                )},
                {SliderPreference(
                    title = { Text(stringResource(R.string.history_duration)) },
                    icon = { Icon(painterResource(R.drawable.history), null) },
                    value = historyDuration,
                    onValueChange = onHistoryDurationChange,
                )},
            )
        )
    }
}
