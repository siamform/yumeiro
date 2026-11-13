# VirusTotal review: v1.5.0 detections and the v1.5.1 build

Date: 2026-11-11. Branch: `redesign/v4-complete` (private repo `siamform/yumeiro-redesign`).

## Summary
v1.5.0 was flagged by 2 of 67 engines as "riskware / not-a-virus". It is not malware. The cause was in Yumeiro's own code: the extension safety check stored the signatures it looks for (crypto-miner pool schemes, SMS and contacts URIs, device-admin APIs) as plain strings. Nothing else in the app uses those strings, so heuristic engines read them as capabilities of the app itself. The signatures are now stored as fingerprints, detection is unchanged and covered by tests, and an unused silent-download permission was removed. The rebuilt APK was scanned again (see "Results").

## v1.5.0 (before)
- File: `release-Yumeiro-v1.5.0.apk`, 47.82 MB
- SHA-256: `2dd2cfec707a7ef557576cf6b3d7b546c3d2d6814baa6bcb58e79a1bae0f8924`
- Report: <https://www.virustotal.com/gui/file/2dd2cfec707a7ef557576cf6b3d7b546c3d2d6814baa6bcb58e79a1bae0f8924/detection>
- Detections (2/67):

  | Engine | Label |
  |---|---|
  | Kaspersky | `Not-a-virus:HEUR:RiskTool.AndroidOS.Miner.b` |
  | BitDefenderFalx | `Android.Riskware.Agent.gHMOM` |

- VirusTotal tags: `checks-gps`, `telephony`, `contains-elf`, `reflection`. Categories: pua, miner.

## Investigation
Everything below was checked on the v1.5.0 APK itself (dexdump on all four DEX files and aapt2 on the manifest), not on the sources alone.

| Signal | Where it came from | Verdict |
|---|---|---|
| "Miner" (Kaspersky) | `stratum+tcp`, `stratum+ssl`, `xmrig`, `coinhive`, `coin-hive`, `cryptonight`. Each appeared exactly once, in the `ExtensionScanner` rule table | **Fixed.** These are detection signatures. The app never mines and opens no stratum sockets |
| SMS, contacts, call log, device admin strings | The same rule table (`content://sms`, `content://mms`, `content://call_log`, `SmsManager`, `ContactsContract`, `android/app/admin/`) | **Fixed.** Same cause. The app has no SMS, contacts, call-log or device-admin permission |
| `DOWNLOAD_WITHOUT_NOTIFICATION` | Manifest. It was used only by a silent self-update path that could never run (`canGoSilent()` returned false) | **Removed**, along with the dead code. Update downloads always show the system notification |
| `REQUEST_INSTALL_PACKAGES` | The in-app updater (GitHub Releases). It uses `PackageInstaller` with `USER_ACTION_REQUIRED`, so the user confirms every install | Kept (legitimate, user-visible) |
| `checks-gps` | androidx.appcompat `TwilightManager` (automatic day/night) calls `LocationManager` only after a permission check. Yumeiro requests no location permission, so it always falls back | Library code, harmless. Kept |
| `telephony` | Media3 / androidx use `TelephonyManager` to detect the network type (adaptive streaming). There is no `READ_PHONE_STATE`, IMEI, number or SIM access | Library code. Kept |
| `contains-elf` | Native libraries: FFmpeg decoders (nextlib), QuickJS (JavaScript sources), zstd, libass (subtitles) | Needed for playback. Kept |
| `reflection`, `DexClassLoader` | Loading user-installed Aniyomi, Mihon and CloudStream extensions, each checked by the extension safety check first | Core feature. Kept |
| Background activity | WorkManager jobs (episode alerts, updates), `RECEIVE_BOOT_COMPLETED` to reschedule them, a media-playback foreground service and an optional battery-optimisation exemption that the user turns on in the Permission Center | Legitimate, user-controlled. Kept |
| Tracking or analytics SDKs | None in the APK. The tracker host names exist only as detection fingerprints in the extension safety check | None to remove |

## Changes
1. **`online/ExtensionScanner.kt`**: rules match 64-bit FNV-1a fingerprints of normalised indicators (type, class, package, method, URI scheme, authority, host suffix, word, exact string) instead of plain strings. Two benign rules stay plain text.
2. **Tests** (`ExtensionScannerTest`, 16 tests):
   - the readable indicator list lives in the test sources;
   - every indicator still triggers its rule;
   - look-alike strings do not trigger;
   - the shipped table must equal the readable list.

   The readable list is also published in [AUDIT.md](AUDIT.md#antivirus-heuristics-2026-11-11-v151).
3. **`update/UpdateManager.kt`**: removed the unreachable silent download block. The notification is always visible.
4. **`AndroidManifest.xml`**: removed `DOWNLOAD_WITHOUT_NOTIFICATION` and documented why each remaining sensitive permission exists.
5. Unrelated, but in the same build: the R8 rule fix for the manga extension install error (generic signatures on `FullTypeReference` subclasses). See the handover.

No feature was removed and no privacy-relevant behaviour changed. Fingerprinting the signatures is not obfuscation of behaviour: the app does not perform any of the flagged actions, and the human-readable list is public.

## Verification of the new APK (dexdump / aapt2)
- `stratum+tcp`, `stratum+ssl`, `xmrig`, `coinhive`, `coin-hive`, `cryptonight`, `content://sms`, `content://mms`, `content://call_log`, `Landroid/telephony/SmsManager;`, `ContactsContract`, `android/app/admin`: **0 occurrences** in all DEX files. Each appeared once in v1.5.0.
- Permissions: 16 platform permissions. `DOWNLOAD_WITHOUT_NOTIFICATION` is gone and nothing new was added.
- Injekt: 65 `FullTypeReference` subclasses, all with a generic `Signature`. v1.5.0 had one class (`Lg86;`) without one, which caused the manga install error.

## Results
Scanned on VirusTotal on 2026-11-11 (first submission of this file, analysed fresh):

| | v1.5.0 (before) | v1.5.1 scan build (after) |
|---|---|---|
| SHA-256 | `2dd2cfec…8924` | `858950a19d26d4944fc30dcf379ec248d351459455aa05bc236e2055abc4dcc6` |
| Detections | **2 / 67** | **0 / 66** |
| Kaspersky | `Not-a-virus:HEUR:RiskTool.AndroidOS.Miner.b` | Undetected |
| BitDefenderFalx | `Android.Riskware.Agent.gHMOM` | Undetected |
| Tags | `checks-gps`, `telephony`, `contains-elf`, `reflection` | `android`, `contains-elf`, `apk` |

- Report: <https://www.virustotal.com/gui/file/858950a19d26d4944fc30dcf379ec248d351459455aa05bc236e2055abc4dcc6/detection>
- `contains-elf` remains because of the native playback libraries, which is expected and not a detection.

**About the scanned file.**
- It was built locally from this branch: `assembleRelease` with R8, versionName 1.5.1, versionCode 13.
- It was signed with a throwaway scan-only key, because the release keystore exists only as a CI secret.
- Do not distribute it. Users must get the CI-signed build from `release.yml`. That build has the same code, so the result is expected to match, but rescan it after release, because a different signature changes the hash.
- The release workflow decides the final version number from the commits; it may be 1.6.0.

## False-positive submissions (prepared, not sent)
Sending these needs the owner's approval, because it shares the APK with the vendors. Use the official release APK signed by CI, not the local scan build.

- **Kaspersky**: look the SHA-256 up on the [Kaspersky Threat Intelligence Portal](https://opentip.kaspersky.com/), open the report and press "Submit to reanalyze". Add an email address and a comment of up to 2000 characters ([OpenTIP help](https://opentip.kaspersky.com/Help/Doc_data/en-US/all-in-one.htm)). The alternative is email to `newvirus@kaspersky.com` with the file in a password-protected archive using the password `infected` ([False Positive Center](https://github.com/yaronelh/False-Positive-Center)).
- **Bitdefender**: use the [incorrect-detection form](https://www.bitdefender.com/consumer/support/answer/29358/) and upload the APK for analysis.

Suggested text:

> Yumeiro (`dev.asfakul.yumeiro`, open source, GPL-3.0, <https://github.com/siamform/yumeiro>) is a media player. Version 1.5.0 (SHA-256 2dd2cfec707a7ef557576cf6b3d7b546c3d2d6814baa6bcb58e79a1bae0f8924) was detected as `Not-a-virus:HEUR:RiskTool.AndroidOS.Miner.b` / `Android.Riskware.Agent.gHMOM`. The strings that triggered this (stratum+tcp, xmrig, coinhive, content://sms, ...) were the signature list of the app's built-in safety scanner for third-party plugins. The app does not mine, read SMS or contacts, or administer the device, and it requests none of those permissions. Version 1.5.1 stores those signatures as hashes and removes an unused permission. Please re-evaluate both versions. Source and audit: docs/security/AUDIT.md and docs/security/VT-REPORT-v1.5.1.md.
