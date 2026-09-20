package com.scooterre.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.scooterre.client.update.UpdateInfo
import com.scooterre.client.viewmodel.UiState

/** What the update notice can trigger; provided once in [ScooterApp] so both screens that show the
 * notice do not need extra parameters. */
data class UpdateActions(val onDownload: () -> Unit, val onInstall: () -> Unit)

val LocalUpdateActions = staticCompositionLocalOf { UpdateActions({}, {}) }

/** "A newer version exists" notice. With the release's APK available it can download it and open the
 * system installer (the user still confirms; nothing is installed silently); the release page stays
 * reachable as the manual way. */
@Composable
fun UpdateBanner(info: UpdateInfo, state: UiState, s: AppStrings, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val actions = LocalUpdateActions.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 2.dp)) {
            Text(
                s.updateAvailable(info.version),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            val note = state.updateProblem?.let { s.updateProblemText(it) } ?: if (state.updateNeedsPermission) s.updateAllowInstallHint else null
            note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val progress = state.updateProgress
                when {
                    progress != null -> Text(
                        s.updateDownloading(progress),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    info.apkUrl == null -> Unit
                    state.updateReady -> TextButton(onClick = actions.onInstall) { Text(s.updateInstallButton) }
                    else -> TextButton(onClick = actions.onDownload) { Text(s.updateDownloadButton) }
                }
                TextButton(onClick = { uriHandler.openUri(info.url) }) { Text(s.updateOpenButton) }
            }
        }
    }
}
