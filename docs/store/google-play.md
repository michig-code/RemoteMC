# Google Play – Checkliste

Was die App schon erfüllt und was du in der Play Console noch selbst machen musst.

## Schon in der App erledigt ✅

| Anforderung | Stand |
|---|---|
| Ziel-API (targetSdk) | 36 (Android 16), aktuelle Pflicht erfüllt |
| 16-KB-Speicherseiten (ab Android 15 Pflicht für Apps mit nativen Bibliotheken) | alle 64-Bit-Bibliotheken (ML Kit, CameraX) sind 16-KB-ausgerichtet, geprüft im Release-Build |
| App-Bundle (AAB) | der Release-Workflow baut zusätzlich `RemoteMC-App-<version>.aab` (Artefakt „RemoteMC-App-bundle“) |
| Versionsnummer | steigt mit der Version (1.0.1 → 10001); mit `-Prmc.versionCode=…` überschreibbar |
| Prüfer ohne Minecraft-Server | eingebauter **Demo-Server** („Demo-Server ausprobieren“ bei leerer Serverliste), läuft komplett im Handy |
| Datenschutzerklärung in der App | Einstellungen → Über (Kurzfassung), vollständig in `docs/PRIVACY.md` |
| Markenrecht Minecraft | Name ohne „Minecraft“, eigenes Logo, Hinweis „Kein offizielles Minecraft-Produkt …“ in App und Store-Text |
| Keine Werbung, kein Tracking, kein Konto | ✅ |
| Berechtigungen | nur Kamera (QR-Code), Benachrichtigungen, Internet; alle erst bei Bedarf abgefragt |
| Vordergrunddienst | Typ `specialUse` mit Begründung im Manifest, nur wenn Benachrichtigungen eingeschaltet sind |
| Keine Selbst-Aktualisierung | die App lädt keinen Code nach (wichtig: der geplante APK-Updater B9 darf nur in der GitHub-Version aktiv sein) |

## Vor dem Hochladen klären ⚠️

1. **Öffentliche Links.** Die App verlinkt Plugin-Download, Quellcode und Datenschutzerklärung auf
   GitHub. Bei einem privaten Repo sehen Play-Nutzer und Prüfer nur „404“. Play verlangt außerdem
   eine öffentlich erreichbare **Datenschutz-URL**. Lösung: Repo öffentlich machen oder ein
   öffentliches Repo nur für Releases + Datenschutzerklärung anlegen.
2. **Signaturschlüssel.** Einen Upload-Schlüssel erzeugen und als Secrets hinterlegen
   (`RMC_KEYSTORE_BASE64`, `RMC_KEYSTORE_PASSWORD`, `RMC_KEY_ALIAS`, `RMC_KEY_PASSWORD`):
   ```
   keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
   base64 -w0 upload.jks   # → RMC_KEYSTORE_BASE64
   ```
   Den Schlüssel gut sichern. Play signiert die App danach selbst (Play App Signing). Folge: Die
   Play-Version und die GitHub-APK haben verschiedene Signaturen und lassen sich **nicht
   übereinander installieren** – wer wechselt, muss die App einmal deinstallieren.
3. **Neues privates Entwicklerkonto** (25 $ einmalig, Identitätsprüfung): Vor der Veröffentlichung
   verlangt Google einen **geschlossenen Test mit mindestens 12 Testern über 14 Tage**.

## Play Console ausfüllen

**Store-Eintrag**
- Name: `RemoteMC` · Kurz- und Langbeschreibung: [`app-description.md`](app-description.md)
- App-Symbol 512×512: [`icon-512.png`](icon-512.png)
- Vorstellungsgrafik 1024×500: [`feature-graphic-1024x500.png`](feature-graphic-1024x500.png)
- Mindestens 2 Handy-Screenshots: [`docs/screenshots`](../screenshots) (ohne Mojang-Logos)
- Kategorie: Tools

**App-Inhalte**
- *Datenschutzerklärung:* öffentliche URL zu `PRIVACY.md` (siehe oben)
- *App-Zugriff:* „Alle Funktionen sind ohne besonderen Zugang verfügbar“ und als Hinweis:
  „Die App steuert den eigenen Minecraft-Server über ein Plugin. Zum Testen ohne Server auf
  *Try the demo server* tippen – alle Funktionen arbeiten dort mit Beispieldaten.“
- *Werbung:* Nein
- *Zielgruppe:* 13 Jahre und älter (unter 13 würde die Familien-Richtlinien auslösen)
- *Inhaltsbewertung (Fragebogen):* Kategorie „Dienstprogramm/Tools“. Nutzer können miteinander
  kommunizieren: **Ja** (Server-Chat). Keine Gewalt, keine Käufe, kein Glücksspiel.
- *Datensicherheit:*
  - Daten werden verschlüsselt übertragen: Ja
  - Erfasst (nur an den eigenen Server des Nutzers, für die App-Funktion, nicht weitergegeben):
    „Nachrichten → Andere In-App-Nachrichten“ (Chat), „App-Aktivitäten → Andere Aktionen“
    (Befehle, Spieleraktionen), „Persönliche Daten → Sonstige“ (selbst gewählter Gerätename).
    Erfassung ist optional (nur wenn der Nutzer sie auslöst).
  - Nicht erfasst: Standort, Kontakte, Fotos, Gerätekennungen, Absturz- und Analysedaten
  - Löschung: Daten liegen nur auf dem eigenen Server; Kopplung in der App entfernen bzw.
    `/remotemc token revoke` auf dem Server
- *Vordergrunddienst-Berechtigung (specialUse):* Beschreibung
  „Hält auf Wunsch des Nutzers eine Verbindung zu seinen eigenen Minecraft-Servern, um über
  Ausfälle, Lags, Spielerbeitritte und Chat-Erwähnungen zu benachrichtigen. Es gibt keinen
  Push-Dienst, der Dienst läuft nur, wenn der Nutzer Benachrichtigungen einschaltet, und zeigt
  dauerhaft eine Benachrichtigung.“ Dazu verlangt Google ein kurzes **Video** (Einstellungen →
  Benachrichtigungen einschalten → Benachrichtigung erscheint), z. B. per Bildschirmaufnahme.
- *Regierungs-, Finanz-, Gesundheits-, Nachrichten-App:* Nein

## Veröffentlichen
1. Version taggen, z. B. `git tag v1.0.1 && git push origin v1.0.1`
2. Im Release-Workflow das Artefakt **RemoteMC-App-bundle** herunterladen
3. Play Console → Test → Geschlossener Test → neuer Release → `.aab` hochladen
4. Nach 14 Tagen mit 12 Testern: Produktion beantragen
