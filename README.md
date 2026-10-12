# McRemote – Minecraft-Server per Android-App verwalten

Dieses Repository enthält zwei Teile:

| Ordner | Inhalt |
|---|---|
| [`plugin/`](plugin) | Paper-Plugin (1.21+), das eine abgesicherte REST-API auf deinem Server öffnet |
| [`android-app/`](android-app) | Android-App (Kotlin / Jetpack Compose, ab Android 8), die sich mit dieser API verbindet |

## Funktionen der App

- **Übersicht:** Spielerzahl, TPS (1/5/15 min), MSPT, Arbeitsspeicher, Laufzeit, Welten
- **Spieler:** Online-Liste mit Position, Leben, Hunger und Ping; Aktionen: Nachricht, Heilen, OP/DeOP, Spielmodus, Kicken, Bannen
- **Konsole:** Live-Konsole und beliebige Befehle mit Rückgabe, dazu Schnellbefehle
- **Chat:** Chatverlauf mitlesen und Nachrichten an alle senden
- Mehrere Server speicherbar, Verbindungstest, Einrichtung per Link

## Installation

### 1. Plugin auf dem Server

1. Bauen: `cd plugin && mvn package` → `plugin/target/McRemote-1.0.0.jar`
   (oder die fertige Datei aus dem GitHub-Actions-Artefakt **McRemote-plugin** nehmen)
2. Die JAR in den Ordner `plugins/` deines **Paper**-Servers (oder Purpur/Pufferfish) legen und den Server starten.
3. Beim ersten Start wird `plugins/McRemote/config.yml` angelegt und ein zufälliger **Token** erzeugt.
4. Den Port (Standard **8765**, TCP) in der Firewall freigeben. Für den Zugriff von außerhalb deines Heimnetzes zusätzlich eine Portweiterleitung im Router einrichten.

### 2. App auf dem Handy

1. Bauen: `cd android-app && ./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
   (oder das Artefakt **McRemote-app** aus GitHub Actions nehmen)
2. Die APK aufs Handy kopieren und installieren („Installation aus unbekannten Quellen“ erlauben).

### 3. Verbinden

**Variante A – per Link (empfohlen):**
Im Spiel oder in der Konsole `/mcremote link` eingeben. Mit einem Klick auf den Link landet er in der Zwischenablage. In der App auf **+ Server** → **Link aus Zwischenablage** tippen → **Speichern**.
Öffnest du den Link direkt auf dem Handy, startet die App automatisch mit den richtigen Daten.

**Variante B – manuell:** Adresse/IP, Port und Token (aus `/mcremote info` oder der `config.yml`) eintragen.

Tippe vor dem Speichern auf **Verbindung testen**.

## Konfiguration (`plugins/McRemote/config.yml`)

```yaml
api:
  host: 0.0.0.0          # Interface, auf dem gelauscht wird
  port: 8765             # API-Port
  public-address: ""     # Domain/IP für /mcremote link (leer = server-ip)
  token: "..."           # wird automatisch erzeugt

security:
  max-failed-attempts: 5 # falsche Tokens bis zur Sperre
  lockout-seconds: 300   # Sperrdauer pro IP
  allow-commands: true   # Konsolenbefehle aus der App erlauben
  blocked-commands:      # Befehle, die die App nicht ausführen darf
    - stop
    - restart

console:
  buffer-size: 500       # zwischengespeicherte Konsolenzeilen
```

Nach Änderungen: `/mcremote reload`

### Befehle (Permission `mcremote.admin`, Standard: OP)

| Befehl | Beschreibung |
|---|---|
| `/mcremote info` | Status, Port und Token (Token anklicken = kopieren) |
| `/mcremote link` | Verbindungslink für die App |
| `/mcremote newtoken` | Neuen Token erzeugen; alte Verbindungen werden ungültig |
| `/mcremote reload` | Config neu laden und API neu starten |

## Sicherheit

- Jede Anfrage braucht den Token (`Authorization: Bearer <token>`). Nach zu vielen Fehlversuchen wird die IP gesperrt.
- Der Token hat **volle Konsolenrechte**. Gib ihn nicht weiter. Falls er doch bekannt wird: `/mcremote newtoken`.
- Die API spricht standardmäßig **unverschlüsseltes HTTP**. Im Heimnetz ist das in Ordnung. Über das Internet sollte HTTPS davor:
  zum Beispiel ein Reverse-Proxy (Caddy/nginx) auf eine Domain oder ein Tunnel (Tailscale, Cloudflare Tunnel). In der App dann **HTTPS** aktivieren und den Proxy-Port eintragen.
  Beispiel mit Caddy: `mc-api.example.de { reverse_proxy 127.0.0.1:8765 }`

## API-Referenz

| Methode | Pfad | Body | Beschreibung |
|---|---|---|---|
| GET | `/api/ping` | – | Plugin-Version (ohne Token) |
| GET | `/api/status` | – | TPS, RAM, Spielerzahl, Welten |
| GET | `/api/players` | – | Online-Spieler |
| GET | `/api/console?since=<id>` | – | Neue Konsolenzeilen |
| POST | `/api/command` | `{"command":"time set day"}` | Befehl ausführen, Ausgabe zurück |
| POST | `/api/chat` | `{"message":"Hallo"}` | Broadcast an alle |
| POST | `/api/players/<name>/<aktion>` | je nach Aktion | `kick`, `ban` (`reason`), `op`, `deop`, `heal`, `gamemode` (`mode`), `message` (`message`) |

Beispiel: `curl -H "Authorization: Bearer TOKEN" http://server:8765/api/status`
