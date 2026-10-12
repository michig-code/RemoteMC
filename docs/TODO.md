# Open issues

## Player heads show only Steve
Reported by the user: the app shows the default Steve head instead of the players' real skins.

- Today the app loads `https://mc-heads.net/avatar/<name>` (`PlayerHead` in
  `android-app/.../ui/components/Components.kt`).
- Likely causes: offline-mode / cracked servers (the name is not a premium account, so the
  service returns Steve) or skins applied by plugins such as SkinsRestorer, which the service
  cannot know about.
- Fix idea: the plugin sends each player's skin texture URL from their `PlayerProfile`
  textures (covers online mode and SkinsRestorer); the app cuts the face (8×8 + hat layer)
  from the skin. Fallback: lookup by UUID, then by name.
