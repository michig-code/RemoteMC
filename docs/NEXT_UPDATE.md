# Nächstes Update

Hier wird alles gesammelt, was ins nächste Update soll – **bevor** programmiert wird
(siehe [`CLAUDE.md`](../CLAUDE.md)). Erst wenn die Liste freigegeben ist, beginnt die Umsetzung.

**Status:** sammeln

| # | Funktion / Änderung | Teile | Beschreibung | Erledigt |
|---|---|---|---|---|
| 1 | QR-Karte verschwindet beim Wegwerfen | Plugin | Wirft ein Spieler die Kopplungs-Karte aus `/remotemc pair` **selbst** weg (Q-Taste, aus dem Inventar ziehen), wird sie gelöscht statt auf dem Boden zu landen. Fällt sie nur herunter, weil das Inventar voll war, bleibt sie liegen und kann aufgehoben werden. | ☐ |
| 2 | QR-Karte verschwindet nach dem Koppeln | Plugin | Sobald mit dem Code ein Handy gekoppelt wurde, wird die Karte aus dem Inventar (und ggf. der Hand) des Spielers entfernt – auch wenn er sie in eine Truhe gelegt hat, wird sie beim nächsten Anfassen ungültig. | ☐ |

## Offene Fragen

- B9 (App-Updates direkt in der APK): wartet auf die Entscheidung, ob das Repo öffentlich wird
  oder ein eigenes öffentliches Repo für Releases bekommt.

## Ideen für spätere Updates

- QR-Karte auch nach Ablauf des Codes entfernen; Karte direkt in die Hand geben (nicht gewählt).

Siehe [`ROADMAP.md`](ROADMAP.md), Abschnitt C.
