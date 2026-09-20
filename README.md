# Xiaomi Electric Scooter 5 / 5 Pro / 5 Max — Android BLE Client (Mi Home alternative)

**English | [Deutsch](README.de.md)**

**An alternative Android app for the Xiaomi Electric Scooter 5 Pro / 5 Max (the standard 5 should work as well) — dashboard, ride modes, lock, battery and ride data over Bluetooth Low Energy, no Mi Home app needed.**

---

A standalone Android client with public source code (non-commercial license) for **Xiaomi Electric Scooter** 5-series models
(tested: 5 Pro and 5 Max; the standard 5 should work too) that talks to the vehicle directly over Bluetooth Low Energy —
without the official Mi Home app. The complete BLE auth protocol (ECDH P-256 → HKDF-SHA256 →
AES-CCM) was reverse-engineered from scratch and is verified byte-for-byte against both real
devices.

This is an independent, non-commercial project with no affiliation with Xiaomi.

## ⚠️ Disclaimer

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
  2.7.0_0015.x). The standard **Electric Scooter 5** (without "Pro"/"Max") **should work as
  well**, but it has not been tested — for models the app does not recognize it uses the
  5 Pro's property table.
- **No speed limiter is bypassed.** This project deliberately implements **no** function to
  change the regional speed limit or to flash the motor-controller firmware — that would require
  physical ST-Link/SWD access to the motor controller and was intentionally not built, regardless
  of use case. Anyone using this code as a starting point for that does so on their own
  responsibility and outside what this project is intended for.
- **Cloud credentials and terms of service.** The cloud-login part talks to undocumented, internal
  Xiaomi cloud APIs. This may violate your Xiaomi account's terms of service; in the worst case it
  could lead to restrictions on your account. Password and device PIN are never hardcoded anywhere
  and are not stored on your device either — they are only needed for the one-time login / key
  retrieval; only the BLE key obtained with them is stored, encrypted via the Android Keystore —
  even so, use of these APIs is at your own responsibility. The same applies to an exported access
  code and export file (export/import feature): they contain the full BLE key for a device (the
  file also documents and history) and should only be shared with people who are actually allowed
  to use that device — preferably with the file encrypted by a password.
- **Personal documents.** The documents feature stores photos/PDFs of your papers (e.g. insurance
  confirmation) in your phone's app-private storage (not in the gallery, unencrypted); the
  original camera photos do stay in your gallery, though. You are responsible for handling this
  personal data. Whether a digital copy is accepted at a check is up to the checking authority —
  this is not legal advice; carry the originals if in doubt.
- **Backup and app lock.** Without the password a backup cannot be restored — there is no reset. The app
  lock protects against casual access to the app's interface, but is no guarantee against targeted attacks
  on the phone.
- **No support, no promise of continued development.** Xiaomi can
  change the protocol at any time via a firmware update and render this software non-functional,
  with no update to this repository to be expected.

## Features

**Riding and overview**

- **Overview** as the start page after connecting: remaining range and battery level in large
  numbers, riding mode (Walk/Drive/Sport) and energy recovery switchable right there, plus riding
  state, trip, ride time, average/top speed, lock, find-my-scooter and fault status — all on one
  screen.
- **Side menu** with the sections Ride, Battery, Settings, Vehicle, Identification, Ride log,
  History and App settings. All known MIoT properties with correct unit/scaling display and plain
  text instead of raw values; most settings are settable, with a safety/legal note for regionally
  sensitive functions (cruise control, tail light).
- **Live values**: automatic refresh; about every 2.5 s while riding (the ride-relevant values),
  adjustable while parked (economy/normal/fast).
- **Ride log** (the scooter's most recent rides) can be exported as a file; **consumption
  history** (distance and Wh/km per riding mode, experimental): after connecting, the app asks
  which mode you mostly rode in.
- **Tire maintenance**: reminder on/off and interval (14–180 days) settable.
- **Home-screen widget** with the last known status (battery, lock, range).

<p align="center">
  <img src="docs/screenshots/02-overview.png" width="560" alt="Übersicht / Overview"><br>
  <em>Overview — German on the left, English on the right (the app switches language at the tap of a button)</em>
</p>

<p align="center">
  <img src="docs/screenshots/06-side-menu.png" width="250" alt="Seitenmenü / Side menu">
  <img src="docs/screenshots/07-ride-prompt.png" width="400" alt="Fahrt-Zuordnung / Ride prompt"><br>
  <em>Side menu and the riding-mode question after connecting</em>
</p>

**Documents per scooter** (offline, no connection to the scooter needed)

- A "Documents" tile at the top of the device list plus a "📄 N" button on every scooter card.
- **Scan** opens your phone's camera app (choose e.g. its document mode there) and then the photo
  picker; **Photos** takes existing photos; **File** imports a PDF or image. Several selected
  photos become **one** multi-page document, more pages can be added later.
- **Show-it view**: full screen, paging, zoom, screen stays on, full brightness; PDFs page by
  page. Rename and delete with confirmation.

<p align="center">
  <img src="docs/screenshots/03-documents.png" width="560" alt="Dokumente / Documents">
  <img src="docs/screenshots/04-document-viewer.png" width="560" alt="Vorzeige-Ansicht / Viewer"><br>
  <em>Documents per scooter and the show-it view with a sample document (German left, English right)</em>
</p>

**Multiple scooters, export and import**

- Any number of scooters side by side: save, rename, forget (with confirmation) — forgetting also
  removes the scooter's documents and history.
- **Export of everything that belongs to a scooter** (key, name/model, documents, history) in one
  file, optionally encrypted with a password of your choice (AES-256). Import recognizes the file
  by itself and asks for the password if needed. The short text code (key only) remains for the
  quick case.
- **Full backup** of all scooters in **one** encrypted file (keys, documents, history and app settings;
  password required, AES-256) under App settings → Backup. Restoring merges; the app settings can be
  deselected while restoring.
- Sign-in directly in the app: cloud login (password **or** QR code via Chrome Custom Tabs — also
  for accounts without a separate Xiaomi password, e.g. Google sign-in), BLE scan without
  knowing the MAC address, automatic retrieval of the BLE key (`ltmk`).

<p align="center">
  <img src="docs/screenshots/01-device-list.png" width="560" alt="Geräteliste / Device list">
  <img src="docs/screenshots/08-export-dialog.png" width="300" alt="Export-Dialog / Export dialog"><br>
  <em>Device list with the documents tile (German left, English right) and the export dialog (access-code field hidden)</em>
</p>

**App settings** (also reachable without a connected scooter)

- Language (German/English), theme (system/light/dark), automatic brightness via the light sensor
  (experimental), keep screen on, units (metric/imperial), refresh while parked, connect
  automatically to the last used scooter, confirmation before lock/unlock, ride prompt on/off,
  update notice (checks GitHub once a day for a newer version, can be turned off).
- **App lock** (optional, off by default): asks for fingerprint or device PIN when the app starts. It
  only applies at app start — a running app stays unlocked when it goes to the background, and the
  widget is not protected.
- **Insurance plate reminder** (optional, off by default, Germany): notifications one month, one week and on
  the last day before the plate expires at the end of February, with a "New insurance applied for" button
  in the notification (or a checkbox in the documents) to stop them. No connection to the scooter needed.
- The back gesture steps outward: close menu → overview → clean disconnect to the device list →
  leave the app. There is also a "Disconnect" button.

<p align="center">
  <img src="docs/screenshots/05-app-settings.png" width="560" alt="App-Einstellungen / App settings"><br>
  <em>App settings (German left, English right)</em>
</p>

**Under the hood**: persistent BLE session (no reconnect per request) with automatic retry on
transient connection issues.

## Getting started

1. Download the APK from the [Releases](https://github.com/desperado0044/xiaomi-scooter-5pro-ble/releases)
   and install it (sideload, allow "unknown sources"; not a Play Store release). Allow the Bluetooth
   permission. From version 2.2 on the APK is signed with a dedicated release key (certificate SHA-256
   see "Building"). **Switching from 2.1 or older:** those versions were debug-signed, so Android will
   not accept the update over the old app — create a backup once in the app settings, uninstall the
   old app, install the new one and restore the backup. Updates work normally after that.
2. "Add scooter": cloud login (QR code or password); if a sharing PIN is set, enter the device PIN
   — the app fetches the key once. Alternatively import an export file or code.
3. Tap the scooter in the device list — the overview opens.
4. Documents: "Documents" tile → Scan, Photos or File.
5. Second phone (e.g. family): on the first phone "Export", share the file, on the second
   "Add scooter" → "Choose file".
6. New phone: on the old phone create a backup in the app settings (⚙️) under "Backup" (remember the
   password) and move the file to the new phone. Install the app there and, already on the empty
   "Add scooter" start screen, choose ⚙️ → Backup → "Restore" — no cloud login needed.

## License

[PolyForm Noncommercial License 1.0.0](LICENSE) — free to use, share, and modify for **any
non-commercial purpose** (personal, research, teaching, hobby). Commercial use, whether as an
open- or closed-source product, is **not** permitted. This is a deliberate choice: nobody should
make money off this work, not me and not anyone else. Because of this restriction the project is
source-available, but not open source in the sense of the OSI definition.

## Acknowledgments / Foundations

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

## Building

```
cd android-app
./gradlew assembleDebug
```

The APK lands under `android-app/app/build/outputs/apk/debug/`. Requires the Android SDK,
minSdk 26 (Android 8.0); tested on Android 15/16, plus (BLE connectivity check only) Android 9.

Release builds (`./gradlew assembleRelease`) are signed with a key read from
`~/.scooter-signing/keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`);
without that file the release APK stays unsigned, debug builds are unaffected. Certificate SHA-256
of the official releases from 2.2 on:
`BE:42:25:12:15:B5:39:17:90:10:D7:7D:9A:FE:DD:52:AD:F9:43:E6:35:27:AC:0F:79:39:B9:D5:6E:13:BC:9F`
