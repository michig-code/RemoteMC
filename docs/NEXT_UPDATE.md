# Nächstes Update

Hier wird alles gesammelt, was ins nächste Update soll – **bevor** programmiert wird
(siehe [`CLAUDE.md`](../CLAUDE.md)). Erst wenn die Liste freigegeben ist, beginnt die Umsetzung.

**Status:** sammeln

| # | Funktion / Änderung | Teile | Beschreibung | Erledigt |
|---|---|---|---|---|
| 1 | QR-Karte verschwindet beim Wegwerfen | Plugin | Wirft ein Spieler die Kopplungs-Karte aus `/remotemc pair` weg (Q-Taste, Inventar), wird sie gelöscht statt auf dem Boden zu landen. | ☐ |
| 2 | QR-Karte verschwindet nach dem Koppeln | Plugin | Sobald mit dem Code ein Handy gekoppelt wurde, wird die Karte aus dem Inventar (und ggf. der Hand) des Spielers entfernt – auch wenn er sie in eine Truhe gelegt hat, wird sie beim nächsten Anfassen ungültig. | ☐ |
| 3 | *(Vorschlag)* QR-Karte verschwindet nach Ablauf | Plugin | Wenn der Code nach 5 Minuten abläuft, wird die Karte ebenfalls entfernt. | ☐ |
| 4 | *(Vorschlag)* Karte direkt in die Hand | Plugin | Die Karte kommt in die Haupthand; bei vollem Inventar Hinweis im Chat statt stillem Fallenlassen. | ☐ |

## Offene Fragen

- B9 (App-Updates direkt in der APK): wartet auf die Entscheidung, ob das Repo öffentlich wird
  oder ein eigenes öffentliches Repo für Releases bekommt.

## Ideen für spätere Updates

Siehe [`ROADMAP.md`](ROADMAP.md), Abschnitt C.
