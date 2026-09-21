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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.withResumed
import com.scooterre.client.security.AppLock
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

    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lockStrings = strings(state.language)
    fun requestUnlock() {
        (context as? FragmentActivity)?.let { activity ->
            AppLock.authenticate(activity, lockStrings.lockPromptTitle, lockStrings.cancelButton) { ok -> if (ok) viewModel.unlock() }
        }
    }
    // Nothing may start (auto-connect, update check) and no prompt may be missed while locked.
    LaunchedEffect(state.locked) {
        if (state.locked) {
            lifecycle.withResumed { requestUnlock() }
        } else {
            viewModel.autoConnectOnStart()
            viewModel.checkForUpdateOnStart()
        }
    }

    val systemDark = isSystemInDarkTheme()
    AmbientBrightnessEffect(state.autoBrightness, forceMax = state.screen == Screen.DOCUMENT_VIEWER)
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
    val keepScreenOn = (state.screen == Screen.DASHBOARD && state.keepScreenOn) || state.screen == Screen.DOCUMENT_VIEWER
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
    BackHandler(enabled = state.screen == Screen.DOCUMENTS || state.screen == Screen.DOCUMENT_VIEWER) { viewModel.navigateBack() }

    // Android 13+ asks for the notification permission once, when the insurance reminders are switched on.
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.setInsuranceReminder(true)
        else Toast.makeText(context, lockStrings.insuranceNotificationsBlocked, Toast.LENGTH_LONG).show()
    }

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
        onRestoreBackup = viewModel::restoreBackup,
        onBackupCreated = viewModel::markBackupDone,
        onDismissBackupMessage = viewModel::dismissBackupMessage,
        onSetInsuranceReminder = { enable ->
            if (!enable) {
                viewModel.setInsuranceReminder(false)
            } else if (android.os.Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                Toast.makeText(context, lockStrings.insuranceNotificationsBlocked, Toast.LENGTH_LONG).show()
            } else {
                viewModel.setInsuranceReminder(true)
            }
        },
        onTestInsuranceNotification = {
            val sent = viewModel.sendInsuranceTest()
            Toast.makeText(context, if (sent) lockStrings.insuranceTestSent else lockStrings.insuranceNotificationsBlocked, Toast.LENGTH_LONG).show()
        },
        onSetAppLock = { enable ->
            if (!enable) {
                viewModel.setAppLock(false)
            } else if (!AppLock.isAvailable(context)) {
                Toast.makeText(context, lockStrings.lockUnavailable, Toast.LENGTH_LONG).show()
            } else {
                // Turning it on must first succeed once, so the lock can never lock the owner out.
                (context as? FragmentActivity)?.let { activity ->
                    AppLock.authenticate(activity, lockStrings.lockPromptTitle, lockStrings.cancelButton) { ok -> if (ok) viewModel.setAppLock(true) }
                }
            }
        },
    )

    val documentActions = DocumentActions(
        onSelectDevice = viewModel::selectDocumentsDevice,
        onOpen = viewModel::openDocument,
        onAddPhotos = viewModel::addDocumentPhotos,
        onImport = viewModel::addDocumentFromUri,
        onAppendPhotos = viewModel::appendDocumentPhotos,
        onRename = viewModel::renameDocument,
        onDelete = viewModel::deleteDocument,
        onImportBundle = viewModel::importDocumentsBundle,
        onSetInsuranceApplied = viewModel::setInsuranceApplied,
    )

    CompositionLocalProvider(
        LocalUnits provides state.units,
        LocalUpdateActions provides UpdateActions(viewModel::downloadUpdate, viewModel::installUpdate),
    ) {
        ScooterTheme(darkTheme = dark) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (state.locked) {
                    LockScreen(lockStrings) { requestUnlock() }
                    return@Surface
                }
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
                        onOpenSettings = viewModel::openAppSettings,
                        onBackToPicker = viewModel::openDevicePicker,
                        onImportTextChanged = viewModel::onImportTextChanged,
                        onImportDevice = viewModel::importDevice,
                        onImportBundle = viewModel::importBundle,
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
                        onOpenDocuments = viewModel::openDocuments,
                    )
                    Screen.APP_SETTINGS -> AppSettingsScreen(
                        state = state,
                        settings = settingsActions,
                        onBack = viewModel::closeAppSettings,
                    )
                    Screen.DOCUMENTS -> DocumentsScreen(state, documentActions, viewModel::navigateBack)
                    Screen.DOCUMENT_VIEWER -> DocumentViewerScreen(state, documentActions, viewModel::navigateBack)
                }
            }
        }
    }
}
