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
) {
    val s = strings(state.language)
    val context = LocalContext.current
    // Saving to a file sidesteps re-typing/pasting a long code by hand entirely - useful when the
    // two phones can be connected to the same PC/cloud-drive folder, or just to avoid a messaging
    // app mangling a long pasted string. The launcher must be created unconditionally here (Compose
    // rule), so the actual text to write is stashed in this remembered var until the picker result
    // comes back.
    var pendingExportWrite by remember { mutableStateOf<String?>(null) }
    val saveFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = pendingExportWrite
        pendingExportWrite = null
        if (uri != null && text != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
    }
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

        state.availableUpdate?.let { UpdateBanner(it, s, Modifier.padding(top = 8.dp)) }

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
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelectDevice(device) },
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
                                        device.mac,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
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

        Button(onClick = onAddDevice, modifier = Modifier.fillMaxWidth()) {
            Text(s.addAnotherDeviceButton)
        }

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

    state.exportCode?.let { code ->
        AlertDialog(
            onDismissRequest = onDismissExportCode,
            title = { Text(s.exportDialogTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        s.exportDialogHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(s.exportDeviceButton) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        pendingExportWrite = code
                        saveFileLauncher.launch("scooter-zugang.txt")
                    }) { Text(s.saveAsFileButton) }
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, code)
                        }
                        context.startActivity(Intent.createChooser(intent, s.shareButton))
                    }) { Text(s.shareButton) }
                }
            },
            dismissButton = { TextButton(onClick = onDismissExportCode) { Text(s.cancelButton) } },
        )
    }
}
