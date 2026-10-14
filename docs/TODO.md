# Open issues

_None right now._

# Resolved

## Player heads show only Steve (fixed in 1.0.0)
Cause: mc-heads.net returned the default head for some real accounts (e.g. Kai_Miner,
flatsch), even when asked by UUID. The plugin now sends each player's skin texture URL and the
app cuts the face from it; without a skin URL (offline players in chat) it uses minotar.net.

<details><summary>Original notes</summary>

Reported by the user: the app shows the default Steve head instead of the players' real skins.

- Today the app loads `https://mc-heads.net/avatar/<name>` (`PlayerHead` in
  `android-app/.../ui/components/Components.kt`).
- The affected players have real (premium) accounts, so offline mode is not the cause.
- Checked 2026-10-12: mc-heads.net returns the correct heads for real accounts (e.g. Notch,
  jeb_) when called directly. Remaining suspects: mc-heads.net falling back to Steve when its
  own lookup at Mojang fails or is rate-limited, Bedrock/Geyser names, or skins set by plugins
  such as SkinsRestorer. Needs the affected player names to reproduce.
- Fix idea (no third-party service): the plugin sends each player's skin texture URL from
  their `PlayerProfile` textures (`textures.minecraft.net`, also covers SkinsRestorer); the
  app downloads the skin and cuts the face (8×8 + hat layer). Fallback: mc-heads.net by UUID.

</details>
