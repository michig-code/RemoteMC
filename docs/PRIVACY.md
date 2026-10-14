# Privacy policy – RemoteMC

_Last updated: 2026-10-14_

RemoteMC (the Android app and the server plugin) does not collect, sell or share personal data.

**App**
- Connects only to the Minecraft servers you add. Server addresses and access tokens are stored on
  your device, encrypted with a key from the Android Keystore, and are excluded from backups.
- Loads player skins from Mojang's texture server (`textures.minecraft.net`, address supplied by
  your server) and, for players who are not online, head images from `minotar.net` by name.
- What you do in the app (console commands, chat messages, player actions, file edits) and the device
  name you choose when pairing are sent only to your own server, over an encrypted connection. The
  developer never receives them.
- Uses the camera only while you scan a pairing QR code; images are processed on the device and not stored.
- Background notifications are off by default. When you turn them on, the app keeps a connection to
  your servers (shown as a permanent notification) and nothing else.
- The built-in demo server runs entirely on your device; it sends nothing anywhere.
- No analytics, no advertising, no accounts.

**Plugin**
- Stores hashed access tokens, the names of paired devices and an audit log of actions
  (time, device name, IP address, action) in the plugin folder on your server.
- Checks GitHub once at startup for a newer release (can be disabled with `update-check: false`).

Questions: open an issue at https://github.com/michig-code/RemoteMC/issues
