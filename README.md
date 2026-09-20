# Scooter RE Client — Xiaomi Electric Scooter (Android)

**[Deutsch](#deutsch) | [English](#english)**

**Alternative Android-App für den Xiaomi Electric Scooter 5 Pro / 5 Max — Dashboard, Fahrmodi, Sperre, Akku- und Fahrdaten direkt per Bluetooth, ohne Mi-Home-App.**
*An alternative Android app for the Xiaomi Electric Scooter 5 Pro / 5 Max — dashboard, ride modes, lock, battery and ride data over Bluetooth Low Energy, no Mi Home app needed.*

---

## Deutsch

Ein eigenständiger, quelloffener Android-Client für **Xiaomi Electric Scooter** der 5er-Reihe
(getestet: 5 Pro und 5 Max), der das Fahrzeug direkt per Bluetooth Low Energy anspricht — ohne
die offizielle Mi-Home-App. Das komplette BLE-Auth-Protokoll (ECDH P-256 → HKDF-SHA256 →
AES-CCM) wurde durch eigene Reverse-Engineering-Arbeit nachvollzogen und ist Byte für Byte
gegen beide realen Geräte verifiziert.

Dies ist ein unabhängiges, nicht-kommerzielles Projekt ohne Verbindung zu Xiaomi.

### ⚠️ Haftungsausschluss

**Die Nutzung dieser Software erfolgt vollständig auf eigenes Risiko. Der Autor übernimmt keinerlei
Haftung für Schäden, Verletzungen, Bußgelder, Garantieverlust, Kontosperrungen oder sonstige
Folgen jeder Art, die aus der Nutzung, Installation oder Modifikation dieser Software entstehen
— unabhängig davon, ob diese Folgen vorhersehbar waren oder nicht.** Das gilt in vollem, gesetzlich
zulässigem Umfang (siehe auch [LICENSE](LICENSE), Abschnitt "No Liability", die rechtlich bindende
Fassung dieses Ausschlusses).

- **Keine Verbindung zu und keine Unterstützung durch Xiaomi.** Dieses Projekt steht in keiner
  Beziehung zu Xiaomi Corporation oder verbundenen Unternehmen, ist nicht von Xiaomi autorisiert,
  gesponsert oder geprüft. Alle Marken- und Produktnamen gehören ihren jeweiligen Inhabern.
- **Keine Gewährleistung, keine Garantie für Funktion, Sicherheit oder Korrektheit.** Die Software
  wird **"as is"**, ohne jede ausdrückliche oder stillschweigende Zusicherung bereitgestellt —
  weder für Eignung zu einem bestimmten Zweck noch für Fehlerfreiheit. Ein durch Reverse
  Engineering nachgebautes Protokoll kann Fehlinterpretationen enthalten; fehlerhafte Befehle
  könnten im ungünstigsten Fall dein Gerät beschädigen, Fahrfunktionen beeinträchtigen oder zu
  unerwartetem Verhalten während der Fahrt führen.
- **Verantwortung für Straßenverkehr und Sicherheit liegt vollständig bei dir.** Die App zeigt bei
  bestimmten Einstellungen (z.B. Tempomat, Beleuchtung) Warnhinweise an — das ersetzt keine eigene
  Prüfung der in deinem Land/deiner Region geltenden Vorschriften für Elektrokleinstfahrzeuge.
  Nutze keine Funktion während der Fahrt in einer Weise, die dich selbst oder andere gefährdet
  oder gegen geltendes Recht verstößt.
- **Nur für eigene Geräte verwenden.** Die Software ist für Interoperabilität mit Geräten gedacht,
  die dir (oder Personen, die dir ausdrücklich Zugriff gewähren, z.B. über die
  Export/Import-Funktion) gehören — nicht für fremde Geräte ohne Zustimmung des Eigentümers.
- **Getestet am Xiaomi Electric Scooter 5 Pro und 5 Max** (BLE-Chip RTL8762C, Firmware
  2.7.0_0015.x). Ob weitere Modelle der 5er-Reihe (z.B. "Electric Scooter 5" ohne "Pro"/"Max")
  dasselbe Protokoll sprechen, ist **nicht verifiziert** — die App verwendet in diesem Fall die
  5-Pro-Tabelle als ungeprüften Startpunkt.
- **Keine Geschwindigkeitsbegrenzung wird umgangen.** Dieses Projekt implementiert bewusst
  **keine** Funktion zum Ändern der regionalen Geschwindigkeitsbegrenzung oder zum Beschreiben
  der Motorsteuerungs-Firmware — das würde physischen ST-Link/SWD-Zugriff auf den Motorcontroller
  erfordern und wurde absichtlich nicht gebaut, unabhängig vom Anwendungsfall. Wer diesen Code
  als Ausgangspunkt für sowas nimmt, tut das eigenverantwortlich und außerhalb dessen, wofür
  dieses Projekt gedacht ist.
- **Cloud-Zugangsdaten und Nutzungsbedingungen.** Der Cloud-Login-Teil spricht undokumentierte,
  interne Xiaomi-Cloud-Schnittstellen an. Das kann gegen die Nutzungsbedingungen deines
  Xiaomi-Kontos verstoßen; im ungünstigsten Fall könnte das zu Einschränkungen deines Kontos
  führen. Dein Passwort/PIN wird nirgendwo fest im Code gespeichert — Eingabe erfolgt in der App
  und wird nur verschlüsselt über den Android Keystore lokal auf deinem Gerät abgelegt — trotzdem
  gilt: Nutzung dieser Schnittstellen ist eigenverantwortlich. Dasselbe gilt für den exportierten
  Zugangscode (Export/Import-Funktion): er enthält den vollständigen BLE-Schlüssel eines Geräts
  und sollte nur an Personen weitergegeben werden, die dieses Gerät auch tatsächlich mitbenutzen
  dürfen.
- **Kein Support, keine Zusicherung von Weiterentwicklung.**
  Xiaomi kann das Protokoll jederzeit per Firmware-Update ändern und diese Software funktionsunfähig
  machen, ohne dass eine Aktualisierung dieses Repositories zu erwarten ist.

### Funktionsumfang

- **Mehrere Scooter parallel verwalten**: Geräteauswahl-Bildschirm statt eines einzelnen
  austauschbaren Slots — beliebig viele Geräte speichern, umbenennen und (mit
  Bestätigungsdialog) wieder vergessen.
- **Zugang exportieren/importieren**: ein bereits eingerichteter Scooter lässt sich als Code
  oder Datei exportieren, damit eine zweite Person (z.B. Familienmitglied) ihn auf ihrem eigenen
  Handy direkt einrichten kann, ohne selbst Cloud-Login/PIN durchlaufen zu müssen.
- Cloud-Login direkt in der App (Passwort **oder** QR-Code über Chrome Custom Tabs — Letzteres
  auch für Konten ohne eigenes Xiaomi-Passwort, z.B. bei Google-Anmeldung)
- BLE-Scan zum Auffinden des eigenen Rollers, ohne die MAC-Adresse vorher zu kennen
- Automatischer Bezug des BLE-Sicherheitsschlüssels (`ltmk`) über die Xiaomi-Cloud-API
- Persistente BLE-Sitzung (keine Neuverbindung bei jeder Abfrage), mit automatischem
  Wiederholungsversuch bei kurzzeitigen Verbindungsproblemen
- Vollständige Oberfläche auf Deutsch und Englisch umschaltbar (merkt sich die Wahl)
- Alle bekannten MIoT-Properties in sechs Tabs, mit korrekter Einheiten-/Skalierungsanzeige und
  Klartext statt Rohwerten wo möglich:
  - **Fahrt**: Fahrmodus (einstellbar), Durchschnitts-/Höchstgeschwindigkeit, Gesamt-/Fahrstrecke,
    Fahrzustand, Fehlerstatus, Restreichweite
  - **Akku**: Akkustand, Spannung/Strom/Leistung, Akkustatus/-temperatur, Ladezyklen, Akkugesundheit
  - **Einstellungen**: Sperre, Tempomat, Rücklicht, Rekuperationsstärke, ASR/TCS, Berg-Features,
    Ambientelicht, Bluetooth-Suche u.a. (die meisten davon einstellbar, jeweils mit
    Sicherheits-/Rechtshinweis bei regional heiklen Funktionen wie Tempomat und Rücklicht)
  - **Fahrzeug**: Scootertemperatur, Schloss-Warnsignal, Reifen-Wartungserinnerung, Ruhezustand,
    sowie ein "Scooter suchen"-Button (löst einen echten Piepton/Blinken am Gerät aus)
  - **Identifikation**: Seriennummern, Firmware-Versionen, Produktions-/Aktivierungsdatum
  - **Fahrtenbuch**: die letzten aufgezeichneten Fahrten des Rollers (Dauer, Distanz,
    Durchschnitts-/Höchstgeschwindigkeit pro Fahrt)
- Ein "Trennen"-Button für einen sauberen Verbindungsabbau statt nur App-Beenden

### Lizenz

[PolyForm Noncommercial License 1.0.0](LICENSE) — freie Nutzung, Weitergabe und Veränderung für
**jeden nicht-kommerziellen Zweck** (privat, Forschung, Lehre, Hobby). Kommerzielle Nutzung,
egal ob als Open- oder Closed-Source-Produkt, ist **nicht** gestattet. Das ist bewusst so
gewählt: niemand soll mit dieser Arbeit Geld verdienen, weder ich noch sonst wer.

### Danksagung / Grundlagen

Ohne folgende Vorarbeiten anderer wäre dieses Projekt nicht möglich gewesen:

- [KuziaMother/SCOOTER_5_PRO](https://github.com/KuziaMother/SCOOTER_5_PRO) — die entscheidende
  Referenz für das BLE-Auth-Protokoll dieses Geräts (ECDH/HKDF/AES-CCM-Ablauf, Kanaltransport,
  MIoT-Property-Layer). Dessen Code wird hier **nicht mitgeliefert**, nur eigenständig in Kotlin
  neu implementiert und gegen die realen Geräte verifiziert.
- [PiotrMachowski/Xiaomi-cloud-tokens-extractor](https://github.com/PiotrMachowski/Xiaomi-cloud-tokens-extractor) —
  Vorlage für den Mi-Cloud-Login-Ablauf (RC4-Verschlüsselung, Signatur, `askbluetoothkey`), hier
  eigenständig nach Kotlin portiert.

Der vollständige, chronologische Reverse-Engineering-Prozess (inkl. Sackgassen, verworfener
Hypothesen und aller Zwischenschritte) ist dokumentiert in [`docs/RESEARCH_LOG.md`](docs/RESEARCH_LOG.md).

### Bauen

```
cd android-app
./gradlew assembleDebug
```

APK liegt danach unter `android-app/app/build/outputs/apk/debug/`. Benötigt: Android SDK,
minSdk 26 (Android 8.0), getestet auf Android 15/16 sowie (nur BLE-Verbindungsprüfung) Android 9.

---

## English

A standalone, open-source Android client for **Xiaomi Electric Scooter** 5-series models
(tested: 5 Pro and 5 Max) that talks to the vehicle directly over Bluetooth Low Energy —
without the official Mi Home app. The complete BLE auth protocol (ECDH P-256 → HKDF-SHA256 →
AES-CCM) was reverse-engineered from scratch and is verified byte-for-byte against both real
devices.

This is an independent, non-commercial project with no affiliation with Xiaomi.

### ⚠️ Disclaimer

**Use of this software is entirely at your own risk. The author accepts no liability whatsoever
for damage, injury, fines, warranty loss, account suspension, or any other consequence of any
kind arising from the use, installation, or modification of this software — regardless of
whether such consequences were foreseeable.** This applies to the fullest extent permitted by
law (see also [LICENSE](LICENSE), "No Liability" section, the legally binding version of this
disclaimer).

- **No affiliation with, and no support from, Xiaomi.** This project has no relationship with
  Xiaomi Corporation or its affiliates, and is not authorized, sponsored, or reviewed by Xiaomi.
  All trademarks and product names belong to their respective owners.
- **No warranty, no guarantee of function, safety, or correctness.** The software is provided
  **"as is"**, without any express or implied warranty — neither fitness for a particular purpose
  nor freedom from defects. A protocol reconstructed through reverse engineering may contain
  misinterpretations; incorrect commands could, in the worst case, damage your device, impair
  ride functions, or cause unexpected behavior while riding.
- **Responsibility for road safety and traffic law is entirely yours.** The app shows warnings
  for certain settings (e.g. cruise control, tail light) — this does not replace your own review
  of the rules that apply to light electric vehicles in your country/region. Do not use any
  function while riding in a way that endangers yourself or others, or that violates applicable
  law.
- **For use with your own devices only.** This software is intended for interoperability with
  devices that belong to you (or to someone who has explicitly granted you access, e.g. via the
  export/import feature) — not for someone else's device without the owner's consent.
- **Tested on the Xiaomi Electric Scooter 5 Pro and 5 Max** (BLE chip RTL8762C, firmware
  2.7.0_0015.x). Whether other 5-series models (e.g. "Electric Scooter 5" without "Pro"/"Max")
  speak the same protocol is **not verified** — the app falls back to the 5 Pro's property table
  as an unverified starting point in that case.
- **No speed limiter is bypassed.** This project deliberately implements **no** function to
  change the regional speed limit or to flash the motor-controller firmware — that would require
  physical ST-Link/SWD access to the motor controller and was intentionally not built, regardless
  of use case. Anyone using this code as a starting point for that does so on their own
  responsibility and outside what this project is intended for.
- **Cloud credentials and terms of service.** The cloud-login part talks to undocumented, internal
  Xiaomi cloud APIs. This may violate your Xiaomi account's terms of service; in the worst case it
  could lead to restrictions on your account. Your password/PIN is never hardcoded anywhere — it's
  entered in the app and stored only encrypted, locally on your device via the Android Keystore —
  even so, use of these APIs is at your own responsibility. The same applies to an exported access
  code (export/import feature): it contains the full BLE key for a device and should only be
  shared with people who are actually allowed to use that device.
- **No support, no promise of continued development.** Xiaomi can
  change the protocol at any time via a firmware update and render this software non-functional,
  with no update to this repository to be expected.

### Features

- **Manage multiple scooters at once**: a device picker screen instead of a single swappable
  slot — save, rename, and (with a confirmation dialog) forget any number of devices.
- **Export/import access**: an already set-up scooter can be exported as a code or a file so a
  second person (e.g. a family member) can set it up directly on their own phone, without going
  through a cloud login/PIN of their own.
- Cloud login directly in the app (password **or** QR code via Chrome Custom Tabs — the latter
  also works for accounts with no separate Xiaomi password, e.g. Google sign-in)
- BLE scan to find your own scooter without knowing its MAC address beforehand
- Automatic retrieval of the BLE security key (`ltmk`) via the Xiaomi cloud API
- Persistent BLE session (no reconnect on every request), with automatic retry on transient
  connection issues
- Fully switchable German/English UI (remembers your choice)
- All known MIoT properties across six tabs, with correct unit/scaling display and plain text
  instead of raw values where possible:
  - **Ride**: riding mode (settable), average/top speed, total/trip distance, riding state,
    fault status, remaining range
  - **Battery**: battery level, voltage/current/power, battery status/temperature, charge
    cycles, battery health
  - **Settings**: lock, cruise control, tail light, energy recovery strength, ASR/TCS, hill
    features, ambient light, Bluetooth search, and more (most of these settable, each with a
    safety/legal note for regionally sensitive functions like cruise control and tail light)
  - **Vehicle**: scooter temperature, lock warning signal, tire maintenance reminder, sleep
    state, plus a "find my scooter" button (triggers a real beep/flash on the device)
  - **Identification**: serial numbers, firmware versions, production/activation date
  - **Ride log**: the scooter's most recently recorded rides (duration, distance,
    average/top speed per ride)
- A "Disconnect" button for a clean connection teardown instead of just closing the app

### License

[PolyForm Noncommercial License 1.0.0](LICENSE) — free to use, share, and modify for **any
non-commercial purpose** (personal, research, teaching, hobby). Commercial use, whether as an
open- or closed-source product, is **not** permitted. This is a deliberate choice: nobody should
make money off this work, not me and not anyone else.

### Acknowledgments / Foundations

This project would not have been possible without the following prior work by others:

- [KuziaMother/SCOOTER_5_PRO](https://github.com/KuziaMother/SCOOTER_5_PRO) — the decisive
  reference for this device's BLE auth protocol (ECDH/HKDF/AES-CCM flow, channel transport, MIoT
  property layer). Its code is **not bundled** here, only independently reimplemented in Kotlin
  and verified against the real devices.
- [PiotrMachowski/Xiaomi-cloud-tokens-extractor](https://github.com/PiotrMachowski/Xiaomi-cloud-tokens-extractor) —
  template for the Mi Cloud login flow (RC4 encryption, signature, `askbluetoothkey`),
  independently ported to Kotlin here.

The full, chronological reverse-engineering process (including dead ends, discarded hypotheses,
and every intermediate step) is documented in [`docs/RESEARCH_LOG.md`](docs/RESEARCH_LOG.md).

### Building

```
cd android-app
./gradlew assembleDebug
```

The APK lands under `android-app/app/build/outputs/apk/debug/`. Requires the Android SDK,
minSdk 26 (Android 8.0); tested on Android 15/16, plus (BLE connectivity check only) Android 9.
