# PhoneKey

Free Android VPN client. WireGuard works. Xray and SSTP have a place to plug in a core later. There is no account, no subscription, no ad SDK, and no analytics SDK.

Package: `com.phonekey.vpn`

## What it does

- Foreground `VpnService` (`PhoneKeyVpnService`) driven by the official WireGuard userspace tunnel (`com.wireguard.android:tunnel`).
- Connection state on a `StateFlow`: Disconnected, Connecting, Connected, Error. The ongoing notification shows upload and download rate and a disconnect action.
- Quick Settings tile (`QuickTileService`) toggles the selected profile.
- Boot receiver (`AppOnBootReceiver`) connects after boot or an update when “Auto-connect on boot” is on and VPN permission was already granted.
- Profiles: add, edit, delete, select. Import a `.conf`, Xray JSON, or SSTP text from a file, a QR code, or a form. Export a profile as a QR code (it contains the private key).
- Deep links `phonekey://activate` and `https://phonekey.app/activate` open a welcome screen. No license check.
- Settings: auto-connect on boot, auto-connect on launch, a reconnect-on-drop kill switch, and split tunneling by package name you type yourself.

## Protocols

| Protocol | Behavior |
| --- | --- |
| WireGuard | Parses a `.conf`, builds the tunnel with `GoBackend`, and reports `wg` byte counters. |
| Xray / V2Ray | Config is stored. Connecting throws `UnsupportedOperationException`. See `XrayEngine`. |
| SSTP | Same idea. See `SstpEngine`. |

`VpnEngine` is the extension point. `EngineFactory` picks the implementation.

The kill switch retries a dropped WireGuard session a few times. It is not a hard traffic block. Android can still leak during the gap. For a real block, set PhoneKey as Always-on VPN and enable “Block connections without VPN”. That setting is in the system VPN screen, which Settings can open. A userspace blackhole is marked `TODO` in `KillSwitch` because `GoBackend.VpnService.stopSelf()` tears the interface down.

## Not included

No Google Mobile Ads, Facebook, Unity Ads, ironSource, Vungle, AppLovin, MBridge, Fyber, Amazon ads, Huawei ads, Play Billing, Firebase, Crashlytics, analytics, or attribution. No `AD_ID`, no `ACCESS_ADSERVICES_*`, no `QUERY_ALL_PACKAGES`, no `READ_BASIC_PHONE_STATE`, no `WRITE_EXTERNAL_STORAGE`, no `DUMP`.

Keys live in the app’s private files directory (`profiles.json`) and DataStore. Backup is disabled.

## Open in Android Studio

Android Studio Ladybug or newer. JDK 17.

1. Open the `phonekey` directory (the folder that contains `settings.gradle.kts`).
2. Let Gradle sync. The wrapper uses Gradle 8.11.1 and Android Gradle Plugin 8.7.3.
3. Run the `app` configuration on a device or emulator, API 26 or higher. An emulator can install the app and import a config. A real WireGuard handshake needs a reachable endpoint; the bundled example uses the documentation address `203.0.113.1` and will not come up.

`minSdk` 26, `targetSdk` 35, `compileSdk` 35. Kotlin, Jetpack Compose, Material 3.

From a shell, with the Android SDK installed:

```bash
./gradlew :app:assembleDebug
```

## Permissions

`INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_VPN`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `CAMERA`. The VPN service is guarded by `BIND_VPN_SERVICE`. The first connect calls `VpnService.prepare()`.

## Layout

```text
app/src/main/java/com/phonekey/vpn/
  MainActivity.kt            launcher
  ActivateActivity.kt        deep links
  PhoneKeyApp.kt             process start, notification channel, always-on callback
  domain/model               profiles, settings, session
  domain/engine              VpnEngine
  data/parser                WireGuard, Xray JSON, SSTP text
  data/store                 DataStore settings, profiles file
  data/vpn                   WireGuardEngine, XrayEngine, SstpEngine, VpnRepository
  service/                   VpnService, tile, boot receiver, notification actions
  ui/                        Compose screens
```

## Libraries

AndroidX (core, lifecycle, activity, Compose, navigation, DataStore), Kotlin coroutines, `com.wireguard.android:tunnel`, ZXing Android Embedded (`com.journeyapps:zxing-android-embedded:4.3.0`) for scanning, ZXing core (pulled in by that library) for drawing QR codes, Gson, OkHttp (DNS check of the WireGuard endpoint host only). All of those are free and open source.
