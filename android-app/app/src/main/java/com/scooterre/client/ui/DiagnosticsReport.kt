package com.scooterre.client.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.scooterre.client.diagnostics.Diagnostics
import com.scooterre.client.viewmodel.Screen
import com.scooterre.client.viewmodel.UiState

/** Builds the diagnostics text for a bug report from the current state - see [Diagnostics] for what
 * is deliberately left out (MAC address, serial numbers, keys, documents, account data). */
fun diagnosticsReport(context: Context, state: UiState): String {
    val pkg = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    val firmware = state.values["FIRMWARE_VERSION"]?.value as? String
    val bmsFirmware = state.values["BMS_FIRMWARE_VERSION"]?.value as? String
    return Diagnostics.build(
        Diagnostics.Info(
            app = "${pkg?.versionName ?: "?"} (${pkg?.longVersionCode ?: 0})",
            android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            phone = "${Build.MANUFACTURER} ${Build.MODEL}",
            settings = listOf(
                "language" to state.language.name,
                "units" to state.units.name,
                "orientation" to state.orientationMode.name,
                "autoConnect" to state.autoConnect.toString(),
                "refresh" to state.refreshRate.name,
                "confirmCritical" to state.confirmCritical.toString(),
                "rideTracking" to state.rideTracking.toString(),
                "updateCheck" to state.updateCheck.toString(),
                "appLock" to state.appLock.toString(),
                "insuranceReminder" to state.insuranceReminder.toString(),
            ),
            scooter = "model=${state.activeModel ?: "-"}, firmware=${firmware ?: "-"}, bms=${bmsFirmware ?: "-"}, " +
                "connected=${state.screen == Screen.DASHBOARD}, saved=${state.knownDevices.size} " +
                "(${state.knownDevices.mapNotNull { it.model }.distinct().joinToString().ifEmpty { "-" }})",
            errors = Diagnostics.recentErrors(),
            log = Diagnostics.recentLog(),
        ),
    )
}

fun copyDiagnostics(context: Context, state: UiState) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("diagnostics", diagnosticsReport(context, state)))
}
