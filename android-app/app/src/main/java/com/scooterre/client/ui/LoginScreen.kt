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
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Scooter 5 Pro", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Eigenes Xiaomi-Konto - PIN und Passwort werden nie im Code gespeichert, nur verschlüsselt auf diesem Gerät (Android Keystore).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.macAddress,
                onValueChange = onMacChanged,
                label = { Text("Roller BLE-MAC") },
                placeholder = { Text("z.B. per Scan finden") },
                modifier = Modifier.weight(1f),
                enabled = !state.busy,
                singleLine = true,
            )
            if (state.scanning) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            } else {
                TextButton(onClick = onStartScan, enabled = !state.busy) { Text("Scannen") }
            }
        }

        if (state.scanning || state.scanResults.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.scanning) {
                    Text(
                        "Suche nach Geräten in der Nähe ...",
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
            Text("Für diese MAC ist bereits ein Schlüssel gespeichert.")
            Button(onClick = onConnectSaved, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Verbinden (gespeicherter Schlüssel)")
            }
            TextButton(onClick = onForgetSaved, enabled = !state.busy) {
                Text("Gespeicherten Schlüssel löschen")
            }
        }

        HorizontalDivider()
        Text("Neu anmelden / anderes Konto:")

        OutlinedTextField(
            value = state.pin,
            onValueChange = onPinChanged,
            label = { Text("Geräte-PIN (nur falls in Mi Home eine Sharing-PIN gesetzt ist)") },
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
                Text("Mit der App scannen, die mit dem Roller verbunden ist (z.B. Xiaomi Home):")
                Image(bitmap = bitmap, contentDescription = "QR-Login-Code", modifier = Modifier.size(220.dp))
                if (state.qrWaiting) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                    Text("Warte auf Bestätigung ...")
                }
                state.qrLoginUrl?.let { url ->
                    TextButton(onClick = {
                        // Explicit chooser so the user can pick the app that actually holds the
                        // scooter's account session - a plain ACTION_VIEW can silently land in a
                        // different installed Xiaomi/Mi app that isn't logged into that account.
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(Intent.createChooser(intent, "Öffnen mit ..."))
                    }) { Text("Stattdessen mit App/Browser öffnen (Auswahl)") }
                }
            }
        } else {
            Button(onClick = onStartQrLogin, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text("QR-Code-Login (auch ohne Mi-Passwort, z.B. bei Google-Konto)")
            }

            HorizontalDivider()
            Text("Alternativ mit Mi-Account-Passwort (falls gesetzt):", style = MaterialTheme.typography.bodySmall)

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Xiaomi-Konto (E-Mail/Telefon)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Mi-Account-Passwort") },
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
                Text("Mit Passwort anmelden")
            }
        }

        if (state.needsPin) {
            Button(onClick = onRetryWithPin, enabled = !state.busy && state.pin.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("Mit PIN erneut versuchen")
            }
        }

        if (state.busy) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator()
                Text(state.busyMessage)
            }
        }

        state.error?.let {
            Text("Fehler: $it", color = MaterialTheme.colorScheme.error)
        }
    }
}
