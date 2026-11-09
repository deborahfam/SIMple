# SIMple Android foundation

## Stack and boundaries

Recommend Kotlin, Jetpack Compose, coroutines/Flow, Room and MVVM for the product.
The repository currently uses native Java 17 and framework Views. This increment keeps
that stack buildable and provides interoperable domain types and a CompletableFuture
query adapter. It does not claim a completed Kotlin migration, ViewModel, or database.

Use these package boundaries (single Gradle app module initially):

```text
com.simple.mobile/
  domain/
    model/           SimAccount, BalanceSnapshot, DataPlanBundle
    query/           CarrierResponseParser
    repository/      planned: SnapshotRepository contract
    usecase/         planned: CheckBalance, ObserveHistory, EstimateDepletion
  data/
    telephony/       UssdDispatcher
    parser/          versioned, carrier-specific parsers
    local/           planned: Room entities, DAOs, migrations, encrypted DB factory
    repository/      planned: repository implementations
    SimRepository    active subscription metadata adapter
  presentation/      planned: dashboard ViewModel, immutable UI state, Compose UI
  MainActivity       current framework-Views dashboard
```

Domain must not depend on Android, Room, or UI. Data implements domain contracts;
presentation depends on use cases. Supply one application-scoped dispatcher and an
owned background executor through dependency injection. Do not retain an Activity.

## Permissions and platform behavior

- `READ_PHONE_STATE`: runtime permission for active subscriptions and per-subscription
  network metadata. Already requested by the dashboard.
- `CALL_PHONE`: runtime permission for `sendUssdRequest`. Declared now, but request it
  in the future query UI only after the user chooses a SIM and confirms the query.
  The dispatcher returns `PERMISSION` until granted; it never prompts from data code.
- `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`: not included. Google Play restricts these
  permissions to eligible default handlers or approved exceptions. A balance tracker
  must not assume eligibility. Start with user-pasted/shared carrier text, parsed
  locally with a source-specific parser. SMS Retriever is not a general inbox reader.
- No `READ_PHONE_NUMBERS`, location, contacts, call-log, storage, accessibility service,
  or Internet permission is needed for the supplied foundation.
- Telephony hardware is optional. Discover active subscriptions using
  `SubscriptionManager`; route every query through `createForSubscriptionId`.
  Refresh on resume, and eventually observe subscription changes while the UI is active.
  Subscription ID and slot are mutable routing metadata, not durable SIM identity.
- `SimRepository.accounts` adds carrier, current data radio technology and SIM state.
  Network type can be unknown; this is not a measurement of speed. Active subscription
  enumeration alone cannot enumerate every absent/locked SIM. Observe physical slot
  state separately if the UI needs to display empty/locked slots.
- API 26+ supplies asynchronous USSD callbacks. Carrier and OEM support varies. This
  is a one-shot request API, not an API for driving arbitrary interactive USSD menus,
  intercepting the dialer's sessions, or reading an SMS delivered later.

## Models

`SimAccount`: app-generated UUID for local history, current subscription ID and slot,
carrier label, optional explicitly selected carrier profile, SIM state, and data
network type. Never infer a carrier query code from display name alone, especially
when roaming or using MVNOs. Ask the user to associate a newly detected SIM with history.

`BalanceSnapshot`: immutable local snapshot UUID, account UUID, received timestamp,
optional decimal money plus ISO currency, optional remaining voice seconds, optional
balance expiry, immutable bundles, source, and parser version. Unknown is null, never
zero. A negative monetary balance can represent carrier debt; a profile controls that
interpretation. No raw response, phone number, IMSI, ICCID, or IMEI is stored.

`DataPlanBundle`: carrier-profile bundle key, display name, optional remaining and total
bytes, explicit unlimited flag, and optional expiry instant. Bundles stay separate:
social-only, night-only, rollover and main quota must not be summed indiscriminately.
The sample parser uses decimal MB/GB; other profiles must explicitly choose decimal or
binary units. Carrier-local inclusive expiry dates become exclusive next-day instants.

## Query and parsing flow

1. UI selects a currently active subscription and a reviewed, read-only carrier query.
   Explain any carrier charges before dispatch. Never run arbitrary response-supplied
   codes or silently execute top-ups, purchases, transfers, or subscription changes.
2. Request permissions in presentation. Call the application-scoped `UssdDispatcher`
   with the account UUID, current subscription ID, verified `CubacelQuery` enum value and a response-specific parser.
3. Dispatcher rechecks permissions and active subscriptions immediately before sending.
   Busy requests fail rather than queueing stale intents across both SIMs.
4. Android callback supplies a response; the executor parses it off the main thread.
   The future yields a typed snapshot or a sanitized failure, without raw text in errors.
5. Persist only accepted typed snapshots in one transaction. Update ViewModel state;
   deliver UI changes on the main thread. Display last successful check time and stale
   status rather than claiming continuous real-time monitoring.

A 30-second local timeout completes the future but does not cancel the modem operation.
Cancelling the future also cannot stop it. Dispatch remains blocked until Android's
callback arrives. If it never arrives, show an unresolved-session state and require
user recovery; do not clear the gate and automatically retry. This conservative gate
is in-process only, so process death still requires an explicit user-driven next check.
No automatic retries: USSD can incur charges or change account state.

`ExampleCarrierParser` is intentionally a strict, synthetic English profile. It accepts:

```text
Balance: USD 12.50; Data: 1.25 GB; Minutes: 30; Expires: 2026-12-01
```

It normalizes Unicode and spacing, caps input size, anchors the complete response,
uses bounded numeric patterns, exact decimal arithmetic, explicit units and currency,
and validates dates. Unrecognized/ambiguous text produces a parse failure, never a
fabricated zero or a partially trusted snapshot. The Cubacel command catalog is verified, but its production response parsers are not implemented
yet; see [Cubacel codes](CUBACEL_CODES.md). Add one parser version per carrier/locale/response variant, with redacted fixtures
for missing fields, menu/error replies, multiple bundles, grouping and decimal separators,
translations, expiry zones and changed wording. Do not run a generic “find all numbers”
regex. Raw text is ephemeral in memory, including framework callback arguments; JVM
strings cannot be reliably wiped. Never send these values to logs or crash reporting.

## Local schema and encryption plan

Persistence is a design here, not an implemented encrypted database. Room alone does
not encrypt SQLite. Use a maintained encrypted SQLite provider compatible with Room,
with a random database key wrapped by an Android Keystore key. Do not hardcode keys or
store a plaintext database passphrase in preferences. Treat lost/invalidated keys as
an explicit recovery/reset state. Disable backup/transfer of database and keys.

Proposed schema:

```sql
CREATE TABLE sim_account (
  id TEXT PRIMARY KEY NOT NULL,
  label TEXT NOT NULL,
  carrier_profile_id TEXT
);
CREATE TABLE balance_snapshot (
  id TEXT PRIMARY KEY NOT NULL,
  account_id TEXT NOT NULL REFERENCES sim_account(id) ON DELETE CASCADE,
  captured_at_ms INTEGER NOT NULL,
  money_decimal TEXT,
  currency_iso TEXT,
  voice_seconds INTEGER CHECK(voice_seconds >= 0),
  balance_expires_at_ms INTEGER,
  source TEXT NOT NULL,
  parser_version TEXT NOT NULL,
  CHECK ((money_decimal IS NULL) = (currency_iso IS NULL))
);
CREATE INDEX snapshot_account_time ON balance_snapshot(account_id, captured_at_ms);
CREATE TABLE data_bundle (
  snapshot_id TEXT NOT NULL REFERENCES balance_snapshot(id) ON DELETE CASCADE,
  bundle_key TEXT NOT NULL,
  display_name TEXT NOT NULL,
  remaining_bytes INTEGER CHECK(remaining_bytes >= 0),
  total_bytes INTEGER CHECK(total_bytes >= 0),
  unlimited INTEGER NOT NULL CHECK(unlimited IN (0,1)),
  expires_at_ms INTEGER,
  PRIMARY KEY(snapshot_id, bundle_key),
  CHECK(remaining_bytes IS NULL OR total_bytes IS NULL OR remaining_bytes <= total_bytes),
  CHECK(unlimited = 0 OR (remaining_bytes IS NULL AND total_bytes IS NULL))
);
```

Enable foreign keys, use transactional inserts and tested migrations, and bound retention
(e.g. user-configurable 90 days). Do not persist subscription IDs as account identity.
Add a cycle identifier before implementing consumption estimation: two snapshots from
separate renewals must never be treated as one continuous quota. Export/delete actions
must be explicit and on-device. No analytics or identifier collection.

## Burn rate and forecast

Within a confirmed bundle and billing cycle, compute positive quota reductions over
valid consecutive intervals, divided by the total interval duration in days. Exclude
resets, top-ups, bundle changes, expired bundles and unknown/unlimited readings; a quota
increase starts a new segment rather than becoming negative consumption. Require at
least two distinct timestamped readings. Label the result an estimate: purchases and
carrier corrections can also change allowances.

Estimated depletion = latest capture time + remaining bytes / bytes-per-day, only for
a positive rate and a sufficiently recent reading. Zero rate or insufficient history
means no estimate. Keep contractual expiry separate and display the earlier of known
contract expiry and estimated depletion as the effective forecast. Never overwrite
carrier expiry with an estimate. Snapshot-based estimates are not live traffic counters.

## Validation

Run `./gradlew assembleDebug lintDebug`. Parser checks can run without Android:

```sh
mkdir -p /tmp/simple-parser-tests
javac -d /tmp/simple-parser-tests app/src/main/java/com/simple/mobile/domain/model/*.java app/src/main/java/com/simple/mobile/domain/query/*.java app/src/main/java/com/simple/mobile/data/parser/*.java tests/CarrierParserTest.java
java -cp /tmp/simple-parser-tests CarrierParserTest
```

Device validation still required: two active SIMs, permission revocation during a query,
SIM removal/swap, unsupported USSD, no service, timeout/late callback, cancellation,
interactive menu responses, process recreation and executor shutdown. Do not use real
subscriber identifiers or raw responses in test reports.

## Platform sources

- https://developer.android.com/reference/android/telephony/TelephonyManager#sendUssdRequest(java.lang.String,android.telephony.TelephonyManager.UssdResponseCallback,android.os.Handler)
- https://developer.android.com/reference/android/telephony/SubscriptionManager
- https://developer.android.com/guide/topics/permissions/default-handlers
- https://support.google.com/googleplay/android-developer/answer/10208820
