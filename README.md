# PhoneKey23

PhoneKey23 is an Android WireGuard VPN client with a separate browser-based profile companion. The **actual VPN tunnel runs only in the Android app**. The website cannot create an Android `VpnService`, establish a WireGuard tunnel, change your public IP, or protect device traffic.

- Android package: `com.phonekey.vpn`
- Android minimum: API 26 (Android 8.0)
- Build: Kotlin, Jetpack Compose, JDK 17
- Website: [PhoneKey23 project page](https://tajoor621.github.io/PhoneKey23/)
- APK releases: [GitHub Releases](https://github.com/Tajoor621/PhoneKey23/releases)

## Protocol status

| Protocol | Android status |
| --- | --- |
| WireGuard | Implemented through the WireGuard GoBackend. Requires your own working server and a valid WireGuard profile. |
| Xray / V2Ray | Not implemented; rejected rather than falsely reporting a connection. |
| SSTP | Not implemented; rejected rather than falsely reporting a connection. |

PhoneKey23 does not provide a VPN server, subscription, account, or hosted endpoint. If you do not already have a WireGuard server, you must obtain or deploy one before a real VPN connection can be tested. Documentation-only IP ranges and placeholder endpoints are rejected.

## Download and install

1. Open [GitHub Releases](https://github.com/Tajoor621/PhoneKey23/releases).
2. Download the APK attached to the latest release. Prefer the signed `PhoneKey23-release.apk` asset.
3. Install it on Android. If Android reports that an existing app cannot be updated, check that the installed app has the same package ID and signing certificate; do not uninstall or clear app data until you have backed up any profiles.
4. Open PhoneKey23, import a valid WireGuard `.conf` or scan its QR code, approve Android's VPN permission prompt, and connect.

The website is a companion and documentation page, not a browser VPN. The browser companion can manage/test profile UI, but any browser-side session preview is simulated and does not route traffic.

## Build Android locally

Open the `phonekey` directory in Android Studio with JDK 17, or run:

```bash
cd phonekey
./gradlew :core:test
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

The GitHub Actions pipeline runs core tests, web typecheck/tests/build, Android Compose UI tests on an emulator, and Android unit tests before the debug APK artifact is produced. Version tags publish a **signed release APK only** after all required jobs pass and all signing secrets are present.

Required GitHub Actions secrets for signed releases:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

The signing keystore must be securely retained by the owner. Losing it can prevent future in-place updates for users with the existing app installed. Never commit the keystore or passwords.

## Android security and behavior

- WireGuard private-key profile data is encrypted using AES-GCM with a key held in Android Keystore.
- Backup and device transfer of app data are disabled.
- Camera access is requested only when the QR scanner is opened.
- Split tunneling uses the Android app/package controls described in the Android project documentation.
- The in-app kill-switch behavior is process-lifetime limited. For a persistent block, enable PhoneKey as Android Always-on VPN and turn on **Block connections without VPN** in system VPN settings.

## Website

The GitHub Pages site under `docs/` provides project information and links to GitHub Releases. The React companion is a separate browser-based profile manager; it does not have access to Android's VPN service.

## License

[Apache License 2.0](LICENSE). The WireGuard tunnel library is also Apache-2.0.
