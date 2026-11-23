# PhoneKey23 for Android

Native Android WireGuard client. Package: `com.phonekey.vpn`. JDK 17, Kotlin, Jetpack Compose, `minSdk 24`, `targetSdk 37`, `compileSdk 37`. Version name 1.2.0. Android 7.0 is the oldest installable version because the WireGuard tunnel library requires API 24. Android 17 is API 37.

Project page, which does not itself connect a VPN: [https://tajoor621.github.io/PhoneKey23/](https://tajoor621.github.io/PhoneKey23/). The line on the repository is **Chaabi jo aapko Jorhy Apno sy** — the key that joins you with your own.

**A real connection requires a working WireGuard server and a valid profile.** PhoneKey23 does not include or host a VPN server. The GitHub Pages website is not a VPN client; only this native Android app can request Android VPN permission and establish the system tunnel.

## Android capabilities

- Foreground `VpnService` implemented by `PhoneKeyVpnService` and driven by WireGuard `GoBackend`.
- WireGuard profile import from a `.conf` file, QR code, or manual form.
- Profile editing, selection, deletion, QR display and JSON export/import.
- Notification actions, Quick Settings tile and optional auto-connect behavior.
- Xray/V2Ray and SSTP are deliberately rejected because their tunnel engines are not implemented.

## Before connecting

1. Obtain a valid WireGuard client configuration from your VPN server administrator or your own server.
2. Confirm that the endpoint is reachable and that the server peer has the correct public key, allowed IPs and routing.
3. Import the profile into PhoneKey23.
4. Approve the Android VPN permission dialog.
5. Connect and verify a recent WireGuard handshake and traffic through the server.

A profile can be imported without a server being reachable, but that does not mean a tunnel can connect. An endpoint that is a placeholder or documentation address is rejected.

## Kill switch

The in-app setting retries a dropped WireGuard session and can hold a no-forwarding VPN interface while the app process remains alive. This is not a reboot-safe or force-stop-safe kill switch. For a persistent system-level block, set PhoneKey23 as **Always-on VPN** and enable **Block connections without VPN** in Android system VPN settings.

## Key storage

Profile config text is sealed with AES-GCM using a key generated in Android Keystore. The key is not stored in `profiles.json`. Backup/device transfer is disabled. Reinstalling the app can make old encrypted profile data unrecoverable; export profiles securely before reinstalling or changing devices.

## Split tunneling

The app supports Android allowed/excluded application lists. Validate this behavior on a physical device with the actual profile and traffic; UI selection alone is not proof that packets are routed as intended.

## Permissions

| Permission | Purpose |
| --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Tunnel operation and endpoint checks |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Foreground VPN service and ongoing notification |
| `POST_NOTIFICATIONS` | VPN status notification |
| `RECEIVE_BOOT_COMPLETED` | Optional boot auto-connect |
| `WAKE_LOCK` | Partial wake lock for the whole connect attempt (60 second cap), released when the attempt ends |
| `CAMERA` | Optional QR scanning |

The VPN service is guarded by `BIND_VPN_SERVICE`; Android's `VpnService.prepare()` consent flow must be completed before connecting. The service currently declares the `specialUse` foreground-service subtype `vpn`; this declaration and distribution-policy compliance must be reviewed against the target Android versions and chosen distribution channel.

## Build and test

Open this `phonekey` directory in Android Studio with JDK 17, or run:

```bash
./gradlew :core:test
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

CI runs core unit tests, web checks, Compose instrumented UI tests on an emulator matrix (API 24, 26, 31, 34, 36, and 37.0), and Android unit tests before generating the debug APK artifact. A version tag runs `lintRelease`, `apksigner` from `build-tools/37.0.0`, and `scripts/check-16kb.sh` before publishing. A debug APK is never the production asset. Android 17’s platform package is `platforms;android-37.0`. `compileSdk 37` looks up `platforms/android-37`, so the build aliases that name to `android-37.0`.

## Deep link

The app handles `phonekey://activate` only. `https://tajoor621.github.io/PhoneKey23/activate/` is a web page that links to that scheme. It is not an Android App Link.

## Battery

A connected tunnel is a foreground service. Doze can still restrict a process the user force-stops or that the vendor puts in deep sleep. If the tunnel drops while the phone is idle, exempt PhoneKey from battery optimization in system settings. The app does not request that exemption by itself.

## 16 KB pages

`com.wireguard.android:tunnel:1.0.20260102` ships arm64-v8a and x86_64 libraries with ELF LOAD alignment `0x4000`. The 32-bit libraries in that same artifact are aligned to `0x1000`. 16 KB devices do not load the 32-bit copies. See [BASELINE.md](BASELINE.md).

## Signed GitHub Releases

Version tags (`v*`) publish only after all required test/build jobs pass. The release workflow requires these GitHub Actions secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

It creates a signed `PhoneKey23-release.apk`, a source archive and `SHA256SUMS.txt`. A release tag fails closed if signing secrets are missing; a debug APK is never substituted as a production release.

Keep the signing keystore and passwords private and backed up securely. Do not commit them to the repository.

## Project layout

```text
core/                          JVM module: parser, models, policy, codecs, unit tests
app/src/main/java/com/phonekey/vpn/
  MainActivity.kt              launcher
  ActivateActivity.kt          deep links
  PhoneKeyApp.kt               process start, notification channel
  data/store                   DataStore settings, encrypted profiles
  data/vpn                     WireGuard engine and VPN repository
  service/                     VPN service, tile, boot receiver, notification actions
  ui/                          Compose screens
```
