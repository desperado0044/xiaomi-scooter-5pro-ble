package com.scooterre.client.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.scooterre.client.protocol.ScooterDocument
import com.scooterre.client.viewmodel.UiState

/** What the document screens can ask the ViewModel to do. */
data class DocumentActions(
    val onSelectDevice: (String) -> Unit,
    val onOpen: (String) -> Unit,
    val onAddPhotos: (List<Uri>, String) -> Unit,
    val onImport: (Uri, String) -> Unit,
    val onAppendPhotos: (String, List<Uri>) -> Unit,
    val onRename: (String, String) -> Unit,
    val onDelete: (String) -> Unit,
)

/** Returns a function that opens the phone's own camera app (normal mode, so e.g. its document
 * scanner can be chosen there) and, once the user comes back to this app, opens the system photo
 * picker so the new shots can be picked - several at once for a multi-page document. The photo
 * picker needs no storage permission. The shots stay in the phone's gallery. */
@Composable
fun rememberScanLauncher(onPhotos: (List<Uri>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var awaitingReturn by rememberSaveable { mutableStateOf(false) }
    val awaiting by rememberUpdatedState(awaitingReturn)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) onPhotos(uris)
    }
    // Only the ON_RESUME that follows a real trip to the camera counts - an observer added while the
    // app is already resumed is told about ON_RESUME immediately, which must not open the picker.
    val trip = remember { booleanArrayOf(false) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (awaiting) trip[0] = true
                Lifecycle.Event.ON_RESUME -> if (awaiting && trip[0]) {
                    trip[0] = false
                    awaitingReturn = false
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return {
        trip[0] = false
        awaitingReturn = true
        // Start the phone's default camera app itself (normal mode, with its mode strip) instead of a
        // generic "still image camera" intent, which can pop up an app chooser on some phones.
        val pm = context.packageManager
        val cameraPackage = pm.resolveActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeIf { it != "android" }
        val launch = cameraPackage?.let { pm.getLaunchIntentForPackage(it) } ?: Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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

    val takePhoto = rememberScanLauncher { uris ->
        pendingPhotos = pendingPhotos + uris.map { it.toString() }
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

        Text(
            s.docsScanHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

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
                            actions.onAddPhotos(photosTaken.map(Uri::parse), name.trim())
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
