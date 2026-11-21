# Published-to-production upgrade probe

Run only on a disposable `sdk_gphone` emulator. The fixtures are synthetic, make no carrier/bank
requests, and must not be installed on a user's phone. The two stages intentionally survive one
APK update. Ordinary instrumentation suites skip `MigrationBeforeTest` unless explicitly selected.

1. Install the public `SIMple-22.apk` and the `directDebugAndroidTest` APK. Build the candidate with
   versionCode 23. Sign it with `scripts/sign_release.py` using the old/new keys and signed lineage.
2. Stage that candidate at `cache/updates/update.apk` in the old debuggable application's sandbox.
   For example, `adb -s SERIAL shell -T run-as com.simple.mobile tee cache/updates/update.apk < CANDIDATE > /dev/null`
   after creating `cache/updates` with `run-as`. Do not use this on a real user's installation.
3. Run `adb -s SERIAL shell am instrument -w -e class com.simple.mobile.MigrationBeforeTest -e migrationStage before com.simple.mobile.test/androidx.test.runner.AndroidJUnitRunner`.
   Require `OK (1 test)`. This seeds encrypted history/profile/recipient records and an old-format
   saved PIN, records the UID, and invokes the **published app's updater** against the candidate.
4. Install the candidate with `adb -s SERIAL install -r CANDIDATE`; never uninstall or clear storage.
5. Build the platform-only after-probe with `scripts/build_migration_probe.py --sdk SDK --keystore KEY
   --alias ALIAS --password-file PASSWORD_FILE`. Use the production signer. Install its `probe.apk`.
6. Run `adb -s SERIAL shell am instrument -w -e productionDigest SHA256 com.simple.mobile.migrationprobe/com.simple.mobile.migrationprobe.AfterUpgradeProbe`.
   Require the explicit `PASS:` result; adb's process exit code alone is insufficient.

The after-probe uses no Kotlin, AndroidX or target-app classes, so it works with the actual minified
release. It checks the production current signer, two-certificate rotation history, non-debuggable
flag, preserved UID and preferences, decryption of all synthetic records with the old Keystore key,
and removal of the legacy PIN/key with a replacement notice. Android API 36 passed this probe on
2026-11-21. `apksigner verify` also passed for API 26, 27, 28, 32, 33 and 36; those are cryptographic
checks, not emulator execution on every Android release. Real-device acceptance remains required.
