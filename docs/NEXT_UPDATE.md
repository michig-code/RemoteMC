# Nächstes Update

Hier wird alles gesammelt, was ins nächste Update soll – **bevor** programmiert wird
(siehe [`CLAUDE.md`](../CLAUDE.md)). Erst wenn die Liste freigegeben ist, beginnt die Umsetzung.

**Status:** umgesetzt, wird gepusht

| # | Funktion / Änderung | Teile | Beschreibung | Erledigt |
|---|---|---|---|---|
| 1 | Serverliste als Raster | Handy + Windows | Wie auf dem Android-Startbildschirm: Karte gedrückt halten → blauer Rahmen mit Griffen; Griff ziehen ändert die Größe **frei in Rasterzellen** (Breite und Höhe einzeln, z. B. 1×1, 2×1, 3×2 … bis zur vollen Breite), die Karte rastet ein. Der Inhalt passt sich der Größe an (klein: Name + Status; größer: Spieler, TPS, Spielerköpfe, Verlauf). Karten im Raster verschieben; Ordner und Anpinnen bleiben. | ☑ |
| 2 | Gleiche Größen-Griffe in der Übersicht | Handy + Windows | Die Karten der Server-Übersicht bekommen beim Anpassen denselben blauen Rahmen mit Griffen statt des ⇄-Knopfs. | ☑ |
| 3 | Alles im Bearbeiten-Modus verschiebbar | Handy + Windows | Gilt für die **Serverliste** und die **Server-Übersicht**: Im Bearbeiten-Modus lassen sich die Teile in einer Karte (Name, Status, Spieler, TPS …) und die Bausteine des Bildschirms (Kopfzeile, Suche, Karten, Knöpfe) frei anordnen. „Zurücksetzen“ stellt alles wieder her. | ☑ |

Zuletzt umgesetzt (siehe `CHANGELOG.md`): Ordner durch Ineinanderziehen und Ordner anpinnen (Handy +
PC), Server-Ordner am PC, Logo ohne Kasten, Tabs verschieben und Server andocken (PC), die übrigen
Handy-Funktionen am PC (Hilfe, Willkommensseiten, Sprache, erneut koppeln, Spieler suchen/sortieren
u. a.), Installer in RemoteMC-Farben.

## Offene Fragen


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
