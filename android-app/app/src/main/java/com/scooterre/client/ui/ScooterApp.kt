package com.scooterre.client.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scooterre.client.viewmodel.Screen
import com.scooterre.client.viewmodel.ScooterViewModel

@Composable
fun ScooterApp(viewModel: ScooterViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.autoConnectOnStart()
        viewModel.checkForUpdateOnStart()
    }

    val systemDark = isSystemInDarkTheme()
    AmbientBrightnessEffect(state.autoBrightness)
    val dark = when (state.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
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

    // Back gesture steps outward one level at a time: (drawer / sub-section, handled inside
    // DashboardScreen) -> disconnect cleanly to the device list -> only there does it leave the app.
    // Registered before the screens so DashboardScreen's own, later BackHandler takes priority.
    BackHandler(enabled = state.screen == Screen.DASHBOARD) { viewModel.disconnect() }
    BackHandler(enabled = state.screen == Screen.LOGIN && state.knownDevices.isNotEmpty()) { viewModel.openDevicePicker() }
    BackHandler(enabled = state.screen == Screen.APP_SETTINGS) { viewModel.closeAppSettings() }

    val settingsActions = SettingsActions(
        onSetLanguage = viewModel::setLanguage,
        onSetThemeMode = viewModel::setThemeMode,
        onSetAutoBrightness = viewModel::setAutoBrightness,
        onSetKeepScreenOn = viewModel::setKeepScreenOn,
        onSetUnits = viewModel::setUnits,
        onSetRefreshRate = viewModel::setRefreshRate,
        onSetAutoConnect = viewModel::setAutoConnect,
        onSetConfirmCritical = viewModel::setConfirmCritical,
        onSetRideTracking = viewModel::setRideTracking,
        onSetUpdateCheck = viewModel::setUpdateCheck,
    )

    CompositionLocalProvider(LocalUnits provides state.units) {
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
                        settings = settingsActions,
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
                        onOpenSettings = viewModel::openAppSettings,
                    )
                    Screen.APP_SETTINGS -> AppSettingsScreen(
                        state = state,
                        settings = settingsActions,
                        onBack = viewModel::closeAppSettings,
                    )
                }
            }
        }
    }
}
