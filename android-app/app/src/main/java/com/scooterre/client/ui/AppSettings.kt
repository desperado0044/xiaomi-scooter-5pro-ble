package com.scooterre.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scooterre.client.viewmodel.RefreshRate
import com.scooterre.client.viewmodel.UiState

/** The app-wide settings as a screen of their own - reachable from the device list, so they work
 * before any scooter is connected (the dashboard menu shows the same content). */
@Composable
fun AppSettingsScreen(state: UiState, settings: SettingsActions, onBack: () -> Unit) {
    val s = strings(state.language)
    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Text("←", fontSize = 22.sp) }
            Text(
                s.sectionApp,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        HorizontalDivider()
        AppSettingsContent(state, s, settings)
    }
}

data class SettingsActions(
    val onSetLanguage: (Lang) -> Unit,
    val onSetThemeMode: (ThemeMode) -> Unit,
    val onSetAutoBrightness: (Boolean) -> Unit,
    val onSetKeepScreenOn: (Boolean) -> Unit,
    val onSetUnits: (UnitSystem) -> Unit,
    val onSetRefreshRate: (RefreshRate) -> Unit,
    val onSetAutoConnect: (Boolean) -> Unit,
    val onSetConfirmCritical: (Boolean) -> Unit,
    val onSetRideTracking: (Boolean) -> Unit,
    val onSetUpdateCheck: (Boolean) -> Unit,
    val onSetAppLock: (Boolean) -> Unit,
)

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), content = content)
    }
}

@Composable
private fun <T> SettingsRadioCard(
    title: String,
    hint: String?,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    SettingsCard {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        hint?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for ((value, label) in options) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == value, onClick = { onSelect(value) })
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun SettingsSwitchCard(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
fun AppSettingsContent(state: UiState, s: AppStrings, settings: SettingsActions) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsRadioCard(
            title = s.settingsLanguageLabel,
            hint = null,
            options = listOf(Lang.DE to "Deutsch", Lang.EN to "English"),
            selected = state.language,
            onSelect = settings.onSetLanguage,
        )
        SettingsRadioCard(
            title = s.themeLabel,
            hint = null,
            options = listOf(ThemeMode.SYSTEM to s.themeSystem, ThemeMode.LIGHT to s.themeLight, ThemeMode.DARK to s.themeDark),
            selected = state.themeMode,
            onSelect = settings.onSetThemeMode,
        )
        SettingsSwitchCard(s.autoBrightnessLabel, s.autoBrightnessHint, state.autoBrightness, settings.onSetAutoBrightness)
        SettingsSwitchCard(s.keepScreenOnLabel, s.keepScreenOnHint, state.keepScreenOn, settings.onSetKeepScreenOn)
        SettingsRadioCard(
            title = s.unitsLabel,
            hint = null,
            options = listOf(UnitSystem.METRIC to s.unitsMetric, UnitSystem.IMPERIAL to s.unitsImperial),
            selected = state.units,
            onSelect = settings.onSetUnits,
        )
        SettingsRadioCard(
            title = s.refreshRateLabel,
            hint = s.refreshRateHint,
            options = listOf(
                RefreshRate.ECONOMY to s.refreshEconomy,
                RefreshRate.NORMAL to s.refreshNormal,
                RefreshRate.FAST to s.refreshFast,
            ),
            selected = state.refreshRate,
            onSelect = settings.onSetRefreshRate,
        )
        SettingsSwitchCard(s.autoConnectLabel, s.autoConnectHint, state.autoConnect, settings.onSetAutoConnect)
        SettingsSwitchCard(s.confirmCriticalLabel, s.confirmCriticalHint, state.confirmCritical, settings.onSetConfirmCritical)
        SettingsSwitchCard(s.rideTrackingLabel, s.rideTrackingHint, state.rideTracking, settings.onSetRideTracking)
        SettingsSwitchCard(s.updateCheckLabel, s.updateCheckHint, state.updateCheck, settings.onSetUpdateCheck)
        SettingsSwitchCard(s.appLockLabel, s.appLockHint, state.appLock, settings.onSetAppLock)
        SettingsCard {
            Text(s.aboutLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(s.aboutVersion(versionName), style = MaterialTheme.typography.bodyMedium)
            state.availableUpdate?.let { UpdateBanner(it, s, Modifier.padding(vertical = 6.dp)) }
            Text(
                s.aboutBody,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(onClick = { uriHandler.openUri("https://github.com/desperado0044/xiaomi-scooter-5pro-ble") }) {
                Text(s.aboutGithubButton)
            }
        }
    }
}

