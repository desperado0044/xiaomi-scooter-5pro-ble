# Xiaomi Electric Scooter 5 Pro – BLE Reverse Engineering

Ziel: eigenständige Auth-Implementierung, Telemetrie lesen, Steuerung — unabhängig von
Xiaomi Home / ScooterHacking Utility. Zielplattform für die eigene Umsetzung: **Android App**.

## Hardware / Zielgerät

- BLE-Chip: RTL8762C
- MAC: `AA:BB:CC:DD:EE:FF`
- FW: `2.7.0_0015`

### GATT Service `0000fe95-0000-1000-8000-00805f9b34fb`

| Handle | Eigenschaften  | Zweck |
|--------|----------------|-------|
| 0x0004 | READ           | FW-Version |
| 0x0016 | NOTIFY / WNR   | Auth-Handshake |
| 0x0017 | NOTIFY / W     | Command-Kanal |
| 0x001B | NOTIFY / WNR   | Daten, chunked |
| 0x001C | NOTIFY / WNR   | Geräteinfo |

Weitere im ScooterHacking-Code referenzierte Service/Charakteristik-UUIDs (App unterstützt
mehrere Scooter-Familien/Transportarten):
- `00000010-0000-1000-8000-00805f9b34fb`
- `00000019-0000-1000-8000-00805f9b34fb`
- Nordic UART: `6e400003-b5a3-f393-e0a9-e50e24dcca9e`

## Bisheriges Vorgehen (chronologisch)

1. **BLE-HCI-Snoop-Log-Ansatz** (nRF Connect + Android Bluetooth-HCI-Snoop-Log, Auszug per
   adb): auf Android 16 ohne Root nicht direkt zugänglich (Scoped Storage,
   `/data/misc/bluetooth/logs/` braucht Root). Wäre nur über `adb bugreport` gegangen — nicht
   weiterverfolgt, da Alternative gefunden (siehe unten).
2. **MITM-Proxy (mitmdump) auf die Xiaomi Home App**, um Cloud-Token/Beaconkey abzugreifen:
   - CA-Zertifikat auf dem Handy nur als **User-CA** installiert (kein Root).
   - `network_security_config.xml` der Xiaomi-Home-App vertraut nur `src="system"` →
     **alle TLS-Verbindungen der App schlagen fehl**, MITM funktioniert so nicht.
   - **Wichtiger Nebenbefund:** Xiaomi Home App und ScooterHacking Utility sind beide
     **Flutter-Apps** — die eigentliche Business-Logik steckt kompiliert in `libapp.so`
     (Dart-AOT-Snapshot), nicht im normal decompilierbaren Java/Kotlin-Code.
3. **Drittanbieter-App "ScooterHacking Utility"** (`scooterhacking.apk`, von scooterhack.in)
   gefunden — hat das Protokoll für diesen Scooter vermutlich bereits implementiert.
   → Fokus verschoben von "Cloud-Token extrahieren" auf "Protokoll aus dieser App extrahieren".
4. **jadx-Decompile**: `base.apk` (Xiaomi Home) → `jadx-out-mihome` (OOM-Crash bei jadx-cli,
   unvollständig, `hs_err_pid25016.log`); `scooterhacking.apk` → `jadx-out-sh` (vollständig,
   aber nur Java/Kotlin-Glue-Code, keine Business-Logik wegen Flutter).
5. **Blutter** (Dart-AOT-Snapshot-RE-Tool) gegen `libapp.so` der ScooterHacking-App:
   - `pp.txt`/`objs.txt` (String-/Objekt-Pool) liefern sehr ergiebige Klartext-Strings
     (siehe Protokoll-Erkenntnisse unten).
   - `asm/*.dart` (634 Dateien) enthalten dagegen **nur Klassen-/Methodenskelette ohne echte
     Instruktionen** — alle relevanten Methoden sind anonyme Closures, für die Blutter keine
     Code-Größe/Disassembly auflösen kann (bekannte Blutter-Einschränkung, kein Bug in unserem
     Setup — verifiziert durch Neulauf mit korrigierter Python-Umgebung, identisches Ergebnis).
   - Ohne IDA Pro ist über diesen Weg an echte Byte-Layouts/Krypto-Parameter nicht
     heranzukommen → **statischer Weg ausgereizt**.
6. **Pivot auf dynamische Instrumentierung (Frida)**:
   - `frida` (Python-Bindings, v17.16.4), `frida-tools` (14.10.4) und `objection` (1.12.5)
     installiert.
   - Blutter hat bereits ein fertiges Hook-Skript mit aufgelösten Adressen erzeugt:
     `blutter-out/blutter_frida.js`.
   - Handy hat **keinen Root** → kein `frida-server`, stattdessen **Frida-Gadget in die APK
     patchen** (kein Root nötig).
   - `objection patchapk -N` patcht **gleichzeitig** `network_security_config.xml` auf
     User-CA-Vertrauen — löst nebenbei auch Problem #2 (MITM-Zertifikat), falls das später
     doch noch gebraucht wird.
   - Fehlende Tools unterwegs gefunden/ergänzt: `aapt.exe`/`aapt2.exe` lagen bereits unter
     `<Nutzerverzeichnis>\AppData\Local\Datastream\FireToolbox\adb\` (Fire-Toolbox-Installation,
     zweckentfremdet). `apksigner` war nirgends vorhanden → Plan: `objection patchapk -C`
     (Signing überspringen) + manuell mit `jarsigner` (aus dem JDK) + selbst erzeugtem
     Debug-Keystore signieren (v1/JAR-Signing reicht zum Sideloaden).

### Wichtiger Zwischenfall

Ein **systemweiter (adb-gesetzter globaler) HTTP-Proxy** (`192.168.x.x:8080`, Rest dieses
PCs `mitmdump`) war aus einer früheren Session aktiv stehen geblieben. Anders als ein
WLAN-Proxy gilt das für **alle** Netzwerktypen inkl. Mobilfunk → Handy hatte komplett kein
Internet mehr (WLAN *und* LTE). Behoben per:

```
adb shell settings put global http_proxy :0
adb shell settings delete global global_http_proxy_host
adb shell settings delete global global_http_proxy_port
adb shell settings delete global global_http_proxy_exclusion_list
```

**Lektion:** globalen Proxy nur gezielt kurz vor einer Capture-Session setzen und danach
sofort wieder zurücksetzen, nicht dauerhaft stehen lassen.

## Protokoll-Erkenntnisse (aus Blutter String-/Objekt-Pool der ScooterHacking-App)

- Der Pairing-Ablauf heißt intern **„Elliptic"** (vermutlich ECDH-basiert, passt zu neueren
  Xiaomi-Scootern ab Modelljahr 2023+, wozu der 5 Pro gehört).
- Handshake-Opcodes:
  - `0x5B` — Scooter sendet seinen Zufallswert ("BLE random"). Timeout nach **10 Versuchen**.
  - `0x5C` — App schlägt eigenen Zufallswert vor ("app-random proposal"), Scooter bestätigt.
  - `0x5D` — Scooter akzeptiert final. Timeout nach **5 Versuchen**.
- **Pairing-Bestätigung läuft über physischen Tastendruck** ("Toggle the headlight by
  pressing the power button, then retry") — **rein lokale Presence-Bestätigung, keine
  Cloud-Token-Abhängigkeit!** Das bedeutet: der MITM/Cloud-Token-Weg (Schritt 2) ist für die
  eigentliche BLE-Auth wahrscheinlich gar nicht nötig.
- Danach läuft ein verschlüsselter Kanal ("Elliptic encrypt/decrypt"):
  - Frames haben einen laufenden Nachrichtenzähler `msgIt` zur Sync-/Drift-Erkennung
    ("msgIt drift on decrypt: trailer=... expected=... local_before=...").
  - Wire-Checksum wird geprüft ("Dropped B frame with invalid wire checksum").
  - Command-Dispatch adressiert über `reqCmd`/`rspCmd` (Hex) mit Retry-Logik
    ("Retry ... for reqCmd=0x...→rspCmd=0x...").
- Eigene Klasse **„NinebotCrypto"** vorhanden (Xiaomi-Scooter werden mit Segway-Ninebot
  co-entwickelt) — vermutlich die Basis-Frame-Codierung unter/neben der Elliptic-Verschlüsselung,
  oder für andere von der App unterstützte Modelle.
- Generische Krypto-Bibliothek mit **`AES_CBC_PKCS7Padding`** vorhanden — vermutlich die
  tatsächliche Verschlüsselung des Elliptic-Kanals nach Schlüsseleinigung.

## Arbeitsverzeichnis auf diesem PC

Alles bisherige liegt unter `C:\tmp\scooter-re\` (temporär, nicht Teil dieses Projektordners):

```
apk/                    base.apk (Xiaomi Home), scooterhacking.apk, split_arm64_v8a.apk
jadx-out-mihome/         unvollständig (jadx OOM-Crash, hs_err_pid25016.log)
jadx-out-sh/             vollständig, aber nur Java/Kotlin-Glue (Flutter → wenig Nutzen)
flutter-libs/            libapp.so, libflutter.so (aus scooterhacking.apk)
blutter/                 Blutter-Tool (Git-Checkout + gebaute exe)
blutter-out/, blutter-out2/   Blutter-Analyse: asm/*.dart (Skelette, siehe oben),
                         objs.txt, pp.txt (String-/Objektpool, sehr nützlich),
                         blutter_frida.js (fertiges Hook-Skript mit Adressen)
mitm-capture.flow, mitm.log, mitmproxy-ca-cert.cer   MITM-Versuch (großteils TLS-Fehler)
patch/                  objection patchapk Arbeitsverzeichnis, debug.jks (Signing-Keystore,
                         storepass/keypass "android123", alias "debugkey")
```

### Tool-Stolperfallen (für nächstes Mal)

- Das `python`/`pip`, das in der Bash-Standard-PATH zuerst gefunden wird, ist ein
  **hermes-agent-venv** ohne die benötigten Pakete. Immer explizit
  `<Nutzerverzeichnis>\AppData\Local\Programs\Python\Python312\python.exe` verwenden — dort sind
  `capstone`, `elftools`, `mitmproxy`, `frida`, `frida-tools`, `objection` installiert.
- `aapt`/`aapt2` liegen unter `<Nutzerverzeichnis>\AppData\Local\Datastream\FireToolbox\adb\` —
  für `objection patchapk` in PATH aufnehmen.
- `apksigner` ist auf diesem System **nicht** vorhanden — Workaround: `jarsigner`
  (`C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\`) + `patch/debug.jks`.
- Git-Bash wandelt `/sdcard/...`-Pfade fälschlich in Windows-Pfade um — für `adb shell ls ...`
  mit Unix-Pfaden `export MSYS_NO_PATHCONV=1` setzen. Für native Windows-`.exe`-Aufrufe
  (z. B. `keytool.exe`) darf `MSYS_NO_PATHCONV` dagegen **nicht** gesetzt sein, sonst bekommen
  die Windows-Programme kaputte `/c/...`-Pfade statt `C:/...`.

## Nächste Schritte

1. Patch-Lauf abschließen: `objection patchapk -s apk/scooterhacking.apk -a arm64-v8a -N -C`
   (aus `C:\tmp\scooter-re\patch`, FireToolbox-`adb`-Ordner in PATH) — Ausgabedatei
   verifizieren/lokalisieren.
2. Mit `jarsigner` + `patch/debug.jks` signieren.
3. Original-App auf dem Handy deinstallieren (Pairing-/Einstellungsdaten gehen verloren),
   gepatchte+signierte APK installieren.
4. App starten, mit `frida`/`blutter_frida.js` an den laufenden Prozess (Gadget) hängen,
   **echtes Pairing mit dem physischen Scooter durchführen** (Tastendruck-Bestätigung nötig)
   und dabei die Hooks in den `hSa`/`TQa`/`NinebotCrypto`-nahen Funktionen mitloggen, um die
   echten Bytes von `0x5B`/`0x5C`/`0x5D` sowie Schlüsselmaterial/AES-Parameter zu erfassen.
5. Aus den geloggten Rohdaten das Byte-Layout, den ECDH-/Schlüsselableitungsmechanismus,
   die AES-CBC-PKCS7-Verschlüsselung sowie `msgIt`-Zähler und Checksumme rekonstruieren.
6. Eigenen Android-BLE-Client (Kotlin) mit Auth + Telemetrie + Steuerung bauen, unabhängig
   von der ScooterHacking-App.
7. Prüfen, ob „NinebotCrypto" tatsächlich als Basis-Framing unter Elliptic verwendet wird
   oder nur für andere Modelle in derselben App zuständig ist.

Optional/nicht mehr Priorität: MITM-Cloud-Token-Weg (Schritt 2) weiterverfolgen, falls sich
herausstellt, dass doch ein Cloud-Token gebraucht wird (bisher sieht die Auth aber rein lokal
aus).

## Update 2026-09-13: Frida-Weg an bekanntem Upstream-Bug gescheitert, Umstieg auf Ghidra

### Frida/Gadget-Versuch — im Detail gescheitert, aber lehrreich

1. `objection patchapk -N` gepatcht (aapt/apksigner/zipalign/apktool fehlten komplett auf dem
   System → über offizielle Android-`cmdline-tools` + `build-tools;35.0.0` (SHA-256 verifiziert)
   nachinstalliert unter `C:\Android\sdk`, apktool.jar von apktool.org unter `C:\Android\apktool`).
2. App zeigte nach Install eigene **Anti-Tamper-Erkennung**: „App cannot run on this device —
   reason: signature mismatch" (App prüft ihre eigene Signatur zur Laufzeit gegen die Original-
   ScooterHacking-Signatur, erkennt das Debug-Resigning).
3. Gegenmaßnahme entwickelt: Original-Zertifikat (PEM) aus der unveränderten `scooterhacking.apk`
   per `apksigner verify --print-certs-pem` extrahiert, Frida-Java-Hook geschrieben, der
   `SigningInfo.getApkContentsSigners()`/`getSigningCertificateHistory()` und
   `PackageManager.getPackageInfo(...).signatures` auf das Original-Zertifikat umbiegt
   (`hooks/hsa_hooks.js` im Projekt, Java.perform-Block oben im File).
4. **Kernproblem, das den ganzen Ansatz blockiert hat:** `script.load()` hängt bei dieser
   Kombination (Frida-Gadget 17.18.0 + Android 16) unabhängig vom Skriptinhalt (auch ein
   triviales Skript ohne jeden Java-Code hängt) — durch kontrollierten Test verifiziert, nicht nur
   vermutet. Recherche bestätigt: **bekannter, offener Upstream-Bug** in Frida 17.x
   (`on_load: wait`-Modus lädt Skripte nicht mehr zuverlässig seit einer ART-Änderung Ende 2024),
   zusätzlich eigene Android-16-spezifische Frida-Probleme. Kein Fix durch uns möglich; auch ein
   Downgrade auf Frida 16.x hätte andere dokumentierte Probleme (kaputtes Java-Hooking ab
   Android 15). Quellen: frida/frida#3572, #3526, #3700, #3661.
5. **Entscheidung:** dynamischen Weg für dieses Gerät/diese Android-Version vorerst aufgegeben,
   auf statische Analyse mit Ghidra umgestiegen (kein weiteres Risiko fürs Handy, kein Reinstall
   mehr nötig).

### Ghidra-Analyse (statisch, offline) — erste Ergebnisse

- Ghidra 12.1.2 war bereits unter `C:\Tools\ghidra_12.1.2_PUBLIC` installiert.
- **Wichtig:** Ghidra 12 hat Jython entfernt — `.py`-GhidraScripts laufen nicht mehr ohne
  PyGhidra-Setup. Skripte stattdessen als **Java-GhidraScript** geschrieben
  (`ghidra/GhidraDumpHooks.java` im Projekt).
- `libapp.so` erfolgreich importiert + autoanalysiert (Image-Base `0x100000`, `.text`
  `0x320000–0x9eef1f` — Blutters Adressen entsprechen direkt den Ghidra-Adressen, keine
  Umrechnung nötig).
- Die von Blutter gefundenen hSa-Closure-Adressen waren nicht automatisch als Funktionen
  erkannt (typisch für Dart-AOT-Closures), ließen sich aber mit `clearListing()` +
  `disassemble()` erzwingen — **echte, gültige ARM64-Instruktionen vorhanden**, kein
  Fehlalarm. Vollständiger Dump: `ghidra/hSa_closures_disasm.txt`.
- Erste Interpretation (Dart-AOT-ABI: `x28`=Heap-Basis für komprimierte Pointer, `x26`=Thread,
  `x27`=Object-Pool, `x15`=Stack, Smi-Tagging via `sbfx .. #0x1,#0x1f`):
  - **0x480500**: Schleife über 7 Indexpositionen (Offsets 0x7/0xb/0xf/…/0x2b, je +4), pro
    Position werden zwei Listen-Elemente geladen und an `bl 0x480eb8` übergeben — sieht nach
    einem **Byte-/Element-weisen Vergleich oder XOR zwischen zwei Puffern (z. B. den beiden
    Zufallswerten aus dem 0x5B/0x5C-Handshake)** aus.
  - **0x48d924 / 0x48de78**: Schleifen mit Bounds-Check gegen Listenlänge, Inkrement `x2+1`,
    laufende Summenbildung (`add w.. , w.., w..` je Iteration) — passt zur bekannten Log-Zeile
    „Dropped B frame with invalid wire checksum" → vermutlich die **Checksummen-/Prüfsummen-
    Berechnung** für eingehende Frames.
  - Alle Funktionen rufen an mehreren Stellen `bl 0x009e79xx`/`0x009e98bc`/`0x009e9a1c` auf —
    das sind Standard-Dart-AOT-Runtime-Stubs (GC-Write-Barrier bzw. RangeError-Handler), keine
    App-eigene Logik, können beim Lesen ignoriert werden.
- **Nächster Schritt für tiefere Analyse:** Das bestehende Ghidra-Projekt
  (`C:\tmp\scooter-re\ghidra_project\ScooterRE`) interaktiv in der Ghidra-GUI öffnen und den
  **Decompiler** (nicht nur Listing) auf diese Adressen ansetzen — liefert deutlich lesbareren
  C-ähnlichen Pseudocode statt Rohassembler und ist der praktikablere Weg, die exakte
  Byte-Semantik (welches Feld ist der Zufallswert, wo wird XOR/AES angewendet) zu klären.

## Update 2026-09-13 (Fortsetzung): Decompiler-Durchbruch — BLAKE2s/SHA-256-Konstanten gefunden

Headless-Decompilierung (statt nur Rohassembler) per zweitem Java-GhidraScript
(`ghidra/GhidraDecompile.java`, nutzt `DecompInterface`) auf die 6 hSa-Closure-Adressen
angesetzt. Volles Ergebnis: `ghidra/decompiled.txt`.

**Wichtige Korrektur der ersten Einschätzung:** `0x480500`, `0x4d4ec8`, `0x47510c` sind
**keine** Handshake-Logik, sondern von Dart automatisch generierte Boilerplate-Methoden
(feldweiser `==`-Operator/HashCode-Kombination für kleine Datenklassen wie `dSa`/`ASa`/`WRa`,
sowie eine Cursor-Advance-Funktion die ein „remaining"-Feld dekrementiert und ein
„position"-Feld inkrementiert — passt zum `msgIt`-Zähler-Konzept, ist aber generisch).

**Durchbruch:** `0x48a4a8`, `0x48d924` und `0x48de78` sind gar keine drei separaten Closures,
sondern liegen alle in **einer** großen Funktion (`0x489cb8–0x4901e4`, ~26KB). Deren
Fehlerpfad (erreicht aus der Boilerplate-Funktion `0x480500`) baut ein Array aus 8×4-Byte-Werten
auf, von denen sich sechs eindeutig per Cross-Referenz mit `objs.txt`/`pp.txt` als
**SHA-256/BLAKE2s Initial-Hash-Konstanten** identifizieren ließen:

```
pp+0x113a8 = 0x6a09e667   (H0)
pp+0x113b0 = 0xbb67ae85   (H1)
pp+0x113b8 = 0xa54ff53a   (H3)
pp+0x113c0 = 0x510e527f   (H4)
pp+0x113c8 = 0x9b05688c   (H5)
pp+0x113d0 = 0x5be0cd19   (H7)
```

(H2=`0x3c6ef372` und H6=`0x1f83d9ab` fehlen in der Pool-Referenz — vermutlich weil sie im
zweiten, mit dem Parameter-Block bereits XOR-verknüpften Konstantenblock
(`pp+0x12b38/0x12b48/0x12b50/0x12b58/0x12b60/0x12b68`) sowie den vier direkt inline codierten
Werten `0x78dde6e4`, `0x567cd83e`, `0x3f07b356`, `0x26fc42f2` aufgehen — das Muster passt zu
**BLAKE2s' Initialisierung** (Standard-IV XOR Parameter-Block aus Digest-/Key-Länge, Salt,
Personalisierung), nicht zu reinem SHA-256.)

**Konsequenz für das Protokollverständnis:** Die Session-Schlüsselableitung für den
„Elliptic"-Kanal nutzt sehr wahrscheinlich **BLAKE2s** (nicht reines SHA-256) zur Ableitung
des AES-Schlüssels aus den ausgetauschten Zufallswerten (0x5B/0x5C), gefolgt von
AES-CBC-PKCS7 für die eigentliche Frame-Verschlüsselung (String `AES_CBC_PKCS7Padding` bereits
vorher gefunden). Der Rest der großen Funktion (~6500 dekompilierte Zeilen) besteht überwiegend
aus generischem Dart-Listen-/Bounds-Check-Code und wurde nicht vollständig manuell
durchgearbeitet — sinnvoller nächster Schritt dafür: interaktive Ghidra-GUI mit
Cross-Reference-Navigation statt linearem Lesen.

## Update 2026-09-13 (Teil 3): Krypto-Bibliothek identifiziert — `pointycastle`, keine Eigenentwicklung

Nachkontrolle ergab: Die IV-Konstanten tauchen **identisch dupliziert** an mehreren Call-Sites
auf (0x480500 UND 0x480898 enthalten exakt denselben IV-Aufbau-Code, beide enden bei derselben
Adresse 0x480cd3) — typisches Muster für vom AOT-Compiler **inlinen Bibliothekscode**, nicht für
individuelle App-Logik. Gezielte Suche im String-Pool (`pp.txt`) bestätigt eindeutig:

```
"AES_CBC_PKCS7Padding"
"PKCS7"
"Blake2b"
"sha256"
"SHA256 mismatch for "
"invalid parameters passed to AEADBlockCipher"
"Only types ParametersWithIV<KeyParameter> or KeyParameter allowed for seeding"
```

Der letzte String ist **wörtlicher Quelltext aus der Dart-Package `pointycastle`**
(pub.dev, Standard-Krypto-Bibliothek für Dart/Flutter, Open Source). Das heißt:

- Die App implementiert SHA-256/Blake2b/AES-CBC/PKCS7 **nicht selbst**, sondern nutzt die
  fertige, gut dokumentierte `pointycastle`-Bibliothek.
- **Praktische Konsequenz für die eigene Android-Implementierung:** Wir müssen diese
  Primitiven nicht aus Assembler zurückgewinnen — sie lassen sich 1:1 mit einer beliebigen
  Standard-Krypto-Bibliothek (z. B. auf Android: `javax.crypto`/Bouncy Castle für AES-CBC-PKCS7,
  `MessageDigest`/eine Blake2-Implementierung für den Hash) nachbauen. Die eigentliche noch
  offene Frage ist **nicht "welcher Algorithmus", sondern "welche Bytes werden wann wie
  hinein- und herausgeführt"** (Reihenfolge/Aufbau der Hash-Eingabe aus den 0x5B/0x5C-
  Zufallswerten, Schlüssellänge, IV-Herkunft für AES, Frame-Layout).
- Hinweis: `"SHA256 mismatch for "`/`"bin_sha256"`/`"enc_sha256"` deuten auf eine **weitere,
  vermutlich firmware-bezogene** Verwendung von SHA-256 hin (Firmware-Integritätsprüfung beim
  Flashen) — die hier gefundene Instanz ist aber über hSa (die Connection-/Session-Klasse)
  erreichbar, nicht über einen Firmware-Download-Pfad, spricht also eher für Pairing/Verbindung
  als für Firmware-Hashing. Nicht 100% sicher ohne Caller-Graph-Analyse, aber wahrscheinlich.

**Auswirkung auf die Umsetzungsstrategie:** Für die eigene App reicht es, die *Handshake-Byte-
Reihenfolge* zu kennen (0x5B-Random → wie kombiniert mit 0x5C-Random → Hash → AES-Schlüssel);
die Kryptoprimitiven selbst sind Standardware und müssen nicht reverse-engineered werden.

## Update 2026-09-13 (Teil 4): Protokoll vollständig identifiziert — öffentliche Referenz gefunden ("mible")

**Durchbruch per Websuche/GitHub, nicht mehr per Binär-RE nötig.** "Elliptic" war tatsächlich
wörtlich zu nehmen: **Elliptic Curve Diffie-Hellman (ECDH)**. Zwei öffentliche Repos liefern die
vollständige, funktionierende Spezifikation:

1. [`scooterhacking/NinebotCrypto`](https://github.com/scooterhacking/NinebotCrypto) (jetzt unter
   `reference/NinebotCrypto/` geklont) — dokumentiert das **alte** M365/Ninebot-Schema
   (SHA-1 + AES-ECB-als-Stream-Cipher). Bestätigt Kommando-Semantik 0x5B/0x5C/0x5D und
   Frame-Philosophie, ist aber **nicht** das Krypto-Schema des 5 Pro (siehe unten).
2. [`macbury/m365`](https://github.com/macbury/m365) (jetzt unter `reference/m365-rust/`
   geklont) — vollständige Rust-Implementierung des **neuen, verschlüsselten "mible"-Protokolls**,
   das exakt zu unseren Binärfunden passt (ECDH, SHA-256-Konstanten, AES). `doc/protocol.md`
   und `src/mi_crypto.rs` sind produktionsreif und decken den kompletten Ablauf ab.

### Vollständige Protokoll-Spezifikation (aus `mi_crypto.rs`)

**Registration** (einmalig, erstes Pairing):
1. App erzeugt ECDH-Schlüsselpaar auf Kurve **SECP256R1** (P-256).
2. Public Keys mit dem Scooter austauschen.
3. `secret = ECDH(scooter_public_key, app_private_key)`
4. `derived = HKDF-SHA256(ikm=secret, salt=None, info="mible-setup-info", length=64)`
   → `token = derived[0:12]`, `bind_key = derived[12:28]`, `A = derived[28:44]`
5. `did_ct = AES-128-CCM(key=A, nonce=[0x10,0x11,...,0x1b] (fest!), aad="devID", plaintext=remote_info[4:])`
6. `token` (12 Byte) muss dauerhaft gespeichert werden (entspricht dem "S/N"/Pairing-Datensatz,
   den `NinebotCrypto` für den alten Schema-Weg beschreibt — hier aber kryptographisch, nicht die
   Seriennummer selbst).

**Login** (bei jeder Verbindung, entspricht 0x5B/0x5C/0x5D):
1. App erzeugt 16-Byte-Zufallswert (`rand_key`), tauscht ihn mit dem Scooter (`remote_info`) aus.
2. `salt = rand_key + remote_info`, `salt_inv = remote_info + rand_key`
3. `derived = HKDF-SHA256(ikm=token, salt=salt, info="mible-login-info", length=64)`
   → `dev_key=derived[0:16]`, `app_key=derived[16:32]`, `dev_iv=derived[32:36]`, `app_iv=derived[36:40]`
4. Gegenseitige Verifikation: `info = HMAC-SHA256(app_key, salt)`,
   `expected_remote_info = HMAC-SHA256(dev_key, salt_inv)` — müssen mit den vom Scooter
   gesendeten Werten übereinstimmen (das erklärt die **SHA-256-Initialkonstanten**, die wir per
   Ghidra in der von hSa erreichbaren Funktion gefunden haben — HMAC/HKDF-SHA256 nutzen intern
   dieselbe Kompressionsfunktion mit denselben IV-Werten!).

**UART/Telemetrie-Verschlüsselung** (laufender Betrieb):
- `nonce = iv (4 Byte) + 0x00000000 (4 Byte) + counter (4 Byte, big-endian) = 12 Byte`
- `ciphertext = AES-128-CCM(key, nonce, plaintext, tag_length=4 Byte)`
- Frame: `0x55 0xAB | size | counter_high | counter_low | ciphertext... | CRC16(2 Byte)`
- Custom CRC16: Summe aller Bytes als signed 16-bit, Ergebnis `-(sum)-1`, danach Byte-Reihenfolge
  vertauscht (Low/High tauschen).

**Wichtiger offener Punkt:** Das ist die Spezifikation für "mible"-Scooter allgemein (macbury/m365
zielt vermutlich auf ein früheres Modell mit demselben Protokoll-Layer). Für den **5 Pro**
speziell noch zu verifizieren: identische Info-Strings ("mible-setup-info"/"mible-login-info"),
identischer fester Registration-Nonce, identisches Frame-Header-Byte (0x55 0xAB) — plausibel,
da die IV-Konstanten und die Gesamtstruktur exakt übereinstimmen, aber nicht 1:1 aus dem
5-Pro-Binary bestätigt (dafür bräuchte es entweder erfolgreiches dynamisches Tracing, sobald der
Frida-Bug behoben ist, oder tieferes manuelles Nachvollziehen der restlichen ~6500 Zeilen
Dekompilat in `ghidra/decompiled.txt`).

### Nächste Schritte (aktualisiert)

1. Kotlin/Android-Implementierung auf Basis der obigen Spezifikation bauen (BLE GATT-Layer +
   `java.security.KeyPairGenerator("EC", "secp256r1")` für ECDH, `javax.crypto.Mac`/`MessageDigest`
   für HKDF/HMAC-SHA256, Bouncy-Castle o.ä. für AES-CCM da `javax.crypto` AES-CCM auf Android
   uneinheitlich unterstützt).
2. Gegen echten Scooter testen, dabei die drei offenen Annahmen (Info-Strings, Registration-Nonce,
   Frame-Header) live verifizieren — sollte an einem Fehlschlag/Erfolg der ersten Registration
   schnell erkennbar sein.
3. Falls Abweichungen: gezielt die Bytes vergleichen (z. B. via BLE-Sniffing mit einem zweiten
   Gerät/nRF-Dongle, das kein Frida braucht) statt weiter blind im Assembler zu suchen.

## Update 2026-09-13 (Teil 5): Android-App implementiert — Build erfolgreich

Vollständiges Android-Studio-Projekt unter `android-app/` angelegt und **erfolgreich gebaut**
(`gradlew.bat assembleDebug` → `BUILD SUCCESSFUL`, APK liegt unter
`android-app/app/build/outputs/apk/debug/app-debug.apk`). Kotlin-Code ist 1:1 nach der
gefundenen Referenzimplementierung (`reference/m365-rust`) portiert:

```
android-app/app/src/main/java/com/scooterre/client/
  MainActivity.kt              minimaler Test-Screen: Connect / Pair+Login, Log-Ausgabe
  ble/
    GattConstants.kt           UUIDs (fe95/0010/0019, Nordic UART) + MiCommands-Bytes
    ScooterBleManager.kt       Coroutine-Wrapper um BluetoothGatt (connect/discover/notify/write)
  crypto/
    MiCrypto.kt                ECDH (secp256r1), HKDF-SHA256, HMAC-SHA256, AES-128-CCM,
                                calcDid()/calcLoginDid()/encryptUart()/decryptUart()
    Crc16.kt                   Custom-Checksumme aus der Referenz
  protocol/
    MiProtocol.kt               Chunked "mi parcel"/"nb parcel" Lesen/Schreiben, Response-Warten
    RegistrationFlow.kt         Port von register.rs (einmaliges Pairing)
    LoginFlow.kt                Port von login.rs (Session-Keys je Verbindung)
    ScooterSession.kt           Verschlüsselter UART-Kanal (Senden/Empfangen)
    TokenStore.kt                Persistiert den 12-Byte-Auth-Token (SharedPreferences)
```

**Setup-Details, die für den Build nötig waren** (falls das Projekt auf einem anderen Rechner
neu aufgesetzt wird): Android SDK unter `C:\Android\sdk` (cmdline-tools + platform-tools +
build-tools 35.0.0/34.0.0 + platforms;android-35), `local.properties` mit `sdk.dir`, Gradle
8.9 Wrapper (mit AGP 8.6.0 + Kotlin 2.0.21).

**Wichtig — noch nicht getestet:**
- Kompiliert fehlerfrei, wurde aber **noch nicht auf dem echten Scooter ausprobiert**.
- `MainActivity` ist ein reiner Test-Harness (kein fertiges UI), nutzt die MAC-Adresse aus den
  ursprünglichen Notizen (`AA:BB:CC:DD:EE:FF`) hart codiert.
- Offene Verifikationspunkte bleiben wie oben beschrieben: Info-Strings, Registration-Nonce,
  Frame-Header exakt für den 5 Pro bestätigen (erster Registrierungsversuch wird das direkt
  zeigen: entweder klappt der Handshake, oder eine der `ProtocolException`s in `RegistrationFlow`/
  `LoginFlow` gibt genau an, an welcher Stelle es abweicht).
- `ScooterSession`/UART-Kommandos (Fahren, Einstellungen) sind bewusst **nicht implementiert** —
  bisher nur Pairing + Login + der rohe Verschlüsselungs-Umschlag für spätere Telemetrie-/
  Steuerbefehle.
- AES/CCM läuft über Androids eingebauten Provider (`Cipher.getInstance("AES/CCM/NoPadding")`,
  API 26+) — falls das auf dem Testgerät nicht verfügbar ist, wäre Bouncy Castle als
  Fallback-Abhängigkeit nötig.

**Nächster Schritt:** APK auf das Handy installieren und den ersten echten Pairing-Versuch am
Roller machen (Tastendruck-Bestätigung nötig) — das verifiziert oder widerlegt die drei offenen
Annahmen in einem Schritt.

## Update 2026-09-13 (Teil 6, finaler Durchbruch): Live-Test erfolgreich — nur noch PIN fehlt

Drei Live-Testversuche (ECDH/mible, Legacy-NinebotCrypto, Xiaomi-Home-Verbindungskonflikt)
schlugen fehl — **auf Anweisung, nicht mehr zu raten, sondern zu recherchieren**, wurde gezielt
nach GitHub-Projekten gesucht, die explizit "5 Pro" im Namen tragen (vorher übersehen zugunsten
eines falschen Referenzprojekts für ein anderes Modell).

### Gefunden: `KuziaMother/SCOOTER_5_PRO` — exaktes Modell, verifizierte Doku

Repo gleicht Hardware 1:1 ab (RTL8762C, BLE-FW `2.7.0_0015`, MAC-Präfix passend zur bekannten OUI). Jetzt unter
`reference/SCOOTER_5_PRO/` geklont. Wichtigste Korrekturen gegenüber unseren bisherigen Annahmen:

- **`0x0017` ist der DFU-Kommandokanal (Firmware-Update), nicht Login/Command!** Deshalb keine
  Reaktion auf all unsere bisherigen Schreibversuche dorthin.
- Echter Login läuft über **`0x0010`** (Control) + **`0x0016`** (Login-Transport, Kanal 5).
- Vor jeglicher Kommunikation ist ein **A4-Handshake** nötig: `write 0xA4 → 0x0010`, Antwort
  `MNG` auf `0x0016` (liefert `maxPackageNum`/`maxDMTU`), dann `MNG_ACK` zurück. Erst danach
  akzeptiert der Roller strukturierte Kanal-Nachrichten (`CTR`/`ACK`/`DATA`/`EVENT`, feste
  Frame-Größe 18 Byte).
- **Krypto-Schema bestätigt: ECDH P-256 → HKDF-SHA256(shared‖ltmk, salt=`smartcfg-login-salt`,
  info=`smartcfg-login-info`) → 64-Byte Session-Key.** Bestätigung: AES-CCM(key=sk[16:32],
  nonce=`10 11 ... 1b` fest, tag 4 Byte), Klartext = CRC32(Geräte-Pubkey) LE.
- **`ltmk` (Long-Term-Key) kommt aus der Xiaomi-Cloud**, nicht lokal: `POST
  /app/share/askbluetoothkey {type:own, did, keyid:0}` → `result.{key, encrypt_type}`.
  `encrypt_type==1` bedeutet: ein **Sharing-PIN** ist auf dem Account für dieses Gerät gesetzt,
  `ltmk = AES-128-CBC-NoPad(key=MD5(pin), iv=7aa4c68c...3800, key_hex)`.

### Live verifiziert (direkt vom Entwicklungs-PC aus, PC hat eigenes BLE)

`reference/SCOOTER_5_PRO/test_stage_a.py` (neu, nutzt `core/dreame_auth.py` als Bibliothek):

1. **A4-Handshake: erfolgreich** (`maxPackageNum=6, maxDMTU=242`)
2. **ECDH-Public-Key-Austausch: erfolgreich** — eigenen Public Key gesendet, **echten
   Public Key des Rollers empfangen** (64 Byte, per Kanal-Transport CTR/DATA/ACK).

Das bestätigt die komplette Dokumentation als exakt korrekt für unser Gerät.

### Mi-Cloud-Zugriff eingerichtet

`webui/micloud_ltmk.py` (QR-Login via `tools/xct/token_extractor.py`) erfolgreich genutzt:
- QR-Login abgeschlossen (Login direkt per `adb` im Browser auf dem Handy geöffnet, da Passwort
  nur dort per Autofill verfügbar war — PC-Browser und normales QR-Scannen scheiterten aus
  Praxisgründen).
- `list_devices()` findet den Roller korrekt: `{'name': 'Xiaomi Electric Scooter 5 Pro', 'model':
  'xiaomi.scooter.5pro', 'did': '<did>', 'mac': 'AA:BB:CC:DD:EE:FF', 'country': 'de'}`
- `fetch_ltmk()` liefert `encrypt_type==1` → **PIN erforderlich, aber nicht bekannt.**

### Einziger offener Blocker: der Sharing-PIN

Laut `docs/BLE.md` des Referenzprojekts beginnt der PIN mit `12` (Rest maskiert) — das ist aber
ein Wert vom Account des Repo-Autors, nicht unserer. Bisher nicht gefunden in: Mi-Home-Account-
Geräteliste (zeigt nur Handys), generische Roller-Geräteeinstellungen. Vermutung: eine
**Roller-eigene Diebstahlschutz-/Sperr-PIN** (evtl. direkt am Display des Rollers eingerichtet,
nicht in Standard-App-Menüs) — noch nicht verifiziert.

**Kein Brute-Force versuchen** (Risiko: Account-Sperre durch Xiaomis Cloud-API-Ratenbegrenzung).

### ✅ ERFOLG — kompletter Login live verifiziert (2026-09-13)

Mit PIN vom Nutzer (`webui/get_ltmk.py <PIN>` → `ltmk` gespeichert unter
`secrets/ltmk_AABBCCDDEEFF.hex`), dann `core/dreame_auth.py AA:BB:CC:DD:EE:FF`
(mit `PYTHONIOENCODING=utf-8` wegen kyrillischer Log-Strings) komplett durchgelaufen:

```
--- ответ устройства на 0x0010: 21000000 ---
[Stage B] LOGIN OK — пин верный!
```

Danach automatisch echte Gerätedaten über den jetzt aufgebauten Sicherheitskanal gelesen:
- Firmware `0x0004`: `2.7.0_0015`
- MCU-Version (Kanal `0x001c`, Opcode 1): `0007`
- Hardware (Opcode 3): `RTL8762C`
- Seriennummer (Opcode 8): ausgelesen
- DFU `getFragmentSize` (`0x0017`, nach Login): `512`

**Damit ist die komplette Protokollkette live gegen das echte Gerät bestätigt:**
A4-Handshake → Kanal-Transport (CTR/ACK/DATA, 18-Byte-Frames) → ECDH-P256-Schlüsseltausch →
HKDF-SHA256-Session-Key (`smartcfg-login-salt`/`smartcfg-login-info`) → AES-CCM-Bestätigung mit
`ltmk` aus der Cloud (PIN-geschützt) → verschlüsselter Zugriff auf Telemetrie/Geräteinfo/DFU.

Voller Log: `reference/SCOOTER_5_PRO/docs/scooter_info.txt` (Report), Konsolen-Output oben.

**Ort des `ltmk`:** `reference/SCOOTER_5_PRO/secrets/ltmk_AABBCCDDEEFF.hex` (und Kopie unter
`secrets/ltmk.hex` für das Skript-Legacy-Default). ⚠️ **Geheim halten** — dieser Schlüssel
erlaubt vollen authentifizierten Zugriff auf den Roller. Nicht committen (Repo hat
`secrets/` vermutlich schon in `.gitignore`, prüfen vor jedem Push).

### Nächste Schritte

1. **Telemetrie/Steuerung ausbauen**: `probes/spec_read.py`, `probes/spec_listen.py`,
   `tools/dump_telemetry.py` gegen den echten Roller ausprobieren (nutzen denselben
   `ltmk`/Login-Mechanismus). `webui/app.py` (fertiges Flask-Dashboard) direkt starten für
   sofortige Live-Ansicht, falls eine Desktop/Web-Lösung ausreicht statt einer Android-App.
2. **Android-Kotlin-Implementierung korrigieren**: Der bisherige `android-app/`-Code
   (ECDH/mible- und NinebotCrypto-Versuche) basierte auf falschen Annahmen (falsches
   Referenzprojekt) und funktioniert nicht — jetzt mit der bestätigten echten Spezifikation
   neu aufbauen: A4-Handshake, Kanal-Transport-Layer (CTR/ACK/DATA/EVENT, 18-Byte-Frames),
   korrekte UUID-Rollen (0x0010=Control, 0x0016=Login, 0x001a/0x001b=Telemetrie/SPEC,
   0x0017/0x0018=DFU), HKDF-Salt/Info `smartcfg-login-*`, CRC32-Bestätigung statt CRC16.
   `ltmk` muss weiterhin per Cloud-API + PIN geholt werden (einmalig, dann lokal speichern).
3. Alte, jetzt widerlegte Kotlin-Dateien (`NinebotCrypto.kt`, Teile von `MiCrypto.kt`/
   `MiProtocol.kt`) markieren/ersetzen, um Verwirrung in künftigen Sessions zu vermeiden.

### Nächste Schritte (historisch, vor PIN-Fund — z. T. überholt)

1. `webui/get_ltmk.py` mit PIN-Parameter erneut ausführen → `ltmk` wird nach
   `reference/SCOOTER_5_PRO/secrets/ltmk_AABBCCDDEEFF.hex` gespeichert.
2. `python core/dreame_auth.py` komplett laufen lassen (Stage A+B) → sollte mit `21 00 00 00`
   (Login OK) antworten, dann `collect_info()` zur Verifikation (liest z. B. Firmware-Version
   über den jetzt aufgebauten Sicherheitskanal).
3. Die Android-Kotlin-Implementierung (`android-app/`) entsprechend korrigieren: A4-Handshake +
   Kanal-Transport-Layer (CTR/ACK/DATA, 18-Byte-Frames) ergänzen, UUID-Rollen korrigieren
   (0x0010=Control, 0x0016=Login, 0x001a/0x001b=Telemetrie/SPEC, 0x0017/0x0018=DFU), Krypto auf
   `smartcfg-login-salt/info` + CRC32-Bestätigung umstellen. Der aktuelle Kotlin-Code
   (ECDH/mible- und NinebotCrypto-Versuche) war beides falsch und kann verworfen/ersetzt werden.
4. Optional: `webui/app.py` (fertiges Flask-Dashboard des Referenzprojekts) direkt nutzen, statt
   alles in Kotlin nachzubauen — falls eine Desktop/Web-Lösung ausreicht statt einer Android-App.

### Aufgeräumt

Alle temporären Downloads (Android-cmdline-tools-Zip, jadx-Zips, redundanter Blutter-Rerun-Output)
nach Verifikation gelöscht, ~300MB freigegeben. Verbleibendes Arbeitsverzeichnis weiterhin unter
`C:\tmp\scooter-re\`; Android-SDK/Tools dauerhaft unter `C:\Android\`.

## Update 2026-09-19: Zweites Modell (5 Max) — Property-Tabelle live bestätigt, keine Erweiterung gefunden

Nutzer besitzt zusätzlich einen **Xiaomi Electric Scooter 5 Max** (`xiaomi.scooter.5max`,
MAC `AA:BB:CC:DD:EE:FF`) und möchte ihn in derselben App verwalten wie den 5 Pro.

**Vorab recherchiert:** Die von diesem Projekt genutzte siid/piid-Tabelle ist NICHT die
öffentliche MIoT-Spec. Über miot-spec.org bestätigt: Die öffentliche Spezifikation
dokumentiert für `xiaomi.scooter.5pro` UND `xiaomi.scooter.5max` ausschließlich siid=1
"Device Information" (5 generische Felder: Hersteller, Modell, Geräte-ID, Firmware,
Seriennummer). Die eigentliche Telemetrie-/Steuerungstabelle ist eine proprietäre,
undokumentierte lokale BLE-Erweiterung — es gibt also keine öffentliche Quelle, um
Modellunterschiede vorab nachzuschlagen; nur ein Live-Test am echten Gerät kann das klären.

**LTMK-Beschaffung:** `webui/get_ltmk.py`/`micloud_ltmk.py` funktionierten unverändert für
das zweite Gerät — einfach die Ziel-MAC ändern und die PIN für den 5 Max angeben (beide
Geräte hängen am selben Mi-Account). Kleiner, unabhängig gefundener Bug:
`probes/spec_read.py`s `run()`-Funktion liest hartkodiert die Legacy-Datei
`secrets/ltmk.hex` statt `dreame_auth.ltmk_path_for_mac(mac)` zu benutzen — mit der
falschen (fremden) LTMK schlägt der Login mit `status=0x22` fehl, mit der richtigen
per-MAC-Datei sofort `status=0x21` (OK). Kein Protokollunterschied, nur ein
Skript-Fehler in diesem einen Probe; `dreame_auth.py`'s eigenes `ltmk_path_for_mac()`
ist korrekt.

**Gezielter Sweep (20 bekannte Properties über alle 5 siids 1/2/3/4/6), einzeln abgefragt
(nicht gebündelt):** alle 20 kamen mit plausiblen, korrekten Werten zurück — u. a.
`REMAINING_MILEAGE=60.5 km` (passt exakt zur offiziellen 60-km-Reichweiten-Spec des 5 Max),
`ENERGY_RECOVERY=30` (bestätigt dasselbe 30/60/90-Enum wie der 5 Pro),
`FIRMWARE_VERSION="2.7.0_0015.0016"`, `TIRE_MAINTENANCE`/`MORE_BATTERY_INFO`/`LOG_1`
dekodieren alle im exakt selben Packed-Hex-Format wie beim 5 Pro.

**Breiter Sweep zur Suche nach Max-exklusiven Properties (siid 1–8 × piid 1–25, je 200
Kombinationen, einzeln abgefragt):** keine einzige Property jenseits der bekannten 51
gefunden. Als Methodik-Gegenprobe wurde derselbe Sweep gegen den 5 Pro wiederholt (dessen
lokal gecachte LTMK war zwischenzeitlich abgelaufen/rotiert — vermutlich durch die
Mi-Home-App im Hintergrund, nicht durch eine bewusste Nutzeraktion; per Cloud+PIN neu
geholt): identisches Ergebnis, identisches Statuscode-Paar für "unbekannt" (`status=0xf05d`
= Service existiert nicht, z. B. siid 5/7/8 komplett; `status=0xf05f` = Property existiert
nicht innerhalb eines vorhandenen Service, z. B. siid 6 piid>5) auf beiden Geräten. Das
bestätigt die Sweep-Methodik selbst und zeigt: auch der 5 Pro hat im selben Bereich keine
undokumentierten Extra-Properties — die 51-Properties-Tabelle ist für beide Modelle
vollständig, nicht nur zufällig kompatibel für die getesteten 20.

**Ergebnis: `SCOOTER_5_MAX`-Profil = `SCOOTER_5_PRO`-Profil, 1:1, ohne einen einzigen
Unterschied** (siehe `SpecClient.kt`s `SpecProfiles`). Kotlin-Umbau auf eine
`SpecProfile`-pro-Modell-Abstraktion plus Mehrgeräte-Verwaltung (Geräteauswahl-Screen)
lief direkt im Anschluss (Plan lag in der lokalen Plan-Datei des Assistenten).

**Unbestätigte Vermutung, nicht getestet:** Der Nutzer besitzt keine Basisversion
"Xiaomi Electric Scooter 5" (ohne Pro/Max-Zusatz), vermutet aber, dass auch diese
dieselbe Property-Tabelle nutzt, da alle drei Varianten vermutlich identische/sehr
ähnliche Firmware fahren (gleiche MIoT-Geräteklasse `scooter:0000A077`, gleiche
Generation). Plausibel angesichts der 1:1-Übereinstimmung zwischen Pro und Max, aber
**nicht verifiziert** — bei Bedarf mit demselben Sweep-Verfahren nachprüfen, sobald ein
solches Gerät verfügbar ist. Der App-Code behandelt ein unbekanntes Modell ohnehin per
Fallback wie den 5 Pro (`SpecProfiles.forModel()`), ohne dass das extra kodiert werden
müsste.

## 2026-09-20: Mehrfachabfrage (Batching) ist auf dieser Firmware nicht möglich

Live gegen den 5 Max (Notebook-BLE-Adapter, nur lesend, ein GET-Frame mit 1, 2 und 3 Objekten
`(1,2)`, `(1,4)`, `(1,5)`): Nur das **erste** Objekt kommt als gültiges Ergebnis zurück
(`BATTERY_LEVEL=100`), alle weiteren als Fehlereinträge (`status=0xf05d` bzw. `0xf05f`, ohne
sinnvolles siid/piid-Echo, kürzer als ein Erfolgseintrag). Das deckt sich mit den drei
Experimenten des Referenzprojekts (`reference/SCOOTER_5_PRO/docs/BLE.md`: "Устройство
обслуживает только ПЕРВЫЙ объект в запросе"). Der frühere "Müll" beim 19-Objekte-Versuch war
teilweise ein Parser-Desync (Fehlereinträge sind 5 Byte, nicht 7+len), die zugrundeliegende
Grenze ist aber real. Konsequenz: eine Property pro Request bleibt Pflicht; der Vollabruf (~9 s)
lässt sich nicht bündeln, nur der kleine Fahr-Refresh (7 Properties, ~2,5 s) ist schnell genug.
