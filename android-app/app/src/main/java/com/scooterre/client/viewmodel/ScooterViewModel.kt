package com.scooterre.client.viewmodel

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scooterre.client.ble.FoundDevice
import com.scooterre.client.ble.ScooterScanner
import com.scooterre.client.cloud.CloudDeviceMatch
import com.scooterre.client.cloud.CloudException
import com.scooterre.client.cloud.PinRequiredException
import com.scooterre.client.cloud.QrLoginStart
import com.scooterre.client.cloud.XiaomiCloudClient
import com.scooterre.client.protocol.MiProtocol
import com.scooterre.client.protocol.ProtocolException
import com.scooterre.client.protocol.SecureStore
import com.scooterre.client.protocol.SpecClient
import com.scooterre.client.protocol.SpecProperties
import com.scooterre.client.protocol.SpecProperty
import com.scooterre.client.protocol.SpecReadResult
import com.scooterre.client.protocol.SpecType
import com.scooterre.client.protocol.encodeValue
import com.scooterre.client.ui.Lang
import com.scooterre.client.ui.propertyName
import com.scooterre.client.ui.strings
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

enum class Screen { LOGIN, DASHBOARD }

data class UiState(
    val screen: Screen = Screen.LOGIN,
    val language: Lang = Lang.DE,
    val macAddress: String = DEFAULT_SCOOTER_MAC,
    // Preferably the name from the Xiaomi cloud account (finishCloudLogin), falling back to the
    // BLE-advertised name if picked from a scan, or null for a generic label in the dashboard.
    val deviceName: String? = null,
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
    private var protocol: MiProtocol? = null

    // Kept around across the "PIN required" round-trip so retryWithPin() doesn't have to repeat
    // the whole cloud login (password or QR) just because the device also needs its sharing PIN.
    private var pendingCloud: XiaomiCloudClient? = null
    private var pendingDevice: CloudDeviceMatch? = null

    private val _state = MutableStateFlow(
        UiState(
            macAddress = prefs.getString(KEY_LAST_MAC, DEFAULT_SCOOTER_MAC) ?: DEFAULT_SCOOTER_MAC,
            language = if (prefs.getString(KEY_LANG, "DE") == "EN") Lang.EN else Lang.DE,
        )
    )
    val state: StateFlow<UiState> = _state

    init {
        _state.update { it.copy(hasSavedLtmk = secureStore.loadLtmk(it.macAddress) != null) }
    }

    fun toggleLanguage() {
        val next = if (_state.value.language == Lang.DE) Lang.EN else Lang.DE
        prefs.edit().putString(KEY_LANG, next.name).apply()
        _state.update { it.copy(language = next) }
    }

    fun onMacChanged(mac: String) {
        prefs.edit().putString(KEY_LAST_MAC, mac).apply()
        _state.update { it.copy(macAddress = mac, deviceName = null, hasSavedLtmk = secureStore.loadLtmk(mac) != null) }
    }

    fun onPinChanged(pin: String) = _state.update { it.copy(pin = pin) }

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
                _state.update { it.copy(qrWaiting = false, qrPng = null, qrLoginUrl = null, error = e.message ?: e.toString()) }
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
        if (found.name != null) _state.update { it.copy(deviceName = found.name) }

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
            _state.update { it.copy(screen = Screen.DASHBOARD, error = null) }
            refreshAll()
            return
        }
        throw lastError ?: ProtocolException("Connection failed")
    }

    /** Cleanly closes the BLE connection and returns to the login screen - lets the user end the
     * session properly (or switch to a different scooter) instead of the only alternative being
     * to kill the app, which skips this cleanup and is what causes the next connect attempt to
     * need its automatic retry. */
    fun disconnect() {
        protocol?.dispose()
        protocol = null
        _state.update { it.copy(screen = Screen.LOGIN, values = emptyMap(), error = null) }
    }

    fun forgetSavedLtmk() {
        secureStore.clearLtmk(_state.value.macAddress)
        _state.update { it.copy(hasSavedLtmk = false) }
    }

    fun refreshAll() = launchBusy(s.readingValuesBusy) {
        val spec = protocol?.requireSpecClient() ?: return@launchBusy
        for (property in SpecProperties.ALL) {
            val result = withContext(Dispatchers.IO) { spec.get(property) }
            _state.update { it.copy(values = it.values + (property.name to result)) }
        }
    }

    fun refreshOne(property: SpecProperty) = launchBusy(null) {
        val spec = protocol?.requireSpecClient() ?: return@launchBusy
        val result = withContext(Dispatchers.IO) { spec.get(property) }
        _state.update { it.copy(values = it.values + (property.name to result)) }
    }

    fun setBoolProperty(property: SpecProperty, value: Boolean) = launchBusy(null) {
        val spec = protocol?.requireSpecClient() ?: return@launchBusy
        val status = withContext(Dispatchers.IO) { spec.set(property, encodeValue(SpecType.BOOL, if (value) 1L else 0L)) }
        refreshOneNow(spec, property)
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
