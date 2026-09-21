package com.scooterre.client.ui

/** The two UI languages this app supports, toggled in-app (flag icon) rather than following the
 * OS locale - the app chrome, property labels and enum values must all switch together, see
 * [strings], [propertyName], [enumLabel] and [cycleLabel] below. */
enum class Lang { DE, EN }

/** The language to use: the saved choice if there is one, otherwise German on a German-language
 * device and English everywhere else (English is the project's first language). */
fun resolveLang(stored: String?, systemLanguage: String = java.util.Locale.getDefault().language): Lang = when (stored) {
    "DE" -> Lang.DE
    "EN" -> Lang.EN
    else -> if (systemLanguage == "de") Lang.DE else Lang.EN
}

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
    val webLoginButton: String,
    val qrLoginButton: String,
    val passwordAltDivider: String,
    val usernameFieldLabel: String,
    val passwordFieldLabel: String,
    val passwordLoginButton: String,
    val retryPinButton: String,
    val qrImageContentDescription: String,
    // Device picker screen
    val devicePickerTitle: String,
    val addDeviceTitle: String,
    val addAnotherDeviceButton: String,
    val forgetDeviceButton: String,
    val noSavedDevicesText: String,
    val backToDeviceListButton: String,
    val genericDeviceName: String,
    val cancelButton: String,
    val renameDeviceButton: String,
    val renameDeviceTitle: String,
    val renameDeviceFieldLabel: String,
    val renameDeviceSaveButton: String,
    val forgetDeviceConfirmTitle: String,
    val forgetDeviceConfirmText: (String) -> String,
    val exportDeviceButton: String,
    val exportDialogTitle: String,
    val exportDialogHint: String,
    val shareButton: String,
    val saveAsFileButton: String,
    val importDeviceTitle: String,
    val importDeviceHint: String,
    val importFieldLabel: String,
    val importButton: String,
    val pickFileButton: String,
    val importInvalidCodeError: String,
    // Dashboard chrome
    val refreshButton: String,
    val disconnectButton: String,
    val tabRide: String,
    val tabBattery: String,
    val tabSettings: String,
    val tabVehicleStatus: String,
    val tabIdentification: String,
    val tabRideLog: String,
    val tabHistory: String,
    val sectionOverview: String,
    val menuContentDescription: String,
    val pendingRideTitle: String,
    val pendingRideBody: (km: String, wh: String, since: String) -> String,
    val pendingRideBodyNoSince: (km: String, wh: String) -> String,
    val pendingRideSkipButton: String,
    val resetHistoryButton: String,
    val resetHistoryConfirmTitle: String,
    val resetHistoryConfirmText: String,
    val noHistoryYetText: String,
    val historyKmDrivenFormat: (mode: String, km: String) -> String,
    val historyWhPerKmFormat: (whPerKm: String) -> String,
    val rangeByHabitFormat: (mode: String, range: String) -> String,
    val historyRangeFormat: (range: String) -> String,
    val historyRangeHint: String,
    val batteryLogTitle: String,
    val batteryLogEntryFormat: (date: String, health: String, cycles: String, km: String) -> String,
    val noRidesYet: String,
    val regionWarningTitle: String,
    val regionWarningConfirmSuffix: String,
    val regionWarningConfirmButton: String,
    val regionWarningCancelButton: String,
    val setButton: String,
    val triggerButton: String,
    val tireIntervalLabel: String,
    val sectionApp: String,
    val keepScreenOnLabel: String,
    val keepScreenOnHint: String,
    val themeLabel: String,
    val themeSystem: String,
    val autoBrightnessLabel: String,
    val autoBrightnessHint: String,
    val settingsLanguageLabel: String,
    val autoConnectLabel: String,
    val autoConnectHint: String,
    val refreshRateLabel: String,
    val refreshRateHint: String,
    val refreshEconomy: String,
    val refreshNormal: String,
    val refreshFast: String,
    val unitsLabel: String,
    val unitsMetric: String,
    val unitsImperial: String,
    val confirmCriticalLabel: String,
    val confirmCriticalHint: String,
    val confirmChangeTitle: String,
    val confirmChangeText: (String) -> String,
    val confirmChangeButton: String,
    val rideTrackingLabel: String,
    val rideTrackingHint: String,
    val aboutLabel: String,
    val aboutVersion: (String) -> String,
    val aboutBody: String,
    val aboutGithubButton: String,
    val diagnosticsButton: String,
    val diagnosticsCopied: String,
    val deviceListMenu: String,
    val updateAvailable: (String) -> String,
    val updateOpenButton: String,
    val updateDownloadButton: String,
    val updateInstallButton: String,
    val updateDownloading: (Int) -> String,
    val updateAllowInstallHint: String,
    val updateProblemText: (com.scooterre.client.update.UpdateProblem) -> String,
    val updateCheckLabel: String,
    val updateCheckHint: String,
    val docsTileTitle: String,
    val docsTileSubtitle: (Int) -> String,
    val docsTitle: String,
    val docsEmpty: String,
    val docsScan: String,
    val docsImport: String,
    val docsAddPageShort: String,
    val docsPages: (Int) -> String,
    val docsNameTitle: String,
    val docsNameLabel: String,
    val docsDefaultName: String,
    val docsMorePage: String,
    val docsPagesTaken: (Int) -> String,
    val docsDeleteTitle: String,
    val docsDeleteText: (String) -> String,
    val docsDeleteButton: String,
    val docsImportError: String,
    val docsScanHint: String,
    val docsSendButton: String,
    val docsReceived: (Int) -> String,
    val docsUnknownScooter: String,
    val docsFromPhotos: String,
    val exportBundleLabel: String,
    val exportCodeLabel: String,
    val shareCodeButton: String,
    val closeButton: String,
    val exportPasswordLabel: String,
    val exportPasswordHint: String,
    val importPasswordTitle: String,
    val importPasswordLabel: String,
    val importWrongPasswordError: String,
    val appLockLabel: String,
    val appLockHint: String,
    val insuranceLabel: String,
    val insuranceHint: String,
    val insuranceTestButton: String,
    val insuranceTestSent: String,
    val insuranceNotificationsBlocked: String,
    val insuranceAppliedLabel: (String) -> String,
    val insuranceChannelName: String,
    val insuranceTestPrefix: String,
    val insuranceActionApplied: String,
    val insuranceNotifTitle: (com.scooterre.client.reminder.InsuranceSchedule.Stage) -> String,
    val insuranceNotifText: (String, String) -> String,
    val lockScreenTitle: String,
    val lockUnlockButton: String,
    val lockPromptTitle: String,
    val lockUnavailable: String,
    val backupTitle: String,
    val backupLast: (String) -> String,
    val backupNever: String,
    val backupCreate: String,
    val backupRestore: String,
    val backupPassword: String,
    val backupPasswordRepeat: String,
    val backupWarning: String,
    val backupInvalidPassword: String,
    val backupSettingsCheckbox: String,
    val backupRestoreTitle: String,
    val backupDone: (Int, Int) -> String,
    val backupTooLarge: String,
    val themeLight: String,
    val themeDark: String,
    val exportRideLogButton: String,
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
    macFieldLabel = "Scooter BLE-MAC",
    macFieldPlaceholder = "z.B. per Scan finden",
    scanButton = "Scannen",
    scanningStatus = "Suche nach Geräten in der Nähe ...",
    savedKeyText = "Für diese MAC ist bereits ein Schlüssel gespeichert.",
    connectSavedButton = "Verbinden (gespeicherter Schlüssel)",
    forgetSavedButton = "Gespeicherten Schlüssel löschen",
    newLoginDivider = "Neu anmelden / anderes Konto:",
    pinFieldLabel = "Geräte-PIN (nur falls in Xiaomi Home eine Sharing-PIN gesetzt ist)",
    qrScanInstruction = "Mit der App scannen, die mit dem Scooter verbunden ist (z.B. Xiaomi Home):",
    qrWaitingText = "Warte auf Bestätigung ...",
    qrOpenChooserTitle = "Öffnen mit ...",
    qrOpenButton = "Stattdessen mit App/Browser öffnen (Auswahl)",
    webLoginButton = "Hier direkt anmelden (kein Zweitgerät nötig)",
    qrLoginButton = "QR-Code-Login (auch ohne Xiaomi-Passwort, z.B. bei Google-Konto)",
    passwordAltDivider = "Alternativ mit Xiaomi-Konto-Passwort (falls gesetzt):",
    usernameFieldLabel = "Xiaomi-Konto (E-Mail/Telefon)",
    passwordFieldLabel = "Xiaomi-Konto-Passwort",
    passwordLoginButton = "Mit Passwort anmelden",
    retryPinButton = "Mit PIN erneut versuchen",
    qrImageContentDescription = "QR-Login-Code",
    devicePickerTitle = "Meine Scooter",
    addDeviceTitle = "Scooter hinzufügen",
    addAnotherDeviceButton = "Weiteren Scooter hinzufügen",
    forgetDeviceButton = "Vergessen",
    noSavedDevicesText = "Noch kein Scooter gespeichert.",
    backToDeviceListButton = "Zurück zur Geräteliste",
    genericDeviceName = "Scooter",
    cancelButton = "Abbrechen",
    renameDeviceButton = "Umbenennen",
    renameDeviceTitle = "Scooter umbenennen",
    renameDeviceFieldLabel = "Name",
    renameDeviceSaveButton = "Speichern",
    forgetDeviceConfirmTitle = "Scooter vergessen?",
    forgetDeviceConfirmText = { name -> "Der gespeicherte Schlüssel für \"$name\" wird gelöscht - zum erneuten Verbinden ist wieder ein Login (Cloud/PIN) nötig. Auch die Dokumente und der Verlauf dieses Scooters werden von diesem Handy gelöscht." },
    exportDeviceButton = "Exportieren",
    exportDialogTitle = "Zugang exportieren",
    exportDialogHint = "Die Datei enthält den vollständigen Zugangsschlüssel, die Dokumente und den Verlauf dieses Scooters. Nur an Personen weitergeben, die den Scooter auch wirklich mitbenutzen dürfen (z.B. per WhatsApp/Signal) - nicht öffentlich teilen.",
    shareButton = "Teilen",
    saveAsFileButton = "Als Datei speichern",
    importDeviceTitle = "Zugang importieren",
    importDeviceHint = "Von jemandem, der bereits Zugriff auf diesen Scooter hat (z.B. über \"Exportieren\") erhalten und hier einfügen - kein eigener Cloud-Login nötig.",
    importFieldLabel = "Zugangscode",
    importButton = "Importieren",
    pickFileButton = "Datei auswählen",
    importInvalidCodeError = "Ungültiger Zugangscode - bitte den vollständigen Text erneut kopieren und einfügen.",
    refreshButton = "Aktualisieren",
    disconnectButton = "Trennen",
    tabRide = "Fahrt",
    tabBattery = "Akku",
    tabSettings = "Einstellungen",
    tabVehicleStatus = "Fahrzeug",
    tabIdentification = "Identifikation",
    tabRideLog = "Fahrtenbuch",
    tabHistory = "Verlauf",
    sectionOverview = "Übersicht",
    menuContentDescription = "Menü",
    pendingRideTitle = "Fahrmodus seit letztem Mal?",
    pendingRideBody = { km, wh, since -> "Seit deiner letzten Verbindung ($since) bist du $km gefahren und hast $wh Wh verbraucht. In welchem Modus überwiegend?" },
    pendingRideBodyNoSince = { km, wh -> "Seit deiner letzten Verbindung bist du $km gefahren und hast $wh Wh verbraucht. In welchem Modus überwiegend?" },
    pendingRideSkipButton = "Weiß nicht / ignorieren",
    resetHistoryButton = "Verlauf zurücksetzen",
    resetHistoryConfirmTitle = "Verlauf wirklich zurücksetzen?",
    resetHistoryConfirmText = "Alle bisher aufgezeichneten Kilometer und der Verbrauch pro Modus werden gelöscht - das lässt sich nicht rückgängig machen. Sinnvoll z.B. nach einem Akkutausch.",
    noHistoryYetText = "Noch keine Fahrten aufgezeichnet - das baut sich mit der Zeit auf, jedes Mal wenn du dich mit dem Scooter verbindest.",
    historyKmDrivenFormat = { mode, dist -> "Gefahrene Strecke ($mode): $dist" },
    historyWhPerKmFormat = { consumption -> "Verbrauch: $consumption" },
    rangeByHabitFormat = { mode, range -> "Nach deinem Verbrauch ($mode): ca. $range" },
    historyRangeFormat = { range -> "Reichweite mit aktuellem Akkustand: ca. $range" },
    historyRangeHint = "Die Reichweite nach deinem Verbrauch rechnet den Rest im Akku mit deinem gemessenen Verbrauch je Modus hoch; neuere Fahrten zählen mehr. Sie erscheint ab 5 km Daten im jeweiligen Modus.",
    batteryLogTitle = "Akku-Gesundheit",
    batteryLogEntryFormat = { date, health, cycles, km -> "$date: Gesundheit $health, $cycles Zyklen, $km" },
    noRidesYet = "Noch keine aufgezeichneten Fahrten",
    regionWarningTitle = "Rechtlicher Hinweis",
    regionWarningConfirmSuffix = " Mit dem Aktivieren bestätigst du, dass du die Verantwortung dafür übernimmst.",
    regionWarningConfirmButton = "Ich bestätige, aktivieren",
    regionWarningCancelButton = "Abbrechen",
    setButton = "Setzen",
    triggerButton = "Auslösen",
    tireIntervalLabel = "Wartungsintervall (Tage)",
    sectionApp = "App-Einstellungen",
    keepScreenOnLabel = "Bildschirm anlassen",
    keepScreenOnHint = "Solange der Scooter verbunden ist und die App im Vordergrund bleibt.",
    themeLabel = "Design",
    themeSystem = "System",
    autoBrightnessLabel = "Helligkeit automatisch",
    autoBrightnessHint = "Passt die Bildschirmhelligkeit an das Umgebungslicht an, solange die App offen ist - hell bei Sonne, gedimmt im Dunkeln.",
    settingsLanguageLabel = "Sprache",
    autoConnectLabel = "Automatisch verbinden",
    autoConnectHint = "Beim App-Start direkt mit dem zuletzt genutzten Scooter verbinden. Die Zurück-Geste führt zur Geräteliste.",
    refreshRateLabel = "Aktualisierung im Stand",
    refreshRateHint = "Wie oft alle Werte neu gelesen werden, solange der Scooter steht. Beim Fahren sind es immer etwa 2,5 Sekunden.",
    refreshEconomy = "Sparsam (ca. 40 s)",
    refreshNormal = "Normal (ca. 20 s)",
    refreshFast = "Schnell (ca. 12 s)",
    unitsLabel = "Einheiten",
    unitsMetric = "Metrisch (km, km/h, °C)",
    unitsImperial = "Imperial (mi, mph, °F)",
    confirmCriticalLabel = "Vor Sperren/Entsperren bestätigen",
    confirmCriticalHint = "Fragt kurz nach, damit nichts versehentlich umgeschaltet wird.",
    confirmChangeTitle = "Wirklich ändern?",
    confirmChangeText = { name -> "\"$name\" jetzt ändern?" },
    confirmChangeButton = "Ändern",
    rideTrackingLabel = "Fahrten-Abfrage nach dem Verbinden",
    rideTrackingHint = "Fragt nach dem Verbinden, in welchem Modus du gefahren bist, und führt daraus den Verbrauchs-Verlauf.",
    aboutLabel = "Über diese App",
    aboutVersion = { v -> "Version $v" },
    aboutBody = "Unabhängiges, nicht-kommerzielles Projekt ohne Verbindung zu Xiaomi. Nutzung auf eigenes Risiko, ohne Gewähr. Lizenz: PolyForm Noncommercial 1.0.0.",
    aboutGithubButton = "Projekt auf GitHub",
    diagnosticsButton = "Diagnose kopieren",
    diagnosticsCopied = "Kopiert: App-, Handy- und Scooter-Modell, Einstellungen und die letzten Fehlermeldungen. Ohne MAC-Adresse, Schlüssel und Dokumente. Lies den Text kurz durch, bevor du ihn in ein Issue einfügst.",
    deviceListMenu = "Geräteliste",
    updateAvailable = { v -> "Update verfügbar: Version $v" },
    updateOpenButton = "Ansehen",
    updateDownloadButton = "Update laden",
    updateInstallButton = "Installieren",
    updateDownloading = { p -> "Lade … $p %" },
    updateAllowInstallHint = "Erlaube dieser App im nächsten Fenster, Apps zu installieren, und tippe danach auf „Installieren\".",
    updateProblemText = { problem ->
        when (problem) {
            com.scooterre.client.update.UpdateProblem.DOWNLOAD -> "Der Download ist fehlgeschlagen. Bitte später erneut versuchen oder die Release-Seite öffnen."
            com.scooterre.client.update.UpdateProblem.HASH -> "Die geladene Datei stimmt nicht mit der veröffentlichten Prüfsumme überein und wurde verworfen."
            com.scooterre.client.update.UpdateProblem.SIGNER -> "Die geladene Datei ist mit einem anderen Schlüssel signiert als die installierte App und wurde verworfen."
            com.scooterre.client.update.UpdateProblem.NOT_APK -> "Die geladene Datei ist keine gültige App und wurde verworfen."
        }
    },
    updateCheckLabel = "Nach Updates suchen",
    updateCheckHint = "Fragt einmal täglich bei GitHub nach einer neuen Version. Dabei sieht GitHub deine IP-Adresse.",
    docsTileTitle = "Dokumente",
    docsTileSubtitle = { n -> if (n == 0) "Versicherung, Betriebserlaubnis & Co. griffbereit" else if (n == 1) "1 Dokument, auch ohne Verbindung" else "$n Dokumente, auch ohne Verbindung" },
    docsTitle = "Dokumente",
    docsEmpty = "Noch keine Dokumente. Fotografiere oder importiere z. B. die Versicherungsbestätigung und die Betriebserlaubnis - sie sind dann auch ohne Verbindung und ohne Netz sofort da.",
    docsScan = "Scannen",
    docsImport = "Datei",
    docsFromPhotos = "Fotos",
    exportBundleLabel = "Komplett: Schlüssel, Dokumente und Verlauf",
    exportCodeLabel = "Nur der Zugangscode (Text, ohne Dokumente)",
    shareCodeButton = "Code teilen",
    closeButton = "Schließen",
    exportPasswordLabel = "Passwort (frei wählbar)",
    exportPasswordHint = "Verschlüsselt die Datei. Der Empfänger braucht dasselbe Passwort zum Import (z. B. die Scooter-PIN oder ein eigenes). Leer lassen = unverschlüsselt.",
    importPasswordTitle = "Datei ist verschlüsselt",
    importPasswordLabel = "Passwort",
    importWrongPasswordError = "Falsches Passwort (oder beschädigte Datei).",
    appLockLabel = "App-Sperre",
    appLockHint = "Fragt beim Start der App nach Fingerabdruck oder Geräte-PIN. Nach dem Wechsel in den Hintergrund bleibt die laufende App entsperrt; das Widget ist nicht geschützt.",
    insuranceLabel = "Versicherungskennzeichen-Erinnerung",
    insuranceHint = "Erinnert einen Monat, eine Woche und am letzten Tag vor Ablauf (Deutschland: das Kennzeichen gilt jeweils bis Ende Februar). Mit „Neue Versicherung ist beantragt\" bei den Dokumenten lassen sich die Meldungen stoppen.",
    insuranceTestButton = "Testbenachrichtigung senden",
    insuranceTestSent = "Testbenachrichtigung gesendet.",
    insuranceNotificationsBlocked = "Benachrichtigungen sind für die App nicht erlaubt. Bitte in den Systemeinstellungen freigeben.",
    insuranceAppliedLabel = { d -> "Neue Versicherung ist beantragt (aktuelles Kennzeichen gilt bis $d)" },
    insuranceChannelName = "Versicherungskennzeichen",
    insuranceTestPrefix = "Test: ",
    insuranceActionApplied = "Neue Versicherung beantragt",
    insuranceNotifTitle = { stage ->
        when (stage) {
            com.scooterre.client.reminder.InsuranceSchedule.Stage.MONTH -> "Versicherungskennzeichen läuft in einem Monat ab"
            com.scooterre.client.reminder.InsuranceSchedule.Stage.WEEK -> "Versicherungskennzeichen läuft in einer Woche ab"
            com.scooterre.client.reminder.InsuranceSchedule.Stage.LAST_DAY -> "Versicherungskennzeichen läuft heute ab"
            else -> "Versicherungskennzeichen"
        }
    },
    insuranceNotifText = { date, names -> "Gültig bis $date – $names. Neue Versicherung schon beantragt?" },
    lockScreenTitle = "App gesperrt",
    lockUnlockButton = "Entsperren",
    lockPromptTitle = "App entsperren",
    lockUnavailable = "Auf diesem Handy ist keine Displaysperre oder kein Fingerabdruck eingerichtet.",
    backupTitle = "Sicherung",
    backupLast = { d -> "Letzte Sicherung: $d" },
    backupNever = "Noch keine Sicherung erstellt.",
    backupCreate = "Sicherung erstellen",
    backupRestore = "Wiederherstellen",
    backupPassword = "Passwort (optional)",
    backupPasswordRepeat = "Passwort wiederholen",
    backupWarning = "Alle Scooter mit Schlüsseln, Dokumenten, Verlauf und App-Einstellungen in einer Datei. Leer lassen = ohne Passwort (bequem, dann nur an Vertraute weitergeben). Mit Passwort lässt sich die Datei auf jedem Handy öffnen, aber nicht ohne das Passwort.",
    backupInvalidPassword = "Die Passwörter stimmen nicht überein.",
    backupSettingsCheckbox = "App-Einstellungen wiederherstellen",
    backupRestoreTitle = "Sicherung wiederherstellen",
    backupDone = { n, docs -> "Wiederhergestellt: $n Scooter, $docs Dokumente." },
    backupTooLarge = "Die Sicherung wäre größer als 60 MB.",
    docsAddPageShort = "+ Seite",
    docsPages = { n -> if (n == 1) "1 Seite" else "$n Seiten" },
    docsNameTitle = "Dokument speichern",
    docsNameLabel = "Name",
    docsDefaultName = "Dokument",
    docsMorePage = "Weitere Seite",
    docsPagesTaken = { n -> if (n == 1) "1 Seite aufgenommen" else "$n Seiten aufgenommen" },
    docsDeleteTitle = "Dokument löschen?",
    docsDeleteText = { name -> "\"$name\" wird von diesem Handy gelöscht." },
    docsDeleteButton = "Löschen",
    docsImportError = "Die Datei konnte nicht gespeichert werden.",
    docsSendButton = "📤 Dokumente senden",
    docsReceived = { n -> if (n == 0) "Keine neuen Dokumente - alles war schon da." else "$n neue(s) Dokument(e) hinzugefügt." },
    docsUnknownScooter = "Dieser Scooter ist auf diesem Handy noch nicht eingerichtet. Bitte zuerst den Scooter hinzufügen.",
    docsScanHint = "Scannen öffnet die Kamera deines Handys (dort z. B. den Dokumentenmodus wählen) und danach die Auswahl der Aufnahmen. Fotos: bereits vorhandene Fotos auswählen, mehrere = mehrere Seiten. Datei: PDF oder Bild aus dem Dateimanager. Die Originalfotos bleiben in der Galerie (ggf. auch in einer Cloud-Sicherung) - bei Bedarf dort löschen.",
    themeLight = "Hell",
    themeDark = "Dunkel",
    exportRideLogButton = "Fahrtenbuch exportieren",
    loadingPlaceholder = "…",
    emptyValuePlaceholder = "–",
    boolOn = "An",
    boolOff = "Aus",
    errorPrefix = "Fehler: ",
    errorStatusFormat = { status -> "Fehler (status=$status)" },
    unknownValueFormat = { v -> "Unbekannt ($v)" },
    connectingSavedBusy = "Verbinde mit Scooter ...",
    noSavedKeyError = { mac -> "Kein gespeicherter Schlüssel für $mac" },
    cloudLoginBusy = "Xiaomi-Cloud-Login ...",
    qrLoadingBusy = "QR-Code wird geladen ...",
    searchingDeviceBusy = "Suche Scooter im Xiaomi-Konto ...",
    deviceNotFoundError = { mac -> "Kein Gerät mit MAC $mac im Xiaomi-Konto gefunden" },
    fetchingKeyBusy = "Hole Schlüssel (ltmk) ...",
    pinRequiredError = "Für dieses Gerät ist eine Sharing-PIN gesetzt - bitte eingeben und erneut versuchen.",
    noActiveSessionError = "Keine aktive Anmeldung - bitte erneut anmelden",
    noRememberedDeviceError = "Kein Gerät gemerkt - bitte erneut anmelden",
    connectingBluetoothBusy = "Verbinde per Bluetooth ...",
    retryingBusy = { attempt -> "Versuch $attempt von 3 ..." },
    authenticatingBusy = "Authentifiziere ...",
    readingValuesBusy = "Lese Werte ...",
    setRejectedError = { name, status -> "Scooter hat die Änderung an \"$name\" abgelehnt (status=$status)." },
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
    pinFieldLabel = "Device PIN (only if a sharing PIN is set in Xiaomi Home)",
    qrScanInstruction = "Scan with the app that's paired with the scooter (e.g. Xiaomi Home):",
    qrWaitingText = "Waiting for confirmation ...",
    qrOpenChooserTitle = "Open with ...",
    qrOpenButton = "Open with app/browser instead (choose)",
    webLoginButton = "Sign in here directly (no second device needed)",
    qrLoginButton = "QR code login (also works without a Xiaomi password, e.g. Google sign-in)",
    passwordAltDivider = "Alternatively with your Xiaomi account password (if set):",
    usernameFieldLabel = "Xiaomi account (email/phone)",
    passwordFieldLabel = "Xiaomi account password",
    passwordLoginButton = "Sign in with password",
    retryPinButton = "Retry with PIN",
    qrImageContentDescription = "QR login code",
    devicePickerTitle = "My Scooters",
    addDeviceTitle = "Add Scooter",
    addAnotherDeviceButton = "Add another scooter",
    forgetDeviceButton = "Forget",
    noSavedDevicesText = "No scooter saved yet.",
    backToDeviceListButton = "Back to device list",
    genericDeviceName = "Scooter",
    cancelButton = "Cancel",
    renameDeviceButton = "Rename",
    renameDeviceTitle = "Rename scooter",
    renameDeviceFieldLabel = "Name",
    renameDeviceSaveButton = "Save",
    forgetDeviceConfirmTitle = "Forget scooter?",
    forgetDeviceConfirmText = { name -> "This removes the saved key for \"$name\" - you'll need to log in again (cloud/PIN) to reconnect it. Its documents and history are deleted from this phone as well." },
    exportDeviceButton = "Export",
    exportDialogTitle = "Export access",
    exportDialogHint = "The file contains the full access key, the documents and the history of this scooter. Only share it with people who are actually allowed to use the scooter too (e.g. via WhatsApp/Signal) - never share it publicly.",
    shareButton = "Share",
    saveAsFileButton = "Save as file",
    importDeviceTitle = "Import access",
    importDeviceHint = "Received from someone who already has access to this scooter (e.g. via \"Export\") - paste it here, no cloud login of your own needed.",
    importFieldLabel = "Access code",
    importButton = "Import",
    pickFileButton = "Choose file",
    importInvalidCodeError = "Invalid access code - please copy and paste the full text again.",
    refreshButton = "Refresh",
    disconnectButton = "Disconnect",
    tabRide = "Ride",
    tabBattery = "Battery",
    tabSettings = "Settings",
    tabVehicleStatus = "Vehicle",
    tabIdentification = "Identification",
    tabRideLog = "Ride Log",
    tabHistory = "History",
    sectionOverview = "Overview",
    menuContentDescription = "Menu",
    pendingRideTitle = "Riding mode since last time?",
    pendingRideBody = { km, wh, since -> "Since your last connection ($since), you've ridden $km and used $wh Wh. Mostly which mode?" },
    pendingRideBodyNoSince = { km, wh -> "Since your last connection, you've ridden $km and used $wh Wh. Mostly which mode?" },
    pendingRideSkipButton = "Don't know / skip",
    resetHistoryButton = "Reset history",
    resetHistoryConfirmTitle = "Really reset the history?",
    resetHistoryConfirmText = "All recorded distance and consumption per mode will be deleted - this can't be undone. Useful e.g. after a battery replacement.",
    noHistoryYetText = "No rides recorded yet - this builds up over time, every time you connect to the scooter.",
    historyKmDrivenFormat = { mode, dist -> "Distance ridden ($mode): $dist" },
    historyWhPerKmFormat = { consumption -> "Consumption: $consumption" },
    rangeByHabitFormat = { mode, range -> "At your consumption ($mode): about $range" },
    historyRangeFormat = { range -> "Range at the current charge: about $range" },
    historyRangeHint = "The range at your consumption projects the energy left in the battery with the consumption measured per mode; newer rides count more. It appears once a mode has 5 km of data.",
    batteryLogTitle = "Battery health",
    batteryLogEntryFormat = { date, health, cycles, km -> "$date: health $health, $cycles cycles, $km" },
    noRidesYet = "No recorded rides yet",
    regionWarningTitle = "Legal Notice",
    regionWarningConfirmSuffix = " By enabling this, you confirm that you take responsibility for it.",
    regionWarningConfirmButton = "I confirm, enable",
    regionWarningCancelButton = "Cancel",
    setButton = "Set",
    triggerButton = "Trigger",
    tireIntervalLabel = "Maintenance interval (days)",
    sectionApp = "App settings",
    keepScreenOnLabel = "Keep screen on",
    keepScreenOnHint = "While the scooter is connected and the app stays in the foreground.",
    themeLabel = "Theme",
    themeSystem = "System",
    autoBrightnessLabel = "Automatic brightness",
    autoBrightnessHint = "Adapts the screen brightness to the ambient light while the app is open - bright in sun, dimmed in the dark.",
    settingsLanguageLabel = "Language",
    autoConnectLabel = "Connect automatically",
    autoConnectHint = "On app start, connect straight to the last used scooter. The back gesture leads to the device list.",
    refreshRateLabel = "Refresh while parked",
    refreshRateHint = "How often all values are re-read while the scooter is standing. While riding it is always about every 2.5 seconds.",
    refreshEconomy = "Economy (~40 s)",
    refreshNormal = "Normal (~20 s)",
    refreshFast = "Fast (~12 s)",
    unitsLabel = "Units",
    unitsMetric = "Metric (km, km/h, °C)",
    unitsImperial = "Imperial (mi, mph, °F)",
    confirmCriticalLabel = "Confirm lock/unlock",
    confirmCriticalHint = "Asks once so nothing gets switched by accident.",
    confirmChangeTitle = "Really change?",
    confirmChangeText = { name -> "Change \"$name\" now?" },
    confirmChangeButton = "Change",
    rideTrackingLabel = "Ride prompt after connecting",
    rideTrackingHint = "After connecting, asks which mode you rode in and builds the consumption history from that.",
    aboutLabel = "About this app",
    aboutVersion = { v -> "Version $v" },
    aboutBody = "Independent, non-commercial project, not affiliated with Xiaomi. Use at your own risk, no warranty. License: PolyForm Noncommercial 1.0.0.",
    aboutGithubButton = "Project on GitHub",
    diagnosticsButton = "Copy diagnostics",
    diagnosticsCopied = "Copied: app, phone and scooter model, settings and the latest error messages. No MAC address, keys or documents. Give it a quick read before pasting it into an issue.",
    deviceListMenu = "Device list",
    updateAvailable = { v -> "Update available: version $v" },
    updateOpenButton = "View",
    updateDownloadButton = "Download update",
    updateInstallButton = "Install",
    updateDownloading = { p -> "Downloading … $p %" },
    updateAllowInstallHint = "In the next window allow this app to install apps, then tap \"Install\".",
    updateProblemText = { problem ->
        when (problem) {
            com.scooterre.client.update.UpdateProblem.DOWNLOAD -> "The download failed. Please try again later or open the release page."
            com.scooterre.client.update.UpdateProblem.HASH -> "The downloaded file does not match the published checksum and was discarded."
            com.scooterre.client.update.UpdateProblem.SIGNER -> "The downloaded file is signed with a different key than the installed app and was discarded."
            com.scooterre.client.update.UpdateProblem.NOT_APK -> "The downloaded file is not a valid app and was discarded."
        }
    },
    updateCheckLabel = "Check for updates",
    updateCheckHint = "Asks GitHub once a day for a newer version. GitHub sees your IP address when it does.",
    docsTileTitle = "Documents",
    docsTileSubtitle = { n -> if (n == 0) "Insurance, registration & more at hand" else if (n == 1) "1 document, works without a connection" else "$n documents, work without a connection" },
    docsTitle = "Documents",
    docsEmpty = "No documents yet. Photograph or import e.g. the insurance confirmation and the registration papers - they are then available instantly, without a connection or network.",
    docsScan = "Scan",
    docsImport = "File",
    docsFromPhotos = "Photos",
    exportBundleLabel = "Complete: key, documents and history",
    exportCodeLabel = "Access code only (text, no documents)",
    shareCodeButton = "Share code",
    closeButton = "Close",
    exportPasswordLabel = "Password (your choice)",
    exportPasswordHint = "Encrypts the file. The recipient needs the same password to import it (e.g. the scooter PIN or one of your own). Leave empty = unencrypted.",
    importPasswordTitle = "File is encrypted",
    importPasswordLabel = "Password",
    importWrongPasswordError = "Wrong password (or damaged file).",
    appLockLabel = "App lock",
    appLockHint = "Asks for fingerprint or device PIN when the app starts. A running app stays unlocked when it goes to the background; the widget is not protected.",
    insuranceLabel = "Insurance plate reminder",
    insuranceHint = "Reminds you one month, one week and on the last day before it expires (Germany: the plate is valid until the end of February each year). Tick \"New insurance applied for\" in the documents to stop the reminders.",
    insuranceTestButton = "Send test notification",
    insuranceTestSent = "Test notification sent.",
    insuranceNotificationsBlocked = "Notifications are not allowed for the app. Please allow them in the system settings.",
    insuranceAppliedLabel = { d -> "New insurance applied for (current plate is valid until $d)" },
    insuranceChannelName = "Insurance plate",
    insuranceTestPrefix = "Test: ",
    insuranceActionApplied = "New insurance applied for",
    insuranceNotifTitle = { stage ->
        when (stage) {
            com.scooterre.client.reminder.InsuranceSchedule.Stage.MONTH -> "Insurance plate expires in one month"
            com.scooterre.client.reminder.InsuranceSchedule.Stage.WEEK -> "Insurance plate expires in one week"
            com.scooterre.client.reminder.InsuranceSchedule.Stage.LAST_DAY -> "Insurance plate expires today"
            else -> "Insurance plate"
        }
    },
    insuranceNotifText = { date, names -> "Valid until $date – $names. Already applied for the new insurance?" },
    lockScreenTitle = "App locked",
    lockUnlockButton = "Unlock",
    lockPromptTitle = "Unlock app",
    lockUnavailable = "No screen lock or fingerprint is set up on this phone.",
    backupTitle = "Backup",
    backupLast = { d -> "Last backup: $d" },
    backupNever = "No backup created yet.",
    backupCreate = "Create backup",
    backupRestore = "Restore",
    backupPassword = "Password (optional)",
    backupPasswordRepeat = "Repeat password",
    backupWarning = "All scooters with keys, documents, history and app settings in one file. Leave empty = no password (convenient, then only share it with people you trust). With a password the file opens on any phone, but not without the password.",
    backupInvalidPassword = "The passwords do not match.",
    backupSettingsCheckbox = "Restore app settings",
    backupRestoreTitle = "Restore backup",
    backupDone = { n, docs -> "Restored: $n scooters, $docs documents." },
    backupTooLarge = "The backup would be larger than 60 MB.",
    docsAddPageShort = "+ Page",
    docsPages = { n -> if (n == 1) "1 page" else "$n pages" },
    docsNameTitle = "Save document",
    docsNameLabel = "Name",
    docsDefaultName = "Document",
    docsMorePage = "Another page",
    docsPagesTaken = { n -> if (n == 1) "1 page taken" else "$n pages taken" },
    docsDeleteTitle = "Delete document?",
    docsDeleteText = { name -> "\"$name\" will be deleted from this phone." },
    docsDeleteButton = "Delete",
    docsImportError = "The file could not be saved.",
    docsSendButton = "📤 Send documents",
    docsReceived = { n -> if (n == 0) "No new documents - everything was already here." else "Added $n new document(s)." },
    docsUnknownScooter = "This scooter is not set up on this phone yet. Please add the scooter first.",
    docsScanHint = "Scan opens your phone's camera (choose e.g. its document mode there) and then the picker for the shots. Photos: pick existing photos, several = several pages. File: a PDF or image from the file manager. The original photos stay in the gallery (possibly in a cloud backup too) - delete them there if you want.",
    themeLight = "Light",
    themeDark = "Dark",
    exportRideLogButton = "Export ride log",
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
    "BATTERY_TEMPERATURE" to "Akkutemperatur", "SCOOTER_TEMPERATURE" to "Scootertemperatur",
    "MILEAGE_UNIT" to "Streckeneinheit", "ACTIVATION_DATE" to "Aktivierungsdatum",
    "IS_CHARGING" to "Lädt", "NUMBER_OF_CYCLES" to "Ladezyklen", "SOH" to "Akkugesundheit (SOH)",
    "PRODUCTION_DATE" to "Produktionsdatum", "BATTERY_SN" to "Akku-Seriennummer",
    "BMS_FIRMWARE_VERSION" to "BMS-Firmware-Version", "SCOOTER_SN" to "Scooter-Seriennummer",
    "FIRMWARE_VERSION" to "Firmware-Version",
    "REMAINING_MILEAGE_ALGORITHM" to "Reichweiten-Algorithmus", "FAKE_SHUTDOWN_STATUS" to "Ruhezustand",
    "LOCK_WARNING" to "Schloss-Warnsignal", "TIRE_MAINTENANCE" to "Reifen-Wartungserinnerung",
    "MORE_BATTERY_INFO" to "Akku-Detailwerte", "MORE_BATTERY_INFO_2" to "Akku-Extremwerte",
    "BLUETOOTH_CAR_SEARCH" to "Scooter-Suche (Signal)",
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
    "MORE_BATTERY_INFO" to "Battery Detail Values", "MORE_BATTERY_INFO_2" to "Battery Extreme Values",
    "BLUETOOTH_CAR_SEARCH" to "Scooter Finder (Signal)",
    "LOG_1" to "Ride Log 1", "LOG_2" to "Ride Log 2", "LOG_3" to "Ride Log 3",
    "LOG_4" to "Ride Log 4", "LOG_5" to "Ride Log 5",
)

fun propertyName(name: String, lang: Lang): String =
    (if (lang == Lang.DE) PROPERTY_NAMES_DE else PROPERTY_NAMES_EN)[name] ?: name

/** Human-readable label for a scooter model, keyed by the Xiaomi cloud's own model string (see
 * [com.scooterre.client.protocol.SpecProfiles]) - falls back to a generic name for a device that
 * was only ever connected via a saved key (no fresh cloud login, so the model was never learned)
 * or an unrecognized model string. */
fun modelDisplayName(model: String?, lang: Lang): String = when (model) {
    com.scooterre.client.protocol.SpecProfiles.MODEL_5PRO -> "Scooter 5 Pro"
    com.scooterre.client.protocol.SpecProfiles.MODEL_5MAX -> "Scooter 5 Max"
    else -> strings(lang).genericDeviceName
}

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
 * [com.scooterre.client.protocol.SpecProfiles.SCOOTER_5_PRO]'s `regionSensitiveProperties` for
 * which properties and why; the wording itself lives here so it can be bilingual. */
private val REGION_WARNINGS_DE: Map<String, String> = mapOf(
    "CRUISE_IS_ON" to "Der Tempomat ist nicht in jedem Land offiziell freigeschaltet " +
        "(z.B. in Deutschland nicht). Diese App kann nicht wissen, wo du unterwegs bist oder " +
        "was dort erlaubt ist - das musst du selbst prüfen.",
    "TAIL_LIGHT_IS_ON" to "Ein funktionierendes, eingeschaltetes Rücklicht ist in praktisch " +
        "allen EU-Ländern beim Fahren im Straßenverkehr gesetzlich vorgeschrieben. Schalte es " +
        "nur aus, wenn der Scooter gerade nicht im Verkehr genutzt wird.",
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
fun formatRideLog(raw: String, lang: Lang, units: UnitSystem = UnitSystem.METRIC): String {
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
        val dist = "%.1f".format(locale, units.distance(distTenths / 10.0))
        val avg = "%.1f".format(locale, units.speed(avgTenths / 10.0))
        val top = "%.1f".format(locale, units.speed(topTenths / 10.0))
        val du = units.distanceUnit
        val su = units.speedUnit
        records += if (lang == Lang.DE) "$durText, $dist $du, ø $avg $su, max $top $su"
        else "$durText, $dist $du, avg $avg $su, top $top $su"
    }
    return if (records.isEmpty()) strings(lang).noRidesYet else records.joinToString("; ")
}
