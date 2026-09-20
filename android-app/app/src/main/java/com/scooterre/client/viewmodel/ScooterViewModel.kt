package com.scooterre.client.viewmodel

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scooterre.client.ble.FoundDevice
import com.scooterre.client.ble.ScooterScanner
import com.scooterre.client.cloud.CloudDeviceMatch
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
import com.scooterre.client.protocol.ModeEfficiencyTotals
import com.scooterre.client.protocol.PendingRideDelta
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
import com.scooterre.client.ui.Lang
import com.scooterre.client.ui.resolveLang
import com.scooterre.client.ui.ThemeMode
import com.scooterre.client.ui.UnitSystem
import com.scooterre.client.ui.distance
import com.scooterre.client.ui.distanceUnit
import com.scooterre.client.update.UpdateChecker
import com.scooterre.client.update.UpdateInfo
import com.scooterre.client.ui.modelDisplayName
import com.scooterre.client.ui.propertyName
import com.scooterre.client.ui.strings
import com.scooterre.client.widget.ScooterWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

// Deliberately left blank rather than pre-filled with a real device's MAC - this is a public
// build meant for anyone's own scooter, not just the one it was originally developed against.
const val DEFAULT_SCOOTER_MAC = ""
private const val KEY_LAST_MAC = "last_mac"
private const val KEY_LANG = "lang"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
private const val KEY_AUTO_BRIGHTNESS = "auto_brightness"
private const val KEY_UNITS = "units"
private const val KEY_AUTO_CONNECT = "auto_connect"
private const val KEY_LAST_CONNECTED = "last_connected_mac"
private const val KEY_REFRESH_RATE = "refresh_rate"
private const val KEY_CONFIRM_CRITICAL = "confirm_critical"
private const val KEY_RIDE_TRACKING = "ride_tracking"
private const val KEY_UPDATE_CHECK = "update_check"
private const val KEY_APP_LOCK = "app_lock"
private const val KEY_LAST_BACKUP = "last_backup_millis"
private const val KEY_UPDATE_LAST_CHECK = "update_last_check"
private const val KEY_UPDATE_TAG = "update_latest_tag"
private const val KEY_UPDATE_URL = "update_latest_url"
private const val UPDATE_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

/** Polled every ~2.5s (see [ScooterViewModel.startAutoRefresh]) while actually riding, instead of
 * the full ~50-property table - small enough that one pass stays well inside that window even
 * without batching, and covers exactly what changes meaningfully second-to-second on a moving
 * scooter (plus IS_RIDING itself, so the loop notices when the ride ends and drops back to the
 * slower full-sweep cadence). */
private val RIDE_PRIORITY_PROPERTIES = setOf(
    "IS_RIDING", "AVERAGE_SPEED", "CURRENT_MILEAGE", "BATTERY_LEVEL", "REMAINING_MILEAGE", "RIDING_TIME", "RIDING_MODE",
)

/** Pause between full property sweeps while parked (a sweep itself takes ~9s on top). */
enum class RefreshRate(val idleDelayMs: Long) { ECONOMY(30_000L), NORMAL(10_000L), FAST(3_000L) }

enum class Screen { LOGIN, DASHBOARD, DEVICE_PICKER, APP_SETTINGS, DOCUMENTS, DOCUMENT_VIEWER }

data class UiState(
    val screen: Screen = Screen.LOGIN,
    val language: Lang = resolveLang(null),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val keepScreenOn: Boolean = true,
    val autoBrightness: Boolean = false,
    val units: UnitSystem = UnitSystem.METRIC,
    val autoConnect: Boolean = false,
    val refreshRate: RefreshRate = RefreshRate.NORMAL,
    val confirmCritical: Boolean = false,
    val rideTracking: Boolean = true,
    val updateCheck: Boolean = true,
    // App lock (opt-in, default off): asks for fingerprint/PIN once per app start.
    val appLock: Boolean = false,
    val locked: Boolean = false,
    val lastBackupMillis: Long = 0L,
    val backupMessage: String? = null,
    val availableUpdate: UpdateInfo? = null,
    // Documents: which scooter's list is open, its documents, the one shown full screen, and the
    // per-scooter counts shown on the device list.
    val documentsMac: String? = null,
    val documents: List<ScooterDocument> = emptyList(),
    val viewerDocId: String? = null,
    val documentCounts: Map<String, Int> = emptyMap(),
    val macAddress: String = DEFAULT_SCOOTER_MAC,
    // Preferably the name from the Xiaomi cloud account (finishCloudLogin), falling back to the
    // BLE-advertised name if picked from a scan, or null for a generic label in the dashboard.
    val deviceName: String? = null,
    // Xiaomi cloud model string (e.g. "xiaomi.scooter.5max") for the device currently being
    // added/connected - drives both the displayed model name and which SpecProfile applies.
    val activeModel: String? = null,
    val activeSpecProfile: SpecProfile = SpecProfiles.SCOOTER_5_PRO,
    // Every scooter this app has ever connected to (MAC + cosmetic model/name) - shown on the
    // DEVICE_PICKER screen so more than one can be kept side by side instead of one swappable slot.
    val knownDevices: List<KnownDevice> = emptyList(),
    val hasSavedLtmk: Boolean = false,
    val busy: Boolean = false,
    val busyMessage: String = "",
    val error: String? = null,
    val needsPin: Boolean = false,
    val pin: String = "",
    val qrPng: ByteArray? = null,
    val qrLoginUrl: String? = null,
    val qrWaiting: Boolean = false,
    val values: Map<String, SpecReadResult> = emptyMap(),
    val scanning: Boolean = false,
    val scanResults: List<FoundDevice> = emptyList(),
    // Set after exportDevice() - shown as a dialog with the text + a share button, so a second
    // person authorized on the same physical scooter (e.g. a spouse) can add it on their phone
    // without repeating the cloud login/PIN dance.
    val exportCode: String? = null,
    val exportMac: String? = null,
    val importText: String = "",
    // Set right after a fresh connect if the odometer/battery moved meaningfully since the last
    // time this device was seen - the app has no background service, so it cannot know which
    // riding mode was active during that gap; the UI asks the person who actually rode it instead
    // of guessing. Null once resolved (attributed or explicitly skipped) - see BatteryHistoryStore.
    val pendingRideDelta: PendingRideDelta? = null,
    // Lifetime km ridden + real-world Wh/km per riding mode (11=Walk, 2=Drive, 3=Sport), shown on
    // the "Verlauf" tab - a battery-health signal the device's own SOH% doesn't capture, since it
    // reflects actual energy cost per km rather than the device's own internal estimate.
    val efficiencyTotals: Map<Long, ModeEfficiencyTotals> = emptyMap(),
)

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
                lastBackupMillis = prefs.getLong(KEY_LAST_BACKUP, 0L),
                documentCounts = known.associate { it.mac to documentStore.count(it.mac) },
                availableUpdate = if (prefs.getBoolean(KEY_UPDATE_CHECK, true)) storedUpdate() else null,
                knownDevices = known,
            )
        }
    )
    val state: StateFlow<UiState> = _state

    init {
        _state.update { it.copy(hasSavedLtmk = secureStore.loadLtmk(it.macAddress) != null) }
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

    private fun refreshDocuments() {
        val mac = _state.value.documentsMac
        _state.update { st ->
            st.copy(
                documents = mac?.let(documentStore::list) ?: emptyList(),
                documentCounts = deviceRegistry.list().associate { it.mac to documentStore.count(it.mac) },
            )
        }
    }

    /** Opens the documents of [mac] - or, with null, of the scooter used last (else the first one). */
    fun openDocuments(mac: String?) {
        val known = deviceRegistry.list()
        val target = mac
            ?: prefs.getString(KEY_LAST_CONNECTED, null)?.takeIf { last -> known.any { it.mac.equals(last, ignoreCase = true) } }
            ?: known.firstOrNull()?.mac
            ?: return
        _state.update { it.copy(documentsMac = target, error = null) }
        refreshDocuments()
        pushScreen(Screen.DOCUMENTS)
    }

    fun selectDocumentsDevice(mac: String) {
        _state.update { it.copy(documentsMac = mac, error = null) }
        refreshDocuments()
    }

    fun openDocument(id: String) {
        _state.update { it.copy(viewerDocId = id) }
        pushScreen(Screen.DOCUMENT_VIEWER)
    }

    /** The first photo becomes the document, the rest are appended as further pages. */
    fun addDocumentPhotos(uris: List<Uri>, name: String) = documentJob { mac ->
        val resolver = getApplication<Application>().contentResolver
        val first = uris.firstOrNull() ?: return@documentJob
        val doc = documentStore.addImage(mac, name) { resolver.openInputStream(first) }
        uris.drop(1).forEach { u -> documentStore.appendImage(mac, doc.id) { resolver.openInputStream(u) } }
    }

    fun addDocumentFromUri(uri: Uri, name: String) = documentJob { mac ->
        val resolver = getApplication<Application>().contentResolver
        if (resolver.getType(uri) == "application/pdf") {
            documentStore.addPdf(mac, name) { resolver.openInputStream(uri) }
        } else {
            documentStore.addImage(mac, name) { resolver.openInputStream(uri) }
        }
    }

    fun appendDocumentPhotos(docId: String, uris: List<Uri>) = documentJob { mac ->
        val resolver = getApplication<Application>().contentResolver
        uris.forEach { u -> documentStore.appendImage(mac, docId) { resolver.openInputStream(u) } }
    }

    fun renameDocument(docId: String, name: String) = documentJob { mac -> documentStore.rename(mac, docId, name) }

    fun deleteDocument(docId: String) = documentJob { mac -> documentStore.delete(mac, docId) }

    private fun documentJob(block: (String) -> Unit) {
        val mac = _state.value.documentsMac ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block(mac) }
                _state.update { it.copy(error = null) }
            } catch (e: Exception) {
                android.util.Log.e("ScooterVM", "document operation failed", e)
                _state.update { it.copy(error = s.docsImportError) }
            }
            refreshDocuments()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _state.update { it.copy(themeMode = mode) }
    }

    fun setAutoBrightness(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BRIGHTNESS, enabled).apply()
        _state.update { it.copy(autoBrightness = enabled) }
    }

    fun setLanguage(lang: Lang) {
        prefs.edit().putString(KEY_LANG, lang.name).apply()
        _state.update { it.copy(language = lang) }
    }

    fun setUnits(units: UnitSystem) {
        prefs.edit().putString(KEY_UNITS, units.name).apply()
        _state.update { it.copy(units = units) }
        pushWidgetUpdate()
    }

    fun setRefreshRate(rate: RefreshRate) {
        prefs.edit().putString(KEY_REFRESH_RATE, rate.name).apply()
        _state.update { it.copy(refreshRate = rate) }
    }

    fun setAutoConnect(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CONNECT, enabled).apply()
        _state.update { it.copy(autoConnect = enabled) }
    }

    fun setConfirmCritical(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONFIRM_CRITICAL, enabled).apply()
        _state.update { it.copy(confirmCritical = enabled) }
    }

    fun setRideTracking(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RIDE_TRACKING, enabled).apply()
        _state.update { it.copy(rideTracking = enabled) }
    }

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
        connectKnownDevice(device)
    }

    private fun installedVersion(): String =
        runCatching { getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0).versionName }
            .getOrNull() ?: "0"

    /** The newest release seen at the last check, but only if it is still newer than what is installed
     * now - so the notice disappears by itself right after updating. */
    private fun storedUpdate(): UpdateInfo? {
        val tag = prefs.getString(KEY_UPDATE_TAG, null) ?: return null
        val url = prefs.getString(KEY_UPDATE_URL, null) ?: return null
        return if (UpdateChecker.isNewer(tag, installedVersion())) UpdateInfo(tag, url) else null
    }

    private suspend fun refreshUpdateInfo(force: Boolean) {
        if (!_state.value.updateCheck) return
        val now = System.currentTimeMillis()
        if (force || now - prefs.getLong(KEY_UPDATE_LAST_CHECK, 0L) >= UPDATE_CHECK_INTERVAL_MS) {
            val latest = withContext(Dispatchers.IO) { UpdateChecker.fetchLatest() } ?: return
            prefs.edit().putLong(KEY_UPDATE_LAST_CHECK, now).putString(KEY_UPDATE_TAG, latest.version).putString(KEY_UPDATE_URL, latest.url).apply()
        }
        _state.update { it.copy(availableUpdate = storedUpdate()) }
    }

    fun checkForUpdateOnStart() {
        viewModelScope.launch { refreshUpdateInfo(force = false) }
    }

    fun setUpdateCheck(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_UPDATE_CHECK, enabled).apply()
        _state.update { it.copy(updateCheck = enabled, availableUpdate = if (enabled) storedUpdate() else null) }
        if (enabled) viewModelScope.launch { refreshUpdateInfo(force = true) }
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

    fun setAppLock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK, enabled).apply()
        _state.update { it.copy(appLock = enabled) }
    }

    fun markBackupDone() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_BACKUP, now).apply()
        _state.update { it.copy(lastBackupMillis = now) }
    }

    fun dismissBackupMessage() = _state.update { it.copy(backupMessage = null) }

    /** Restores a full backup file; [withSettings] also re-applies the app settings stored in it. */
    fun restoreBackup(uri: Uri, password: String, withSettings: Boolean) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                    ?.let { BackupBundle.restore(getApplication(), it, password, withSettings) }
            }
            when (result) {
                is BackupBundle.RestoreResult.Ok -> {
                    reloadSettings()
                    _state.update { it.copy(knownDevices = deviceRegistry.list()) }
                    refreshDocuments()
                    _state.update { it.copy(backupMessage = s.backupDone(result.devices, result.documents)) }
                }
                BackupBundle.RestoreResult.BadPassword -> _state.update { it.copy(backupMessage = s.importWrongPasswordError) }
                BackupBundle.RestoreResult.TooLarge -> _state.update { it.copy(backupMessage = s.backupTooLarge) }
                else -> _state.update { it.copy(backupMessage = s.importInvalidCodeError) }
            }
        }
    }

    private fun reloadSettings() {
        _state.update {
            it.copy(
                language = resolveLang(prefs.getString(KEY_LANG, null)),
                themeMode = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
                keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true),
                autoBrightness = prefs.getBoolean(KEY_AUTO_BRIGHTNESS, false),
                units = runCatching { UnitSystem.valueOf(prefs.getString(KEY_UNITS, null) ?: "METRIC") }.getOrDefault(UnitSystem.METRIC),
                autoConnect = prefs.getBoolean(KEY_AUTO_CONNECT, false),
                refreshRate = runCatching { RefreshRate.valueOf(prefs.getString(KEY_REFRESH_RATE, null) ?: "NORMAL") }.getOrDefault(RefreshRate.NORMAL),
                confirmCritical = prefs.getBoolean(KEY_CONFIRM_CRITICAL, false),
                rideTracking = prefs.getBoolean(KEY_RIDE_TRACKING, true),
                updateCheck = prefs.getBoolean(KEY_UPDATE_CHECK, true),
            )
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
        _state.update { it.copy(keepScreenOn = enabled) }
    }

    fun toggleLanguage() {
        val next = if (_state.value.language == Lang.DE) Lang.EN else Lang.DE
        prefs.edit().putString(KEY_LANG, next.name).apply()
        _state.update { it.copy(language = next) }
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

    /** Connects to an already-known scooter using its saved key - the picker's tap-to-connect. */
    fun connectKnownDevice(device: KnownDevice) = launchBusy(s.connectingSavedBusy) {
        val ltmk = secureStore.loadLtmk(device.mac) ?: throw CloudException(s.noSavedKeyError(device.mac))
        _state.update { it.copy(macAddress = device.mac, deviceName = device.name, activeModel = device.model) }
        connectAndLogin(device.mac, ltmk)
    }

    /** Removes a saved scooter's key and its entry in the device list - offered from the picker. */
    fun forgetDevice(mac: String) {
        secureStore.clearLtmk(mac)
        deviceRegistry.remove(mac)
        documentStore.deleteAll(mac)
        batteryHistoryStore.clear(mac)
        _state.update { it.copy(knownDevices = deviceRegistry.list()) }
        refreshDocuments()
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

    /** Imports an export bundle file (key, name, documents, history); [password] is needed for an
     * encrypted one. */
    fun importBundle(uri: Uri, password: String?) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                    ?.let { DeviceBundle.import(getApplication(), it, password) }
            }
            when (result) {
                is DeviceBundle.ImportResult.Ok -> {
                    _state.update { it.copy(importText = "", error = null, knownDevices = deviceRegistry.list(), screen = Screen.DEVICE_PICKER) }
                    refreshDocuments()
                }
                DeviceBundle.ImportResult.BadPassword -> _state.update { it.copy(error = s.importWrongPasswordError) }
                else -> _state.update { it.copy(error = s.importInvalidCodeError) }
            }
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
        // Any previous, still-open connection (from an earlier failed attempt this session) must
        // be torn down first - the scooter/BLE stack gets confused by multiple simultaneous GATT
        // clients from this app, silently dropping notifications instead of acking anything.
        protocol?.dispose()
        protocol = null

        val manager = getApplication<Application>().getSystemService(BluetoothManager::class.java)
        val device = manager.adapter.getRemoteDevice(mac)

        // The very first connect attempt right after a killed/restarted app process (or right
        // after cleanly disconnecting and immediately reconnecting) routinely fails fast or times
        // out - the OS Bluetooth stack needs a moment to release the previous GATT client
        // registration. Silent retries with a growing pause paper over that instead of making the
        // user notice and tap "Verbinden" again themselves; an immediate retry with no pause at
        // all was observed to still fail right after a fresh disconnect.
        var lastError: Exception? = null
        for (attempt in 1..3) {
            if (attempt > 1) {
                updateBusyMessage(s.retryingBusy(attempt))
                delay(1500L * (attempt - 1))
            } else {
                updateBusyMessage(s.connectingBluetoothBusy)
            }
            val p = try {
                MiProtocol.connect(getApplication(), device)
            } catch (e: Exception) {
                lastError = e
                continue
            }
            try {
                updateBusyMessage(s.authenticatingBusy)
                p.login(ltmk)
            } catch (e: Exception) {
                p.dispose()
                lastError = e
                continue
            }
            protocol = p
            val model = _state.value.activeModel
            deviceRegistry.upsert(KnownDevice(mac = mac, model = model, name = _state.value.deviceName))
            prefs.edit().putString(KEY_LAST_CONNECTED, mac).apply()
            screenStack.clear()
            _state.update {
                it.copy(
                    screen = Screen.DASHBOARD, error = null,
                    activeSpecProfile = SpecProfiles.forModel(model),
                    knownDevices = deviceRegistry.list(),
                )
            }
            refreshAll()
            startAutoRefresh()
            return
        }
        throw lastError ?: ProtocolException("Connection failed")
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
                val riding = (_state.value.values["IS_RIDING"]?.takeIf { it.ok }?.value as? Long) == 1L
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

    /** Cleanly closes the BLE connection and returns to the login screen - lets the user end the
     * session properly (or switch to a different scooter) instead of the only alternative being
     * to kill the app, which skips this cleanup and is what causes the next connect attempt to
     * need its automatic retry. */
    fun disconnect() {
        stopAutoRefresh()
        protocol?.dispose()
        protocol = null
        screenStack.clear()
        val known = deviceRegistry.list()
        _state.update {
            it.copy(
                screen = if (known.isNotEmpty()) Screen.DEVICE_PICKER else Screen.LOGIN,
                values = emptyMap(), error = null, knownDevices = known,
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
        refreshDocuments()
    }

    fun refreshAll() = launchBusy(s.readingValuesBusy) {
        val spec = protocol?.requireSpecClient() ?: return@launchBusy
        val profile = _state.value.activeSpecProfile
        for (property in profile.all) {
            if (property.name in profile.writeOnly) continue
            val result = withContext(Dispatchers.IO) { spec.get(property) }
            _state.update { it.copy(values = it.values + (property.name to result)) }
        }
        checkPendingRide()
        _state.update { it.copy(efficiencyTotals = batteryHistoryStore.totals(it.macAddress)) }
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

    /** Runs once per connection, right after the first full [refreshAll] populates
     * TOTAL_MILEAGE/REMAINING_BATTERY/VOLTAGE - compares them against the last known reading for
     * this device (see [BatteryHistoryStore.checkForPendingRide]) and, if the odometer moved
     * meaningfully since then, surfaces a dialog asking which riding mode that reflects. The app
     * has no background service (see project research log), so it genuinely cannot know this on
     * its own for a ride that happened while it was closed - asking beats silently guessing or
     * silently discarding the distance/energy entirely. */
    private fun checkPendingRide() {
        if (!_state.value.rideTracking) return
        val values = _state.value.values
        fun long(name: String): Long? = values[name]?.takeIf { it.ok }?.value as? Long
        fun float(name: String): Double? = when (val v = values[name]?.takeIf { it.ok }?.value) {
            is Float -> v.toDouble()
            is Long -> v.toDouble()
            else -> null
        }
        val km = float("TOTAL_MILEAGE")?.let { it * 0.01 } ?: return
        val mah = long("REMAINING_BATTERY") ?: return
        val voltage = float("VOLTAGE")?.let { it * 0.01 } ?: return
        val delta = batteryHistoryStore.checkForPendingRide(_state.value.macAddress, km, mah, voltage) ?: return
        _state.update { it.copy(pendingRideDelta = delta) }
    }

    /** The user answered the pending-ride dialog with the mode they mostly rode in - folds the
     * delta into that mode's lifetime total. [mode] is the raw RIDING_MODE value (11=Walk,
     * 2=Drive, 3=Sport), matching [com.scooterre.client.ui.CYCLE_VALUES]. */
    fun attributeRideMode(mode: Long) {
        val delta = _state.value.pendingRideDelta ?: return
        val (km, mah, voltage) = currentOdometerReading() ?: return
        batteryHistoryStore.attributeRide(_state.value.macAddress, mode, delta, km, mah, voltage)
        _state.update { it.copy(pendingRideDelta = null, efficiencyTotals = batteryHistoryStore.totals(it.macAddress)) }
    }

    /** The user dismissed the pending-ride dialog without picking a mode - the distance/energy is
     * dropped (not counted toward any mode), but the reference point still advances so the same
     * gap isn't asked about again on the next connect. */
    fun skipPendingRide() {
        if (_state.value.pendingRideDelta == null) return
        val (km, mah, voltage) = currentOdometerReading() ?: return
        batteryHistoryStore.skipPendingRide(_state.value.macAddress, km, mah, voltage)
        _state.update { it.copy(pendingRideDelta = null) }
    }

    /** Wipes the active device's accumulated efficiency totals - offered behind a confirmation
     * dialog in the UI, e.g. useful after a battery replacement. */
    fun resetEfficiencyHistory() {
        val mac = _state.value.macAddress
        batteryHistoryStore.clear(mac)
        _state.update { it.copy(efficiencyTotals = batteryHistoryStore.totals(mac)) }
    }

    private fun currentOdometerReading(): Triple<Double, Long, Double>? {
        val values = _state.value.values
        val km = (values["TOTAL_MILEAGE"]?.takeIf { it.ok }?.value as? Float)?.let { it * 0.01 } ?: return null
        val mah = values["REMAINING_BATTERY"]?.takeIf { it.ok }?.value as? Long ?: return null
        val voltage = (values["VOLTAGE"]?.takeIf { it.ok }?.value as? Float)?.let { it * 0.01 } ?: return null
        return Triple(km, mah, voltage)
    }

    fun refreshOne(property: SpecProperty) = launchBusy(null) {
        val spec = protocol?.requireSpecClient() ?: return@launchBusy
        val result = withContext(Dispatchers.IO) { spec.get(property) }
        _state.update { it.copy(values = it.values + (property.name to result)) }
    }

    fun setBoolProperty(property: SpecProperty, value: Boolean) = launchBusy(null) {
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

    fun setNumericProperty(property: SpecProperty, value: Long) = launchBusy(null) {
        val spec = protocol?.requireSpecClient() ?: return@launchBusy
        val status = withContext(Dispatchers.IO) { spec.set(property, encodeValue(property.type, value)) }
        refreshOneNow(spec, property)
        if (status != 0) throw ProtocolException(s.setRejectedError(propertyName(property.name, _state.value.language), status))
    }

    /** For STRING-type settable properties (currently just TIRE_MAINTENANCE's packed
     * state+interval+remaining-days string) - [encodeValue] deliberately has no STRING overload
     * (see its own doc comment), so this writes the raw ASCII bytes directly instead. */
    fun setStringProperty(property: SpecProperty, value: String) = launchBusy(null) {
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

    fun dismissError() = _state.update { it.copy(error = null, needsPin = false) }

    private fun launchBusy(message: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, busyMessage = message ?: it.busyMessage, error = null) }
            try {
                block()
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
