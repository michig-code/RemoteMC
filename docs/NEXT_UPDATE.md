# Nächstes Update

Hier wird alles gesammelt, was ins nächste Update soll – **bevor** programmiert wird
(siehe [`CLAUDE.md`](../CLAUDE.md)). Erst wenn die Liste freigegeben ist, beginnt die Umsetzung.

**Status:** wartet auf Freigabe

| # | Funktion / Änderung | Teile | Beschreibung | Erledigt |
|---|---|---|---|---|
| 1 | iPhone-Testversion (iOS 26) | iPhone (neu, `ios-app/`) | Kleine eigene iPhone-App in SwiftUI (sieht mit dem iOS-26-SDK automatisch nach „Liquid Glass“ aus). Umfang: Server per Link oder Adresse + 6-stelligem Code koppeln (verschlüsselt, mit Zertifikats-Prüfung wie am Handy), Demo-Server, Serverliste, Übersicht (Status, Spieler, TPS, Speicher, Welten), Spielerliste, Live-Konsole mit Befehl senden. Englisch + Deutsch. Gebaut wird auf einem Mac-Rechner von GitHub Actions (für öffentliche Repos kostenlos) nur per „Run workflow“ mit Haken „ios“; heraus kommt eine unsignierte `RemoteMC-iOS-test.ipa`. | ☐ |

Zuletzt umgesetzt (siehe `CHANGELOG.md`): Serverliste als Raster mit Größen-Anfassern (Handy +
PC), Anfasser in der Übersicht, Karteninhalt und Bildschirm-Bausteine frei anordnen.

## Offene Fragen

- iPhone: Ohne Apple-Entwicklerkonto (99 $/Jahr) lässt sich die Test-App nur per „Sideloading“
  installieren: am Windows-PC mit **Sideloadly** (oder AltStore) und deiner normalen Apple-ID, am
  iPhone „Entwicklermodus“ einschalten. Die App läuft dann **7 Tage** und muss danach neu
  aufgespielt werden. Passt das für den Test?
- iPhone: Ich kann hier nichts auf einem echten iPhone ausprobieren und auch nicht lokal bauen (dafür
  braucht es einen Mac) – geprüft wird nur, dass der Build auf GitHub durchläuft. Fehler zeigen sich
  erst bei dir am Gerät.


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
