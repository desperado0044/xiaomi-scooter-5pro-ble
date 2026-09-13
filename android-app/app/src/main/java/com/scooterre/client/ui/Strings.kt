package com.scooterre.client.ui

/** The two UI languages this app supports, toggled in-app (flag icon) rather than following the
 * OS locale - the app chrome, property labels and enum values must all switch together, see
 * [strings], [propertyName], [enumLabel] and [cycleLabel] below. */
enum class Lang { DE, EN }

/** All static chrome text (buttons, labels, dialogs, status messages) for one language. Kept as
 * a single data class instead of string resources so a language switch is a plain in-memory
 * state change, no Activity recreation needed. */
data class AppStrings(
    // Login screen
    val loginSubtitle: String,
    val macFieldLabel: String,
    val macFieldPlaceholder: String,
    val scanButton: String,
    val scanningStatus: String,
    val savedKeyText: String,
    val connectSavedButton: String,
    val forgetSavedButton: String,
    val newLoginDivider: String,
    val pinFieldLabel: String,
    val qrScanInstruction: String,
    val qrWaitingText: String,
    val qrOpenChooserTitle: String,
    val qrOpenButton: String,
    val qrLoginButton: String,
    val passwordAltDivider: String,
    val usernameFieldLabel: String,
    val passwordFieldLabel: String,
    val passwordLoginButton: String,
    val retryPinButton: String,
    val qrImageContentDescription: String,
    // Dashboard chrome
    val refreshButton: String,
    val disconnectButton: String,
    val fallbackDeviceName: String,
    val tabRide: String,
    val tabBattery: String,
    val tabSettings: String,
    val tabVehicleStatus: String,
    val tabIdentification: String,
    val tabRideLog: String,
    val noRidesYet: String,
    val regionWarningTitle: String,
    val regionWarningConfirmSuffix: String,
    val regionWarningConfirmButton: String,
    val regionWarningCancelButton: String,
    val setButton: String,
    val loadingPlaceholder: String,
    val emptyValuePlaceholder: String,
    val boolOn: String,
    val boolOff: String,
    val errorPrefix: String,
    val errorStatusFormat: (Int) -> String,
    val unknownValueFormat: (String) -> String,
    // ViewModel busy/error messages
    val connectingSavedBusy: String,
    val noSavedKeyError: (String) -> String,
    val cloudLoginBusy: String,
    val qrLoadingBusy: String,
    val searchingDeviceBusy: String,
    val deviceNotFoundError: (String) -> String,
    val fetchingKeyBusy: String,
    val pinRequiredError: String,
    val noActiveSessionError: String,
    val noRememberedDeviceError: String,
    val connectingBluetoothBusy: String,
    val retryingBusy: (Int) -> String,
    val authenticatingBusy: String,
    val readingValuesBusy: String,
    val setRejectedError: (String, Int) -> String,
)

val STRINGS_DE = AppStrings(
    loginSubtitle = "Eigenes Xiaomi-Konto - PIN und Passwort werden nie im Code gespeichert, nur verschlüsselt auf diesem Gerät (Android Keystore).",
    macFieldLabel = "Roller BLE-MAC",
    macFieldPlaceholder = "z.B. per Scan finden",
    scanButton = "Scannen",
    scanningStatus = "Suche nach Geräten in der Nähe ...",
    savedKeyText = "Für diese MAC ist bereits ein Schlüssel gespeichert.",
    connectSavedButton = "Verbinden (gespeicherter Schlüssel)",
    forgetSavedButton = "Gespeicherten Schlüssel löschen",
    newLoginDivider = "Neu anmelden / anderes Konto:",
    pinFieldLabel = "Geräte-PIN (nur falls in Mi Home eine Sharing-PIN gesetzt ist)",
    qrScanInstruction = "Mit der App scannen, die mit dem Roller verbunden ist (z.B. Xiaomi Home):",
    qrWaitingText = "Warte auf Bestätigung ...",
    qrOpenChooserTitle = "Öffnen mit ...",
    qrOpenButton = "Stattdessen mit App/Browser öffnen (Auswahl)",
    qrLoginButton = "QR-Code-Login (auch ohne Mi-Passwort, z.B. bei Google-Konto)",
    passwordAltDivider = "Alternativ mit Mi-Account-Passwort (falls gesetzt):",
    usernameFieldLabel = "Xiaomi-Konto (E-Mail/Telefon)",
    passwordFieldLabel = "Mi-Account-Passwort",
    passwordLoginButton = "Mit Passwort anmelden",
    retryPinButton = "Mit PIN erneut versuchen",
    qrImageContentDescription = "QR-Login-Code",
    refreshButton = "Aktualisieren",
    disconnectButton = "Trennen",
    fallbackDeviceName = "Scooter 5 Pro",
    tabRide = "Fahrt",
    tabBattery = "Akku",
    tabSettings = "Einstellungen",
    tabVehicleStatus = "Fahrzeug",
    tabIdentification = "Identifikation",
    tabRideLog = "Fahrtenbuch",
    noRidesYet = "Noch keine aufgezeichneten Fahrten",
    regionWarningTitle = "Rechtlicher Hinweis",
    regionWarningConfirmSuffix = " Mit dem Aktivieren bestätigst du, dass du die Verantwortung dafür übernimmst.",
    regionWarningConfirmButton = "Ich bestätige, aktivieren",
    regionWarningCancelButton = "Abbrechen",
    setButton = "Setzen",
    loadingPlaceholder = "…",
    emptyValuePlaceholder = "–",
    boolOn = "An",
    boolOff = "Aus",
    errorPrefix = "Fehler: ",
    errorStatusFormat = { status -> "Fehler (status=$status)" },
    unknownValueFormat = { v -> "Unbekannt ($v)" },
    connectingSavedBusy = "Verbinde mit Roller ...",
    noSavedKeyError = { mac -> "Kein gespeicherter Schlüssel für $mac" },
    cloudLoginBusy = "Xiaomi-Cloud-Login ...",
    qrLoadingBusy = "QR-Code wird geladen ...",
    searchingDeviceBusy = "Suche Roller im Xiaomi-Konto ...",
    deviceNotFoundError = { mac -> "Kein Gerät mit MAC $mac im Xiaomi-Konto gefunden" },
    fetchingKeyBusy = "Hole Schlüssel (ltmk) ...",
    pinRequiredError = "Für dieses Gerät ist eine Sharing-PIN gesetzt - bitte eingeben und erneut versuchen.",
    noActiveSessionError = "Keine aktive Anmeldung - bitte erneut anmelden",
    noRememberedDeviceError = "Kein Gerät gemerkt - bitte erneut anmelden",
    connectingBluetoothBusy = "Verbinde per Bluetooth ...",
    retryingBusy = { attempt -> "Versuch $attempt von 3 ..." },
    authenticatingBusy = "Authentifiziere ...",
    readingValuesBusy = "Lese Werte ...",
    setRejectedError = { name, status -> "Roller hat die Änderung an \"$name\" abgelehnt (status=$status)." },
)

val STRINGS_EN = AppStrings(
    loginSubtitle = "Your own Xiaomi account - PIN and password are never stored in code, only encrypted on this device (Android Keystore).",
    macFieldLabel = "Scooter BLE MAC",
    macFieldPlaceholder = "e.g. find via scan",
    scanButton = "Scan",
    scanningStatus = "Searching for nearby devices ...",
    savedKeyText = "A key is already saved for this MAC.",
    connectSavedButton = "Connect (saved key)",
    forgetSavedButton = "Delete saved key",
    newLoginDivider = "Sign in again / different account:",
    pinFieldLabel = "Device PIN (only if a sharing PIN is set in Mi Home)",
    qrScanInstruction = "Scan with the app that's paired with the scooter (e.g. Xiaomi Home):",
    qrWaitingText = "Waiting for confirmation ...",
    qrOpenChooserTitle = "Open with ...",
    qrOpenButton = "Open with app/browser instead (choose)",
    qrLoginButton = "QR code login (also works without a Mi password, e.g. Google sign-in)",
    passwordAltDivider = "Alternatively with your Mi account password (if set):",
    usernameFieldLabel = "Xiaomi account (email/phone)",
    passwordFieldLabel = "Mi account password",
    passwordLoginButton = "Sign in with password",
    retryPinButton = "Retry with PIN",
    qrImageContentDescription = "QR login code",
    refreshButton = "Refresh",
    disconnectButton = "Disconnect",
    fallbackDeviceName = "Scooter 5 Pro",
    tabRide = "Ride",
    tabBattery = "Battery",
    tabSettings = "Settings",
    tabVehicleStatus = "Vehicle",
    tabIdentification = "Identification",
    tabRideLog = "Ride Log",
    noRidesYet = "No recorded rides yet",
    regionWarningTitle = "Legal Notice",
    regionWarningConfirmSuffix = " By enabling this, you confirm that you take responsibility for it.",
    regionWarningConfirmButton = "I confirm, enable",
    regionWarningCancelButton = "Cancel",
    setButton = "Set",
    loadingPlaceholder = "…",
    emptyValuePlaceholder = "–",
    boolOn = "On",
    boolOff = "Off",
    errorPrefix = "Error: ",
    errorStatusFormat = { status -> "Error (status=$status)" },
    unknownValueFormat = { v -> "Unknown ($v)" },
    connectingSavedBusy = "Connecting to scooter ...",
    noSavedKeyError = { mac -> "No saved key for $mac" },
    cloudLoginBusy = "Xiaomi cloud login ...",
    qrLoadingBusy = "Loading QR code ...",
    searchingDeviceBusy = "Searching for scooter in Xiaomi account ...",
    deviceNotFoundError = { mac -> "No device with MAC $mac found in Xiaomi account" },
    fetchingKeyBusy = "Fetching key (ltmk) ...",
    pinRequiredError = "A sharing PIN is set for this device - please enter it and try again.",
    noActiveSessionError = "No active session - please sign in again",
    noRememberedDeviceError = "No device remembered - please sign in again",
    connectingBluetoothBusy = "Connecting via Bluetooth ...",
    retryingBusy = { attempt -> "Attempt $attempt of 3 ..." },
    authenticatingBusy = "Authenticating ...",
    readingValuesBusy = "Reading values ...",
    setRejectedError = { name, status -> "Scooter rejected the change to \"$name\" (status=$status)." },
)

fun strings(lang: Lang): AppStrings = if (lang == Lang.DE) STRINGS_DE else STRINGS_EN

/** Human-readable property labels (replaces the old mechanical SCREAMING_SNAKE_CASE -> Title
 * Case conversion) - kept bilingual so switching language doesn't leave English field names next
 * to German buttons/dialogs or vice versa. */
private val PROPERTY_NAMES_DE: Map<String, String> = mapOf(
    "RIDING_MODE" to "Fahrmodus", "BATTERY_LEVEL" to "Akkustand", "REMAINING_BATTERY" to "Restkapazität",
    "VOLTAGE" to "Spannung", "CURRENT" to "Strom", "POWER" to "Leistung",
    "REMAINING_MILEAGE" to "Restreichweite", "FAULT" to "Fehlerstatus", "CURRENT_MILEAGE" to "Fahrstrecke",
    "AVERAGE_SPEED" to "Durchschnittsgeschwindigkeit", "IS_LOCKED" to "Gesperrt", "CRUISE_IS_ON" to "Tempomat",
    "TAIL_LIGHT_IS_ON" to "Rücklicht", "ENERGY_RECOVERY" to "Rekuperation", "TOTAL_MILEAGE" to "Gesamtstrecke",
    "IS_RIDING" to "Fahrzustand", "RIDING_TIME" to "Fahrzeit", "HIGHEST_SPEED" to "Höchstgeschwindigkeit",
    "ASR_IS_ON" to "ASR (Anti-Schlupf-Regelung)", "AUTO_LIGHT" to "Automatisches Licht",
    "TCS" to "TCS (Traktionskontrolle)", "INTELLIGENT_DOWNHILL" to "Intelligente Bergabfahrt",
    "HILL_PARKING" to "Berg-Parkbremse", "ATMOSPHERE_LIGHT" to "Ambientebeleuchtung",
    "BLUETOOTH_SEARCH_ON" to "Bluetooth-Suche", "BATTERY_STATUS" to "Akkustatus",
    "BATTERY_TEMPERATURE" to "Akkutemperatur", "SCOOTER_TEMPERATURE" to "Rollertemperatur",
    "MILEAGE_UNIT" to "Streckeneinheit", "ACTIVATION_DATE" to "Aktivierungsdatum",
    "IS_CHARGING" to "Lädt", "NUMBER_OF_CYCLES" to "Ladezyklen", "SOH" to "Akkugesundheit (SOH)",
    "PRODUCTION_DATE" to "Produktionsdatum", "BATTERY_SN" to "Akku-Seriennummer",
    "BMS_FIRMWARE_VERSION" to "BMS-Firmware-Version", "SCOOTER_SN" to "Roller-Seriennummer",
    "FIRMWARE_VERSION" to "Firmware-Version",
    "REMAINING_MILEAGE_ALGORITHM" to "Reichweiten-Algorithmus", "FAKE_SHUTDOWN_STATUS" to "Ruhezustand",
    "LOCK_WARNING" to "Schloss-Warnsignal", "TIRE_MAINTENANCE" to "Reifen-Wartungserinnerung",
    "RIDING_RECORDS" to "Fahrtaufzeichnungen", "MORE_BATTERY_INFO" to "Akku-Detailwerte",
    "MORE_BATTERY_INFO_2" to "Akku-Extremwerte", "BLUETOOTH_CAR_SEARCH" to "Roller-Suche (Signal)",
    "LOG_1" to "Fahrtenbuch 1", "LOG_2" to "Fahrtenbuch 2", "LOG_3" to "Fahrtenbuch 3",
    "LOG_4" to "Fahrtenbuch 4", "LOG_5" to "Fahrtenbuch 5",
)

private val PROPERTY_NAMES_EN: Map<String, String> = mapOf(
    "RIDING_MODE" to "Riding Mode", "BATTERY_LEVEL" to "Battery Level", "REMAINING_BATTERY" to "Remaining Capacity",
    "VOLTAGE" to "Voltage", "CURRENT" to "Current", "POWER" to "Power",
    "REMAINING_MILEAGE" to "Remaining Range", "FAULT" to "Fault", "CURRENT_MILEAGE" to "Trip Distance",
    "AVERAGE_SPEED" to "Average Speed", "IS_LOCKED" to "Locked", "CRUISE_IS_ON" to "Cruise Control",
    "TAIL_LIGHT_IS_ON" to "Tail Light", "ENERGY_RECOVERY" to "Energy Recovery", "TOTAL_MILEAGE" to "Total Distance",
    "IS_RIDING" to "Riding State", "RIDING_TIME" to "Riding Time", "HIGHEST_SPEED" to "Highest Speed",
    "ASR_IS_ON" to "ASR (Traction Control)", "AUTO_LIGHT" to "Auto Light",
    "TCS" to "TCS (Traction Control)", "INTELLIGENT_DOWNHILL" to "Intelligent Downhill",
    "HILL_PARKING" to "Hill Parking", "ATMOSPHERE_LIGHT" to "Atmosphere Light",
    "BLUETOOTH_SEARCH_ON" to "Bluetooth Search", "BATTERY_STATUS" to "Battery Status",
    "BATTERY_TEMPERATURE" to "Battery Temperature", "SCOOTER_TEMPERATURE" to "Scooter Temperature",
    "MILEAGE_UNIT" to "Distance Unit", "ACTIVATION_DATE" to "Activation Date",
    "IS_CHARGING" to "Charging", "NUMBER_OF_CYCLES" to "Charge Cycles", "SOH" to "Battery Health (SOH)",
    "PRODUCTION_DATE" to "Production Date", "BATTERY_SN" to "Battery Serial Number",
    "BMS_FIRMWARE_VERSION" to "BMS Firmware Version", "SCOOTER_SN" to "Scooter Serial Number",
    "FIRMWARE_VERSION" to "Firmware Version",
    "REMAINING_MILEAGE_ALGORITHM" to "Range Algorithm", "FAKE_SHUTDOWN_STATUS" to "Sleep State",
    "LOCK_WARNING" to "Lock Warning Signal", "TIRE_MAINTENANCE" to "Tire Maintenance Reminder",
    "RIDING_RECORDS" to "Riding Records", "MORE_BATTERY_INFO" to "Battery Detail Values",
    "MORE_BATTERY_INFO_2" to "Battery Extreme Values", "BLUETOOTH_CAR_SEARCH" to "Scooter Finder (Signal)",
    "LOG_1" to "Ride Log 1", "LOG_2" to "Ride Log 2", "LOG_3" to "Ride Log 3",
    "LOG_4" to "Ride Log 4", "LOG_5" to "Ride Log 5",
)

fun propertyName(name: String, lang: Lang): String =
    (if (lang == Lang.DE) PROPERTY_NAMES_DE else PROPERTY_NAMES_EN)[name] ?: name

private val FAULT_LABELS_DE: Map<Long, String> = mapOf(
    0L to "Normal", 10L to "Kommunikationsfehler Display", 11L to "Controller überlastet",
    12L to "Controller-Fehler", 14L to "Fehler Gaskabel", 15L to "Fehler Bremshebel-Kabel",
    18L to "Motorfehler", 21L to "Kommunikationsfehler Akku", 24L to "Überdruck im Akku",
    28L to "Controller-Fehler", 29L to "Controller-Fehler", 39L to "Akkufehler",
    40L to "Controller-Fehler", 45L to "Controller überhitzt", 50L to "Temperaturfehler Akku",
    52L to "Akkufehler",
)

private val FAULT_LABELS_EN: Map<Long, String> = mapOf(
    0L to "Normal", 10L to "Display Communication Error", 11L to "Controller Overloaded",
    12L to "Controller Error", 14L to "Throttle Cable Error", 15L to "Brake Lever Cable Error",
    18L to "Motor Error", 21L to "Battery Communication Error", 24L to "Battery Overpressure",
    28L to "Controller Error", 29L to "Controller Error", 39L to "Battery Error",
    40L to "Controller Error", 45L to "Controller Overheated", 50L to "Battery Temperature Error",
    52L to "Battery Error",
)

/** Human-readable labels for enum-valued properties, confirmed against the plugin's own
 * ENUM_LABELS table (not guessed), in both languages. */
private val ENUM_LABELS_DE: Map<String, Map<Long, String>> = mapOf(
    "ENERGY_RECOVERY" to mapOf(30L to "Schwach", 60L to "Mittel", 90L to "Stark"),
    "ATMOSPHERE_LIGHT" to mapOf(0L to "Aus", 1L to "An", 2L to "Aktiv"),
    "IS_RIDING" to mapOf(0L to "Steht", 1L to "Übergang", 2L to "Fährt"),
    "BATTERY_STATUS" to mapOf(1L to "OK"),
    "MILEAGE_UNIT" to mapOf(1L to "km", 0L to "mi"),
    "FAULT" to FAULT_LABELS_DE,
)

private val ENUM_LABELS_EN: Map<String, Map<Long, String>> = mapOf(
    "ENERGY_RECOVERY" to mapOf(30L to "Weak", 60L to "Medium", 90L to "Strong"),
    "ATMOSPHERE_LIGHT" to mapOf(0L to "Off", 1L to "On", 2L to "Active"),
    "IS_RIDING" to mapOf(0L to "Standing", 1L to "Transitioning", 2L to "Riding"),
    "BATTERY_STATUS" to mapOf(1L to "OK"),
    "MILEAGE_UNIT" to mapOf(1L to "km", 0L to "mi"),
    "FAULT" to FAULT_LABELS_EN,
)

// RIDING_MODE's mode names are shown the same in both languages, like a manufacturer preset name
// (comparable to a car keeping "Sport mode" untranslated) - confirmed with the user directly.
private val RIDING_MODE_LABELS: Map<Long, String> = mapOf(11L to "Walk", 2L to "Drive", 3L to "Sport")

fun enumLabel(propertyName: String, value: Long, lang: Lang): String? =
    if (propertyName == "RIDING_MODE") RIDING_MODE_LABELS[value]
    else (if (lang == Lang.DE) ENUM_LABELS_DE else ENUM_LABELS_EN)[propertyName]?.get(value)

fun hasEnumLabels(propertyName: String): Boolean =
    propertyName == "RIDING_MODE" || ENUM_LABELS_DE.containsKey(propertyName)

/** Ordered raw values for cycle-button properties (paired with [enumLabel]/[cycleLabel] for the
 * button text) - the actual allowed value set, confirmed against the plugin's own setProperty
 * calls, is defined once here rather than per-language. */
val CYCLE_VALUES: Map<String, List<Long>> = mapOf(
    "RIDING_MODE" to listOf(11L, 2L, 3L),
    "ENERGY_RECOVERY" to listOf(30L, 60L, 90L),
    "ATMOSPHERE_LIGHT" to listOf(0L, 1L, 2L),
    "MILEAGE_UNIT" to listOf(1L, 0L),
)

fun cycleLabel(propertyName: String, value: Long, lang: Lang): String =
    enumLabel(propertyName, value, lang) ?: value.toString()

/** Region-sensitive legal warnings shown before turning a property ON - see
 * [com.scooterre.client.protocol.SpecProperties.REGION_SENSITIVE_WARNINGS] for which properties
 * and why; the wording itself lives here so it can be bilingual. */
private val REGION_WARNINGS_DE: Map<String, String> = mapOf(
    "CRUISE_IS_ON" to "Der Tempomat ist nicht in jedem Land offiziell freigeschaltet " +
        "(z.B. in Deutschland nicht). Diese App kann nicht wissen, wo du unterwegs bist oder " +
        "was dort erlaubt ist - das musst du selbst prüfen.",
    "TAIL_LIGHT_IS_ON" to "Ein funktionierendes, eingeschaltetes Rücklicht ist in praktisch " +
        "allen EU-Ländern beim Fahren im Straßenverkehr gesetzlich vorgeschrieben. Schalte es " +
        "nur aus, wenn der Roller gerade nicht im Verkehr genutzt wird.",
)

private val REGION_WARNINGS_EN: Map<String, String> = mapOf(
    "CRUISE_IS_ON" to "Cruise control isn't officially enabled in every country (e.g. not in " +
        "Germany). This app can't know where you are or what's allowed there - you need to " +
        "check that yourself.",
    "TAIL_LIGHT_IS_ON" to "A working, switched-on tail light is legally required when riding " +
        "on public roads in practically every EU country. Only turn it off when the scooter " +
        "isn't being used in traffic.",
)

fun regionWarning(propertyName: String, lang: Lang): String? =
    (if (lang == Lang.DE) REGION_WARNINGS_DE else REGION_WARNINGS_EN)[propertyName]

/** TIRE_MAINTENANCE (3.7) is a packed decimal string "[state:1][interval-days:3][remaining-days:3]"
 * (e.g. "2030030") - decode logic ported from the reference plugin's _fmt_tire(), not guessed.
 * state '2' means the reminder is off, anything else means it's on. */
fun formatTireMaintenance(raw: String, lang: Lang): String {
    val s = raw.trim()
    if (s.length < 7 || !s.all { it.isDigit() }) return s.ifEmpty { strings(lang).emptyValuePlaceholder }
    val on = s[0] != '2'
    val interval = s.substring(1, 4).toInt()
    val remaining = s.substring(4, 7).toInt()
    return if (lang == Lang.DE) {
        val state = if (on) "an" else "aus"
        "Erinnerung $state, Intervall $interval Tage, Rest $remaining Tage"
    } else {
        val state = if (on) "on" else "off"
        "Reminder $state, interval $interval days, $remaining days left"
    }
}

/** MORE_BATTERY_INFO (4.7) is a hex-encoded string: 6 hex chars energy delivered (Wh, /1000 for
 * kWh), 4 hex chars total capacity (Ah), 4 hex chars deep-discharge count. Ported from the
 * reference plugin's _fmt_more_battery() - this is also what confirmed REMAINING_BATTERY's unit
 * is mAh (see docs/RESEARCH_LOG.md), not guessed. */
fun formatMoreBatteryInfo(raw: String, lang: Lang): String {
    val s = raw.trim().lowercase()
    if (s.length < 14 || !s.all { it in "0123456789abcdef" }) return s.ifEmpty { strings(lang).emptyValuePlaceholder }
    return try {
        val energyKwh = s.substring(0, 6).toLong(16) / 1000.0
        val capacityAh = s.substring(6, 10).toLong(16)
        val deepDischarges = s.substring(10, 14).toLong(16)
        val energyText = "%.2f".format(if (lang == Lang.DE) java.util.Locale.GERMANY else java.util.Locale.US, energyKwh)
        if (lang == Lang.DE) "Abgegeben $energyText kWh, Kapazität $capacityAh Ah, Tiefentladungen $deepDischarges"
        else "Delivered $energyText kWh, capacity $capacityAh Ah, deep discharges $deepDischarges"
    } catch (e: NumberFormatException) {
        s
    }
}

/** MORE_BATTERY_INFO_2 (4.8) is a hex-encoded string: date/time of the last extreme-temperature
 * event (2 hex chars each for year/month/day/hour/minute/second) plus a 4 hex char charge-time
 * counter in seconds. Ported from the reference plugin's _fmt_more_battery2(). */
fun formatMoreBatteryInfo2(raw: String, lang: Lang): String {
    val s = raw.trim().lowercase()
    if (s.length < 16 || !s.all { it in "0123456789abcdef" }) return s.ifEmpty { strings(lang).emptyValuePlaceholder }
    return try {
        val y = s.substring(0, 2).toInt(16)
        val mo = s.substring(2, 4).toInt(16)
        val d = s.substring(4, 6).toInt(16)
        val h = s.substring(6, 8).toInt(16)
        val mi = s.substring(8, 10).toInt(16)
        val se = s.substring(10, 12).toInt(16)
        val charge = s.substring(12, 16).toLong(16)
        if (y == 0 && mo == 0 && d == 0) {
            if (lang == Lang.DE) "Extremtemperatur: keine Daten, Ladezeit ${charge}s"
            else "Extreme temp: no data, charge time ${charge}s"
        } else {
            val date = "%04d-%02d-%02d %02d:%02d:%02d".format(y, mo, d, h, mi, se)
            if (lang == Lang.DE) "Extremtemperatur: $date, Ladezeit ${charge}s"
            else "Extreme temp: $date, charge time ${charge}s"
        }
    } catch (e: NumberFormatException) {
        s
    }
}

/** One ride-history slot (LOG_1..LOG_5, siid=6) is a concatenation of 16-digit decimal records
 * "[duration*10 min:4][distance*10 km:4][avg-speed*10 kmh:4][top-speed*10 kmh:4]", all-zero
 * records are empty slots. Ported from the reference plugin's ride_records()/_fmt_ride_record(). */
fun formatRideLog(raw: String, lang: Lang): String {
    val s = raw.trim()
    val records = mutableListOf<String>()
    var i = 0
    while (i + 16 <= s.length) {
        val chunk = s.substring(i, i + 16)
        i += 16
        if (!chunk.all { it.isDigit() }) continue
        val durTenths = chunk.substring(0, 4).toIntOrNull() ?: continue
        val distTenths = chunk.substring(4, 8).toIntOrNull() ?: continue
        val avgTenths = chunk.substring(8, 12).toIntOrNull() ?: continue
        val topTenths = chunk.substring(12, 16).toIntOrNull() ?: continue
        if (durTenths == 0 && distTenths == 0 && avgTenths == 0 && topTenths == 0) continue
        val durMin = durTenths / 10.0
        val locale = if (lang == Lang.DE) java.util.Locale.GERMANY else java.util.Locale.US
        val durText = if (durMin >= 60) {
            val h = (durMin / 60).toInt()
            val m = (durMin % 60).toInt()
            if (lang == Lang.DE) "${h}h %02dm".format(m) else "${h}h %02dm".format(m)
        } else {
            if (lang == Lang.DE) "${durMin.toInt()} min" else "${durMin.toInt()} min"
        }
        val dist = "%.1f".format(locale, distTenths / 10.0)
        val avg = "%.1f".format(locale, avgTenths / 10.0)
        val top = "%.1f".format(locale, topTenths / 10.0)
        records += if (lang == Lang.DE) "$durText, $dist km, ø $avg km/h, max $top km/h"
        else "$durText, $dist km, avg $avg km/h, top $top km/h"
    }
    return if (records.isEmpty()) strings(lang).noRidesYet else records.joinToString("; ")
}
