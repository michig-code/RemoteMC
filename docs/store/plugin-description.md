# RemoteMC – manage your server from your phone

**RemoteMC** connects your Minecraft server to the free RemoteMC Android app. See who is online,
watch performance, read the live console, chat with players and run commands – from anywhere.

## Features
- **Live dashboard** – TPS, MSPT, memory, uptime, worlds and players, updated in real time
- **Player management** – message, heal, change game mode, OP, kick and ban
- **Live console** – coloured server log, commands with their output and suggestions while typing
- **Chat** – read the chat and broadcast messages
- **Pairing in 10 seconds** – `/remotemc pair` shows a QR code on an in-game map or in the console
- **Secure by default** – built-in HTTPS with certificate pinning, per-device tokens (stored hashed),
  roles (admin / moderator / viewer), lockout after failed attempts and an audit log
- **Works everywhere** – Paper, Purpur, Folia and Spigot, Minecraft 1.20.6 up to 26.x
- English, German, Spanish, French, Italian, Dutch, Polish, Brazilian Portuguese, Turkish and Russian

## Getting started
1. Put `RemoteMC.jar` into `plugins/` and restart the server.
2. Open port **8765** (TCP) in your firewall/router, or use an extra port from your host
   (`api.port` in `plugins/RemoteMC/config.yml`).
3. Install the RemoteMC app and run `/remotemc pair` – scan the QR code, done.

## Commands (permission `remotemc.admin`, default: OP)
| Command | Description |
|---|---|
| `/remotemc pair [admin\|moderator\|viewer]` | Pairing code + QR code for the app |
| `/remotemc info` | API status, port, certificate fingerprint |
| `/remotemc token create <name> [role]` | Token for scripts or manual setup |
| `/remotemc token list` / `revoke <name>` | Paired devices / revoke access |
| `/remotemc reload` | Reload the config |

Alias: `/rmc`. Source code and app download: https://github.com/michig-code/RemoteMC
