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
  files, every change in the audit log; `security.allow-files: false` turns it off;
  RemoteMC’s own JAR is shown but cannot be deleted or replaced from the app
- Teleport players to the spawn or to another player (also on Folia); play time in the player list;
  world spawn in the world details
- Supports Paper, Purpur, Folia and Spigot from 1.20.6 up to 26.x
- Messages in English, German, Spanish, French, Italian, Dutch, Polish, Brazilian Portuguese,
  Turkish and Russian; update check
- Imports settings and the access token from the McRemote prototype

### iPhone test version (new, `ios-app/`)
- Small SwiftUI app for iOS 17+ (built with the iOS 26 SDK): pair by scanning the QR code of
  `/remotemc pair` with the camera, by link or by address and code (certificate pinned like on
  Android), server list with status, server page with status, players online and a console command
- Built on request by `ios.yml` on a GitHub Mac as an unsigned .ipa, to install with AltStore or
  Sideloadly; English and German

### Windows app (new)
- Server list as a grid: the grid button (or holding a card without moving it) shows a blue frame
  with handles like a home-screen widget; drag them to size each card in grid cells (1×1 up to full
  width, 1–4 rows). Bigger cards show more: players, TPS, memory bar, version, MOTD; a 1×1 card
  shows the first letter with a status dot
- Edit mode of the server list: reorder what the cards show (name, address, status, players, TPS,
  memory, version, MOTD) and the screen's blocks (header, search, servers); "Reset" restores all
- Overview: the width of a card is set with a blue handle on its right edge (instead of the ⇄
  button), freely in grid columns
- Built-in demo server with sample data, to try everything without a server of your own
- Server list like on the phone: folders (open and close, rename, remove), pinned servers and
  folders on top, drag to sort or drop a server onto another one for a new folder, search with ✕
- Tabs: drag them to change their order (like in a browser) or down into the window to dock a
  server on the left, right, top or bottom of the active one (two servers at once; divider movable)
- Like on the phone: help page with search (F1 or ?), welcome pages on the first start, language
  choice, "Pair again" (menu and banner when the certificate changed or access was revoked), refresh
  all servers, search and sort players with ping bars and game mode, console that stops following
  when you scroll up (button to jump back) and forgets recent commands (✕), real skins in the chat,
  clickable worlds on the overview, chart value under the mouse, player count on the Players icon;
  a click on a notification opens that server (mentions: its chat)
- Installer in RemoteMC's colours with the logo instead of WiX's red defaults
- Server list, pairing (code, pasted link or token, with certificate check), overview with
  history chart, players with all actions, coloured console with suggestions and history, chat
- File manager with editor (Ctrl+S), add and remove plugins; resizable split views
- Customizable overview: hold a card, then drag cards, change their width or hide them;
  “Reset” and “Done” with a short how-to stay at the bottom of the window while scrolling
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
- Server list as a grid: the grid button (or holding a card without moving it) shows a blue frame
  with handles like a home-screen widget; drag them to size each card in grid cells (1×1 up to full
  width, 1–4 rows). Bigger cards show more: players, TPS, memory bar, version, MOTD; a 1×1 card
  shows the first letter with a status dot
- Edit mode of the server list: reorder what the cards show (name, address, status, players, TPS,
  memory, version, MOTD) and the screen's blocks (header, search, servers); "Reset" restores all
- Overview: the width of a card is set with a blue handle on its right edge (instead of the ⇄
  button), freely in grid columns
- Logo without the dark tile behind it (also on Windows and in the README)
- Widget: a server that answers slowly in the background is checked again instead of shown offline
- Animations can be turned off in the settings (on by default)
- Customizable overview: hold a card to move cards, change their width or hide them;
  “Reset” and “Done” with a short how-to stay at the bottom of the screen while scrolling
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
- Drop a server onto another one to put both into a new folder (the app asks for its name; Cancel
  puts the server back); whole folders can be pinned to the top too
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
