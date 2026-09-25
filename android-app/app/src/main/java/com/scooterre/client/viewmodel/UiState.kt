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
import com.scooterre.client.protocol.BatteryLogEntry
import com.scooterre.client.protocol.DeviceBundle
import com.scooterre.client.protocol.DeviceExport
import com.scooterre.client.protocol.DeviceRegistry
import com.scooterre.client.protocol.DocumentStore
import com.scooterre.client.protocol.KnownDevice
import com.scooterre.client.protocol.MiProtocol
import com.scooterre.client.protocol.RideBookEntry
import com.scooterre.client.protocol.RideLog
import com.scooterre.client.protocol.RideWindow
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** [stillScale] stretches the read intervals of the values that only matter while parked (see [com.scooterre.client.protocol.PollPlan]). */
enum class RefreshRate(val stillScale: Double) { ECONOMY(4.0), NORMAL(2.0), FAST(1.0) }

enum class Screen { LOGIN, DASHBOARD, DEVICE_PICKER, APP_SETTINGS, DOCUMENTS, DOCUMENT_VIEWER }

data class UiState(
    val screen: Screen = Screen.LOGIN,
    val language: Lang = resolveLang(null),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val orientationMode: OrientationMode = OrientationMode.AUTO,
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
    // Insurance-plate reminders (opt-in): the end of the running plate period and the scooters
    // already ticked off ("new insurance applied for") for it.
    val insuranceReminder: Boolean = false,
    val insuranceExpiry: java.time.LocalDate = InsuranceSchedule.expiryFor(java.time.LocalDate.now()),
    val insuranceApplied: Set<String> = emptySet(),
    val lastBackupMillis: Long = 0L,
    val backupMessage: String? = null,
    val availableUpdate: UpdateInfo? = null,
    // "Download update": progress 0..100 while downloading, ready once the file is downloaded and
    // verified, and what went wrong (the file is then discarded).
    val updateProgress: Int? = null,
    val updateReady: Boolean = false,
    val updateNeedsPermission: Boolean = false,
    val updateProblem: UpdateProblem? = null,
    // Documents: which scooter's list is open, its documents, the one shown full screen, and the
    // per-scooter counts shown on the device list.
    val documentsMac: String? = null,
    val documents: List<ScooterDocument> = emptyList(),
    val viewerDocId: String? = null,
    val documentCounts: Map<String, Int> = emptyMap(),
    val docsMessage: String? = null,
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
    // Which known device's connect attempt is in flight / most recently failed, for the picker's
    // per-tile green/red display - kept separate from the shared [error] above, which is also
    // written by unrelated background calls (e.g. the very first refreshAll right after login, or
    // a stray in-flight request finishing right as disconnect() tears the connection down); sharing
    // one field produced an intermittent, misleading red tile even after a clean manual disconnect.
    val connectingMac: String? = null,
    val connectFailedMac: String? = null,
    val connectFailedError: String? = null,
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
    // Rolling-window km ridden + real percent-per-km per riding mode (11=Walk, 2=Drive, 3=Sport,
    // see RideWindow), shown on the "Verlauf" tab and feeding the range-at-your-consumption
    // estimate - a battery-health signal the device's own SOH% doesn't capture on its own, since
    // it reflects actual real-world cost per km rather than the device's internal estimate.
    val modeStats: Map<Long, RideWindow.Stats> = emptyMap(),
    val rideLog: RideLog.Result = RideLog.Result(emptyList(), null),
    // Battery health (SOH, charge cycles) once per day and scooter, oldest first - shown on the Verlauf tab.
    val batteryLog: List<BatteryLogEntry> = emptyList(),
    /** The ride book (see [RideBook]) and how many rides were added to it since the tab last showed the note. */
    val rideBook: List<RideBookEntry> = emptyList(),
    val rideBookNew: Int = 0,
    // The first readings after connecting did not fit the property table (unknown model): changes are blocked.
    val layoutMismatch: Boolean = false,
    // The text of a read-only property sweep (see PropertyExplorer), shown in a dialog to copy.
    val explorerReport: String? = null,
) {
    /** The scooter reports its "Ruhezustand" (sleep state, looks switched off): its battery and ride values are then
     * the last ones from before, not live - the dashboard shows "Standby" instead of them until it wakes up. */
    val standby: Boolean get() = (values["FAKE_SHUTDOWN_STATUS"]?.takeIf { it.ok }?.value as? Long) == 1L
}
