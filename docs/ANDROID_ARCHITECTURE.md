# Android architecture

## Implementation

The presentation layer uses Kotlin, Jetpack Compose Material 3 and an Android ViewModel.
The ViewModel exposes immutable state through StateFlow. UI collection follows lifecycle;
queries survive rotation. A shared application-scoped storage worker serializes SMS and USSD writes; StateFlow publishes updated history to the ViewModel. The domain remains Android-free and interoperates through Java records.
Data adapters own telephony, parsing and encrypted SQLite. Parsing and database I/O run on a
single background worker, never the UI thread.

Flow: selected SIM → permission → verified query enum → native USSD callback → command-specific
parser → encrypted transaction → reloaded typed history → dashboard. The UI has no manual-entry
path. A parse/network/storage error preserves the previous successful state.

## Permissions and device routing

`READ_PHONE_STATE` discovers subscriptions and per-SIM metadata. `CALL_PHONE` executes USSD.
The UI requests these permissions; the dispatcher rechecks them and verifies the active
subscription immediately before sending. Only Cubacel SIM operator `36801` is accepted.
No IMSI, ICCID, IMEI or phone number is collected.

One application-scoped dispatcher permits a single modem session across both SIMs. A local
30-second timeout does not cancel the modem session. The gate stays closed until the native
callback arrives; no automatic retries, SIM switching or hidden fallback calls are made.
The adapter receives only its own one-shot requests, not dialer sessions or interactive menus.

SMS synchronization is optional and asks Android for READ_SMS/RECEIVE_SMS permission.
The content-provider query restricts senders before loading bodies, caps imports at 150 recent
messages, validates a currently active Cubacel subscription, and rejects unknown SIM associations.
The protected SMS_RECEIVED broadcast handles future messages only while the feature is enabled.
Multipart messages are joined in memory. Carrier sender names are not cryptographic proof of
origin; no parsed SMS ever triggers a purchase, transfer or call. Promotions are rejected.
No accessibility service, notification scraping, outgoing SMS or default-SMS-role takeover is used.
Play distribution requires separate SMS-permission eligibility review. If Android denies SMS
permission, the app explains the limitation and retains USSD functionality.

## Parser contract

`CarrierReading` carries query type, received timestamp, optional balance in CUP, voice seconds,
SMS count, separate data buckets, optional expiry and separate recharge-limit fields. Unknown
is null, distinct from zero and unlimited. A query result only replaces the same query category;
bonus resources never overwrite the main balance or general Internet quota.

The bounded Spanish parser normalizes accents and Unicode spacing, validates explicit units and
currency, uses exact decimal arithmetic, and rejects duplicate/ambiguous buckets. MB/GB are decimal
units. Dot and comma decimal forms are supported; mixed grouping formats are rejected. Main money
requires a balance label plus CUP/MN, rejecting legacy CUC. Dates accept validated DMY/ISO forms, including the observed two-digit Cubacel years (mapped to 2000–2099) and become an exclusive next-day instant in `America/Havana`, including DST.
National, LTE, night, mail, toDus, WhatsApp and Facebook data stay separate. Promotion and error
messages are rejected, not imported as purchased allowances. Conflicting or unknown unlimited
restrictions also fail parsing rather than falsely displaying unrestricted data.

USSD fixtures are synthetic; SMS receipt fixtures reproduce structures observed on the user-authorized device, with invented amounts and redacted numbers. The carrier's website publishes query codes, not an exhaustive grammar. Do not claim every Cubacel format is
supported until it has been observed and tested. Raw carrier text is held transiently in memory
and is never persisted, added to exception messages or sent to telemetry. JVM strings cannot be
reliably wiped. The app has no network permission.

## Persistence and privacy

`EncryptedReadingStore` stores only random row IDs, random IVs and AES-GCM ciphertext in SQLite.
The authenticated payload contains only typed values, a parser version and local subscription
routing ID. Each row ID is authenticated as AAD to detect payload swapping. Keys are generated
and retained by Android Keystore. This is payload encryption, not whole-database SQLCipher;
row counts, SQLite structure and opaque IDs remain visible. SMS event IDs derive from subscription, timestamp and an ephemeral body digest for idempotence; the body itself is never saved. The schema and key alias are versioned.
No destructive migration or silent reset occurs on an unreadable key/database.

Latest state is derived per subscription and query category. History is bounded to 300 total
snapshots; inserts and retention pruning are transactional. Backup and device-transfer rules
exclude database/root/preferences, and backup is disabled. Deleting history requires confirmation.
Subscription IDs can change or be reused; the app does not claim they are durable SIM identities.
After a physical SIM replacement, clear the old local history. A future account-association flow
can improve this without reading subscriber identifiers.

## Validation

`CubacelParserTest`: amounts, comma decimals, zero, minutes/seconds, SMS, separate restricted
buckets, unlimited resources, expiry, recharge limits, promotions, errors, malformed inputs.
`EncryptedStoreTest`: recognized response → encrypted SQLite → close/reopen → typed reading,
SIM separation, ciphertext inspection, and deletion. This test runs on a dedicated emulator only.

Remaining device checks include actual Cubacel response variants, modem timeout/late callbacks,
permissions revoked mid-session, SIM replacement and carrier-specific sender/subscription metadata on older SMS records. Burn-rate estimation remains future work; no unvalidated forecast is shown.

Sources:
- https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- https://developer.android.com/reference/android/telephony/TelephonyManager
- https://developer.android.com/guide/topics/permissions/default-handlers
- https://www.itu.int/dms_pub/itu-t/opb/sp/T-SP-E.212B-2023-PDF-S.pdf

### Observed SMS receipt semantics

- Current ETECSA plan receipts: parse `Saldo actual`, not `Se desconto`; save bought data/minutes/SMS
  and first-use validity as a purchase, not as an up-to-date remaining allowance.
- Older `Ha comprado un plan...` and `Ha recibido Plan...` variants: preserve separately labelled
  general/LTE purchase totals. Unlabelled monetary amounts before 2021 are not assumed to be CUP.
- Transfer notices: parse `Saldo principal` separately from the transferred amount; keep the
  active-until date and final line-closure date distinct.
- Recharge acknowledgements: record credited CUP without guessing the resulting total balance.
- Failed transfers, credit advertisements and promotion announcements do not update readings.

The database is sorted by carrier receipt timestamp, so importing an older SMS does not replace
a newer measured balance. Event IDs make repeated imports idempotent. Source (SMS/USSD), purchase
metadata and dates survive encryption/reopening. Deleting history disables automatic import.

### Live USSD formats and recharge reopening

Authorized testing on the phone confirmed the summary grammar `Saldo`, `Datos`, `Voz` as
HH:MM:SS, prefix-labelled SMS, separate active-until/closure dates, `Datos.cu` with short-year
expiry, and the national-recharge limit response. The parser converts the full voice clock;
it does not mistake its final segment for a count of minutes. Restricted `.cu` data remains
separate from general Internet.

`nextRechargeAt` stores the first eligible instant independently of line and bundle expiry.
“Ha alcanzado el monto ...” means available capacity is zero, never the amount of the limit.
“Posterior al día D” starts at midnight of D+1 in America/Havana; “a partir del día D” starts
on D. A passed date prompts rechecking rather than asserting that capacity has been restored.
The UI shows last-query time and a dedicated refresh button. Known-invalid v1 clock/recharge
interpretations are not presented; their ciphertext is retained until normal retention pruning.
