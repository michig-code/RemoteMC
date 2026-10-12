# Changelog

## 2.1.0

### Plugin
- Console lines and command output keep their colours (`formatted` field with `§` codes)
- New endpoints `/api/v1/commands` and `/api/v1/complete` for command suggestions
- Security: access tokens, pairing codes and console QR codes no longer appear in the console
  stream that moderators can read
- Player list includes each player's skin texture URL

### App
- Coloured console (Minecraft colour codes, plugin prefixes, warnings and errors)
- Suggestions while typing a command, like Minecraft's chat: command names with descriptions,
  then the server's own completions for arguments, plus a usage hint
- The fixed example commands were replaced by your recently used commands (long-press to remove)
- Targets Android 16 (API 36)
- Player heads are cut from the real skin (mc-heads.net showed Steve for some accounts);
  fallback minotar.net
- Fixed: with the keyboard open, console and chat shrank and left a gap above the keyboard

## 2.0.0

The project is now called **RemoteMC** (previously McRemote).

### Plugin
- Built-in HTTPS with a self-signed certificate; the app pins its fingerprint
- `/remotemc pair`: one-time 6-digit code and QR code (in-game map or console)
- Per-device access with roles **admin**, **moderator** and **viewer**; `/remotemc token create | list | revoke`
- Live updates via Server-Sent Events (console, chat, joins/leaves, status)
- Versioned API (`/api/v1`) with stable error codes, audit log (`audit.log`)
- Supports Paper, Purpur, Folia and Spigot from 1.20.6 up to 26.x
- English and German messages, update check
- Imports settings and the access token from McRemote 1.x

### App
- New pairing flow: scan the QR code or enter the code, with a security check
- Encrypted storage of access tokens (Android Keystore)
- Live connection with automatic reconnect and offline fallback
- Role-aware UI, player search and sorting, undo for chat messages
- Settings: theme, Material You colours, language, haptics
- English and German, tablet layout, accessibility improvements

## 1.0.0
- First version (McRemote): REST API plugin and Android app
