# Nächstes Update

Hier wird alles gesammelt, was ins nächste Update soll – **bevor** programmiert wird
(siehe [`CLAUDE.md`](../CLAUDE.md)). Erst wenn die Liste freigegeben ist, beginnt die Umsetzung.

**Status:** freigegeben – in Arbeit

| # | Funktion / Änderung | Teile | Beschreibung | Erledigt |
|---|---|---|---|---|
| 1 | Updates in der Handy-App (B9) | Handy (nur GitHub-Version) | Die App sucht beim Start und alle 6 Stunden auf GitHub nach einer neuen Version (abschaltbar). Gibt es eine, **ploppt unten ein Hinweis auf** („Update 1.1.0 verfügbar – Jetzt aktualisieren“). Ein Tipp lädt die APK, prüft die Prüfsumme und öffnet den Android-Installer; Einstellungen bleiben erhalten. In den Einstellungen: „Nach Updates suchen“ und die aktuelle Version. Die Play-Version bekommt nichts davon (Updates über den Play Store). | ☐ |
| 2 | Update-Popup unten rechts am PC | Windows | Die Windows-App sucht schon nach Updates; neu ist ein **kleines Popup unten rechts** im Fenster („Update verfügbar – Installieren / Später“), statt nur eines Hinweises in der Titelleiste. | ☐ |
| 3 | Hilfe-Seite | Handy | Neues Thema „App aktualisieren“ in allen Sprachen. | ☐ |
| 4 | Changelog in den Einstellungen | Handy + Windows | Neuer Punkt „Neuigkeiten / Changelog“ in den Einstellungen: alle Funktionen von Version 1.0.0, verständlich und nach Bereichen gegliedert (App, Plugin, Windows), in allen 10 Sprachen. | ☐ |

**Hinweis:** Beides findet erst etwas, wenn es auf GitHub einen Release gibt – der erste Release kommt aber erst auf deine Anweisung. Testen kann ich es vorher mit einem nachgestellten Release.

Zuletzt umgesetzt (siehe `CHANGELOG.md`): QR-Karte verschwindet beim Wegwerfen und nach dem Pairen.

## Offene Fragen

- Keine (das Repo ist öffentlich, damit sind App-Updates über GitHub-Releases möglich).

## Ideen für spätere Updates

- QR-Karte auch nach Ablauf des Codes entfernen; Karte direkt in die Hand geben (nicht gewählt).

Siehe [`ROADMAP.md`](ROADMAP.md), Abschnitt C.
