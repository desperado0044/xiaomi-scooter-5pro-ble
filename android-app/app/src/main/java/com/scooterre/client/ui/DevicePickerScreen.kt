package com.scooterre.client.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.core.content.FileProvider
import com.scooterre.client.protocol.DeviceBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scooterre.client.protocol.KnownDevice
import com.scooterre.client.viewmodel.UiState

/** Material3 has no built-in "success" role - a plain, theme-independent green reads clearly as
 * "in progress, going well" against both light and dark surfaces, unlike e.g. primaryContainer
 * which can already mean something else in a custom theme. */
private val ConnectingGreen = androidx.compose.ui.graphics.Color(0xFF2E7D32)

/** Lets the user choose which previously-connected scooter to reconnect to - the app now keeps
 * every device it has ever logged into (see [com.scooterre.client.protocol.DeviceRegistry])
 * rather than a single swappable slot, so this screen shows all of them side by side. */
@Composable
fun DevicePickerScreen(
    state: UiState,
    onSelectDevice: (KnownDevice) -> Unit,
    onForgetDevice: (KnownDevice) -> Unit,
    onRenameDevice: (KnownDevice, String) -> Unit,
    onExportDevice: (KnownDevice) -> Unit,
    onDismissExportCode: () -> Unit,
    onAddDevice: () -> Unit,
    onToggleLanguage: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDocuments: (String?) -> Unit,
) {
    val s = strings(state.language)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // A saved key can only be replaced by logging in again (cloud/PIN) - "Vergessen" is
    // deliberately not a single one-tap action so a misplaced tap can't silently strand a device.
    var pendingForget by remember { mutableStateOf<KnownDevice?>(null) }
    var renaming by remember { mutableStateOf<KnownDevice?>(null) }

    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s.devicePickerTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenSettings) { Text("⚙️") }
                TextButton(onClick = onToggleLanguage) { Text(if (state.language == Lang.DE) "🇩🇪" else "🇬🇧") }
            }
        }

        state.availableUpdate?.let { UpdateBanner(it, state, s, Modifier.padding(top = 8.dp)) }

        if (state.knownDevices.isNotEmpty()) {
            DocumentsTile(state.documentCounts.values.sum(), s) { onOpenDocuments(null) }
        }

        if (state.knownDevices.isEmpty()) {
            Text(
                s.noSavedDevicesText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp).weight(1f, fill = false),
            ) {
                items(state.knownDevices, key = { it.mac }) { device ->
                    // Dedicated fields (not the shared busy/error state) - see their doc comment in
                    // UiState: a stray unrelated background failure landing in `error` right as this
                    // device's macAddress happened to still match it produced a misleading red tile.
                    val connecting = state.connectingMac?.equals(device.mac, ignoreCase = true) == true
                    val failed = state.connectFailedMac?.equals(device.mac, ignoreCase = true) == true
                    val colors = when {
                        connecting -> androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = ConnectingGreen, contentColor = androidx.compose.ui.graphics.Color.White,
                        )
                        failed -> androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        else -> androidx.compose.material3.CardDefaults.cardColors()
                    }
                    Card(
                        colors = colors,
                        // While any connect attempt is running, other tiles can't be tapped too -
                        // there's only one BLE connection/protocol instance at a time.
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !state.busy) { onSelectDevice(device) },
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        device.name ?: modelDisplayName(device.model, state.language),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        when {
                                            // The live stage text ("Suche Scooter ...", "Versuch 2
                                            // ...", "Authentifiziere ...") - falls back to the
                                            // static label only in the unlikely case busyMessage
                                            // hasn't been set yet for this tick.
                                            connecting -> state.busyMessage.ifBlank { s.tileConnectingText }
                                            failed -> state.connectFailedError?.let { "${s.tileConnectFailedText}: $it" } ?: s.tileConnectFailedText
                                            else -> device.mac
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (connecting || failed) androidx.compose.ui.graphics.Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (!connecting) {
                                    TextButton(onClick = { onOpenDocuments(device.mac) }) {
                                        Text("\uD83D\uDCC4 ${state.documentCounts[device.mac] ?: 0}")
                                    }
                                }
                            }
                            if (!connecting) {
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = { renaming = device }) { Text(s.renameDeviceButton) }
                                    TextButton(onClick = { onExportDevice(device) }) { Text(s.exportDeviceButton) }
                                    TextButton(onClick = { pendingForget = device }) { Text(s.forgetDeviceButton) }
                                }
                            }
                        }
                    }
                }
            }
        }

        Button(onClick = onAddDevice, modifier = Modifier.fillMaxWidth()) {
            Text(s.addAnotherDeviceButton)
        }

        // A failed connect attempt shows on its own tile above (red, connectFailedMac/-Error) -
        // this generic banner is for anything else that sets the shared `error` field.
        state.error?.let {
            Text(
                "${s.errorPrefix}$it",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    pendingForget?.let { device ->
        val label = device.name ?: modelDisplayName(device.model, state.language)
        AlertDialog(
            onDismissRequest = { pendingForget = null },
            title = { Text(s.forgetDeviceConfirmTitle) },
            text = { Text(s.forgetDeviceConfirmText(label)) },
            confirmButton = {
                TextButton(onClick = { onForgetDevice(device); pendingForget = null }) { Text(s.forgetDeviceButton) }
            },
            dismissButton = { TextButton(onClick = { pendingForget = null }) { Text(s.cancelButton) } },
        )
    }

    renaming?.let { device ->
        var text by remember(device.mac) { mutableStateOf(device.name ?: "") }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(s.renameDeviceTitle) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(s.renameDeviceFieldLabel) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { onRenameDevice(device, text.trim()); renaming = null }) { Text(s.renameDeviceSaveButton) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(s.cancelButton) } },
        )
    }

    state.exportMac?.let { exportMac ->
        val code = state.exportCode ?: return@let
        var password by remember(exportMac) { mutableStateOf("") }
        var working by remember(exportMac) { mutableStateOf(false) }
        val fileLabel = (state.knownDevices.firstOrNull { it.mac.equals(exportMac, ignoreCase = true) }?.name ?: "scooter")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val saveBundleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) {
                scope.launch {
                    working = true
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { DeviceBundle.export(context, exportMac, it, password) }
                    }
                    working = false
                }
            }
        }
        AlertDialog(
            onDismissRequest = onDismissExportCode,
            title = { Text(s.exportDialogTitle) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(s.exportDialogHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Text(s.exportBundleLabel, style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(s.exportPasswordLabel) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(s.exportPasswordHint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        TextButton(enabled = !working, onClick = { saveBundleLauncher.launch("scooter-$fileLabel.zip") }) { Text(s.saveAsFileButton) }
                        TextButton(
                            enabled = !working,
                            onClick = {
                                scope.launch {
                                    working = true
                                    val file = withContext(Dispatchers.IO) {
                                        val dir = File(context.cacheDir, "export").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                                        File(dir, "scooter-$fileLabel.zip").also { f ->
                                            f.outputStream().use { DeviceBundle.export(context, exportMac, it, password) }
                                        }
                                    }
                                    working = false
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/zip"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, s.shareButton))
                                }
                            },
                        ) { Text(s.shareButton) }
                    }
                    HorizontalDivider()
                    Text(s.exportCodeLabel, style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = code,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(s.exportDeviceButton) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, code)
                        }
                        context.startActivity(Intent.createChooser(intent, s.shareButton))
                    }) { Text(s.shareCodeButton) }
                }
            },
            confirmButton = { TextButton(onClick = onDismissExportCode) { Text(s.closeButton) } },
        )
    }
}

/** One tap from the start screen to the documents - meant for showing them at a traffic check, so it
 * has to work without any scooter connection. */
@Composable
private fun DocumentsTile(total: Int, s: AppStrings, onClick: () -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clickable(onClick = onClick),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("\uD83D\uDCC4", fontSize = androidx.compose.ui.unit.TextUnit(28f, androidx.compose.ui.unit.TextUnitType.Sp))
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    s.docsTileTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(s.docsTileSubtitle(total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}
