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

/** The version that is installed now ("0" if it cannot be read). */
internal fun installedVersionOf(app: Application): String =
    runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "0"

/** The newest release seen at the last check, but only if it is still newer than what is installed
 * now - so the notice disappears by itself right after updating. */
internal fun storedUpdate(prefs: SharedPreferences, installedVersion: String): UpdateInfo? {
    val tag = prefs.getString(KEY_UPDATE_TAG, null) ?: return null
    val url = prefs.getString(KEY_UPDATE_URL, null) ?: return null
    val apk = prefs.getString(KEY_UPDATE_APK_URL, null)
    val sha = prefs.getString(KEY_UPDATE_APK_SHA, null)
    return if (UpdateChecker.isNewer(tag, installedVersion)) UpdateInfo(tag, url, apk, sha) else null
}

/** The daily update check and the "Download update" button. */
internal class UpdateController(private val shared: Shared) {
    private val _state get() = shared.state
    private val prefs get() = shared.prefs
    private val s get() = shared.s
    private val app get() = shared.app
    private val scope get() = shared.scope

    private suspend fun refreshUpdateInfo(force: Boolean) {
        if (!_state.value.updateCheck) return
        val now = System.currentTimeMillis()
        if (force || now - prefs.getLong(KEY_UPDATE_LAST_CHECK, 0L) >= UPDATE_CHECK_INTERVAL_MS) {
            val latest = withContext(Dispatchers.IO) { UpdateChecker.fetchLatest() } ?: return
            prefs.edit().putLong(KEY_UPDATE_LAST_CHECK, now).putString(KEY_UPDATE_TAG, latest.version).putString(KEY_UPDATE_URL, latest.url)
                .putString(KEY_UPDATE_APK_URL, latest.apkUrl).putString(KEY_UPDATE_APK_SHA, latest.apkSha256).apply()
        }
        _state.update { it.copy(availableUpdate = storedUpdate(prefs, installedVersionOf(app))) }
    }

    private var downloadedUpdate: java.io.File? = null

    /** The "Download update" button: downloads and checks the APK, then opens the system installer. */
    fun downloadUpdate() {
        val info = _state.value.availableUpdate ?: return
        if (info.apkUrl == null || _state.value.updateProgress != null) return
        _state.update { it.copy(updateProgress = 0, updateProblem = null, updateReady = false, updateNeedsPermission = false) }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                UpdateInstaller.downloadAndVerify(app, info) { percent -> _state.update { it.copy(updateProgress = percent) } }
            }
            when (result) {
                is UpdateDownloadResult.Ok -> {
                    downloadedUpdate = result.file
                    _state.update { it.copy(updateProgress = null, updateReady = true) }
                    startUpdateInstall()
                }
                is UpdateDownloadResult.Failed -> _state.update { it.copy(updateProgress = null, updateProblem = result.problem) }
            }
        }
    }

    /** The "Install" button, shown once the update is downloaded (e.g. after allowing installs). */
    fun installUpdate() = startUpdateInstall()

    private fun startUpdateInstall() {
        val file = downloadedUpdate?.takeIf { it.exists() }
        if (file == null) {
            _state.update { it.copy(updateReady = false) }
            return
        }
        when (UpdateInstaller.install(app, file)) {
            UpdateInstaller.InstallStart.OPENED -> _state.update { it.copy(updateNeedsPermission = false) }
            UpdateInstaller.InstallStart.NEEDS_PERMISSION -> {
                _state.update { it.copy(updateNeedsPermission = true) }
                UpdateInstaller.openInstallPermissionSettings(app)
            }
        }
    }

    fun checkForUpdateOnStart() {
        scope.launch { refreshUpdateInfo(force = false) }
    }

    fun setUpdateCheck(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_UPDATE_CHECK, enabled).apply()
        _state.update { it.copy(updateCheck = enabled, availableUpdate = if (enabled) storedUpdate(prefs, installedVersionOf(app)) else null) }
        if (enabled) scope.launch { refreshUpdateInfo(force = true) }
    }
}
