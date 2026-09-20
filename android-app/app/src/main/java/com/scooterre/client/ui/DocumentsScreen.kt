package com.scooterre.client.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.scooterre.client.protocol.ScooterDocument
import com.scooterre.client.viewmodel.UiState
import java.io.File

/** What the document screens can ask the ViewModel to do. */
data class DocumentActions(
    val onSelectDevice: (String) -> Unit,
    val onOpen: (String) -> Unit,
    val onAddPhotos: (List<File>, String) -> Unit,
    val onImport: (Uri, String) -> Unit,
    val onAppendPhoto: (String, File) -> Unit,
    val onRename: (String, String) -> Unit,
    val onDelete: (String) -> Unit,
)

/** Returns a function that starts the phone's camera app for one photo and hands the resulting file
 * to [onPhoto]. The target file lives in the cache dir behind a FileProvider URI, so neither a
 * camera nor a storage permission is needed. The pending path is saved across process death (the
 * camera app can push this app out of memory). */
@Composable
fun rememberCameraLauncher(onPhoto: (File) -> Unit): () -> Unit {
    val context = LocalContext.current
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingPath?.let { File(it) }
        pendingPath = null
        if (ok && file != null && file.exists()) onPhoto(file) else file?.delete()
    }
    return {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
        pendingPath = file.absolutePath
        launcher.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
    }
}

@Composable
fun DocumentsScreen(state: UiState, actions: DocumentActions, onBack: () -> Unit) {
    val s = strings(state.language)
    val context = LocalContext.current
    val mac = state.documentsMac
    val documents = state.documents

    // Photos taken but not saved yet (a multi-page document collects several before the name dialog
    // finishes it), and a picked file waiting for its name.
    var pendingPhotos by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var pendingImport by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    var nameDialogOpen by rememberSaveable { mutableStateOf(false) }
    var draftName by rememberSaveable { mutableStateOf("") }
    var renaming by remember { mutableStateOf<ScooterDocument?>(null) }
    var deleting by remember { mutableStateOf<ScooterDocument?>(null) }

    val takePhoto = rememberCameraLauncher { file ->
        pendingPhotos = pendingPhotos + file.absolutePath
        nameDialogOpen = true
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
            pendingImport = uri to (displayName?.substringBeforeLast('.') ?: "${s.docsDefaultName} ${documents.size + 1}")
        }
    }

    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Text("←", fontSize = 22.sp) }
            Text(s.docsTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
        }
        HorizontalDivider()

        if (state.knownDevices.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                state.knownDevices.forEach { device ->
                    FilterChip(
                        selected = device.mac.equals(mac, ignoreCase = true),
                        onClick = { actions.onSelectDevice(device.mac) },
                        label = { Text(device.name ?: modelDisplayName(device.model, state.language)) },
                    )
                }
            }
        } else {
            state.knownDevices.firstOrNull()?.let { device ->
                Text(
                    device.name ?: modelDisplayName(device.model, state.language),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        state.error?.let {
            Text("${s.errorPrefix}$it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Button(onClick = { takePhoto() }, modifier = Modifier.weight(1f)) { Text("📷  ${s.docsScan}") }
            OutlinedButton(
                onClick = { pickFile.launch(arrayOf("image/*", "application/pdf")) },
                modifier = Modifier.weight(1f),
            ) { Text("📁  ${s.docsImport}") }
        }

        if (documents.isEmpty()) {
            Text(
                s.docsEmpty,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp, bottom = 16.dp)) {
                items(documents, key = { it.id }) { doc ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { actions.onOpen(doc.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 2.dp)) {
                            Text(doc.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text(
                                if (doc.isPdf) "PDF" else s.docsPages(doc.pages.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { renaming = doc }) { Text(s.renameDeviceButton) }
                                TextButton(onClick = { deleting = doc }) { Text(s.docsDeleteButton) }
                            }
                        }
                    }
                }
            }
        }
    }

    // Name dialog after a photo (with "another page") or after picking a file.
    val photosTaken = pendingPhotos
    val importing = pendingImport
    if ((nameDialogOpen && photosTaken.isNotEmpty()) || importing != null) {
        var name by remember(importing) {
            mutableStateOf(importing?.second ?: draftName.ifBlank { "${s.docsDefaultName} ${documents.size + 1}" })
        }
        fun discard() {
            photosTaken.forEach { File(it).delete() }
            pendingPhotos = emptyList()
            nameDialogOpen = false
            pendingImport = null
            draftName = ""
        }
        AlertDialog(
            onDismissRequest = { discard() },
            title = { Text(s.docsNameTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(s.docsNameLabel) }, singleLine = true)
                    if (importing == null) {
                        Text(s.docsPagesTaken(photosTaken.size), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { draftName = name; nameDialogOpen = false; takePhoto() }) { Text("📷  ${s.docsMorePage}") }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        if (importing != null) {
                            actions.onImport(importing.first, name.trim())
                        } else {
                            actions.onAddPhotos(photosTaken.map { File(it) }, name.trim())
                        }
                        pendingPhotos = emptyList()
                        nameDialogOpen = false
                        pendingImport = null
                        draftName = ""
                    },
                ) { Text(s.renameDeviceSaveButton) }
            },
            dismissButton = { TextButton(onClick = { discard() }) { Text(s.cancelButton) } },
        )
    }

    renaming?.let { doc ->
        var name by remember(doc.id) { mutableStateOf(doc.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(s.renameDeviceButton) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(s.docsNameLabel) }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { actions.onRename(doc.id, name.trim()); renaming = null }) { Text(s.renameDeviceSaveButton) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(s.cancelButton) } },
        )
    }

    deleting?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(s.docsDeleteTitle) },
            text = { Text(s.docsDeleteText(doc.name)) },
            confirmButton = { TextButton(onClick = { actions.onDelete(doc.id); deleting = null }) { Text(s.docsDeleteButton) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(s.cancelButton) } },
        )
    }
}
