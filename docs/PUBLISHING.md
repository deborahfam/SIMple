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

## Signing decision required before publication

The current public release `v0.6.13` / `SIMple-22.apk` has a certificate subject
`C=US, O=Android, CN=Android Debug`. It has no personal name or email in its subject,
but it is a development signing identity. The workflow now refuses it.

Do not silently replace the key or tell users to uninstall: uninstalling deletes their encrypted
local history and keys. Choose between a fresh production installation with an explicit data-loss
migration notice and a signing-key rotation plan verified across supported Android versions.
The updater currently compares signing certificates for equality and does not implement rotation.
A fresh production key should use a non-personal subject such as `CN=SIMple, O=deborahfam`.
Store its credentials only in a password manager and Actions secrets; keep an encrypted offline
backup. Do not reuse the developer debug keystore. No production key has been generated or
installed in Actions by this review.

The production APK must pass `apksigner verify`, match the configured public certificate digest,
and install/update on a real test device. The Play AAB also requires upload signing and Play App
Signing configuration. The local `bundlePlayRelease` output is unsigned until that is configured.

## Public Git history cleanup

The repository is already public. Personal-email metadata occurs in historical commits.
A private, ignored local mirror is prepared for review; the original repository is unchanged.
Replacing public branches and tags requires an explicit coordinated history rewrite, with
force-with-lease checks against a fresh remote snapshot, release/tag verification, and instructions
for collaborators to reclone. GitHub caches, forks and existing clones may retain old metadata;
a history rewrite cannot guarantee removal of copies that others already obtained. Do not push
`refs/original`, local review refs, or a mirror containing backup refs.

## Store setup and device acceptance

1. Publish `docs/privacy.html` at a public, stable HTTPS URL and add it to Play Console. The policy
   names SIMple and its creator, explains permissions, local storage, deletion and external services.
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

## Verification scope (2026-11-20)

The checked runtime inventory contains 58 distinct Maven coordinates. OSV's batch lookup found no
known vulnerabilities for those versions on this date. That does not establish that the app or
native/transitive components have no vulnerabilities. Repeat the check when dependencies change.
Source/history pattern scans, encryption tests and emulator tests do not replace real-device
acceptance or a complete independent security assessment. No release was published during this work.

References:
- [Android release preparation](https://developer.android.com/studio/publish/preparing)
- [Android authentication-bound keys](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder)
- [Play privacy policy requirements](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Play SMS permissions](https://support.google.com/googleplay/android-developer/answer/10208820)
- [Play package installation policy](https://support.google.com/googleplay/android-developer/answer/12085295)
