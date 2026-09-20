package com.scooterre.client.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scooterre.client.viewmodel.Screen
import com.scooterre.client.viewmodel.ScooterViewModel

@Composable
fun ScooterApp(viewModel: ScooterViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    val systemDark = isSystemInDarkTheme()
    val ambientBright = rememberAmbientBright(state.themeMode == ThemeMode.AUTO)
    val dark = when (state.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AUTO -> ambientBright?.let { !it } ?: systemDark
    }
    val view = LocalView.current
    // Status-bar icons follow the system theme by default - keep them readable when ours differs.
    SideEffect {
        (view.context as? Activity)?.window?.let {
            WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !dark
        }
    }
    // The dashboard only exists while connected: keep the display on there. keepScreenOn only
    // holds while the window is visible, so the screen may still sleep once the app is in the
    // background.
    val keepScreenOn = state.screen == Screen.DASHBOARD && state.keepScreenOn
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    ScooterTheme(darkTheme = dark) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (state.screen) {
                Screen.LOGIN -> LoginScreen(
                    state = state,
                    onMacChanged = viewModel::onMacChanged,
                    onPinChanged = viewModel::onPinChanged,
                    onStartScan = viewModel::startScan,
                    onPickScanned = viewModel::pickScannedDevice,
                    onConnectSaved = viewModel::connectWithSavedLtmk,
                    onCloudLogin = viewModel::connectWithCloudLogin,
                    onStartQrLogin = viewModel::startQrLogin,
                    onRetryWithPin = viewModel::retryWithPin,
                    onForgetSaved = viewModel::forgetSavedLtmk,
                    onToggleLanguage = viewModel::toggleLanguage,
                    onBackToPicker = viewModel::openDevicePicker,
                    onImportTextChanged = viewModel::onImportTextChanged,
                    onImportDevice = viewModel::importDevice,
                )
                Screen.DASHBOARD -> DashboardScreen(
                    state = state,
                    onRefreshAll = viewModel::refreshAll,
                    onDisconnect = viewModel::disconnect,
                    onToggleLanguage = viewModel::toggleLanguage,
                    onSetBool = viewModel::setBoolProperty,
                    onSetNumeric = viewModel::setNumericProperty,
                    onSetString = viewModel::setStringProperty,
                    onAttributeRideMode = viewModel::attributeRideMode,
                    onSkipPendingRide = viewModel::skipPendingRide,
                    onResetHistory = viewModel::resetEfficiencyHistory,
                    onSetThemeMode = viewModel::setThemeMode,
                    onSetKeepScreenOn = viewModel::setKeepScreenOn,
                )
                Screen.DEVICE_PICKER -> DevicePickerScreen(
                    state = state,
                    onSelectDevice = viewModel::connectKnownDevice,
                    onForgetDevice = { viewModel.forgetDevice(it.mac) },
                    onRenameDevice = { device, name -> viewModel.renameDevice(device.mac, name) },
                    onExportDevice = { device -> viewModel.exportDevice(device.mac) },
                    onDismissExportCode = viewModel::dismissExportCode,
                    onAddDevice = viewModel::startAddDevice,
                    onToggleLanguage = viewModel::toggleLanguage,
                )
            }
        }
    }
}
