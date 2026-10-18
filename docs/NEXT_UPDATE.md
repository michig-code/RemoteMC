# Nächstes Update

Hier wird alles gesammelt, was ins nächste Update soll – **bevor** programmiert wird
(siehe [`CLAUDE.md`](../CLAUDE.md)). Erst wenn die Liste freigegeben ist, beginnt die Umsetzung.

**Status:** wartet auf Freigabe

| # | Funktion / Änderung | Teile | Beschreibung | Erledigt |
|---|---|---|---|---|
| 1 | Fabric-Mod (Minecraft 26.x) | Neue Mod `fabric-mod/` + Plugin (Umbau) | Eine reine Server-Mod für Fabric-Server, die genau wie das Plugin arbeitet – die Apps (Handy, Windows, Web) merken keinen Unterschied: Kopplung mit `/remotemc pair` (QR-Karte, Konsole, Link), HTTPS mit eigenem Zertifikat, Rollen-Tokens, Audit-Log, Live-Konsole, Chat, Status/TPS/Verlauf, Spieler (Kick, Bann, Heilen, Spielmodus, Teleport, Inventar), Whitelist/Banliste, Welten (Zeit, Wetter, Schwierigkeit, Spielregeln), Dateien, geplante Aufgaben, Web-Oberfläche, 10 Sprachen. Spieler brauchen die Mod nicht. **Umbau:** Der vom Server unabhängige Teil des Plugins (API, Kopplung, TLS, Puffer, Verlauf, Dateien …) wird gemeinsam genutzt, damit Plugin und Mod immer gleich bleiben; nur die Anbindung an Paper bzw. Fabric ist getrennt. Test gegen einen echten Fabric-Server mit `apitest.py`. CI baut die Mod mit. | ☐ |

Zuletzt umgesetzt (siehe `CHANGELOG.md`): Nether/End ohne Uhrzeit und Wetter, Web-Oberfläche im Plugin (Browser/iPhone), iPhone-Testversion (`ios-app/`), Serverliste als Raster mit Größen-Anfassern (Handy +
PC), Anfasser in der Übersicht, Karteninhalt und Bildschirm-Bausteine frei anordnen.

## Offene Fragen

- Fabric-Mod: weitere Minecraft-Versionen (z. B. 1.21.x) später als eigene Ausgaben? Upload auf
  Modrinth erst auf ausdrücklichen Auftrag.



## Hinweise

- Ruhezeiten am PC: Windows kennt keine stillen Benachrichtigungen – in den Ruhezeiten kommen am PC
  daher weiter nur „offline“/„wieder online“ (am Handy kommen alle, aber still).
- Auf Erwähnungen direkt in der Benachrichtigung antworten geht unter Windows nicht; ein Klick auf
  die Benachrichtigung öffnet stattdessen den Chat des Servers.

## Ideen für spätere Updates

- Große Serverkarten: Spielerköpfe und Mini-Verlauf (braucht je Server eine zusätzliche Abfrage).
- Übersicht: Teile innerhalb einer Karte (z. B. die drei TPS-Werte) einzeln verschieben; Höhe der
  Übersichtskarten per Anfasser.
- QR-Karte auch nach Ablauf des Codes entfernen; Karte direkt in die Hand geben (nicht gewählt).

Siehe [`ROADMAP.md`](ROADMAP.md), Abschnitt C.
