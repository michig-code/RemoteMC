# Roadmap: Geschwindigkeit, Komfort und mehr

Gilt für Handy-App und Plugin. Der Windows-Port wartet vorerst.

Jeder Punkt wird ein eigener Commit mit grünen Tests:
- Plugin-Tests und API-Checks (Paper und Spigot)
- Android-Lint, Unit- und Screenshot-Tests

Nach jedem größeren Block gibt es eine APK `RemoteMC-App-1.0.0-N.apk`.

## Reihenfolge

1. **A1–A3:** Sofortstart, Komprimierung, „unverändert“-Antworten
2. **B0:** Befehlsvorschläge funktionieren immer
3. **B1:** Zahnrad-Menü mit Befehlen, Favoriten und Makros
4. **B2:** Konsolensuche
5. danach der Rest von A, B (inkl. B9 App-Updates) und C

---

## A. Geschwindigkeit

| # | Feature | Nutzen |
|---|---|---|
| A1 ✅ | **Sofortstart aus dem Cache:** Die letzten Werte (Status, Spieler, Konsole, Verlauf) werden lokal gespeichert und beim Öffnen sofort angezeigt, danach im Hintergrund aktualisiert. | Kein leerer Bildschirm und kein Ladekreis beim Öffnen |
| A2 ✅ | **GZIP-Komprimierung** der Plugin-Antworten (Konsole, Verlauf, Dateilisten, Plugin-Liste) | 70–90 % weniger Daten im Mobilnetz |
| A3 ✅ | **ETag / 304 „unverändert“** für Whitelist, Banns, Plugins, Welten, Aufgaben, Dateien und Befehlsliste | Bei unveränderten Listen geht fast nichts übers Netz |
| A4 | **Nur Änderungen über den Live-Stream (SSE):** Spieler kommt oder geht, TPS-Änderung usw. statt ganzer Status-Pakete | Weniger Akku und Daten, flüssigere Anzeige |
| A5 | **Verbindung vorwärmen:** TLS-Verbindung schon in der Serverliste aufbauen, Spielerköpfe dauerhaft auf dem Gerät speichern | Server-Seite öffnet schneller |
| A6 | **Lange Konsole flüssig:** ältere Zeilen gezielt nachladen, Farben vorberechnen, Obergrenze im Speicher | Kein Ruckeln bei 10.000+ Zeilen |
| A7 | **Messung:** Macrobenchmark für Start und Ruckler, Baseline-Profile prüfen, Plugin-Antwortzeiten im Debug-Log | Verbesserungen werden messbar |

---

## B. Komfort

### B0 – Befehlsvorschläge funktionieren immer ✅

**Warum heute manchmal nichts kommt**
1. Die Befehlsliste wird nur einmal geladen (`ServerViewModel.loadCommands`). Schlägt das fehl, bleibt sie bis zum Neuöffnen leer.
2. Fehler und Zeitüberschreitungen werden still verschluckt (`runCatching … getOrDefault(emptyList())`). Es gibt keinen Hinweis und keinen zweiten Versuch.
3. Nur Admin-Tokens bekommen Vorschläge (`/api/v1/commands` und `/api/v1/complete` verlangen `ADMIN`).
4. Argumente kommen nur live vom Server. Bei langsamer Verbindung ist die Antwort zu spät da oder wird durch Weitertippen abgebrochen.
5. Ergänzt wird nur das letzte Wort, nicht das Wort am Cursor.

**Umsetzung**
- **Befehlsliste:**
  - Sie wird pro Server auf dem Gerät gespeichert. Vorschläge sind deshalb sofort und auch ohne Netz da.
  - Sie wird im Hintergrund über ETag (A3) aktualisiert, z. B. wenn ein neues Plugin dazukommt.
  - Schlägt das Laden fehl, gibt es automatische Wiederholungen.
- **Argument-Vorschläge:**
  - Häufige Argumente werden lokal ergänzt: Online-Spieler, Gamemodes, Welten, Schwierigkeit, `true/false`.
  - Der Server ergänzt danach den Rest.
  - Bereits abgefragte Eingaben werden zwischengespeichert. Beim Zurücklöschen erscheinen sie sofort wieder.
- **Robust beim Tippen:**
  - Verspätete Server-Antworten werden noch angezeigt, solange sie zur aktuellen Eingabe passen.
  - Ohne Verbindung zeigt die Leiste: „Keine Verbindung – nur gespeicherte Vorschläge“.
- ~~**Alle Rollen:**~~ entfällt: Moderatoren und Zuschauer dürfen keine Konsolenbefehle ausführen. Ihr Eingabefeld ist gesperrt, Vorschläge würden ihnen nichts nützen. Gesperrte Befehle (`security.blocked-commands`) filtert das Plugin bereits.
- **Am Cursor ergänzen:** Vorgeschlagen und ersetzt wird das Wort an der Cursor-Position.
- **Plugin:**
  - Wirft das Tab-Complete eines fremden Plugins einen Fehler, kommen die übrigen Vorschläge trotzdem, statt dass ein Fehler zurückkommt.
  - Zeitlimit, damit ein hängendes Plugin nichts blockiert.
  - Getestet auf Paper, Spigot und Folia.
- **Tests:**
  - Unit-Tests für Cache, Cursor-Ergänzung und Rollen
  - API-Checks für `/complete` mit Admin-, Moderator- und Zuschauer-Token
  - Fehlerfall „Plugin wirft beim Tab-Complete einen Fehler“

### B1 – Zahnrad-Menü: Befehle, Favoriten, Makros ✅

- **Zahnrad-Knopf** in der Konsole links neben „Senden“. Er öffnet ein Menü von unten mit den Bereichen **Befehle**, **Favoriten** und **Makros**.
- **Befehle:**
  - Alle Server-Befehle, auch die anderer Plugins (`/lp`, `/essentials`, `/worldedit` …).
  - **Ausgeblendet werden Befehle, die die App schon als Feature hat:**
    - kick, ban, pardon, op, deop
    - whitelist
    - time, weather, difficulty, gamerule, gamemode
    - say/tell (Chat)
    - geplante reload/stop (Aufgaben)
  - Die Liste ist nach Plugin gruppiert und durchsuchbar, mit Beschreibung und Verwendung.
  - Ein Tipp setzt den Befehl ins Eingabefeld. Ein langer Druck speichert ihn als Favoriten.
  - Befehle ohne Rechte für den eigenen Token sind ausgegraut.
- **Favoriten:**
  - Ein Tipp führt den Befehl aus, optional mit Nachfrage (z. B. bei `/stop`).
  - Platzhalter `{spieler}` (Auswahl aus den Online-Spielern) und `{text}`
  - Reihenfolge per Ziehen ändern, gespeichert pro Server
- **Makros:**
  - Mehrere Befehle nacheinander mit optionaler Wartezeit, z. B. `say Neustart in 10s` → 10 s warten → `save-all` → `restart`.
  - Favoriten und Makros lassen sich optional als Kachel auf die Übersicht legen.
- **Technik:** `/api/v1/commands` liefert zusätzlich Plugin und „darf ausführen“ pro Token. Die Antwort wird wie in B0 zwischengespeichert.

### B2 – Konsole durchsuchen und filtern ✅
- Volltextsuche
- Filter für WARN/ERROR
- an eine Zeile springen
- Ausschnitte teilen

### B3 – Mehrfachauswahl bei Spielern ✅
Mehrere Spieler auf einmal kicken, eine Nachricht senden oder whitelisten.

### B4 – Spieler-Details ✅
- Spielzeit sowie erste und letzte Anmeldung
- eigene Notizen
- Position und Teleport
- Inventar und Endertruhe ansehen (nur lesen)

### B5 – Benachrichtigungen ✅
- Ruhezeiten
- pro Server ein- und ausschaltbar
- gebündelt („3 Spieler sind beigetreten“)
- Schnellantwort auf Chat-Erwähnungen

### B6 – Widget 2.0 ✅
- mehrere Server
- Größe änderbar
- Schnellaktion (Favorit oder Makro aus B1)

### B7 – Offline-Modus
Die letzten Daten werden mit Zeitstempel angezeigt statt nur einer Fehlermeldung. Baut auf A1 auf.

### B8 – Mehr Rückgängig
- Entbannen nach Bann
- Whitelist-Entfernung
- Papierkorb für gelöschte Dateien (24 h, im Plugin)

### B9 – App-Updates direkt in der App (APK)
Für alle, die die App als APK installieren (nicht über einen Store):
- Die App prüft beim Start und alle 6 Stunden GitHub Releases auf eine neuere Version. Das ist abschaltbar.
- Gibt es eine, erscheint ein Hinweis mit den Änderungen und „Jetzt aktualisieren“.
- Die APK wird heruntergeladen und ihre Prüfsumme (SHA-256) kontrolliert. Danach öffnet sich der Android-Installer, die Einstellungen bleiben erhalten.
- Download nur von GitHub über HTTPS. Android prüft zusätzlich, dass die neue APK mit demselben Schlüssel signiert ist.
- In den Einstellungen gibt es „Nach Updates suchen“ und die aktuelle Version.
- Technik: wie der Updater der Windows-App. Nötig sind die Berechtigung `REQUEST_INSTALL_PACKAGES` und ein `FileProvider` für die heruntergeladene Datei.

---

## C. Größere Features

1. **Backups:** planen, ansehen, herunterladen, wiederherstellen (über Aufgaben und Datei-Manager)
2. **Log-Archiv:** `logs/*.log.gz` durchsuchen, ohne sie herunterzuladen
3. **Karte:** BlueMap/Dynmap einbinden, Spielerpositionen live
4. **Velocity/BungeeCord:** alle Unterserver in einer Ansicht
5. **Team-Zugänge:** Rollen-Token per QR vergeben, Aktivitätsprotokoll in der App
6. **Wear OS:** Status und Benachrichtigungen auf der Uhr
