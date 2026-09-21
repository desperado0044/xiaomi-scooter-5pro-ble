package com.scooterre.client.viewmodel

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import com.scooterre.client.protocol.*
import com.scooterre.client.reminder.InsuranceReminders
import com.scooterre.client.reminder.InsuranceSchedule
import com.scooterre.client.ui.*
import com.scooterre.client.update.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update

/** Full backups and importing files (scooter bundle, backup, documents-only file) on the add-scooter screen. */
internal class BackupController(
    private val shared: Shared,
    private val deviceRegistry: DeviceRegistry,
    private val documents: DocumentsController,
    private val settings: SettingsController,
) {
    private val _state get() = shared.state
    private val prefs get() = shared.prefs
    private val s get() = shared.s
    private val app get() = shared.app
    private val scope get() = shared.scope

    fun markBackupDone() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_BACKUP, now).apply()
        _state.update { it.copy(lastBackupMillis = now) }
    }

    fun dismissBackupMessage() = _state.update { it.copy(backupMessage = null) }

    /** Restores a full backup file; [withSettings] also re-applies the app settings stored in it. */
    fun restoreBackup(uri: Uri, password: String?, withSettings: Boolean) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { app.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                    ?.let { BackupBundle.restore(app, it, password, withSettings) }
            }
            when (result) {
                is BackupBundle.RestoreResult.Ok -> {
                    settings.reloadSettings()
                    _state.update { it.copy(knownDevices = deviceRegistry.list()) }
                    documents.refreshDocuments()
                    _state.update { it.copy(backupMessage = s.backupDone(result.devices, result.documents)) }
                }
                BackupBundle.RestoreResult.BadPassword -> _state.update { it.copy(backupMessage = s.importWrongPasswordError) }
                BackupBundle.RestoreResult.TooLarge -> _state.update { it.copy(backupMessage = s.backupTooLarge) }
                else -> _state.update { it.copy(backupMessage = s.importInvalidCodeError) }
            }
        }
    }

    /** Imports an export bundle file (key, name, documents, history); [password] is needed for an
     * encrypted one. */
    /** Imports a file picked on the add-scooter screen: one scooter's bundle, a full backup (new phone,
     * family) or a documents-only file. A backup brings keys, names and documents, not the sender's settings. */
    fun importBundle(uri: Uri, password: String?) {
        scope.launch {
            val data = withContext(Dispatchers.IO) {
                runCatching { app.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (data == null) {
                _state.update { it.copy(error = s.importInvalidCodeError) }
                return@launch
            }
            fun done() {
                _state.update { it.copy(importText = "", error = null, knownDevices = deviceRegistry.list(), screen = Screen.DEVICE_PICKER) }
                documents.refreshDocuments()
            }
            val plainFormat = if (DeviceBundle.kindOf(data) == DeviceBundle.Kind.PLAIN) BundleFormats.plainFormat(data) else null
            when {
                BundleCrypto.isBackup(data) || plainFormat == BackupBundle.FORMAT ->
                    when (withContext(Dispatchers.IO) { BackupBundle.restore(app, data, password, withSettings = false) }) {
                        is BackupBundle.RestoreResult.Ok -> done()
                        BackupBundle.RestoreResult.BadPassword -> _state.update { it.copy(error = s.importWrongPasswordError) }
                        BackupBundle.RestoreResult.TooLarge -> _state.update { it.copy(error = s.backupTooLarge) }
                        else -> _state.update { it.copy(error = s.importInvalidCodeError) }
                    }
                plainFormat == DocumentsBundle.FORMAT ->
                    when (withContext(Dispatchers.IO) { DocumentsBundle.import(app, data) }) {
                        is DocumentsBundle.ImportResult.Ok -> done()
                        DocumentsBundle.ImportResult.UnknownScooter -> _state.update { it.copy(error = s.docsUnknownScooter) }
                        else -> _state.update { it.copy(error = s.importInvalidCodeError) }
                    }
                else ->
                    when (withContext(Dispatchers.IO) { DeviceBundle.import(app, data, password) }) {
                        is DeviceBundle.ImportResult.Ok -> done()
                        DeviceBundle.ImportResult.BadPassword -> _state.update { it.copy(error = s.importWrongPasswordError) }
                        else -> _state.update { it.copy(error = s.importInvalidCodeError) }
                    }
            }
        }
    }
}
