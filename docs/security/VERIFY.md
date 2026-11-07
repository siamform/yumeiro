# Verification log: chore/hardening (2026-11-07)

Local run on JDK 21 with Android SDK 37, using `--max-workers=2`:

| Check | Result |
|---|---|
| `assembleDebug` | Pass |
| `assembleRelease` (unsigned locally: no keystore outside CI) | Pass. R8 and shrinkResources ran |
| `testDebugUnitTest` | Pass: 157 tests, 0 failures, 0 skipped (includes `OnlineSecretsTest`) |
| `lintDebug` | 2 errors that already exist on `main` (`RememberReturnType` in `VideoPlayerModal.kt:230` and `WatchKit.kt:128`). CI runs lint with `continue-on-error`. Not changed here (out of scope) |
| `apkanalyzer manifest debuggable` (release) | `false` |
| Exported components (merged release manifest) | MainActivity (launcher/VIEW/SEND), PlaybackService and VideoPlaybackService (MediaSession, required), DownloadCompleteReceiver (`SEND_DOWNLOAD_COMPLETED_INTENTS`), plus library components guarded by `BLUETOOTH_PRIVILEGED`, `BIND_JOB_SERVICE` or `DUMP` |
| Token patterns (GitHub PAT/OAuth, AWS, Google API, private keys, Slack) across every APK entry | 0 hits |
| `gitleaks dir` on the extracted APK | 1 false positive: a checksum in `META-INF/.../kotlinx-serialization-core-jvm/verification.properties` |
| `gitleaks dir` on `strings` of all dex files (157k strings) | 0 leaks |
| OSV scan of the release runtime classpath (272 coordinates) | 3 advisories, fixed with constraints (see DEPENDENCIES.md) |

## CI findings fixed during verification
- CodeQL `java/android/cleartext-storage-shared-prefs` (High) at `OnlineSettings.kt` `setSecret`. Account tokens and
  the OpenSubtitles key were stored in plaintext; this was already the case on `main`. They are now sealed with an
  AES-256-GCM key held in the Android Keystore (`security/SecretBox.kt`). Existing plaintext values are encrypted on
  first launch. If the Keystore is unavailable, secrets are kept in memory only and never written to disk in plaintext.
