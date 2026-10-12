# RemoteMC – deinen Server vom Handy aus verwalten

**RemoteMC** verbindet deinen Minecraft-Server mit der kostenlosen RemoteMC-App für Android. Sieh, wer
online ist, behalte die Leistung im Blick, lies die Live-Konsole, chatte und führe Befehle aus – von überall.

## Funktionen
- **Live-Übersicht** – TPS, MSPT, RAM, Laufzeit, Welten und Spieler in Echtzeit
- **Spielerverwaltung** – Nachricht, Heilen, Spielmodus, OP, Kicken, Bannen
- **Live-Konsole** – Server-Log in Echtzeit, Befehle mit Ausgabe
- **Chat** – mitlesen und Nachrichten an alle senden
- **Koppeln in 10 Sekunden** – `/remotemc pair` zeigt einen QR-Code auf einer Karte im Spiel oder in der Konsole
- **Sicher ab Werk** – eingebautes HTTPS mit Zertifikats-Pinning, eigener Token pro Gerät (nur gehasht gespeichert),
  Rollen (Admin / Moderator / Zuschauer), Sperre nach Fehlversuchen und Audit-Log
- **Läuft überall** – Paper, Purpur, Folia und Spigot, Minecraft 1.20.6 bis 26.x
- Deutsch und Englisch

## Loslegen
1. `RemoteMC.jar` in `plugins/` legen und den Server neu starten.
2. Port **8765** (TCP) in Firewall/Router freigeben oder einen Zusatzport deines Hosters nutzen
   (`api.port` in `plugins/RemoteMC/config.yml`).
3. RemoteMC-App installieren, `/remotemc pair` eingeben, QR-Code scannen – fertig.
