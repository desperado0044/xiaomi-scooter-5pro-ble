package com.scooterre.client.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import com.scooterre.client.protocol.DeviceBundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.scooterre.client.viewmodel.UiState

@Composable
fun LoginScreen(
    state: UiState,
    onMacChanged: (String) -> Unit,
    onPinChanged: (String) -> Unit,
    onStartScan: () -> Unit,
    onPickScanned: (String) -> Unit,
    onConnectSaved: () -> Unit,
    onCloudLogin: (username: String, password: String) -> Unit,
    onStartQrLogin: () -> Unit,
    onRetryWithPin: () -> Unit,
    onForgetSaved: () -> Unit,
    onToggleLanguage: () -> Unit,
    onBackToPicker: () -> Unit,
    onImportTextChanged: (String) -> Unit,
    onImportDevice: () -> Unit,
    onImportBundle: (Uri, String?) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current
    val s = strings(state.language)
    // Reading an exported file straight in avoids re-typing/pasting a long code by hand - the
    // file's whole content becomes the import text field's value, same as pasting it would.
    // An export bundle (ZIP, maybe encrypted) is imported as a whole; anything else is read as the
    // text export code and put into the import field, as if pasted.
    var pendingBundle by remember { mutableStateOf<Uri?>(null) }
    var bundlePassword by remember { mutableStateOf("") }
    val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            when (bytes?.let(DeviceBundle::kindOf)) {
                DeviceBundle.Kind.ENCRYPTED -> {
                    bundlePassword = ""
                    pendingBundle = uri
                }
                DeviceBundle.Kind.PLAIN -> onImportBundle(uri, null)
                else -> bytes?.toString(Charsets.UTF_8)?.let { onImportTextChanged(it.trim()) }
            }
        }
    }
    pendingBundle?.let { uri ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingBundle = null },
            title = { Text(s.importPasswordTitle) },
            text = {
                OutlinedTextField(
                    value = bundlePassword,
                    onValueChange = { bundlePassword = it },
                    label = { Text(s.importPasswordLabel) },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
            },
            confirmButton = {
                TextButton(enabled = bundlePassword.isNotEmpty(), onClick = { onImportBundle(uri, bundlePassword); pendingBundle = null }) {
                    Text(s.importButton)
                }
            },
            dismissButton = { TextButton(onClick = { pendingBundle = null }) { Text(s.cancelButton) } },
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s.addDeviceTitle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onToggleLanguage) { Text(if (state.language == Lang.DE) "🇩🇪" else "🇬🇧") }
        }
        if (state.knownDevices.isNotEmpty()) {
            TextButton(onClick = onBackToPicker) { Text(s.backToDeviceListButton) }
        }

        HorizontalDivider()
        Text(s.importDeviceTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            s.importDeviceHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.importText,
            onValueChange = onImportTextChanged,
            label = { Text(s.importFieldLabel) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
            minLines = 2,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { pickFileLauncher.launch("*/*") },
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
            ) { Text(s.pickFileButton) }
            Button(
                onClick = onImportDevice,
                enabled = !state.busy && state.importText.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text(s.importButton) }
        }
        // Import problems show right here - the general error line at the bottom of this long
        // screen would be out of sight.
        val importErrors = setOf(s.importWrongPasswordError, s.importInvalidCodeError)
        state.error?.takeIf { it in importErrors }?.let {
            Text("${s.errorPrefix}$it", color = MaterialTheme.colorScheme.error)
        }
        HorizontalDivider()

        Text(
            s.loginSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.macAddress,
                onValueChange = onMacChanged,
                label = { Text(s.macFieldLabel) },
                placeholder = { Text(s.macFieldPlaceholder) },
                modifier = Modifier.weight(1f),
                enabled = !state.busy,
                singleLine = true,
            )
            if (state.scanning) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            } else {
                TextButton(onClick = onStartScan, enabled = !state.busy) { Text(s.scanButton) }
            }
        }

        if (state.scanning || state.scanResults.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.scanning) {
                    Text(
                        s.scanningStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.scanResults.forEach { found ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onPickScanned(found.address) },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(found.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                found.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (state.hasSavedLtmk) {
            HorizontalDivider()
            Text(s.savedKeyText)
            Button(onClick = onConnectSaved, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(s.connectSavedButton)
            }
            TextButton(onClick = onForgetSaved, enabled = !state.busy) {
                Text(s.forgetSavedButton)
            }
        }

        HorizontalDivider()
        Text(s.newLoginDivider)

        OutlinedTextField(
            value = state.pin,
            onValueChange = onPinChanged,
            label = { Text(s.pinFieldLabel) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
        )

        if (state.qrPng != null) {
            val bitmap = remember(state.qrPng) {
                BitmapFactory.decodeByteArray(state.qrPng, 0, state.qrPng.size).asImageBitmap()
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(s.qrScanInstruction)
                Image(bitmap = bitmap, contentDescription = s.qrImageContentDescription, modifier = Modifier.size(220.dp))
                if (state.qrWaiting) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                    Text(s.qrWaitingText)
                }
                state.qrLoginUrl?.let { url ->
                    TextButton(onClick = {
                        // Chrome Custom Tabs, NOT a raw WebView: this same login page is where a
                        // Google-linked account's "Sign in with Google" step happens, and Google
                        // deliberately refuses to complete OAuth inside an embedded WebView
                        // (anti-phishing policy - confirmed live: a plain WebView here just renders
                        // blank). A Custom Tab runs the real Chrome engine (so it isn't blocked)
                        // while still feeling attached to the app - it opens over this screen and
                        // the user comes right back, no full app-switch/home-screen round trip like
                        // a plain ACTION_VIEW chooser needs. This is the single-device login path
                        // for accounts with no separate Mi password.
                        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
                    }) { Text(s.webLoginButton) }
                    TextButton(onClick = {
                        // Explicit chooser so the user can pick the app that actually holds the
                        // scooter's account session - a plain ACTION_VIEW can silently land in a
                        // different installed Xiaomi/Mi app that isn't logged into that account.
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(Intent.createChooser(intent, s.qrOpenChooserTitle))
                    }) { Text(s.qrOpenButton) }
                }
            }
        } else {
            Button(onClick = onStartQrLogin, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(s.qrLoginButton)
            }

            HorizontalDivider()
            Text(s.passwordAltDivider, style = MaterialTheme.typography.bodySmall)

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(s.usernameFieldLabel) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(s.passwordFieldLabel) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            )
            OutlinedButton(
                onClick = { onCloudLogin(username, password) },
                enabled = !state.busy && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(s.passwordLoginButton)
            }
        }

        if (state.needsPin) {
            Button(onClick = onRetryWithPin, enabled = !state.busy && state.pin.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(s.retryPinButton)
            }
        }

        if (state.busy) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator()
                Text(state.busyMessage)
            }
        }

        state.error?.takeUnless { it in importErrors }?.let {
            Text("${s.errorPrefix}$it", color = MaterialTheme.colorScheme.error)
        }
    }
}
