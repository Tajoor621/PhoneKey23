# Changelog

## 1.2.0

Runs on Android 7.0 (API 24) through Android 17 (API 37). WireGuard tunnel 1.0.20260102 itself requires API 24, so Android 7.0 is the oldest version this client can install on.

- `minSdk` is 24. Android 7 has no `startForegroundService`, so the VPN service is started with `startService` there and with `startForegroundService` on Android 8 and newer.
- Android 7 does not load the variable font files. Those need Android 8, so Android 7 uses the system serif and sans fonts. Launcher icons also have a non-adaptive fallback, because Android 7 cannot resolve the adaptive icon.
- `compileSdk` and `targetSdk` are 37. Toolchain: Android Gradle Plugin 9.1.1, Gradle 9.3.1, Kotlin 2.2.21, Compose BOM 2026.09.00. Built-in Kotlin and the new AGP DSL stay off so the existing Kotlin plugin still applies. The SDK package is `platforms;android-37.0` (there is no `platforms;android-37`); the build aliases `platforms/android-37` to that directory so `compileSdk 37` can find it. Build tools are 37.0.0. Emulator checks cover API 24 and API 37.0.
- On Android 17 the app asks for local-network access only when the selected endpoint is on the LAN, link-local, or a local name. A public endpoint does not show that prompt. Denying it still allows a public endpoint, and tells you a LAN server will not handshake.

## 1.1.0

Correctness and Android 16 target for the WireGuard client. No new protocols.

- Profiles decrypt one at a time. A bad entry is shown by name and id, the original `profiles.json` is kept, and the first later save copies it to `profiles.json.bak-<timestamp>` before writing. Loading no longer runs on app startup's main thread.
- Split tunneling writes `IncludedApplications` and `ExcludedApplications`, the keys WireGuard tunnel 1.0.20260102 actually parses. `AllowedApplications` was rejected by that parser.
- Importing a profile bundle assigns a new id. A profile with the same WireGuard public key is skipped instead of replacing the one you already have. The result counts imported, skipped, and failed.
- Boot auto-connect only starts the foreground service. The handshake runs in the service. If Android blocks that start, a notification asks you to tap Connect. Revoked VPN consent does not start anything.
- The connect wake lock is held for the whole attempt, with a 60 second cap, and released when the attempt finishes.
- The DNS lookup that warns about an unresolved endpoint runs beside the connect path, with a 3 second timeout. It cannot delay the tunnel.
- Notification Connect opens the app, which still asks for VPN consent. Notification Disconnect still disconnects.
- `phonekey://activate` is the only app deep link. The https Pages URL is a handoff, not a verified App Link.
- `localhost` is allowed as a WireGuard endpoint. Documentation names and the RFC 5737 ranges are still rejected.
- `targetSdk` and `compileSdk` are 36. Toolchain: Android Gradle Plugin 8.13.2, Kotlin 2.2.21, Gradle 8.13, Compose BOM 2026.06.01 (Compose 1.11.4). BOM 2026.09.00 pulls Compose 1.12, which refuses to compile unless the project moves to compileSdk 37 and AGP 9. WireGuard tunnel stays at 1.0.20260102, the newest published artifact.
- After a drop or a network change, WireGuard is shut down and PhoneKey’s own VPN service is started again before the next handshake. The library’s shutdown stops the service; starting the removed library service left the tunnel down. A kill-switch block is applied only after that shutdown, and it falls back to IPv4 when the device rejects an IPv6 route.
- A sealed profile file is not rewritten on every launch. Only a file that still has a plaintext config is sealed again.
- 64-bit native libraries in tunnel 1.0.20260102 are 16 KB aligned. 32-bit libraries in that artifact are 4 KB aligned; 16 KB devices do not load them.

## 1.0.2

Debug-signed test build. WireGuard only. See the 1.0.2 GitHub release.
