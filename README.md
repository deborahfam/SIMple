# SIMple

Android app for Cubacel balance, data bundles, voice minutes, SMS and recharge status.
Kotlin + Jetpack Compose presentation, MVVM, native subscription-aware USSD and on-device parsing.

## Download and update

Download the APK from [GitHub Releases](https://github.com/deborahfam/SIMple/releases/latest).
Choose the **SIMple-N.apk** asset in the latest release, open it on Android, and confirm the update.
Existing SIMple users can also use **Ajustes → Actualizar SIMple → Buscar actualización**.
This build uses the same signing key as the existing locally installed builds, so Android can
update the app in place while preserving saved readings and recipients. Do not uninstall first.

## What works

- Separate selectable cards for active SIMs, including Dual SIM. SMS must carry a currently active Cubacel subscription ID; unknown historical SIM associations are skipped.
- Read-only Cubacel USSD queries, gated by permissions and the SIM operator code.
- National-recharge availability card: confirmed CUP capacity and carrier-provided reopening date.
- Automatic parsing of recognized Spanish carrier responses and automatic encrypted saving.
- SQLite history with AES-GCM encrypted typed payloads; keys remain in Android Keystore.
- Dashboard and history survive app restarts. No manual balance-entry forms.
- Resource cards show reading age and timestamps; known expired quotas are explicitly marked.
- Each resource shows its own loading state, scoped to the SIM being queried.
- Dashboard notices highlight low resources, known expiries within 24 hours, expired allowances,
  and missing/stale readings for the selected SIM. Low-resource claims require readings newer than 24 hours.
- Resource trends compare the last two measured readings per category within seven days. They show
  observed changes, not continuous consumption or forecasts. Known purchases/recharges, increases,
  and expiry changes suppress reduction estimates; unlimited data is not compared to finite quotas.
- **Comprar planes** selects and reviews four data/combo offers, then sends one subscription-scoped purchase USSD only after **Confirmar compra**. The full chained codes are owner-supplied and have not been validated by a completed live purchase; carrier responses are shown without assuming success.
- Optional local reminders cover known expiry dates, recharge reopening and low resources.
- **Actualizar todo** queries balance, data, minutes, SMS, bonuses and recharge status sequentially
  on the selected SIM, with category progress. **Detener** skips pending queries while allowing
  the in-flight response to be saved. Any query or storage failure stops the sequence without retries.
- Plan purchases use a separate allowlisted native USSD action. Recargar uses native Metropolitano authentication and recharge requests, with optional bank response reading.
- Optional sender-filtered SMS import and new-message ingestion after Android permissions.
- Optional single-contact picker without address-book permission. Saved recipients and transfer preparation records remain encrypted locally.
- No contacts or identifier permissions; no telemetry or raw carrier logs. Internet is used only for GitHub update checks (manual or optional once daily on app open) and user-requested APK downloads.

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

Enable the switch in **Mensajes de Cubacel** and allow the Android SMS permission request. The app queries
only ETECSA/Cubacel/222/2266 senders (initial review: latest 150; subsequent reviews: new provider IDs), matches each message to an active Cubacel
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

## Local reminders

Enable **Ajustes → Recordatorios locales** and allow notifications when Android asks.
Reminders are off by default. The app uses only saved readings and never queries USSD in the
background. Notification details are hidden in the default lock-screen public version.

- Expiry: approximately 24 hours before an explicit carrier expiry; no estimated first-use dates.
- Recharge: at the carrier-provided reopening date, prompting a fresh query rather than claiming
  recharge capacity is available.
- Low resources: a new reading after opt-in at or below 20 CUP, 100 MB of general Internet,
  5 voice minutes or 5 SMS. Restricted/unlimited data buckets do not trigger general-data warnings.
  Readings older than 24 hours and expired quotas do not trigger low-resource alerts.
- Repeated low readings are suppressed until a newer reading shows recovery. Reminders are
  scoped to active Cubacel SIMs; changing readings reschedules outstanding reminders.
- Turning reminders off cancels pending and visible notifications. Clearing history also removes
  reminders based on those readings. Schedules are rebuilt after restart, reboot and app updates.

Reminders use Android inexact alarms, so battery-saving modes may delay them; no exact-alarm
permission is requested. See [Android alarm delivery behavior](https://developer.android.com/develop/background-work/services/alarms).
Android notification settings can block delivery even while the app switch is on; Settings
shows this state and links to Android notification controls. Force-stopping the app requires
opening it again before reminders can resume.

## Managing saved lines

Active SIMs and lines with saved history appear in the same selector. Removed SIMs are labeled
**Guardada · solo historial** and cannot receive USSD queries. **Cambiar nombre** saves an encrypted
nickname (up to 40 characters); an empty name restores the default. Names use the same local Android
subscription association as readings, not a permanent hardware identity.

**Eliminar datos de esta línea** removes only that line's readings and nickname after confirmation.
Other lines are preserved. The global deletion action remains in Settings. Both actions disable SMS
import to prevent immediate repopulation; explicitly reconnecting SMS can import inbox history again.
Deletion is unavailable during a USSD query. Database version 2 adds encrypted nickname storage
without rewriting existing readings.

The SMS panel shows connection and permission status, last successful sync time, and newly inserted
readings (duplicates excluded). Turning import off invalidates queued work. Failed syncs preserve
the last successful summary. **Sincronizar ahora** is available when import and read permissions are enabled.

History supports category filters, rolling 24-hour/7-day/30-day windows, and **Cargar más** in
25-reading increments. Filters apply to the selected line; storage still retains at most 300
readings globally. A requested query or refresh-all resumes after phone permissions are granted,
including a return from Android Settings. The original SIM is retained and the pending action
is consumed once; removing that SIM requires choosing a line and requesting the query again.

## Accessibility and recovery

The dashboard follows Android light/dark mode. Small labels now use at least 12sp, and resource
and action pairs stack on narrow screens or when system font scale exceeds 1.2. Section labels
wrap vertically instead of competing for horizontal space.

Database version 3 marks individually unreadable encrypted readings. Readable history remains
available with a persistent warning and a retry action. Detected damaged rows retain their original
ciphertext and are excluded from the normal 300-reading pruning limit. Retrying reads them again;
this cannot repair damaged ciphertext or restore a lost encryption key. Whole-database or Keystore
failures still report storage unavailable. Explicit deletion of all local data remains destructive;
per-line deletion fails safely if a damaged record's SIM association cannot be read.

## Updating from the repository

**Ajustes → Actualizar SIMple** checks the latest public stable GitHub Release for
`deborahfam/SIMple`. It offers a download only for an asset named `SIMple-N.apk`, where N is a
higher Android versionCode. After downloading, it checks the package ID, exact versionCode,
and signing certificate against the installed app. Android then asks the user to approve
installation; if necessary, enable installation from SIMple and tap Install again.
No balance, SMS or history content is sent to GitHub. Checks are manual by default; Settings offers an optional once-daily check on app open with an in-app update notice.

### Automatic publication

Pushing application, build, release-script or workflow changes to `main` starts
[Publish Android update](https://github.com/deborahfam/SIMple/actions/workflows/publish-update.yml).
Documentation and Pencil-only changes do not publish an APK; implement designs in the app first.
You can also run the workflow manually from GitHub Actions on `main`.

The workflow runs versioning tests, Android unit tests and lint, builds an APK, signs it with
the existing installation key, verifies the certificate, and publishes a stable GitHub Release
with `SIMple-N.apk` and `SHA256SUMS.txt`. Failed checks never publish an update.
Android versionCode and the patch version increase automatically from existing releases;
a higher base version in `app/build.gradle.kts` can start a new minor/major version.
Generated versions are injected through Gradle properties without creating a commit loop.

Publication is serialized. If several pushes arrive close together, GitHub may replace queued
runs; stale builds skip publication so the latest source is published. Rerunning a published
commit does not create another release. Failed draft uploads can be resumed with **Re-run jobs**.
The release body records the exact source commit and reserved Android version code.

Repository Actions secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. The public certificate fingerprint is stored in
`ANDROID_SIGNING_CERT_SHA256` (Actions variable). The signing key is restored only after tests,
kept outside the checkout and Gradle cache, and removed after signing. Never commit this key.
Only trusted maintainers should be allowed to push workflows to `main`.

This automates **GitHub publication**, not SMS/balance synchronization or silent installation.
After the workflow finishes, use **Ajustes → Actualizar SIMple → Buscar actualización**;
Android still asks for installation confirmation. Merely committing locally does not publish.

For a local build with a specific published version:

```sh
./gradlew packageDebugUpdate -PreleaseVersionCode=8 -PreleaseVersionName=0.5.2
```

Use the same signing key as the installed app. Local default versions remain the source base
version; official automatic release versions are recorded in GitHub Releases.

Production releases should instead use a protected, stable release signing key and upload the
signed release APK with the same naming convention. Never commit a signing key. A debug APK
cannot update a release-signed installation, or vice versa. Do not uninstall to bypass a signature
mismatch: doing so removes local history and its encryption key. Signing-key rotation is not
supported by this updater's conservative certificate equality check.

A private repository or one without a public stable release returns a helpful unavailable message.
The repository source itself is not installable: it must first be built into a signed APK. The
initial version containing the updater must be installed manually. Android may require allowing
this app to install packages: [Android package installation permission](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()).

## Compact dashboard (0.5.0)

Home groups the selected SIM’s balance and four compact resource rows in one card. Bottom navigation uses 12sp labels, consistent 24dp icons and a small selected-tab marker; large text or very narrow screens allow horizontal scrolling instead of compressing the labels.
Resource rows align labels and remaining amounts, with expiry (or reading age when expiry is unknown) below. Tap a row to refresh that resource; its icon becomes a loading indicator. Narrow layouts and large fonts stack the label and amount. Exact timestamps and package breakdowns are available under **Paquetes y lecturas**. Refresh all lives inside the
balance card; the smaller refresh icon queries only balance. Line renaming and deletion are in
Settings. SMS connection is summarized on Home with a link to Settings, and package breakdowns
expand on demand. Bank notices live under **Avisos bancarios**, with the latest rejection and storage errors visible in the summary. Reminder rules, privacy details and advanced SMS import controls use collapsed sections in Settings. Query progress, expiry warnings and damaged-reading notices remain visible.

## Saved recipients and balance transfers

**Transferir saldo** prepares a recipient and amount, shows the sending SIM and a review,
then opens the interactive `*234#` dialer menu. The user enters the recipient, amount and
PIN in the carrier menu and chooses the SIM there; SIMple does not submit a transfer or
store a PIN. The number can be copied explicitly from the review. Published reference
fee: 5 CUP, with up to three daily transfers (ETECSA verified November 16, 2026);
the user must confirm current carrier conditions.

Save up to 50 recipients with nicknames and favorites; edit or delete them in the transfer
screen, also accessible from Settings. Android's phone-contact picker reads only the selected
contact, without requesting READ_CONTACTS. Recipient numbers and names are stored in
AES-GCM encrypted database rows. Database schema 4 migrates existing readings in place.

History retains up to 100 transfer preparations, scoped to the selected SIM, with separate
prepared, menu-opened, open-failed and user-reported outcome states. User confirmation is
explicitly labelled as a personal annotation, never carrier verification. Clearing a line
removes its transfer records but retains shared recipients; clearing all app data also removes
recipients. Returning from a carrier menu or recharge website offers a refresh for the original
SIM; no automatic carrier queries are sent.

## Statistics and recharge planning

**Estadísticas** shows reductions in balance, general Internet, voice and SMS over the last
7 or 30 days for the selected SIM. These are partial observations from retained readings,
not an itemized bill: transfers, unobserved purchases and carrier adjustments may affect them.
Known purchases/recharges, quota increases, expiry changes, unknown/unlimited quotas and
expired intervals are excluded. The UI shows comparable and excluded interval counts.

Choose a recharge date separately for each SIM; otherwise the latest carrier recharge date
is used. Personal dates are encrypted in the existing line profile and survive renaming.
Deleting a line removes its date; clearing all local data removes all dates. The daily data
budget divides the latest general Internet quota by Havana calendar days until the target
(including today, excluding recharge day). Stale readings, expired packages or packages
expiring before the target cannot produce a budget; unknown validity is explicitly labeled.
It is a suggested upper limit from the last query, not live remaining usage.

**Datos móviles por aplicación** requires optional Android Usage Access. It queries Android's
NetworkStatsManager off the main thread with a null subscriber ID: all mobile subscriptions
combined, excluding Wi-Fi. It never attributes this total to the selected SIM or reads an IMSI.
Counters may lag or differ from carrier billing. Only launcher-app visibility is requested;
unresolved UIDs are shown as application/service identifiers. Shared UIDs appear together.
The results remain in memory, are refreshed on entering/resuming Statistics, and are not uploaded.

Local reminders must be enabled separately. Personal recharge dates notify at/after 09:00
America/Havana, once per date for each active Cubacel SIM, and expire at the next midnight.
The app also shows a recharge-day card on Home. Personal dates override carrier-date reminders;
Android may delay delivery, and dates do not guarantee carrier recharge availability.

Settings also offers **Avisar de nuevas versiones al abrir**, disabled by default. When enabled,
SIMple checks public GitHub Releases at most once per 24 hours on app open and shows an in-app
notice. Downloads and Android installation still require user action. This is not a background
notification service.

## Faster refresh and Transfermóvil (0.6.1)

- **Actualizar lo necesario** queries missing, expired or at-least-15-minute-old resources for
  the selected SIM. Detected purchases/recharges also invalidate older measurements. The
  separate **Actualizar todo de nuevo** action always forces the six read-only carrier queries.
- Failed/stopped sequences retain their uncompleted queries in memory, separately for each SIM.
  **Reintentar pendientes** only runs those queries. Modem calls remain serialized; a timeout
  never triggers an automatic retry, and an ongoing modem session can still report busy.
- SMS import saves a provider-ID checkpoint scoped to the active Cubacel subscription set.
  Initial/manual re-scans cover the latest 150 matching messages; incremental runs process
  up to 150 new IDs in ascending order and show when another batch remains. A changed SIM set
  starts a fresh review. **Revisar últimos 150 mensajes** also handles restored/reassigned
  provider IDs and late corrections. Clearing local history resets the checkpoint. Cancellation
  or a failed pass cannot advance it. The message timestamp is still used for reading freshness.
- Duplicate SMS are rejected by indexed ID before encryption/decryption or retention work.
  Full damage classification before saving is only necessary when retention may delete records.
  History reads reuse one Keystore key handle and update recovery flags only when needed.
  SIM/history filters reuse the in-memory readings; empty SMS syncs do not reload all history.

## Metropolitano recharge inside SIMple

**Recargar** uses a full-screen step flow: bank, destination/amount, review/authentication,
and request result. It sends native USSD on the selected active Cubacel SIM. This is a real
bank operation, not the former simulator. Only Metropolitano's associated CUP account and
integer amounts from 1 to 360 CUP are supported; the operator enforces available quota.

Authentication and debit require separate actions. The user enters the four-digit bank PIN,
then checks the PAGOxMOVIL authentication confirmation before explicitly confirming the
reviewed amount and destination. Review expires after five minutes. The PIN is only persisted when explicitly requested, encrypted with an Android Keystore key scoped to the paying SIM. Opting in allows automatic PIN loading without further device authentication; existing authentication-bound PINs require a one-time unlock to migrate; deleting the line also removes its saved PIN. Unlocking fills the form without sending any bank request;
the screen blocks screenshots. Each intent is encrypted with Android Keystore before dispatch.
Restarts and uncertain responses never trigger a retry. A USSD response is displayed verbatim
and is not interpreted as payment success or a reason to deduct a local balance.

The numeric encoding was checked offline against 600 vectors from the unmodified codec in
[ETECSA's official Transfermóvil 1.260416 APK](https://www.etecsa.cu/es/aplicaciones/transfermovil),
covering all 100 random seeds. Reference APK SHA-256:
`b18475eb6911209d21e99d72e6ca888b29236a5b1e470acc70238e05a44f160a`.
These tests verify encoding, not bank acceptance. No live bank authentication or recharge
was performed during development validation. The bank protocol can change independently.
Legacy simulator classes remain as isolated test fixtures and have no screen entry point.

## Bank recharge responses (0.6.3)

**Recargar → Avisos bancarios → Leer avisos de PAGOxMOVIL** explicitly opts into a separate sender in the existing
permission-controlled SMS connection. Inbox review and incoming multipart SMS both recognize the
observed 30-day-quota rejection, including international `53` prefixes, and show the destination,
quota and exact “after” date inside Recargar. The quota is never treated as a debit or requested
amount; a processing acknowledgement is not a successful payment. Unknown formats are ignored.

Consent changes invalidate queued imports and reset the sender-scoped checkpoint. Late replies
are imported without a five-minute cutoff. Up to 30 parsed notices are AES-GCM encrypted separately
with Android Keystore; raw SMS bodies and PINs are not retained. Broadcast/inbox duplicates use a
canonical fingerprint. The latest notice is shown for the **receiving** SIM (expand to see up to three), with the destination
explicitly displayed: receiving a rejection does not establish that its destination is that SIM.
Deleting a line removes its received notices; clearing all local data removes all notices. Opting
out stops further imports and preserves previously saved notices.

This connects real **response reading**, not real bank authentication or recharge submission.
There is no verified live submission adapter yet. SIMple does not send, retry, settle or automatically
attribute a bank transaction from these SMS; sender text alone is not cryptographic authentication.
The existing offline recharge simulator remains clearly separate.

## Native plan selection and purchase

**Comprar planes** presents the four entries observed under `*133# → 1 Datos → 4 Planes`:
4.5 GB for 240 CUP; 2 GB + 15 min + 20 SMS for 120 CUP; 4 GB + 35 min + 40 SMS for
240 CUP; and 6 GB + 60 min + 70 SMS for 360 CUP. The daily plan is outside this menu
and is not included. Published allowances include 300 MB of national navigation and
35 days from first use. Prices are a dated catalog, not a live quote.

The full-screen selector leads to a review of the original SIM, resources and charge.
Only **Confirmar compra** sends `*133*1*4*option*1#`, using the fixed catalog options
1–4. These chained codes were supplied/authorized by the owner. The live inspection
verified the menu and the first plan's final confirmation prompt, but deliberately did
not send the final `1`; no successful chained purchase has been verified. The review
states this limitation. Never test these purchase commands without explicit authorization
to incur the displayed charge.

A separate `purchasePlan` entry point shares the application-wide USSD dispatcher with
read-only queries. It checks permissions, active subscription and Cubacel operator code,
and accepts only catalog IDs. There is no arbitrary-code UI, dialer redirect, automatic
retry, SIM fallback, or automatic sending on permission return. Users review and confirm again.

An AES-GCM encrypted last-attempt record is committed before dispatch. Double taps,
closing/reopening the screen, and process recreation cannot replay it. Restored in-flight
attempts and network/timeouts are uncertain, not failed purchases. The application-scoped
controller continues while the UI is closed. A response is displayed in memory only, under
screenshot protection, and is not classified as proof of purchase. No raw reply is stored
or logged, and no balance is locally decremented. **Consultar saldo y recursos** is a
separate user action. Users must acknowledge reviewing the outcome before starting a new
purchase. Deleting local line/all history clears the corresponding attempt; deletion is
blocked while a purchase is in flight.

Unit tests cover the exact command mapping, duplicate submission, storage failure,
restart recovery, timeout uncertainty and pre-dispatch failure. Emulator instrumentation
checks encrypted persistence and tamper rejection. These checks do not execute carrier purchases.

Source: https://www.etecsa.cu/es/movil/planes-personales
Published price image: https://www.etecsa.cu/sites/default/files/inline-images/1.%20900X510.jpg
