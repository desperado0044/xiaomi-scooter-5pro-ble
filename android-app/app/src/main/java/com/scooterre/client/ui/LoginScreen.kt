package com.scooterre.client.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
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
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current
    val s = strings(state.language)

    Column(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Scooter 5 Pro", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onToggleLanguage) { Text(if (state.language == Lang.DE) "🇩🇪" else "🇬🇧") }
        }
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

        state.error?.let {
            Text("${s.errorPrefix}$it", color = MaterialTheme.colorScheme.error)
        }
    }
}
