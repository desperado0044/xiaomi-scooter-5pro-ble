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
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.core.content.FileProvider
import com.scooterre.client.protocol.BackupBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    val onSetInsuranceReminder: (Boolean) -> Unit,
    val onTestInsuranceNotification: () -> Unit,
    val onRestoreBackup: (Uri, String, Boolean) -> Unit,
    val onBackupCreated: () -> Unit,
    val onDismissBackupMessage: () -> Unit,
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
        SettingsSwitchCard(s.insuranceLabel, s.insuranceHint, state.insuranceReminder, settings.onSetInsuranceReminder)
        if (state.insuranceReminder) {
            androidx.compose.material3.TextButton(onClick = settings.onTestInsuranceNotification) { Text(s.insuranceTestButton) }
        }
        BackupCard(state, s, settings)
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

@Composable
private fun BackupCard(state: UiState, s: AppStrings, settings: SettingsActions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCreate by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) restoreUri = uri }

    SettingsCard {
        Text(s.backupTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text(
            if (state.lastBackupMillis > 0) {
                s.backupLast(java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(state.lastBackupMillis)))
            } else {
                s.backupNever
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            TextButton(onClick = { showCreate = true }) { Text(s.backupCreate) }
            TextButton(onClick = { pickBackup.launch("*/*") }) { Text(s.backupRestore) }
        }
    }

    if (showCreate) {
        var password by remember { mutableStateOf("") }
        var repeat by remember { mutableStateOf("") }
        var working by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val valid = password.length >= 8 && password == repeat
        val fileName = "scooter-backup-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) + ".zip"
        val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) {
                scope.launch {
                    working = true
                    val bytes = withContext(Dispatchers.IO) { BackupBundle.create(context, password) }
                    if (bytes == null) {
                        error = s.backupTooLarge
                    } else {
                        withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
                        settings.onBackupCreated()
                        showCreate = false
                    }
                    working = false
                }
            }
        }
        AlertDialog(
            onDismissRequest = { if (!working) showCreate = false },
            title = { Text(s.backupCreate) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.backupWarning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text(s.backupPassword) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    OutlinedTextField(value = repeat, onValueChange = { repeat = it }, label = { Text(s.backupPasswordRepeat) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    if (!valid && (password.isNotEmpty() || repeat.isNotEmpty())) Text(s.backupInvalidPassword, style = MaterialTheme.typography.labelSmall)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
                    if (working) CircularProgressIndicator()
                }
            },
            confirmButton = {
                Row {
                    TextButton(enabled = valid && !working, onClick = { saveLauncher.launch(fileName) }) { Text(s.saveAsFileButton) }
                    TextButton(
                        enabled = valid && !working,
                        onClick = {
                            scope.launch {
                                working = true
                                val file = withContext(Dispatchers.IO) {
                                    val bytes = BackupBundle.create(context, password) ?: return@withContext null
                                    val dir = File(context.cacheDir, "export").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                                    File(dir, fileName).also { it.writeBytes(bytes) }
                                }
                                working = false
                                if (file == null) {
                                    error = s.backupTooLarge
                                } else {
                                    settings.onBackupCreated()
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/zip"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, s.shareButton))
                                    showCreate = false
                                }
                            }
                        },
                    ) { Text(s.shareButton) }
                }
            },
            dismissButton = { TextButton(enabled = !working, onClick = { showCreate = false }) { Text(s.cancelButton) } },
        )
    }

    restoreUri?.let { uri ->
        var password by remember(uri) { mutableStateOf("") }
        var withSettings by remember(uri) { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text(s.backupRestoreTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text(s.importPasswordLabel) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = withSettings, onCheckedChange = { withSettings = it })
                        Text(s.backupSettingsCheckbox, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = password.isNotEmpty(), onClick = { settings.onRestoreBackup(uri, password, withSettings); restoreUri = null }) { Text(s.backupRestore) }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text(s.cancelButton) } },
        )
    }

    state.backupMessage?.let { message ->
        AlertDialog(
            onDismissRequest = settings.onDismissBackupMessage,
            text = { Text(message) },
            confirmButton = { TextButton(onClick = settings.onDismissBackupMessage) { Text(s.closeButton) } },
        )
    }
}
