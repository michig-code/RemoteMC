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
- Supports Paper, Purpur, Folia and Spigot from 1.20.6 up to 26.x
- Messages in English, German, Spanish, French, Italian, Dutch, Polish, Brazilian Portuguese,
  Turkish and Russian; update check
- Imports settings and the access token from the McRemote prototype

### App
- Pairing by QR code or 6-digit code, with a security check; the phone's name is asked once
  and used for every pairing
- Encrypted storage of access tokens (Android Keystore)
- Live connection with automatic reconnect and offline fallback
- Overview with TPS, MSPT, memory and worlds; players with search, sorting, real skin heads
  and actions; coloured console with your recent commands and Minecraft-like suggestions; chat
- Server list: hold a server and drag it to sort it, into or out of a folder, from folder to
  folder or into "Pinned"; pin servers to the top, group them in folders, search by name or address
- Role-aware UI; settings for theme, colour (9 presets or any custom colour), Material You, language and haptics
- 10 languages, tablet layout, accessibility, targets Android 16 (API 36)
