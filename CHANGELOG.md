# Changelog

## 1.0.0

First public release of **RemoteMC**.

### Plugin
- Built-in HTTPS with a self-signed certificate; the app pins its fingerprint
- `/remotemc pair`: one-time 6-digit code and QR code (in-game map or console)
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
- Supports Paper, Purpur, Folia and Spigot from 1.20.6 up to 26.x
- Messages in English, German, Spanish, French, Italian, Dutch, Polish, Brazilian Portuguese,
  Turkish and Russian; update check
- Imports settings and the access token from the McRemote prototype

### App
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
- Role-aware UI; settings for theme, colour (9 presets or any custom colour), Material You, language and haptics
- 10 languages, tablet layout, accessibility, targets Android 16 (API 36)
