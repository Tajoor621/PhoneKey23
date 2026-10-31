# PhoneKey

PhoneKey is a free, privacy-first WireGuard client for Android, plus a web companion for managing configs on this device. There is no account, no subscription, no ad SDK, and no analytics SDK.

Live site: [tajoor621.github.io/PhoneKey23](https://tajoor621.github.io/PhoneKey23/)

## Protocol status

| Protocol | Status |
| --- | --- |
| WireGuard | Production path. The Android app builds the tunnel with `com.wireguard.android:tunnel` (`GoBackend`). |
| Xray / V2Ray | Not implemented. Import and connect are refused. |
| SSTP | Not implemented. Import and connect are refused. |

PhoneKey does not ship a VPN server. You bring your own WireGuard endpoint. Documentation addresses (`203.0.113.0/24`, `192.0.2.0/24`, `198.51.100.0/24`, `example`, `.test`, `.invalid`) are rejected so they cannot be saved or connected.

## Android project

Package `com.phonekey.vpn`. Kotlin, Jetpack Compose, Material 3. `minSdk` 26, `targetSdk` 35, `compileSdk` 35. JDK 17. Android Studio Ladybug or newer.

1. Download the project from Settings in the web companion, from [the source zip](https://github.com/Tajoor621/PhoneKey23/raw/main/public/phonekey-source.zip), or clone this repo and open the `phonekey` directory.
2. Let Gradle sync. The wrapper uses Gradle 8.11.1 and Android Gradle Plugin 8.7.3.
3. Run the `app` configuration on a device or emulator, API 26 or higher.

From a shell, with the Android SDK installed:

```bash
cd phonekey
./gradlew :core:test
./gradlew :app:assembleDebug
```

The debug APK is built on every push by GitHub Actions. A tag `v*` also publishes that APK, and a signed release APK when the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` repository secrets are set. See [phonekey/README.md](phonekey/README.md) for permissions, the kill switch, and key storage.

## Web companion

The site in this repository is a Vite + React companion. It stores WireGuard profiles in the browser and simulates a session so the interface can be tried without a tunnel. It does not route traffic.

```bash
npm install
npm run dev
```

`npm run typecheck` and `npm run build` check the companion. The companion is not the GitHub Pages site. Pages serves the static page in `docs/`.

## What stays on the device

- WireGuard private keys in the Android app are encrypted with an AES key that lives in the Android Keystore. `profiles.json` holds ciphertext, not the keystore key.
- Backup and device transfer of app data are disabled.
- The camera permission is requested only when you open the QR scanner.
- Split tunneling lists launcher-visible apps. It does not request `QUERY_ALL_PACKAGES`. You can still type a package name.

## Kill switch

The in-app switch reconnects a dropped WireGuard session a few times. While the process is alive it can also hold a tunnel that forwards nothing. That block ends if the process dies. For a block that survives a reboot or a force-stop, open system VPN settings from the Android Settings screen and enable Always-on VPN plus “Block connections without VPN”.

## Deep links

- `phonekey://activate`
- `https://tajoor621.github.io/PhoneKey23/activate`

Both open a welcome screen. There is no license check.

## Policy

No Google Mobile Ads, Facebook, Unity Ads, ironSource, Vungle, AppLovin, billing, Firebase, Crashlytics, or attribution. No `AD_ID`. No telemetry.

## License

[Apache License 2.0](LICENSE). WireGuard’s tunnel library is also Apache-2.0.

## Contributing

Issues and pull requests are welcome on [Tajoor621/PhoneKey23](https://github.com/Tajoor621/PhoneKey23).

- Do not add ad, analytics, billing, or tracking dependencies.
- Do not claim Xray or SSTP work until an engine actually connects.
- Keep parser changes covered by `:core:test`.
- UI copy should say when a control is Android-only or when speeds are simulated.
