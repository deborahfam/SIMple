# SIMple production publication

## Implemented

- Direct and Play release variants, with policy/permission separation checked by CI.
- Explicit device-credential confirmation before saving/loading a bank PIN; authenticated
  Android Keystore keys with a 15-second authorization window. No automatic PIN loading.
  Legacy v1/v2 PINs and keys are deleted without decryption and must be entered again.
  PIN input is cleared when the recharge screen stops; no PIN is saved in instance state.
- Non-debuggable release artifacts, no cleartext HTTP, encrypted sensitive stores, backups off.
- Offline creator/copyright, privacy policy and license notices, including a dependency inventory.
- Future local commits use the creator's existing GitHub noreply identity.

## Production signing and publication gate

The current public APK `SIMple-22.apk` uses the old development signing identity. A private
production key with subject `CN=SIMple, O=deborahfam` and a signed rotation lineage are now prepared.
The key has no personal name or email. The CI signer uses the legacy key only for Android 8 / API
26–27; Android 9+ / API 28+ uses the production key. Keeping the legacy signature on Android 8 is
necessary for compatible updates there. The old key must remain protected while those devices are
supported. It has not been exposed by this cleanup, and its local file is restricted to its owner.

`scripts/sign_release.py` rejects debuggable input and checks the exact expected certificate for
API 26, 27, 28, 32, 33 and 36. The new updater permits matching current signers or forward rotation;
it rejects returning to an older key or moving to an unrelated branch of the signing history.
The published version's existing updater also accepted the rotated APK in the migration test.

The optimized version-23 candidate was installed over public version 22 without uninstalling on
Android API 36. The app kept its UID, preferences, original encryption key, encrypted readings,
line profiles and recipients. Legacy saved PINs were deliberately removed and require re-entry.
See [reproducible migration probes](../tests/migration/README.md).

Production key material is in the ignored, owner-restricted `.local/signing/` directory. Back up
that directory securely, and store its password in a password manager; never commit or publish it.
Actions receives encrypted secrets for the production keystore/password/alias and rotation proof.
The old signing secrets remain for Android 8 compatibility. Public expected fingerprints are
`ANDROID_SIGNING_CERT_SHA256` (legacy) and `ANDROID_PRODUCTION_CERT_SHA256` (production).

`ANDROID_PUBLISH_ENABLED=false` pauses APK publication while allowing builds and signature
verification. Set it to `true` only after device acceptance and then run the release workflow.
No new public APK has been released by this work. Play App Signing/upload-key configuration and
Play Console declarations still need to be completed for the Play AAB; do not assume a new Play
signing identity can update the direct edition without a compatible signing lineage.

## Public Git history cleanup

The approved rewrite was applied atomically to `main` and 17 release tags after checking every
remote reference with force-with-lease. Historical source trees are unchanged. Commit/tag email
metadata uses GitHub noreply identities. The local checkout now follows the rewritten history.
Collaborators should reclone; merging or force-pushing the old history would restore the old email.
Release-note source references are updated to the equivalent sanitized commits.

GitHub caches, forks, old release metadata and existing clones may retain old references;
rewriting history cannot erase copies already obtained by others. The ignored local mirrors are
private rollback copies and must never be pushed. No backup refs were included in the public push.

## Store setup and device acceptance

1. Add the published [privacy policy](https://deborahfam.github.io/SIMple/privacy.html) to Play
   Console. HTTPS is enforced; the served page was verified against the reviewed HTML.
2. Complete Play Console Data safety, content rating, target audience and store listing using the
   **Play** variant's behavior. It has no app-server analytics or data upload; carrier/bank requests,
   user-directed browser links and any future SDK behavior must be assessed under Google's current
   definitions. Do not copy declarations from the direct-download edition blindly.
3. Use screenshots from synthetic/emulator data only. The checked-in light/dark dashboard previews
   were visually inspected: no phone numbers, names, account identifiers or PINs were visible.
4. On a real test phone, check installation/update without loss of history; SIM switching;
   permissions and revocation; offline history; deletion; screen-lock cancellation, expiration,
   rotation and backgrounding during PIN entry; and actual operator/bank confirmation. Do not
   perform a paid operation unless explicitly authorized for a controlled test.
5. Submit the signed Play bundle for review. The direct APK is a separate GitHub release.

## Verification scope (2026-11-21)

The checked runtime inventory contains 58 distinct Maven coordinates. OSV's batch lookup found no
known vulnerabilities for those versions on 2026-11-20. That does not establish that the app or
native/transitive components have no vulnerabilities. Repeat the check when dependencies change.
Source/history pattern scans, encryption tests and emulator tests do not replace real-device
acceptance or a complete independent security assessment. No release was published during this work.

References:
- [Android release preparation](https://developer.android.com/studio/publish/preparing)
- [Android authentication-bound keys](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder)
- [Play privacy policy requirements](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Play SMS permissions](https://support.google.com/googleplay/android-developer/answer/10208820)
- [Play package installation policy](https://support.google.com/googleplay/android-developer/answer/12085295)
