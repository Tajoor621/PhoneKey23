# Baseline (Phase 0)

Recorded before the 1.1.0 behavior changes, from the tree at `be973cc` plus a static pass. This environment has JDK 17 and no Android SDK, so `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug`, and the emulator were not run here. `:core:test` is the JVM check that can run without the SDK; its result after the fixes is in the 1.1.0 notes below the original findings.

## Confirmed in the 1.0.2 source

- `ProfileRepository.readQuietly()` caught every exception and returned an empty list, then a later save overwrote `profiles.json`.
- `ConfigTexts.withApplicationFilter` wrote `AllowedApplications`. `com.wireguard.config.Interface` in tunnel 1.0.20260102 parses `includedapplications` and `excludedapplications` only. `javap` on that AAR shows `getIncludedApplications` and `getExcludedApplications`.
- `BundleCodec.import` kept the exported id, so a second import replaced the profile.
- `AppOnBootReceiver` waited on `connectSelectedAndAwait()` inside `goAsync()`.
- `wakeLock()` used `acquire(20_000)` while the handshake wait is 25 seconds.
- `EndpointProbe.unresolvedHost()` called `InetAddress.getAllByName` inside the connect lock.
- `NotificationActionReceiver` handled `ACTION_CONNECT` without `VpnService.prepare`. The notification itself already opened `MainActivity`.
- `ActivateActivity` had an https intent filter and no `android:autoVerify`.
- `ProtocolPolicy.isDocumentationEndpoint` treated `localhost` as a documentation name.
- `.github/workflows/android.yml` called `apksigner` with no `build-tools` directory on `PATH`.
- `minSdk` is 26. `allowBackup` is false. Package id is `com.phonekey.vpn`.

## Native library alignment (tunnel 1.0.20260102)

Checked with `readelf -lW` on the AAR, not on a built APK.

| Library | Align |
| --- | --- |
| arm64-v8a/libwg-go.so, libwg.so, libwg-quick.so | 0x4000 |
| x86_64/libwg-go.so, libwg.so, libwg-quick.so | 0x4000 |
| armeabi-v7a and x86 copies of those three | 0x1000 |

64-bit is 16 KB aligned. 32-bit is not. 16 KB page-size devices are 64-bit, so the 32-bit copies are not loaded there.

## Not run in this environment

- Android lint, debug assemble, instrumented tests, API 26–36 emulators, a 16 KB emulator, a live WireGuard server, and Play Console.
