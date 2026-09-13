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
                )
                Screen.DASHBOARD -> DashboardScreen(
                    state = state,
                    onRefreshAll = viewModel::refreshAll,
                    onDisconnect = viewModel::disconnect,
                    onSetBool = viewModel::setBoolProperty,
                    onSetNumeric = viewModel::setNumericProperty,
                )
            }
        }
    }
}
