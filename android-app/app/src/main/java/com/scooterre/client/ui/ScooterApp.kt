package com.scooterre.client.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scooterre.client.viewmodel.Screen
import com.scooterre.client.viewmodel.ScooterViewModel

@Composable
fun ScooterApp(viewModel: ScooterViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    ScooterTheme {
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
