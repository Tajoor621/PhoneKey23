# PhoneKey23

**Chaabi jo aapko Jorhy Apno sy.** The key that joins you with your own.

PhoneKey23 is a native Android WireGuard VPN client. It is free software: no ads, no subscription, no account, and no tracking. You bring your own WireGuard server. The app does not include one.

**This website does not connect a VPN.** The page at [tajoor621.github.io/PhoneKey23](https://tajoor621.github.io/PhoneKey23/) is a project and download page. It cannot create an Android `VpnService`, change your public IP, or protect device traffic. A tunnel starts only in the installed Android app, after you approve Android’s VPN permission and import a valid WireGuard profile.

- Android package: `com.phonekey.vpn`
- In-app name: PhoneKey
- Android minimum: API 24 (Android 7.0), target API 37 (Android 17)
- Current version name: 1.2.0
- Build: Kotlin, Jetpack Compose, JDK 17
- Project page: [PhoneKey23](https://tajoor621.github.io/PhoneKey23/)
- APK and source archive: [GitHub Releases](https://github.com/Tajoor621/PhoneKey23/releases)

## Protocol status

| Protocol | Android status |
| --- | --- |
| WireGuard | Implemented through the WireGuard GoBackend. The app reports connected only after a peer handshake. Requires your own working server and a valid profile. |
| Xray / V2Ray | Not implemented. Import and connect are rejected rather than reported as a connection. |
| SSTP | Not implemented. Import and connect are rejected rather than reported as a connection. |

Documentation hosts (`example.com`, `.invalid`, `.test`, and the documentation IP ranges) are rejected. PhoneKey23 does not provide a VPN server, subscription, account, or hosted endpoint.

## What the Android app does

- Foreground `VpnService` (`PhoneKeyVpnService`) driven by WireGuard `GoBackend`.
- Profile import from a `.conf` file, a QR code, or a manual form. Edit, select, delete, show a QR, and export or import a JSON profile list.
- QR codes and JSON exports include private keys. Treat them as secrets.
- Session time, upload and download rates, and byte totals while a handshake is up.
- Notification actions, a Quick Settings tile, and optional auto-connect on launch or after boot (boot connect only if VPN permission was already granted).
- Split tunneling by Android package: selected apps bypass the tunnel, or only selected apps use it.
- Profile text sealed with AES-GCM. The key is held in Android Keystore and is not stored in the profile file. Backup and device transfer are disabled.
- An in-app kill switch that retries a dropped session and can hold a non-forwarding interface only while the app process is alive.

The in-app kill switch is not reboot-safe and not force-stop-safe. For a persistent block, set PhoneKey as Android **Always-on VPN** and enable **Block connections without VPN** in system VPN settings.

Camera permission is requested only when the QR scanner is opened.

Technical layout, permissions, and the kill-switch limit are also written in [phonekey/README.md](phonekey/README.md).

## Download and install

1. Open [release 1.2.0](https://github.com/Tajoor621/PhoneKey23/releases/tag/v1.2.0).
2. Download `app-debug.apk`. It is a debug-signed test build of version 1.2.0, `minSdk` 24, and it installs on Android 7.0 through Android 17. It is not the signed production file. A signed `PhoneKey23-release.apk` is published only when the release signing secrets are present.
3. This file will not install over the [v1.0.2](https://github.com/Tajoor621/PhoneKey23/releases/tag/v1.0.2) debug build when the certificates differ. Export profiles before uninstalling. Keystore-encrypted data can become unrecoverable.
4. Open PhoneKey, import a valid WireGuard `.conf` or scan its QR code, approve the VPN permission, and connect. “Connected” means a peer handshake was confirmed. If none arrives within 25 seconds, the app stays disconnected.

## Build Android locally

Open the `phonekey` directory in Android Studio with JDK 17, or run:

```bash
cd phonekey
./gradlew :core:test
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

GitHub Actions runs core unit tests, web typecheck/tests/build, and Android unit tests before uploading the debug APK artifact. Instrumented Compose UI tests run on an emulator matrix (API 24, 26, 31, 34, 36, and 37). A version tag (`v*`) publishes a **signed release APK only after** the core, web, emulator, and debug jobs pass and every signing secret is present. The release job calls `apksigner` from build-tools 36.0.0 and fails if verification or the 16 KB library check fails. If a signing secret is missing, the release job fails closed. A debug APK is not substituted as that production asset.

Required GitHub Actions secrets for signed releases:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Keep the keystore and passwords private and backed up. Losing the keystore blocks in-place updates for anyone who installed a build signed with it. Never commit the keystore or the passwords.

## Repository map

```text
phonekey/     Android app and JVM core (parser, policy, tests)
docs/         GitHub Pages project site. Information and download only.
src/          Browser profile companion. It does not route traffic.
```

The browser companion can import, edit, and export profiles. It checks a WireGuard config and does not report a connection, because it cannot complete a handshake or route traffic. Importing a profile list assigns new ids and skips a profile whose WireGuard public key is already saved.

The companion does not use accounts. `src/lib/auth`, `migrations/auth`, and `server/` stay in this workspace because the preview shell requires them. PhoneKey itself stores profiles only on the device.

The `/activate/` path on the project site is a handoff into `phonekey://activate` when the app is installed. The app does not register that https URL as a verified Android App Link, so Android will not claim it. The page does not connect a tunnel.

## License

[Apache License 2.0](LICENSE). The WireGuard tunnel library is also Apache-2.0.
