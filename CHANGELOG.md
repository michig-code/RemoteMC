# Changelog

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
