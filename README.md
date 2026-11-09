# SIMple

An Android app foundation for tracking mobile balance, active data plans, and remaining allowances.

## Included

- Native Java 17 Android application (Android 8.0 / API 26 and newer).
- Scrollable dashboard with balance and allowance integration placeholders.
- Active SIM and multi-SIM discovery using Android's `SubscriptionManager`.
- Phone permission requested only when the user taps **Find my SIMs**.
- Permission denial, settings recovery, no SIM, and unsupported device states.
- Local device access only; no server, analytics, or network permission. The USSD adapter additionally requires user-granted CALL_PHONE permission.

The domain models, asynchronous USSD adapter and verified Cubacel query catalog are now included. See [Android architecture](docs/ANDROID_ARCHITECTURE.md) and [Cubacel codes and sources](docs/CUBACEL_CODES.md). The dashboard does not yet invoke the query adapter.

Live balances and carrier plan allowances are **not implemented**. Standard Android subscription APIs expose SIM metadata, not prepaid balances or remaining carrier allowances. Future work needs a supported carrier API or an explicitly authorized carrier-specific integration. No sample amounts are presented as real data.

## Run

1. Open this repository in Android Studio.
2. Install Android SDK Platform 36 and use JDK 17.
3. Let Gradle sync, then run the `app` configuration on a device or emulator.

For command-line builds, set `ANDROID_HOME` to your Android SDK directory (or set `sdk.dir` in an untracked `local.properties`):

```sh
./gradlew assembleDebug
./gradlew lintDebug
./gradlew installDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Structure

- `app/src/main/java/com/simple/mobile/MainActivity.java`: dashboard and permission flow.
- `app/src/main/java/com/simple/mobile/data/SimRepository.java`: device subscription access.
- `app/src/main/res/`: text, theme, and launcher artwork.
- `app/build.gradle.kts`: application ID, SDK levels, and release configuration.

The starter application ID is `com.simple.mobile`; choose your own unique ID before publishing. Release signing is intentionally left to your own keystore configuration.

## Device verification

On a physical phone, verify permission grant and denial, return from app settings, no active SIM, and multiple active SIMs where supported. An emulator usually has no active subscriptions. Rotate the device and increase system font size to check the scrollable layout.

## Platform references

- [Android Gradle Plugin 8.11 compatibility](https://developer.android.com/build/releases/agp-8-11-0-release-notes)
- [Android subscription API](https://developer.android.com/reference/android/telephony/SubscriptionManager)
