# SIMple

Android app for Cubacel balance, data bundles, voice minutes, SMS and recharge status.
Kotlin + Jetpack Compose presentation, MVVM, native subscription-aware USSD and on-device parsing.

## What works

- Separate selectable cards for active SIMs, including Dual SIM. SMS must carry a currently active Cubacel subscription ID; unknown historical SIM associations are skipped.
- Read-only Cubacel USSD queries, gated by permissions and the SIM operator code.
- National-recharge availability card: confirmed CUP capacity and carrier-provided reopening date.
- Automatic parsing of recognized Spanish carrier responses and automatic encrypted saving.
- SQLite history with AES-GCM encrypted typed payloads; keys remain in Android Keystore.
- Dashboard and history survive app restarts. No manual balance-entry forms.
- Resource cards show reading age and timestamps; known expired quotas are explicitly marked.
- **Actualizar todo** queries balance, data, minutes, SMS, bonuses and recharge status sequentially
  on the selected SIM, with category progress. **Detener** skips pending queries while allowing
  the in-flight response to be saved. Any query or storage failure stops the sequence without retries.
- Recharge and plan actions open the phone dialer or ETECSA website for user completion.
- Optional sender-filtered SMS import and new-message ingestion after Android permissions.
- No Internet, contacts or identifier permissions; no telemetry or raw carrier logs.

## Limits

Carrier response formats vary. Unknown, promotional, invalid or ambiguous responses are rejected;
they never replace the last valid reading with guessed numbers. Parser coverage is documented in
[the architecture notes](docs/ANDROID_ARCHITECTURE.md) and verified by automated fixtures.
The app receives its own USSD callbacks and, when explicitly enabled, imports supported messages
from fixed Cubacel/ETECSA sender addresses. It does not intercept the dialer's sessions. SMS
permissions must be granted on Android; USSD remains usable without SMS access.

Storage is encrypted at the payload level, rather than full-file SQLite encryption. Random row
IDs, row count and SQLite structure remain visible. No raw responses or hardware subscriber
identifiers are stored. Android subscription IDs are local routing metadata; when replacing a SIM,
clear its local history to avoid associating a reused subscription record with old readings.

## Build and run

Use JDK 17 and Android SDK Platform 36. Open the repository in Android Studio, or set
`ANDROID_HOME` to your SDK and run:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew assembleDebugAndroidTest
```

Minimum Android: API 26. APK: `app/build/outputs/apk/debug/app-debug.apk`.
Install with `adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk`.
Never run carrier queries on an emulator expecting real balances.

The instrumentation test validates parser-to-database persistence, reopening, separate SIM data,
encrypted payloads, and deletion. Run it on a dedicated emulator:

```sh
adb -s <emulator> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <emulator> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <emulator> shell am instrument -w com.simple.mobile.test/androidx.test.runner.AndroidJUnitRunner
```

## Source layout

- `presentation/`: Compose screens, state and Android ViewModel.
- `domain/model/`: immutable typed readings and domain models.
- `data/parser/CubacelParser.java`: bounded carrier grammars and validation.
- `data/telephony/UssdDispatcher.java`: single-flight, subscription-scoped callbacks.
- `data/local/EncryptedReadingStore.kt`: encrypted typed SQLite history.

[Verified carrier codes and sources](docs/CUBACEL_CODES.md).

## SMS synchronization

Tap **Conectar mensajes de saldo** and allow the Android SMS permission request. The app queries
only ETECSA/Cubacel/222/2266 senders (latest 150), matches each message to an active Cubacel
subscription, parses on-device, and saves typed values. New carrier messages use the same parser.
Purchase receipts store purchased quotas separately from measured remaining allowances; a debit,
transfer or credited recharge is never substituted for the current balance. Promotions are rejected.

Disable SMS ingestion in Settings. Deleting local history also disables automatic SMS import to
avoid immediately repopulating deleted history. Existing inbox messages themselves are never changed.
For Play Store distribution, READ_SMS/RECEIVE_SMS eligibility and restricted-permission rules must
be reviewed; sideload availability does not imply Play eligibility.

The recharge parser distinguishes a reached limit from available credit, and treats
“posterior al día D” as available from the following day in Havana time. It never uses
line/package expiry as the recharge date. If the stated date has passed, the card asks
for a fresh query before claiming capacity is available.

Live modem diagnostics are excluded from normal test runs. `LiveUssdProbeTest` requires
explicit `liveCubacel=true`, a subscription ID and an optional enum query list. It accepts
only the read-only catalog, stops on modem failure, never retries, and emits numerically
redacted formats. Run only with the device owner's explicit authorization.
