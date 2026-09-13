# Scooter RE Client — Xiaomi Electric Scooter 5 Pro (Android)

Ein eigenständiger, quelloffener Android-Client für den **Xiaomi Electric Scooter 5 Pro**, der
das Fahrzeug direkt per Bluetooth Low Energy anspricht — ohne die offizielle Mi-Home-App. Das
komplette BLE-Auth-Protokoll (ECDH P-256 → HKDF-SHA256 → AES-CCM) wurde durch eigene
Reverse-Engineering-Arbeit nachvollzogen und ist Byte für Byte gegen das reale Gerät verifiziert.

Dies ist ein privates Hobbyprojekt, entstanden aus Neugier am eigenen Gerät — kein kommerzielles
Produkt, keine Verbindung zu Xiaomi.

## ⚠️ Haftungsausschluss

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
- **Nur für das eigene Gerät verwenden.** Die Software ist für Interoperabilität mit einem Gerät
  gedacht, das dir gehört — nicht für fremde Geräte ohne Zustimmung des Eigentümers.
- **Getestet ausschließlich am Xiaomi Electric Scooter 5 Pro** (BLE-Chip RTL8762C,
  Firmware 2.7.0_0015). Ob andere Modelle der 5er-Reihe (z.B. "Electric Scooter 5" ohne "Pro")
  dasselbe Protokoll sprechen, ist **nicht verifiziert**.
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
  gilt: Nutzung dieser Schnittstellen ist eigenverantwortlich.
- **Kein Support, keine Zusicherung von Weiterentwicklung.** Dies ist ein privates Hobbyprojekt.
  Xiaomi kann das Protokoll jederzeit per Firmware-Update ändern und diese Software funktionsunfähig
  machen, ohne dass eine Aktualisierung dieses Repositories zu erwarten ist.

## Funktionsumfang

- Cloud-Login direkt in der App (Passwort **oder** QR-Code — Letzteres auch für Konten ohne
  eigenes Mi-Passwort, z.B. bei Google/Apple-Anmeldung)
- BLE-Scan zum Auffinden des eigenen Rollers, ohne die MAC-Adresse vorher zu kennen
- Automatischer Bezug des BLE-Sicherheitsschlüssels (`ltmk`) über die Xiaomi-Cloud-API
- Persistente BLE-Sitzung (keine Neuverbindung bei jeder Abfrage), mit automatischem
  Wiederholungsversuch bei kurzzeitigen Verbindungsproblemen
- Vollständige Oberfläche auf Deutsch und Englisch umschaltbar (merkt sich die Wahl)
- Alle bekannten MIoT-Properties in fünf Tabs, mit korrekter Einheiten-/Skalierungsanzeige und
  Klartext statt Rohwerten wo möglich:
  - **Fahrt & Akku**: Fahrmodus (einstellbar), Akkustand, Spannung/Strom/Leistung, Restreichweite,
    Fehlerstatus, Fahrstrecke
  - **Einstellungen**: Sperre, Tempomat, Rücklicht, Rekuperationsstärke, ASR/TCS, Berg-Features,
    Ambientelicht, Bluetooth-Suche u.a. (die meisten davon einstellbar, jeweils mit
    Sicherheits-/Rechtshinweis bei regional heiklen Funktionen wie Tempomat und Rücklicht)
  - **Akku-Detail**: Akkustatus, Temperaturen, Reifen-Wartungserinnerung, Ladezyklen, Akkugesundheit
  - **Identifikation**: Seriennummern, Firmware-Versionen, Produktionsdatum, detaillierte
    Akku-Kennwerte (abgegebene Energie, Kapazität, Tiefentladungen)
  - **Fahrtenbuch**: die letzten aufgezeichneten Fahrten des Rollers (Dauer, Distanz,
    Durchschnitts-/Höchstgeschwindigkeit pro Fahrt)
- Ein "Trennen"-Button für einen sauberen Verbindungsabbau statt nur App-Beenden

## Lizenz

[PolyForm Noncommercial License 1.0.0](LICENSE) — freie Nutzung, Weitergabe und Veränderung für
**jeden nicht-kommerziellen Zweck** (privat, Forschung, Lehre, Hobby). Kommerzielle Nutzung,
egal ob als Open- oder Closed-Source-Produkt, ist **nicht** gestattet. Das ist bewusst so
gewählt: niemand soll mit dieser Arbeit Geld verdienen, weder ich noch sonst wer.

## Danksagung / Grundlagen

Ohne folgende Vorarbeiten anderer wäre dieses Projekt nicht möglich gewesen:

- [KuziaMother/SCOOTER_5_PRO](https://github.com/KuziaMother/SCOOTER_5_PRO) — die entscheidende
  Referenz für das BLE-Auth-Protokoll dieses Geräts (ECDH/HKDF/AES-CCM-Ablauf, Kanaltransport,
  MIoT-Property-Layer). Dessen Code wird hier **nicht mitgeliefert**, nur eigenständig in Kotlin
  neu implementiert und gegen das reale Gerät verifiziert.
- [PiotrMachowski/Xiaomi-cloud-tokens-extractor](https://github.com/PiotrMachowski/Xiaomi-cloud-tokens-extractor) —
  Vorlage für den Mi-Cloud-Login-Ablauf (RC4-Verschlüsselung, Signatur, `askbluetoothkey`), hier
  eigenständig nach Kotlin portiert.

Der vollständige, chronologische Reverse-Engineering-Prozess (inkl. Sackgassen, verworfener
Hypothesen und aller Zwischenschritte) ist dokumentiert in [`docs/RESEARCH_LOG.md`](docs/RESEARCH_LOG.md).

## Bauen

```
cd android-app
./gradlew assembleDebug
```

APK liegt danach unter `android-app/app/build/outputs/apk/debug/`. Benötigt: Android SDK,
minSdk 26 (Android 8.0), getestet auf Android 15/16 sowie (nur BLE-Verbindungsprüfung) Android 9.
