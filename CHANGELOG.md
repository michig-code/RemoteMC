# Changelog

## 1.0.0

First public release of **RemoteMC**.

### Plugin
- Built-in HTTPS with a self-signed certificate; the app pins its fingerprint
- `/remotemc pair`: one-time 6-digit code and QR code (in-game map or console)
- The QR map disappears when the player throws it away or once a phone has paired with its code
- Per-device access with roles **admin**, **moderator** and **viewer**; `/remotemc token create | list | revoke`
- Live updates via Server-Sent Events (console, chat, joins/leaves, status)
- Versioned API (`/api/v1`) with stable error codes, audit log (`audit.log`)
- Console lines and command output keep their colours; command suggestions via
  `/api/v1/commands` and `/api/v1/complete`
- Access tokens, pairing codes and console QR codes never appear in the console stream
- Performance history: TPS, tick time, memory and players once a minute for 7 days
  (`history.csv`, `GET /api/v1/history?range=1h|24h|7d`, `history: false` turns it off)
- Whitelist and ban list: `GET/POST /api/v1/whitelist` (on/off, add, remove) and
  `GET /api/v1/bans`, `POST /api/v1/bans/<name>/pardon`
- World controls: `GET /api/v1/worlds/<world>` and `POST …/time|weather|difficulty|gamerule` (admin)
- `GET /api/v1/plugins`: installed plugins with version, authors and state (moderator)
- Scheduled commands every N minutes or daily at a time, with an optional countdown in the chat
  (`schedules.json`, `/api/v1/schedules`, admin; blocked commands stay blocked)
- Server folder for admins: list, read and save text files, upload (e.g. plugin JARs), delete and
  create folders (`/api/v1/files…`); stays inside the server folder, never reaches RemoteMC's own
  files, every change in the audit log; `security.allow-files: false` turns it off
- Teleport players to the spawn or to another player (also on Folia); play time in the player list;
  world spawn in the world details
- Supports Paper, Purpur, Folia and Spigot from 1.20.6 up to 26.x
- Messages in English, German, Spanish, French, Italian, Dutch, Polish, Brazilian Portuguese,
  Turkish and Russian; update check
- Imports settings and the access token from the McRemote prototype

### Windows app (new)
- Server list, pairing (code, pasted link or token, with certificate check), overview with
  history chart, players with all actions, coloured console with suggestions and history, chat
- File manager with editor (Ctrl+S), add and remove plugins; resizable split views
- Customizable overview: hold a card, then drag cards, change their width or hide them;
  “Reset” and “Done” stay at the bottom of the window while scrolling
- Animations like on the phone (areas and tabs slide and fade, cards appear one by one, numbers,
  gauges and bars move smoothly, cards light up under the mouse); can be turned off
- Whitelist and ban list, world settings, plugin list and scheduled tasks, shown only when the
  server's plugin supports them
- Windows notifications with the same rules as on the phone; RemoteMC stays in the tray while
  they are on, and each server can be muted
- Colour themes, light/dark, 10 languages; tokens protected with Windows DPAPI
- MSI and EXE installers with their own Java runtime, built by CI on Windows
- Updates itself: looks for new releases on GitHub (at start and every 6 hours, can be turned off),
  shows a small pop-up in the bottom right corner and an "Update" button in the title bar, downloads
  the installer, checks its checksum, installs it and restarts; the portable version opens the
  download page instead
- "What's new" in the settings lists everything RemoteMC can do

### App
- Animations can be turned off in the settings (on by default)
- Customizable overview: hold a card to move cards, change their width or hide them;
  “Reset” and “Done” stay at the bottom of the screen while scrolling
- File manager: browse the server folder, edit config files, upload and delete files; add a plugin
  by picking its .jar, remove plugins
- Console text can be selected and copied; a button copies the whole console
- Pairing by QR code or 6-digit code, with a security check; the phone's name is asked once
  and used for every pairing
- Encrypted storage of access tokens (Android Keystore)
- Live connection with automatic reconnect and offline fallback
- Overview with TPS, MSPT, memory and worlds, and charts of the last hour, day or week; players with search, sorting, real skin heads
  and actions; coloured console with your recent commands and Minecraft-like suggestions; chat
- Server list: hold a server and drag it to sort it, into or out of a folder, from folder to
  folder or into "Pinned"; pin servers to the top, group them in folders, search by name or address
- Background notifications (optional): server offline / back online, low TPS, player joins
  (all or chosen players) and chat mentions; per server on or off. Works without Google
  services through a live connection with a small permanent notification
- Home screen widget with every server (online, players, TPS; tap to open, refresh button,
  updates every 15 minutes) and a quick settings tile that turns background notifications on
  or off and shows how many servers are online
- Whitelist (on/off, add, remove) and ban list with reason and dates, unban with one tap
- World settings (admins): tap a world to set the time, weather, difficulty and game rules
- Plugin list on the overview (moderators and admins)
- Scheduled tasks (admins): announcements, saving the worlds or any command, repeating or daily,
  with a chat countdown; run now, pause, edit
- Teleport a player to the spawn or another player; play time per player
- Role-aware UI; settings for theme, colour (9 presets or any custom colour), Material You, language and haptics
- 10 languages, tablet layout, accessibility, targets Android 16 (API 36)
- Updates right in the app (GitHub version): looks for new releases at start and every 6 hours (can
  be turned off), a pop-up at the bottom offers "Update now", the APK is checked (size, SHA-256) and
  handed to Android's installer; the Google Play version updates through the store
- "What's new" in the settings lists everything RemoteMC can do, in all 10 languages
- Help & guide with search and pictures; built-in demo server
