package com.scooterre.client.viewmodel

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scooterre.client.ble.FoundDevice
import com.scooterre.client.ble.ScooterScanner
import com.scooterre.client.cloud.CloudDeviceMatch
import com.scooterre.client.diagnostics.Diagnostics
import com.scooterre.client.cloud.CloudException
import com.scooterre.client.cloud.PinRequiredException
import com.scooterre.client.cloud.QrLoginStart
import com.scooterre.client.cloud.XiaomiCloudClient
import com.scooterre.client.protocol.BackupBundle
import com.scooterre.client.protocol.BatteryHistoryStore
import com.scooterre.client.protocol.DeviceBundle
import com.scooterre.client.protocol.DeviceExport
import com.scooterre.client.protocol.DeviceRegistry
import com.scooterre.client.protocol.DocumentStore
import com.scooterre.client.protocol.KnownDevice
import com.scooterre.client.protocol.MiProtocol
import com.scooterre.client.protocol.ModelSupport
import com.scooterre.client.protocol.RideWindow
import com.scooterre.client.protocol.LiveRideTracker
import com.scooterre.client.protocol.PropertyExplorer
import com.scooterre.client.protocol.ProtocolException
import com.scooterre.client.protocol.ScooterDocument
import com.scooterre.client.protocol.SecureStore
import com.scooterre.client.protocol.SpecClient
import com.scooterre.client.protocol.SpecProfile
import com.scooterre.client.protocol.SpecProfiles
import com.scooterre.client.protocol.SpecProperty
import com.scooterre.client.protocol.SpecReadResult
import com.scooterre.client.protocol.SpecType
import com.scooterre.client.protocol.encodeValue
import com.scooterre.client.protocol.BundleCrypto
import com.scooterre.client.protocol.BundleFormats
import com.scooterre.client.protocol.DocumentsBundle
import com.scooterre.client.reminder.InsuranceReminders
import com.scooterre.client.reminder.InsuranceSchedule
import com.scooterre.client.ui.Lang
import com.scooterre.client.ui.resolveLang
import com.scooterre.client.ui.ThemeMode
import com.scooterre.client.ui.OrientationMode
import com.scooterre.client.ui.UnitSystem
import com.scooterre.client.ui.distance
import com.scooterre.client.ui.distanceUnit
import com.scooterre.client.update.UpdateChecker
import com.scooterre.client.update.UpdateDownloadResult
import com.scooterre.client.update.UpdateInfo
import com.scooterre.client.update.UpdateInstaller
import com.scooterre.client.update.UpdateProblem
import com.scooterre.client.ui.modelDisplayName
import com.scooterre.client.ui.propertyName
import com.scooterre.client.ui.strings
import com.scooterre.client.widget.ScooterWidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

// Deliberately left blank rather than pre-filled with a real device's MAC - this is a public
// build meant for anyone's own scooter, not just the one it was originally developed against.
const val DEFAULT_SCOOTER_MAC = ""

/** Total time [ScooterViewModel.connectAndLogin] keeps trying (scanning for the scooter's
 * advertisement, then connecting) before giving up - not a fixed attempt count, since most of it
 * is normally spent simply waiting for the scooter to be switched on/in range, which can take any
 * amount of time within reason. Long enough to comfortably cover fumbling for the power button,
 * short enough that a picker tile doesn't sit "Verbinde ..." forever if it's genuinely out of reach. */
private const val CONNECT_BUDGET_MS = 25_000L

/** Polled every ~2.5s (see [ScooterViewModel.startAutoRefresh]) while actually riding, instead of
 * the full ~50-property table - small enough that one pass stays well inside that window even
 * without batching, and covers exactly what changes meaningfully second-to-second on a moving
 * scooter (plus IS_RIDING itself, so the loop notices when the ride ends and drops back to the
 * slower full-sweep cadence). */
private val RIDE_PRIORITY_PROPERTIES = setOf(
    "IS_RIDING", "AVERAGE_SPEED", "CURRENT_MILEAGE", "BATTERY_LEVEL", "REMAINING_MILEAGE", "RIDING_TIME", "RIDING_MODE",
    // for the live ride log (see LiveRideTracker): the odometer - BATTERY_LEVEL above doubles as
    // the ride's cost, no separate mAh/voltage reads needed for it (see RideWindow's doc comment)
    "TOTAL_MILEAGE",
)

/** Pause between full property sweeps while parked (a sweep itself takes ~9s on top). */

/**
 * Holds the ONE persistent [MiProtocol] session for the app's lifetime: connect+login happens
 * once (either via a freshly cloud-fetched `ltmk` or a previously saved one), then every
 * property read/write reuses that same BLE connection - never reconnects per request.
 */
class ScooterViewModel(application: Application) : AndroidViewModel(application) {

    private val secureStore = SecureStore(application)
    // MAC addresses aren't secret (advertised openly over BLE) - a plain, unencrypted prefs file
    // is enough just to save the user from re-scanning/retyping it on every app start.
    private val prefs = application.getSharedPreferences("scooter_prefs", Context.MODE_PRIVATE)
    private val deviceRegistry = DeviceRegistry(application)
    private val batteryHistoryStore = BatteryHistoryStore(application)
    private val documentStore = DocumentStore(application)
    private var protocol: MiProtocol? = null
    // Every property read/write/explore that depends on the current BLE connection launches into
    // this scope instead of viewModelScope directly - cancelled the instant disconnect() runs, so
    // an in-flight one is interrupted cleanly (CancellationException) instead of racing the GATT
    // teardown (ScooterBleManager.disconnect() nulls its `gatt` synchronously) and surfacing as a
    // scary, generic "Characteristic ... not found" error - confirmed by the user, 2026-09-22, both
    // right after a fresh connect (refreshAll's own initial sweep still running) and on manual
    // disconnect while something else was mid-request. Recreated fresh on every successful connect.
    private var connectionScope: CoroutineScope? = null

    // Kept around across the "PIN required" round-trip so retryWithPin() doesn't have to repeat
    // the whole cloud login (password or QR) just because the device also needs its sharing PIN.
    private var pendingCloud: XiaomiCloudClient? = null
    private var pendingDevice: CloudDeviceMatch? = null

    private val _state = MutableStateFlow(
        run {
            val known = deviceRegistry.list()
            UiState(
                // Land on the picker when scooters are already known (most returning users), or
                // straight on LOGIN for a first-ever run - matches the old single-device app's
                // behavior for exactly one saved device (the picker just becomes a 1-item list).
                screen = if (known.isNotEmpty()) Screen.DEVICE_PICKER else Screen.LOGIN,
                macAddress = prefs.getString(KEY_LAST_MAC, DEFAULT_SCOOTER_MAC) ?: DEFAULT_SCOOTER_MAC,
                language = resolveLang(prefs.getString(KEY_LANG, null)),
                themeMode = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, null) ?: "SYSTEM") }
                    .getOrDefault(ThemeMode.SYSTEM),
                orientationMode = runCatching { OrientationMode.valueOf(prefs.getString(KEY_ORIENTATION_MODE, null) ?: "AUTO") }
                    .getOrDefault(OrientationMode.AUTO),
                keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true),
                autoBrightness = prefs.getBoolean(KEY_AUTO_BRIGHTNESS, false),
                units = runCatching { UnitSystem.valueOf(prefs.getString(KEY_UNITS, null) ?: "METRIC") }.getOrDefault(UnitSystem.METRIC),
                autoConnect = prefs.getBoolean(KEY_AUTO_CONNECT, false),
                refreshRate = runCatching { RefreshRate.valueOf(prefs.getString(KEY_REFRESH_RATE, null) ?: "NORMAL") }.getOrDefault(RefreshRate.NORMAL),
                confirmCritical = prefs.getBoolean(KEY_CONFIRM_CRITICAL, false),
                rideTracking = prefs.getBoolean(KEY_RIDE_TRACKING, true),
                updateCheck = prefs.getBoolean(KEY_UPDATE_CHECK, true),
                appLock = prefs.getBoolean(KEY_APP_LOCK, false),
                locked = prefs.getBoolean(KEY_APP_LOCK, false),
                insuranceReminder = InsuranceReminders.isEnabled(application),
                insuranceApplied = known.filter {
                    InsuranceReminders.isApplied(application, it.mac, InsuranceSchedule.expiryFor(java.time.LocalDate.now()))
                }.map { it.mac }.toSet(),
                lastBackupMillis = prefs.getLong(KEY_LAST_BACKUP, 0L),
                documentCounts = known.associate { it.mac to documentStore.count(it.mac) },
                availableUpdate = if (prefs.getBoolean(KEY_UPDATE_CHECK, true)) storedUpdate(prefs, installedVersionOf(application)) else null,
                knownDevices = known,
            )
        }
    )
    val state: StateFlow<UiState> = _state

    // Feature controllers (each in its own file) work on the same state and preferences; the public
    // functions below just forward to them, so the screens keep calling the ViewModel as before.
    private val shared = Shared(application, viewModelScope, _state, prefs)
    private val settings = SettingsController(shared, onUnitsChanged = ::pushWidgetUpdate)
    private val insurance = InsuranceController(shared, deviceRegistry)
    private val documents = DocumentsController(shared, deviceRegistry, documentStore, ::pushScreen, insurance::insuranceAppliedNow)
    private val updates = UpdateController(shared)
    private val backups = BackupController(shared, deviceRegistry, documents, settings)

    fun openDocuments(mac: String?) = documents.openDocuments(mac)
    fun selectDocumentsDevice(mac: String) = documents.selectDocumentsDevice(mac)
    fun openDocument(id: String) = documents.openDocument(id)
    fun addDocumentPhotos(uris: List<Uri>, name: String) = documents.addDocumentPhotos(uris, name)
    fun addDocumentFromUri(uri: Uri, name: String) = documents.addDocumentFromUri(uri, name)
    fun appendDocumentPhotos(docId: String, uris: List<Uri>) = documents.appendDocumentPhotos(docId, uris)
    fun renameDocument(docId: String, name: String) = documents.renameDocument(docId, name)
    fun deleteDocument(docId: String) = documents.deleteDocument(docId)
    fun importDocumentsBundle(uri: Uri) = documents.importDocumentsBundle(uri)

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)
    fun setOrientationMode(mode: OrientationMode) = settings.setOrientationMode(mode)
    fun setAutoBrightness(enabled: Boolean) = settings.setAutoBrightness(enabled)
    fun setLanguage(lang: Lang) = settings.setLanguage(lang)
    fun setUnits(units: UnitSystem) = settings.setUnits(units)
    fun setRefreshRate(rate: RefreshRate) = settings.setRefreshRate(rate)
    fun setAutoConnect(enabled: Boolean) = settings.setAutoConnect(enabled)
    fun setConfirmCritical(enabled: Boolean) = settings.setConfirmCritical(enabled)
    fun setRideTracking(enabled: Boolean) = settings.setRideTracking(enabled)
    fun setKeepScreenOn(enabled: Boolean) = settings.setKeepScreenOn(enabled)
    fun toggleLanguage() = settings.toggleLanguage()
    fun setAppLock(enabled: Boolean) = settings.setAppLock(enabled)

    fun downloadUpdate() = updates.downloadUpdate()
    fun installUpdate() = updates.installUpdate()
    fun checkForUpdateOnStart() = updates.checkForUpdateOnStart()
    fun setUpdateCheck(enabled: Boolean) = updates.setUpdateCheck(enabled)

    fun refreshInsuranceState() = insurance.refreshInsuranceState()
    fun setInsuranceReminder(enabled: Boolean) = insurance.setInsuranceReminder(enabled)
    fun setInsuranceApplied(mac: String, applied: Boolean) = insurance.setInsuranceApplied(mac, applied)
    fun sendInsuranceTest(): Boolean = insurance.sendInsuranceTest()

    fun markBackupDone() = backups.markBackupDone()
    fun dismissBackupMessage() = backups.dismissBackupMessage()
    fun restoreBackup(uri: Uri, password: String?, withSettings: Boolean) = backups.restoreBackup(uri, password, withSettings)
    fun importBundle(uri: Uri, password: String?) = backups.importBundle(uri, password)

    init {
        _state.update { it.copy(hasSavedLtmk = secureStore.loadLtmk(it.macAddress) != null) }
        UpdateInstaller.cleanup(getApplication())
        // The last error messages go into the copyable diagnostics text (see Diagnostics).
        viewModelScope.launch { _state.map { it.error }.distinctUntilChanged().collect { message -> message?.let(Diagnostics::recordError) } }
        if (_state.value.insuranceReminder) {
            // Keep the daily job scheduled and catch up on a stage the job may have missed.
            val app = getApplication<Application>()
            InsuranceReminders.schedule(app, replace = false)
            viewModelScope.launch(Dispatchers.IO) { InsuranceReminders.checkAndNotify(app) }
        }
    }

    // Screens that are opened on top of the current one (settings, documents) remember where to
    // return to; the connect/disconnect flows set their screens directly and clear this.
    private val screenStack = ArrayDeque<Screen>()

    private fun pushScreen(target: Screen) {
        val current = _state.value.screen
        if (current == target) return
        screenStack.addLast(current)
        _state.update { it.copy(screen = target) }
    }

    fun navigateBack() {
        val previous = screenStack.removeLastOrNull() ?: Screen.DEVICE_PICKER
        // Settings opened from the empty first-run login screen (to restore a backup) must not
        // return there once scooters exist.
        _state.update {
            it.copy(screen = if (previous == Screen.LOGIN && it.knownDevices.isNotEmpty()) Screen.DEVICE_PICKER else previous)
        }
    }

    /** App settings work without a connected scooter - opened from the device list (or login). */
    fun openAppSettings() = pushScreen(Screen.APP_SETTINGS)

    fun closeAppSettings() = navigateBack()

    private var autoConnectAttempted = false

    /** Called once per process start (not on every recomposition, and not again after the user
     * disconnects to the device list) - with the setting on, connects straight to the scooter
     * that was last connected successfully. */
    fun autoConnectOnStart() {
        if (autoConnectAttempted) return
        autoConnectAttempted = true
        if (!_state.value.autoConnect || _state.value.screen != Screen.DEVICE_PICKER) return
        val lastMac = prefs.getString(KEY_LAST_CONNECTED, null) ?: return
        val device = deviceRegistry.list().firstOrNull { it.mac.equals(lastMac, ignoreCase = true) } ?: return
        if (secureStore.loadLtmk(device.mac) == null) return
        if (SpecProfiles.supportOf(device.model) == ModelSupport.UNSUPPORTED) return
        connectKnownDevice(device)
    }

    private val afterUnlock = mutableListOf<() -> Unit>()

    /** Runs [action] now, or - while the app is locked - right after it gets unlocked. */
    fun runWhenUnlocked(action: () -> Unit) {
        if (_state.value.locked) afterUnlock += action else action()
    }

    fun unlock() {
        _state.update { it.copy(locked = false) }
        val pending = afterUnlock.toList()
        afterUnlock.clear()
        pending.forEach { it() }
    }

    fun onMacChanged(mac: String) {
        prefs.edit().putString(KEY_LAST_MAC, mac).apply()
        _state.update {
            it.copy(macAddress = mac, deviceName = null, activeModel = null, hasSavedLtmk = secureStore.loadLtmk(mac) != null)
        }
    }

    fun onPinChanged(pin: String) = _state.update { it.copy(pin = pin) }

    /** Shows the saved-scooters list, refreshed from disk in case a device was added elsewhere. */
    fun openDevicePicker() {
        _state.update { it.copy(screen = Screen.DEVICE_PICKER, knownDevices = deviceRegistry.list(), error = null) }
    }

    /** Clears the add-device scratch fields and shows the login/add-device screen - used both for
     * a first-ever run and for "add another scooter" from the picker. */
    fun startAddDevice() {
        _state.update {
            it.copy(
                screen = Screen.LOGIN, macAddress = DEFAULT_SCOOTER_MAC, deviceName = null,
                activeModel = null, hasSavedLtmk = false, pin = "", error = null,
            )
        }
    }

    /** Connects to an already-known scooter using its saved key - the picker's tap-to-connect. Its
     * own try/catch rather than the generic [launchBusy] - a failure updates [UiState.connectFailedMac]/
     * [UiState.connectFailedError] instead of the shared [UiState.error], which is also written by
     * unrelated background calls (see the field's own doc comment) and produced an intermittent,
     * misleading red tile even after a clean manual disconnect (confirmed by the user, 2026-09-22:
     * "manchmal, nicht immer"). */
    fun connectKnownDevice(device: KnownDevice) {
        viewModelScope.launch {
            _state.update {
                it.copy(busy = true, busyMessage = s.connectingSavedBusy, connectingMac = device.mac, connectFailedMac = null, connectFailedError = null)
            }
            try {
                val ltmk = secureStore.loadLtmk(device.mac) ?: throw CloudException(s.noSavedKeyError(device.mac))
                _state.update { it.copy(macAddress = device.mac, deviceName = device.name, activeModel = device.model) }
                connectAndLogin(device.mac, ltmk)
            } catch (e: Exception) {
                android.util.Log.e("ScooterVM", "connectKnownDevice failed", e)
                _state.update { it.copy(connectFailedMac = device.mac, connectFailedError = e.message ?: e.toString()) }
            } finally {
                _state.update { it.copy(busy = false, busyMessage = "", connectingMac = null) }
            }
        }
    }

    /** Removes a saved scooter's key and its entry in the device list - offered from the picker. */
    fun forgetDevice(mac: String) {
        secureStore.clearLtmk(mac)
        deviceRegistry.remove(mac)
        documentStore.deleteAll(mac)
        batteryHistoryStore.clear(mac)
        _state.update { it.copy(knownDevices = deviceRegistry.list()) }
        documents.refreshDocuments()
    }

    /** Sets a user-chosen label for a saved device - the only way to tell two same-model-table
     * scooters (5 Pro vs. 5 Max: proven to answer BLE reads identically, see project research log)
     * apart when a device was only ever connected via its saved key, which never learns the cloud
     * model string (no cloud round-trip on that path). An empty [name] clears the label back to
     * the generic/model-based fallback. */
    /** Packs a saved device's MAC/model/name + its `ltmk` into one shareable text blob (see
     * [DeviceExport]) - lets a second person authorized on the same physical scooter add it on
     * their own phone via [importDevice] instead of repeating the cloud login/PIN dance. Sets
     * [UiState.exportCode]; the UI shows it in a dialog with a share button. No-op (silently) if
     * the device or its key isn't actually saved - can't happen from the picker UI, which only
     * offers this action for devices already in the list. */
    fun exportDevice(mac: String) {
        val device = deviceRegistry.list().firstOrNull { it.mac.equals(mac, ignoreCase = true) } ?: return
        val ltmk = secureStore.loadLtmk(mac) ?: return
        _state.update { it.copy(exportCode = DeviceExport.encode(device, ltmk), exportMac = device.mac) }
    }

    fun dismissExportCode() = _state.update { it.copy(exportCode = null, exportMac = null) }

    fun onImportTextChanged(text: String) = _state.update { it.copy(importText = text) }

    /** Decodes an [exportDevice]-produced text blob and saves it directly - no cloud round-trip
     * at all, since the whole point is avoiding that for someone who didn't do the original
     * cloud login. Surfaces a generic error via the normal [UiState.error] field on anything that
     * doesn't parse (the pasted text got mangled, wrong code, etc.). */
    fun importDevice() {
        val decoded = DeviceExport.decode(_state.value.importText)
        if (decoded == null) {
            _state.update { it.copy(error = s.importInvalidCodeError) }
            return
        }
        val (device, ltmk) = decoded
        secureStore.saveLtmk(device.mac, ltmk)
        deviceRegistry.upsert(device)
        _state.update {
            it.copy(
                importText = "", error = null,
                knownDevices = deviceRegistry.list(),
                screen = Screen.DEVICE_PICKER,
            )
        }
    }

    fun renameDevice(mac: String, name: String) {
        deviceRegistry.setName(mac, name.ifBlank { null })
        val updated = deviceRegistry.list()
        _state.update {
            it.copy(
                knownDevices = updated,
                // Keep the currently-connected device's displayed name in sync if it's the one
                // being renamed, instead of only updating the (currently unseen) picker list.
                deviceName = if (it.macAddress.equals(mac, ignoreCase = true)) name.ifBlank { null } else it.deviceName,
            )
        }
    }

    private var scanJob: Job? = null

    /** Scans for nearby BLE devices for ~8s so a new user can pick their scooter by name
     * instead of needing to already know its MAC address (e.g. from adb or Mi Home). */
    fun startScan() {
        scanJob?.cancel()
        _state.update { it.copy(scanning = true, scanResults = emptyList()) }
        scanJob = viewModelScope.launch {
            try {
                withTimeoutOrNull(8000L) {
                    ScooterScanner(getApplication()).scan().collect { found ->
                        _state.update {
                            if (it.scanResults.any { d -> d.address == found.address }) it
                            else it.copy(scanResults = it.scanResults + found)
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: e.toString()) }
            } finally {
                _state.update { it.copy(scanning = false) }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        _state.update { it.copy(scanning = false) }
    }

    fun pickScannedDevice(address: String) {
        stopScan()
        val bleName = _state.value.scanResults.firstOrNull { it.address == address }?.name
        onMacChanged(address)
        _state.update { it.copy(deviceName = bleName) }
    }

    private val s get() = strings(_state.value.language)

    /** Uses the previously saved `ltmk` for the current MAC - no cloud round-trip at all. */
    fun connectWithSavedLtmk() = launchBusy(s.connectingSavedBusy) {
        val mac = _state.value.macAddress
        val ltmk = secureStore.loadLtmk(mac) ?: throw CloudException(s.noSavedKeyError(mac))
        connectAndLogin(mac, ltmk)
    }

    /** Password-based cloud login - only works for accounts that have a separate Mi password set
     * (not accounts only linked via Google/Apple sign-in - use [startQrLogin] for those). */
    fun connectWithCloudLogin(username: String, password: String) = launchBusy(s.cloudLoginBusy) {
        val cloud = XiaomiCloudClient()
        withContext(Dispatchers.IO) { cloud.login(username, password) }
        finishCloudLogin(cloud)
    }

    /** Starts a QR login: fetches the QR image, shows it, then waits in the background for the
     * scan/confirmation - no password needed at all, works for any Mi account. */
    fun startQrLogin() {
        val cloud = XiaomiCloudClient()
        viewModelScope.launch {
            _state.update { it.copy(busy = true, busyMessage = s.qrLoadingBusy, error = null) }
            val start: QrLoginStart
            try {
                start = withContext(Dispatchers.IO) { cloud.startQrLogin() }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: e.toString()) }
                return@launch
            }
            _state.update { it.copy(busy = false, qrPng = start.qrPng, qrLoginUrl = start.loginUrl, qrWaiting = true) }
            try {
                withContext(Dispatchers.IO) { cloud.awaitQrLogin() }
                _state.update { it.copy(qrWaiting = false, qrPng = null, qrLoginUrl = null) }
                finishCloudLogin(cloud)
            } catch (e: Exception) {
                // finishCloudLogin() sets busy=true itself (it's not wrapped in launchBusy, since
                // it's also called from the already-launchBusy-wrapped password/retryWithPin
                // paths) - if it throws anything past its own PinRequiredException handling (e.g.
                // the BLE connect failing after all retries), that busy=true was never cleared
                // here, permanently disabling every button until the app was force-killed
                // (confirmed live: reproducible whenever the scooter is unreachable during the
                // QR-login path specifically - the password-login path is fine, it's wrapped in
                // launchBusy which always resets busy in its own finally).
                _state.update {
                    it.copy(busy = false, qrWaiting = false, qrPng = null, qrLoginUrl = null, error = e.message ?: e.toString())
                }
            }
        }
    }

    /** Shared tail of both login paths: find the scooter's `did` by BLE MAC, fetch+decrypt its
     * `ltmk` (asking for the device sharing PIN if needed), save it, then connect over BLE. */
    private suspend fun finishCloudLogin(cloud: XiaomiCloudClient) {
        _state.update { it.copy(busy = true, busyMessage = s.searchingDeviceBusy) }
        val mac = _state.value.macAddress
        val found = withContext(Dispatchers.IO) { cloud.findDeviceByMac(mac) }
            ?: throw CloudException(s.deviceNotFoundError(mac))
        _state.update { it.copy(deviceName = found.name ?: it.deviceName, activeModel = found.model) }

        updateBusyMessage(s.fetchingKeyBusy)
        try {
            val ltmk = withContext(Dispatchers.IO) { cloud.fetchLtmk(found.did, found.country, _state.value.pin.ifBlank { null }) }
            secureStore.saveLtmk(mac, ltmk)
            _state.update { it.copy(hasSavedLtmk = true, needsPin = false) }
            connectAndLogin(mac, ltmk)
        } catch (e: PinRequiredException) {
            pendingCloud = cloud
            pendingDevice = found
            _state.update {
                it.copy(busy = false, needsPin = true, error = s.pinRequiredError)
            }
        }
    }

    /** Retries only the ltmk fetch + connect after the user entered a PIN, reusing the already
     * cloud-logged-in session from [finishCloudLogin] instead of logging in again. */
    fun retryWithPin() = launchBusy(s.fetchingKeyBusy) {
        val cloud = pendingCloud ?: throw CloudException(s.noActiveSessionError)
        val device = pendingDevice ?: throw CloudException(s.noRememberedDeviceError)
        val mac = _state.value.macAddress
        val ltmk = withContext(Dispatchers.IO) { cloud.fetchLtmk(device.did, device.country, _state.value.pin.ifBlank { null }) }
        secureStore.saveLtmk(mac, ltmk)
        pendingCloud = null
        pendingDevice = null
        _state.update { it.copy(hasSavedLtmk = true, needsPin = false) }
        connectAndLogin(mac, ltmk)
    }

    private suspend fun connectAndLogin(mac: String, ltmk: ByteArray) {
        val model = _state.value.activeModel
        val support = SpecProfiles.supportOf(model)
        Diagnostics.note("connect: model=${model ?: "-"} support=$support")
        // Any previous, still-open connection (from an earlier failed attempt this session) must
        // be torn down first - the scooter/BLE stack gets confused by multiple simultaneous GATT
        // clients from this app, silently dropping notifications instead of acking anything.
        protocol?.dispose()
        protocol = null

        val manager = getApplication<Application>().getSystemService(BluetoothManager::class.java)
        val device = manager.adapter.getRemoteDevice(mac)
        val scanner = ScooterScanner(getApplication())

        // A direct connectGatt() (autoConnect=false, see ScooterBleManager) only succeeds against
        // a device that is already advertising at the exact instant it's called - it does not
        // itself keep listening for the scooter to show up a moment later. Confirmed live: turning
        // the scooter on even one second after tapping "Verbinden" never connected without this.
        // So every attempt below first waits for an actual advertisement from this MAC, then does
        // the (normally fast, ~1-2s) direct connect - which also means a scooter that's simply off
        // shows "Suche ..." instead of burning through blind GATT-timeout retries, and turning it
        // on mid-wait is caught the moment the next advertisement arrives.
        val deadline = SystemClock.elapsedRealtime() + CONNECT_BUDGET_MS
        var lastError: Exception? = null
        var attempt = 0
        while (true) {
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0) break
            attempt++
            updateBusyMessage(if (attempt == 1) s.waitingForScooterBusy else s.retryingBusy(attempt))
            val seen = withTimeoutOrNull(remaining) { scanner.watchForDevice(mac).first() }
            if (seen == null) break // budget ran out while the scooter never showed up

            updateBusyMessage(s.connectingBluetoothBusy)
            val p = try {
                MiProtocol.connect(getApplication(), device)
            } catch (e: Exception) {
                Diagnostics.note("connect attempt $attempt failed: ${e.javaClass.simpleName}: ${e.message}")
                lastError = e
                delay(500)
                continue
            }
            try {
                updateBusyMessage(s.authenticatingBusy)
                p.login(ltmk)
            } catch (e: Exception) {
                Diagnostics.note("login attempt $attempt failed: ${e.javaClass.simpleName}: ${e.message}")
                p.dispose()
                lastError = e
                delay(500)
                continue
            }
            Diagnostics.note("login ok (attempt $attempt)")
            protocol = p
            layoutChecked = false
            liveRide.reset()
            connectionScope?.cancel()
            connectionScope = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob())
            // Reacts to a connection dying underneath us (explicitly or - the common real-world
            // case on some BLE stacks - silently, see ScooterBleManager.connectionLost's doc
            // comment) instead of the previous behaviour: every read kept quietly failing and
            // re-failing forever, the UI just sitting on the last values it ever saw with no
            // indication anything was wrong (confirmed by the user, 2026-09-22: scooter at 100%,
            // app still showing 95% from the last live reading, no error visible anywhere).
            // first() (not collect) - one shot, since disconnect() below tears this scope down
            // anyway, and a second emission during the same dying connection shouldn't retrigger.
            connectionScope?.launch {
                p.connectionLost.first()
                if (protocol === p) {
                    Diagnostics.note("connection lost (silent or explicit) - returning to device list")
                    connectionDied(mac, s.connectionLostError)
                }
            }
            if (support == ModelSupport.UNSUPPORTED) {
                // Not this app's table: read only what the scooter offers, write nothing, then let go.
                try {
                    runExplorer(p.requireSpecClient())
                } finally {
                    disconnect()
                }
                return
            }
            deviceRegistry.upsert(KnownDevice(mac = mac, model = model, name = _state.value.deviceName))
            prefs.edit().putString(KEY_LAST_CONNECTED, mac).apply()
            screenStack.clear()
            _state.update {
                it.copy(
                    screen = Screen.DASHBOARD, error = null, layoutMismatch = false,
                    activeSpecProfile = SpecProfiles.forModel(model),
                    knownDevices = deviceRegistry.list(),
                )
            }
            refreshAll()
            startAutoRefresh()
            return
        }
        // lastError is only set once a direct connect/login attempt actually ran and failed - if
        // the scooter's advertisement was never seen at all within the budget, say so plainly
        // instead of the more generic "Could not connect to device" from a GATT-level failure.
        throw lastError ?: ProtocolException(s.scooterNotFoundError)
    }

    private var autoRefreshJob: Job? = null

    /** Keeps every displayed value live without the user having to tap "Aktualisieren" - runs
     * quietly in the background (no busy spinner, no error banner on a transient failure) so it
     * doesn't fight the manual refresh/set actions for the UI's attention. Individual requests
     * are still safe to interleave with manual actions: SpecClient's own mutex (see its comment)
     * serializes all of them regardless of which caller issued them.
     *
     * Two cadences, not one - the previous single ~19s cycle (9s active pass over all ~50
     * properties + 10s pause) was fine while parked but too sluggish while actually riding, where
     * speed/distance/battery genuinely change second to second. While [RIDE_PRIORITY_PROPERTIES]'s
     * own IS_RIDING reads true, only that small, ride-relevant subset is polled, every ~2.5s -
     * matching the motor controller's own internal telemetry push cadence (2560 MCU ticks ≈ 2.5s,
     * see reference/SCOOTER_5_PRO/research/REPORT.md §33-34's `61 30 0A` push-frame analysis)
     * rather than an arbitrary faster number: the underlying values don't update at the source any
     * faster than that, so polling quicker would just re-read the same stale number sooner.
     * Once stopped/parked, it falls back to the full ~19s sweep as before. Batching several
     * properties into one request is not an option: the scooter answers only the first object of
     * a multi-object GET and returns error records for the rest (verified live, 2026-09-20). */
    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                // IS_RIDING is 0=Steht/Standing, 1=Übergang/Transitioning, 2=Fährt/Riding - "not
                // standing" (not just the single value 2) so the fast cadence, and live tracking
                // below, both start the moment the scooter leaves "Steht" instead of only for
                // the brief transitional tick before it (a real bug until 2026-09-22: both this
                // check and trackLiveRide's compared against exactly 1, so an entire real ride -
                // state 2 throughout - read as "parked" and recorded nothing).
                val riding = ((_state.value.values["IS_RIDING"]?.takeIf { it.ok }?.value as? Long) ?: 0L) != 0L
                delay(if (riding) 2_500L else _state.value.refreshRate.idleDelayMs)
                val spec = protocol?.requireSpecClient() ?: break
                try {
                    // Collected locally and applied in one state update at the end, instead of
                    // one update per property - a background refresh should swap all values over
                    // at once, not visibly re-build the screen property by property the way the
                    // very first load after connecting does.
                    val results = mutableMapOf<String, SpecReadResult>()
                    val allProperties = _state.value.activeSpecProfile.all
                    val toRead = if (riding) allProperties.filter { it.name in RIDE_PRIORITY_PROPERTIES } else allProperties
                    for (property in toRead) {
                        results[property.name] = withContext(Dispatchers.IO) { spec.get(property) }
                    }
                    _state.update { it.copy(values = it.values + results) }
                    trackLiveRide()
                    pushWidgetUpdate()
                } catch (e: Exception) {
                    android.util.Log.w("ScooterVM", "auto-refresh tick failed", e)
                }
            }
        }
    }

    private fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    /** A connection died - explicitly or silently, see [MiProtocol.connectionLost] - while the app
     * still thought it was live. Tears down like a normal [disconnect] and returns to the device
     * list, but leaves a reason on that device's tile (red, like a failed connect attempt)
     * afterwards instead of disconnect()'s usual clean slate: the user did nothing here and should
     * see why they're suddenly back at the list instead of wondering if they misclicked. */
    private fun connectionDied(mac: String, message: String) {
        disconnect()
        _state.update { it.copy(connectFailedMac = mac, connectFailedError = message) }
    }

    /** Cleanly closes the BLE connection and returns to the login screen - lets the user end the
     * session properly (or switch to a different scooter) instead of the only alternative being
     * to kill the app, which skips this cleanup and is what causes the next connect attempt to
     * need its automatic retry. */
    fun disconnect() {
        stopAutoRefresh()
        connectionScope?.cancel()
        connectionScope = null
        liveRide.reset()
        protocol?.dispose()
        protocol = null
        screenStack.clear()
        val known = deviceRegistry.list()
        _state.update {
            it.copy(
                screen = if (known.isNotEmpty()) Screen.DEVICE_PICKER else Screen.LOGIN,
                values = emptyMap(), error = null, knownDevices = known,
                connectingMac = null, connectFailedMac = null, connectFailedError = null,
            )
        }
    }

    fun forgetSavedLtmk() {
        val mac = _state.value.macAddress
        secureStore.clearLtmk(mac)
        deviceRegistry.remove(mac)
        documentStore.deleteAll(mac)
        batteryHistoryStore.clear(mac)
        _state.update { it.copy(hasSavedLtmk = false, knownDevices = deviceRegistry.list()) }
        documents.refreshDocuments()
    }

    fun refreshAll() = launchBusy(s.readingValuesBusy, connectionScope ?: viewModelScope) {
        val spec = protocol?.requireSpecClient() ?: return@launchBusy
        val profile = _state.value.activeSpecProfile
        for (property in profile.all) {
            if (property.name in profile.writeOnly) continue
            val result = withContext(Dispatchers.IO) { spec.get(property) }
            _state.update { it.copy(values = it.values + (property.name to result)) }
        }
        checkLayout()
        trackLiveRide()
        recordBatteryLog()
        _state.update { it.copy(modeStats = batteryHistoryStore.windowStats(it.macAddress), batteryLog = batteryHistoryStore.dailyLog(it.macAddress)) }
        pushWidgetUpdate()
    }

    /** Pushes the latest known status to the home-screen widget (see [ScooterWidgetUpdater]) -
     * cheap no-op if no widget is placed. Called after every full refresh, manual or automatic,
     * so the widget reflects whatever the app itself last saw without a separate poll of its own. */
    private fun pushWidgetUpdate() {
        val state = _state.value
        fun long(name: String): Long? = state.values[name]?.takeIf { it.ok }?.value as? Long
        fun float(name: String): Double? = when (val v = state.values[name]?.takeIf { it.ok }?.value) {
            is Float -> v.toDouble()
            is Long -> v.toDouble()
            else -> null
        }
        viewModelScope.launch {
            ScooterWidgetUpdater.update(
                context = getApplication(),
                deviceName = state.deviceName ?: modelDisplayName(state.activeModel, state.language),
                mac = state.macAddress,
                batteryLevel = long("BATTERY_LEVEL"),
                isLocked = long("IS_LOCKED")?.let { it == 1L },
                remainingKm = float("REMAINING_MILEAGE")?.let { state.units.distance(it * 0.01) },
                distanceUnit = state.units.distanceUnit,
                lang = state.language.name,
            )
        }
    }

    private val liveRide = LiveRideTracker()

    /** The live ride log (see [LiveRideTracker]): called with every fresh set of readings while connected. While the
     * scooter is ridden, distance and battery percentage are added to the window ([RideWindow]) of the riding mode
     * that was active - without any question. Rides while the phone is not connected are not recorded. */
    private fun trackLiveRide() {
        val state = _state.value
        if (!state.rideTracking || state.activeSpecProfile.readOnly || state.layoutMismatch) return
        val values = state.values
        fun long(name: String): Long? = values[name]?.takeIf { it.ok }?.value as? Long
        val km = (values["TOTAL_MILEAGE"]?.takeIf { it.ok }?.value as? Float)?.let { it * 0.01 } ?: return
        val batteryPercent = long("BATTERY_LEVEL") ?: return
        // Same "not standing" fix as startAutoRefresh's own IS_RIDING check just above - see its comment.
        val segments = liveRide.onReading(LiveRideTracker.Reading(km, batteryPercent), riding = (long("IS_RIDING") ?: 0L) != 0L, currentMode = long("RIDING_MODE"))
        if (segments.isEmpty()) return
        segments.forEach {
            Diagnostics.note("live ride segment: mode=${it.mode} km=%.2f pct=%.1f".format(it.km, it.percentUsed))
            batteryHistoryStore.addSegment(state.macAddress, it.mode, it.km, it.percentUsed)
        }
        _state.update { it.copy(modeStats = batteryHistoryStore.windowStats(it.macAddress)) }
    }

    private var layoutChecked = false

    /** Once per connection: if the scooter *answers* every basic reading with a refusal ("no such property"), it does
     * not use this app's property table (an unknown model, e.g. added without model information) - writing to it
     * could change the wrong things, so changes are blocked and the person is told. A timeout says nothing about the
     * table (a bad moment on the radio), so it is checked again on the next refresh instead. */
    private fun checkLayout() {
        if (layoutChecked) return
        val values = _state.value.values
        val results = listOf("BATTERY_LEVEL", "RIDING_MODE", "TOTAL_MILEAGE").mapNotNull { values[it] }
        if (results.any { it.ok }) {
            layoutChecked = true
            Diagnostics.note("layout check: ok")
        } else if (results.size == 3 && results.all { it.status != -1 }) {
            layoutChecked = true
            Diagnostics.note("layout check: the scooter refused all basic readings (status ${results.joinToString { "0x%04x".format(it.status and 0xFFFF) }})")
            _state.update { it.copy(layoutMismatch = true, error = s.layoutMismatchError) }
        } else {
            Diagnostics.note("layout check: inconclusive (timeouts), will check again")
        }
    }

    private fun writesBlocked() = _state.value.activeSpecProfile.readOnly || _state.value.layoutMismatch

    /** Notes today's battery health and odometer for the Verlauf tab (one entry per day). */
    private fun recordBatteryLog() {
        val values = _state.value.values
        fun long(name: String): Long? = values[name]?.takeIf { it.ok }?.value as? Long
        val km = (values["TOTAL_MILEAGE"]?.takeIf { it.ok }?.value as? Float)?.let { it * 0.01 } ?: return
        batteryHistoryStore.recordDaily(_state.value.macAddress, long("SOH"), long("NUMBER_OF_CYCLES"), km)
    }

    /** Wipes the active device's accumulated efficiency totals - offered behind a confirmation
     * dialog in the UI, e.g. useful after a battery replacement. */
    fun resetEfficiencyHistory() {
        val mac = _state.value.macAddress
        batteryHistoryStore.clear(mac)
        _state.update { it.copy(modeStats = batteryHistoryStore.windowStats(mac), batteryLog = batteryHistoryStore.dailyLog(mac)) }
    }

    fun refreshOne(property: SpecProperty) = launchBusy(null, connectionScope ?: viewModelScope) {
        val spec = protocol?.requireSpecClient() ?: return@launchBusy
        val result = withContext(Dispatchers.IO) { spec.get(property) }
        _state.update { it.copy(values = it.values + (property.name to result)) }
    }

    fun setBoolProperty(property: SpecProperty, value: Boolean) = launchBusy(null, connectionScope ?: viewModelScope) {
        if (writesBlocked()) throw ProtocolException(s.writesBlockedError)
        val spec = protocol?.requireSpecClient() ?: return@launchBusy
        val status = withContext(Dispatchers.IO) { spec.set(property, encodeValue(SpecType.BOOL, if (value) 1L else 0L)) }
        // A write-only property (see SpecProfile.writeOnly) has no readable value to confirm
        // against - GET on it always fails, so skip the read-back entirely rather than surface a
        // spurious error for a SET that actually succeeded.
        if (property.name !in _state.value.activeSpecProfile.writeOnly) refreshOneNow(spec, property)
        // The scooter can silently reject a SET (wrong precondition, unsupported in this state,
        // etc.) - without checking this, a failed write looked indistinguishable from the switch
        // just not reacting to the tap, with no indication anything went wrong (see IS_LOCKED
        // incident: a rejected/failed unlock read back as "still locked" with zero feedback).
        if (status != 0) throw ProtocolException(s.setRejectedError(propertyName(property.name, _state.value.language), status))
    }

    fun setNumericProperty(property: SpecProperty, value: Long) = launchBusy(null, connectionScope ?: viewModelScope) {
        if (writesBlocked()) throw ProtocolException(s.writesBlockedError)
        val spec = protocol?.requireSpecClient() ?: return@launchBusy
        val status = withContext(Dispatchers.IO) { spec.set(property, encodeValue(property.type, value)) }
        refreshOneNow(spec, property)
        if (status != 0) throw ProtocolException(s.setRejectedError(propertyName(property.name, _state.value.language), status))
    }

    /** For STRING-type settable properties (currently just TIRE_MAINTENANCE's packed
     * state+interval+remaining-days string) - [encodeValue] deliberately has no STRING overload
     * (see its own doc comment), so this writes the raw ASCII bytes directly instead. */
    fun setStringProperty(property: SpecProperty, value: String) = launchBusy(null, connectionScope ?: viewModelScope) {
        if (writesBlocked()) throw ProtocolException(s.writesBlockedError)
        val spec = protocol?.requireSpecClient() ?: return@launchBusy
        val status = withContext(Dispatchers.IO) { spec.set(property, value.toByteArray(Charsets.US_ASCII)) }
        // The scooter keeps answering GET with the old string for a moment after a SET (seen live
        // on TIRE_MAINTENANCE: an immediate read-back flipped the switch back until the next
        // refresh); the notebook probe only got the new value after waiting ~1s.
        delay(1_000L)
        refreshOneNow(spec, property)
        if (status != 0) throw ProtocolException(s.setRejectedError(propertyName(property.name, _state.value.language), status))
    }

    private suspend fun refreshOneNow(spec: SpecClient, property: SpecProperty) {
        val result = withContext(Dispatchers.IO) { spec.get(property) }
        _state.update { it.copy(values = it.values + (property.name to result)) }
    }

    /** Reads every property of the scooter (never writes) and shows the result as a text to copy. */
    fun exploreValues() = launchBusy(s.exploringBusy, connectionScope ?: viewModelScope) {
        val spec = protocol?.requireSpecClient() ?: return@launchBusy
        runExplorer(spec)
    }

    private suspend fun runExplorer(spec: SpecClient) {
        Diagnostics.note("explorer: start")
        val probes = withContext(Dispatchers.IO) { PropertyExplorer.sweep(spec) { siid -> updateBusyMessage(s.exploringProgress(siid)) } }
        Diagnostics.note("explorer: ${probes.count { it.status == 0 }} readable of ${probes.size}")
        _state.update { it.copy(explorerReport = PropertyExplorer.report(probes)) }
    }

    fun dismissExplorer() = _state.update { it.copy(explorerReport = null) }

    fun dismissError() = _state.update { it.copy(error = null, needsPin = false) }

    private fun launchBusy(message: String?, scope: CoroutineScope = viewModelScope, block: suspend () -> Unit) {
        scope.launch {
            _state.update { it.copy(busy = true, busyMessage = message ?: it.busyMessage, error = null) }
            try {
                block()
            } catch (e: CancellationException) {
                // Never surface a cancellation (disconnect() tearing down connectionScope while
                // this was mid-request) as a user-facing error - and must be rethrown, not
                // swallowed, so the coroutine machinery actually finishes cancelling.
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ScooterVM", "launchBusy failed", e)
                _state.update { it.copy(error = e.message ?: e.toString()) }
            } finally {
                _state.update { it.copy(busy = false, busyMessage = "") }
            }
        }
    }

    private fun updateBusyMessage(message: String) = _state.update { it.copy(busyMessage = message) }

    override fun onCleared() {
        protocol?.dispose()
        super.onCleared()
    }
}
