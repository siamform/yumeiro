# VirusTotal review, round 8: v1.5.0 detections re-investigated

Date: 2026-11-12. Branch: `redesign/v6-polish`. Earlier review: [VT-REPORT-v1.5.1.md](VT-REPORT-v1.5.1.md).

## Summary

v1.5.0 (`release-Yumeiro-v1.5.0.apk`, 47.82 MB) is flagged 2/67 as riskware ("not-a-virus").
The app is not malware. There are two causes, both in what the APK *contains*, not in anything it does:

1. **Main cause (Kaspersky "Miner", Bitdefender "Riskware.Agent").** The extension safety check
   stored the signatures it searches extensions for (`stratum+tcp`, `xmrig`, `coinhive`,
   `content://sms`, `ContactsContract`, device-admin APIs …) as plain strings. Heuristic engines
   read those as the app's own capabilities. This was fixed in round 4 (signatures stored as
   FNV-1a fingerprints, unused `DOWNLOAD_WITHOUT_NOTIFICATION` removed, see the earlier report),
   but **that fix exists only in the redesign repo and was never merged into production**. That
   is why the published v1.5.0 is still flagged.
2. **Secondary cause (VirusTotal tags `telephony`, `checks-gps`).** `app/proguard-rules.pro` kept
   all of `androidx.core.**` for extension compatibility. That forced R8 to ship
   `TelephonyManagerCompat.getImei`, `LocationManagerCompat.requestLocationUpdates` and the
   accessibility-service-list helpers, which Yumeiro never calls and has no permission for.
   Round 8 excludes those three packages from the keep rule; R8 still keeps whatever is really used.

## What was checked (on the built APK, not only the sources)

`aapt2 dump permissions/badging` on the manifest, `dexdump` and `strings` on all four DEX files,
and the zip listing.

| Area | Finding | Verdict |
|---|---|---|
| Permissions | 17: media read, notifications, wake lock, biometric, battery-optimisation request, foreground service (media playback, data sync), audio settings, internet, Wi-Fi state + multicast (casting / DLNA), `REQUEST_INSTALL_PACKAGES` (in-app updater, user confirms every install), boot completed (re-schedules alerts) | All used and user-visible. No SMS, contacts, phone, location, camera, microphone, accounts, overlay or accessibility permission |
| Miner / SMS / contacts / call-log / device-admin strings | 0 occurrences. The only `stratum` is `"SNTP: Untrusted stratum"` from a time-sync library | Clean |
| Phone identifiers | Before: `TelephonyManagerCompat.getImei` (androidx.core, dead code). After: 0. `getDeviceId` hits are `MotionEvent`/`KeyEvent` input-device ids and the Cast device id | Fixed |
| Location | Before: `LocationManagerCompat.requestLocationUpdates` (dead code). After: 0. `getLastKnownLocation` remains only in appcompat `TwilightManager` (automatic day/night), which checks the permission first; Yumeiro never has it | Fixed / library |
| Dynamic code | `PathClassLoader` (no `DexClassLoader`, no `InMemoryDexClassLoader`) used to load user-installed Aniyomi / Mihon / CloudStream extensions after the extension safety check | Core feature, kept |
| Shell / root | No `Runtime.exec`, no `su`, no `ProcessBuilder` call (the only string is the safety check's user-facing description) | Clean |
| Clipboard | `getPrimaryClip` in text-field paste helpers (androidx / Compose) and the Live TV "Paste" button | Expected |
| Trackers / ads | None. `doubleclick` appears only in the ad *blocker* (`online/web/AdFilter`) and NewPipe's link handler; `firebase` is one protobuf annotation name | Clean |
| Native code (`contains-elf`) | FFmpeg decoders, libass (subtitles), QuickJS (JavaScript sources), zstd, androidx graphics | Needed for playback, kept |
| Background work | WorkManager jobs (episode alerts, updates), media foreground service, optional battery exemption the user enables | User-controlled, kept |

No feature was removed and no privacy-relevant behaviour changed.

## Results

| | v1.5.0 (published) | Round 8 release scan build |
|---|---|---|
| SHA-256 | `2dd2cfec707a7ef557576cf6b3d7b546c3d2d6814baa6bcb58e79a1bae0f8924` | `98602092c7c2e4ef3a41849ed68316db03aa2f535868bf5b294a0d271887b0c1` |
| Detections | 2 / 67 | **0 / 66** |
| Kaspersky | `Not-a-virus:HEUR:RiskTool.AndroidOS.Miner.b` | Undetected |
| BitDefenderFalx | `Android.Riskware.Agent.gHMOM` | Undetected |
| Tags | `checks-gps`, `telephony`, `contains-elf`, `reflection` | `android`, `contains-elf`, `apk` |
| Report | <https://www.virustotal.com/gui/file/2dd2cfec707a7ef557576cf6b3d7b546c3d2d6814baa6bcb58e79a1bae0f8924/detection> | <https://www.virustotal.com/gui/file/98602092c7c2e4ef3a41849ed68316db03aa2f535868bf5b294a0d271887b0c1/detection> |

The owner try-out build (`Yumeiro-redesign-r8-preview.apk`, package `dev.asfakul.yumeiro.preview`,
debug-key signed) was scanned too: SHA-256 `4ec5cad37feed857077760cac88b6512b1402c49e221c98df0a629139a8c49de`,
**0 / 66**, Kaspersky and BitDefenderFalx undetected, tags `android`, `contains-elf`, `apk`
(<https://www.virustotal.com/gui/file/4ec5cad37feed857077760cac88b6512b1402c49e221c98df0a629139a8c49de/detection>).

Both scans: 2026-11-12, first submission of each file, analysed fresh.

The scan build is `assembleRelease` (R8) from this branch, signed with a throwaway scan-only key
because the release keystore exists only as a CI secret. Do not distribute it. After the owner
approves and CI builds the signed release, rescan that APK: a different signature changes the hash.

## Fixing the published v1.5.0

Detections attach to a file hash, so the v1.5.0 file will stay flagged until the vendors
re-evaluate it. The real fix is to release a version built from this code. Then, with the owner's
approval (it shares the APK with the vendor), request re-analysis of v1.5.0:

- **Kaspersky**: look the hash up on the [Kaspersky Threat Intelligence Portal](https://opentip.kaspersky.com/) and use "Submit to reanalyze", or email `newvirus@kaspersky.com`.
- **Bitdefender**: [incorrect-detection form](https://www.bitdefender.com/consumer/support/answer/29358/).

Suggested text:

> Yumeiro (`dev.asfakul.yumeiro`, open source, GPL-3.0, https://github.com/siamform/yumeiro) is a
> media player. v1.5.0 (SHA-256 2dd2cfec707a7ef557576cf6b3d7b546c3d2d6814baa6bcb58e79a1bae0f8924)
> is detected as `Not-a-virus:HEUR:RiskTool.AndroidOS.Miner.b` / `Android.Riskware.Agent.gHMOM`.
> The strings behind this (stratum+tcp, xmrig, coinhive, content://sms, ...) were the signature
> list of the app's built-in safety scanner for third-party plugins. The app does not mine, read
> SMS or contacts, read phone identifiers or location, and requests none of those permissions.
> The next version stores the signatures as hashes and drops unused library code (0/66 on
> VirusTotal). Please re-evaluate v1.5.0.
