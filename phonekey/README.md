# PhoneKey for Android

Free WireGuard client. Package `com.phonekey.vpn`. No account, no subscription, no ad SDK, no analytics SDK.

The project overview, the web companion, and the live page are in the [repository README](../README.md). This file is the Android build and behavior notes.

## What it does

- Foreground `VpnService` (`PhoneKeyVpnService`) driven by the official WireGuard userspace tunnel (`com.wireguard.android:tunnel`).
- Connection state on a `StateFlow`: Disconnected, Connecting, Connected, Error. The notification shows upload and download rate and a disconnect action. The home screen shows the same rates above the power button.
- Quick Settings tile (`QuickTileService`) toggles the selected profile. It is unavailable until a WireGuard profile is selected.
- Boot receiver (`AppOnBootReceiver`) connects after boot or an update when “Auto-connect on boot” is on and VPN permission was already granted.
- Always-on VPN starts `PhoneKeyVpnService` without the in-app connect token. That path connects the selected profile.
- Profiles: add, edit, delete, select. Import a WireGuard `.conf` from a file, a QR code, or a form. Export one profile as a QR code, or the whole list as JSON. The JSON contains private keys.
- Xray JSON and SSTP text are recognized and refused. They cannot be selected.
- Documentation endpoints (`203.0.113.0/24` and the other reserved example ranges) cannot be saved or connected. PhoneKey does not ship a server.
- Deep links `phonekey://activate` and `https://tajoor621.github.io/PhoneKey23/activate` open a welcome screen. No license check.

## Protocols

| Protocol | Behavior |
| --- | --- |
| WireGuard | Parses a `.conf`, builds the tunnel with `GoBackend`, and reports `wg` byte counters. |
| Xray / V2Ray | Not implemented. `ProtocolPolicy` blocks selection. `XrayEngine.connect` throws if something calls it anyway. |
| SSTP | Same refusal. See `SstpEngine`. |

`VpnEngine` is the extension point. `EngineFactory` still returns the refusing engines so a stale profile cannot open a tunnel by accident.

## Kill switch

The setting reconnects a dropped WireGuard session up to three times. If the VPN service is still running, PhoneKey also calls `VpnService.Builder.establish()` with routes for all IPv4 and IPv6 and no app forwarding. Packets that enter that interface are not sent anywhere. The interface is released on disconnect or when the process dies.

That is not a reboot-safe kill switch. Android tears the interface down with the process. For a durable block, Settings has **Open system VPN settings**. Set PhoneKey as Always-on VPN and enable “Block connections without VPN”.

## Keys

`ProfileCipher` seals each profile’s config text with AES-GCM. The key is generated in the Android Keystore (`phonekey.profile.v1`) and is not written to `profiles.json`. Older plaintext files are sealed on the next read. Backup is disabled, so a reinstall loses the keystore key and cannot decrypt an old file copied by hand.

## Split tunneling

Launcher-visible apps are listed without `QUERY_ALL_PACKAGES`. Search and multi-select are in Settings. Package names can still be typed. “Only selected apps use the VPN” writes `AllowedApplications`. Off, the same list is `ExcludedApplications`. The two keys are never written together.

## Permissions

| Permission | Why |
| --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE` | The tunnel and a DNS check of the endpoint host. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | The ongoing VPN notification on API 34+. The public SDK this project compiles against does not accept `foregroundServiceType="vpn"`, so the service is `specialUse` with subtype `vpn`. It is still a `VpnService`. |
| `POST_NOTIFICATIONS` | Asked when the main screen first opens, so the status notification can be posted. |
| `RECEIVE_BOOT_COMPLETED` | Auto-connect on boot, only if that setting is on. |
| `WAKE_LOCK` | A short partial wake lock while connect runs. |
| `CAMERA` | Asked only when the QR scanner is opened. The feature is optional. |

The VPN service is guarded by `BIND_VPN_SERVICE`. The first connect calls `VpnService.prepare()`.

## Open in Android Studio

Android Studio Ladybug or newer. JDK 17.

1. Open the `phonekey` directory (the folder that contains `settings.gradle.kts`).
2. Let Gradle sync.
3. Run `app` on API 26 or higher.

```bash
./gradlew :core:test
./gradlew :app:assembleDebug
```

`:core:test` is plain JVM and covers WireGuard parsing, the documentation-endpoint refusal, Xray/SSTP refusal, the profile-list codec, and settings decoding. `ConnectFlowTest` is an instrumented Compose test. CI runs it on an API 30 emulator (`connectedDebugAndroidTest`).

Release signing reads `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` from the environment. Without them, `assembleRelease` is unsigned and CI publishes the debug APK. The workflow installs platform 35 with the runner’s `sdkmanager`. It does not use `android-actions/setup-android`, which fails while installing the removed `tools` package.

## Layout

```text
core/                          JVM module: parser, models, policy, codecs, unit tests
app/src/main/java/com/phonekey/vpn/
  MainActivity.kt              launcher
  ActivateActivity.kt          deep links
  PhoneKeyApp.kt               process start, notification channel
  data/store                   DataStore settings, encrypted profiles, installed-app list
  data/vpn                     WireGuardEngine, refusing Xray/SSTP engines, VpnRepository
  service/                     VpnService, tile, boot receiver, notification actions
  ui/                          Compose screens
```

## Libraries

AndroidX (core, lifecycle, activity, Compose, navigation, DataStore), Kotlin coroutines, `com.wireguard.android:tunnel`, ZXing Android Embedded 4.3.0, Gson, OkHttp (DNS check of the WireGuard endpoint host only). All of those are free and open source.

## Not included

No Google Mobile Ads, Facebook, Unity Ads, ironSource, Vungle, AppLovin, Play Billing, Firebase, Crashlytics, analytics, or attribution. No `AD_ID`, no `ACCESS_ADSERVICES_*`, no `QUERY_ALL_PACKAGES`, no `READ_BASIC_PHONE_STATE`, no `WRITE_EXTERNAL_STORAGE`, no `DUMP`.
