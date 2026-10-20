# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability, please open a private security advisory on GitHub or contact the maintainer directly.

Do not publish exploit details publicly before coordination.

## Updates and Signing

- Releases are signed with the ShackScreen key. Its SHA-256 is
  `a4b212f1b47635da599640c4c61d431b98671833b67f36b97df3a748ef5e8a32`.
- The in-app updater installs an update only if it is ShackScreen, newer, and signed with that same key.
- Only download ShackScreen from this repository's [Releases](../../releases).

## Your Servers and Tokens

- Addresses of your own servers and API tokens (e.g. MeshMonitor) are stored only on the device. They are never sent to GitHub, Google, or anyone else.
- A MeshMonitor API token acts as your user. Create a token for a user with read-only permissions where you can.
- Self-hosted servers on your home network are usually plain `http://`. For remote access, use a VPN (e.g. Tailscale) rather than exposing them to the internet.
