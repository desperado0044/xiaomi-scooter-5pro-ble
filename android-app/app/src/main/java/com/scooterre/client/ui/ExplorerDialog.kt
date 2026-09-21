package com.scooterre.client.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scooterre.client.viewmodel.UiState

/** The result of a read-only property sweep (see PropertyExplorer): the text to copy into a bug report,
 * together with the diagnostics header (app, phone, scooter model, log). */
@Composable
fun ExplorerDialog(state: UiState, onDismiss: () -> Unit) {
    val report = state.explorerReport ?: return
    val s = strings(state.language)
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.explorerTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.explorerIntro, style = MaterialTheme.typography.bodyMedium)
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("scooter explorer", diagnosticsReport(context, state) + "\n\n" + report))
                Toast.makeText(context, s.explorerCopied, Toast.LENGTH_SHORT).show()
            }) { Text(s.explorerCopyButton) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.closeButton) } },
    )
}
