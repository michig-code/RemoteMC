# Privacy policy – RemoteMC

_Last updated: 2026-10-12_

RemoteMC (the Android app and the server plugin) does not collect, sell or share personal data.

**App**
- Connects only to the Minecraft servers you add. Server addresses and access tokens are stored on
  your device, encrypted with a key from the Android Keystore, and are excluded from backups.
- Loads player head images from `mc-heads.net` by player name.
- Uses the camera only while you scan a pairing QR code; images are processed on the device and not stored.
- No analytics, no advertising, no accounts.

**Plugin**
- Stores hashed access tokens, the names of paired devices and an audit log of actions
  (time, device name, IP address, action) in the plugin folder on your server.
- Checks GitHub once at startup for a newer release (can be disabled with `update-check: false`).

Questions: open an issue at https://github.com/michig-code/main/issues
