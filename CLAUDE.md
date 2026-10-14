# RemoteMC – Arbeitsweise

RemoteMC besteht aus dem Server-Plugin (`plugin/`), der Handy-App (`android-app/`, Versionen
„github“ und „play“) und der Windows-App (`desktop-app/`, teilt Code und Texte mit der Handy-App).
Der Besitzer schreibt Deutsch – antworte auf Deutsch.

## Updates: erst sammeln, dann programmieren

Jedes Update läuft in dieser Reihenfolge ab:

1. **Sammeln:** Alle Funktionen und Änderungen, die ins nächste Update sollen, kommen zuerst in
   [`docs/NEXT_UPDATE.md`](docs/NEXT_UPDATE.md) – mit kurzer Beschreibung, betroffenen Teilen
   (Plugin / Handy / Windows) und offenen Fragen. Neue Wünsche während der Arbeit werden dort
   ergänzt, nicht sofort einzeln umgesetzt.
2. **Bestätigen lassen:** Die fertige Liste dem Besitzer zeigen. Mit dem Code wird erst begonnen,
   wenn er die Liste freigegeben hat.
3. **Umsetzen:** Alle Punkte der Liste umsetzen und lokal prüfen (siehe unten). Zwischenstände
   dürfen committet werden, werden aber nicht einzeln gepusht.
4. **Einmal pushen:** Wenn das ganze Update fertig und lokal grün ist, einmal pushen. Erledigte
   Punkte in `docs/NEXT_UPDATE.md` abhaken und ins `CHANGELOG.md` übernehmen; die Liste danach
   für das nächste Update leeren.

Ausnahme: dringende Fehlerbehebungen (App stürzt ab, Sicherheitslücke) dürfen sofort und einzeln
kommen.

## Build-Minuten sparen

Das Repo ist privat; GitHub Actions hat ein monatliches Minuten-Kontingent (Windows zählt doppelt).
- So selten wie möglich pushen (siehe oben) – jeder Push startet einen Build.
- Die CI baut nur, was sich geändert hat. Die Windows-Installer (MSI/EXE) entstehen nur per
  „Run workflow“ mit Haken bei „windows“ oder bei einem Release-Tag.
- Vor jedem Push alles lokal prüfen, damit kein zweiter Build zum Reparieren nötig ist.

## Lokal prüfen

```bash
cd plugin && mvn -B package                                    # Plugin + Unit-Tests
cd android-app && ./gradlew lintGithubDebug lintPlayDebug testGithubDebugUnitTest -Proborazzi.test.verify=true
cd desktop-app && ./gradlew test                               # Windows-App (läuft auch unter Linux)
```

- Nach UI-Änderungen in dieser Reihenfolge: Screenshots neu aufnehmen
  (`-Proborazzi.test.record=true`) und ansehen → `python3 android-app/tools/help_images.py`
  (Bilder der Hilfe-Seite) → die Hilfe-Screenshots noch einmal aufnehmen
  (`--tests '*ScreenshotTest.help*' -Proborazzi.test.record=true`), da sie diese Bilder zeigen.
- Erst committen, wenn alle Prüfungen grün sind (Befehle mit `&&` verketten).
- Neue Funktionen bekommen ein Thema auf der Hilfe-Seite (`ui/HelpScreen.kt`, Texte in
  `res/values*/help.xml`, alle 10 Sprachen).
- Plugin-Änderungen zusätzlich gegen einen echten Paper-Server testen
  (`plugin/integration-test/`).

## Feste Regeln

- Kein Upload zu Modrinth, keine GitHub-Releases und keine Pull Requests ohne ausdrücklichen Auftrag.
- **Der erste Release kommt noch nicht.** Keine Versions-Tags (`v*`) pushen – ein solches Tag
  startet `release.yml` und veröffentlicht Plugin und Apps. Erst auf ausdrückliche Anweisung.
- Die Handy-App wird dem Besitzer als `RemoteMC-App-1.0.0.apk` geschickt (mit dem Plugin-JAR,
  wenn sich das Plugin geändert hat).
