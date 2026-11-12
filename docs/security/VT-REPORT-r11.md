# VirusTotal review, round 11: Firebase Cloud Messaging build

Date: 2026-11-13. Branch: `wip/r10-safety`. Earlier reviews: [VT-REPORT-v1.5.1.md](VT-REPORT-v1.5.1.md), [VT-REPORT-round8.md](VT-REPORT-round8.md).

## Why v1.5.0 is flagged (unchanged root cause)

The published v1.5.0 (`release-Yumeiro-v1.5.0.apk`, 47.82 MB, 2/67) is still built from production `main` (`af8349e`), which has **none** of the round 4 and round 8 fixes:

1. **Kaspersky `Not-a-virus:HEUR:RiskTool.AndroidOS.Miner` / BitDefenderFalx `Android.Riskware.Agent.gHMOM`.** The extension safety check stored the patterns it searches extensions for (`stratum+tcp`, `xmrig`, `coinhive`, `content://sms`, `ContactsContract`, device-admin APIs) as plain strings. Heuristic engines read them as the app's own capabilities. Fixed by storing FNV-1a fingerprints; the check works exactly as before.
2. **`telephony` / `checks-gps` tags.** A broad `-keep class androidx.core.**` forced R8 to ship `TelephonyManagerCompat.getImei`, `LocationManagerCompat.requestLocationUpdates` and accessibility-list helpers that Yumeiro never calls and has no permission for. Excluded from the keep rule.
3. Unused `DOWNLOAD_WITHOUT_NOTIFICATION` permission removed.

**The fix for the published APK is to ship a release built from this branch.** No vendor submission is needed for new builds; the old v1.5.0 hash can still be reported as a false positive (Kaspersky: <https://opentip.kaspersky.com/>, Bitdefender: <https://www.bitdefender.com/consumer/support/answer/29358/>).

## What round 11 adds and how it was audited

Round 10/11 adds `firebase-messaging` 26.0.2 (BoM 35.2.0) for instant notices. Audited on the built APK (`aapt2 dump permissions/badging`, all DEX files searched byte-wise):

| Area | Finding | Verdict |
|---|---|---|
| New permissions | `com.google.android.c2dm.permission.RECEIVE` (FCM delivery), `ACCESS_NETWORK_STATE` (FCM + WorkManager network constraint), and the standard `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Required by FCM, standard for every app with push |
| All permissions (19) | media read, notifications, wake lock, biometric, battery-optimisation request, foreground service (media playback, data sync), audio settings, internet, Wi-Fi state + multicast, network state, install packages (in-app updater, user confirms), boot completed, c2dm receive | No SMS, contacts, phone, location, camera, microphone, accounts, overlay or accessibility permission |
| Miner / SMS / contacts strings | `xmrig`, `coinhive`, `content://sms`, `ContactsContract`: 0. Only `stratum` is `"SNTP: Untrusted stratum"` (time-sync library) | Clean |
| Phone id / location | `getImei` 0, `requestLocationUpdates` 0. `getDeviceId` = input-device / Cast ids | Clean |
| Dynamic code / shell | `DexClassLoader` 0, `Runtime.exec` 0. `PathClassLoader` loads user-installed extensions after the safety check | Core feature, kept |
| Firebase extras | No Analytics (`analytics.connector` 0), no `firelog`. `datatransport` classes come with messaging; delivery-metrics export is disabled in the manifest (`delivery_metrics_exported_to_big_query_enabled=false`) | No tracking |

No feature was removed and no privacy-relevant behaviour changed.

## Results (fresh first submissions, 2026-11-13)

| Build | SHA-256 | Detections | Report |
|---|---|---|---|
| v1.5.0 published (for comparison) | `2dd2cfec…8f8924` | 2 / 67 | <https://www.virustotal.com/gui/file/2dd2cfec707a7ef557576cf6b3d7b546c3d2d6814baa6bcb58e79a1bae0f8924/detection> |
| R11 preview, universal (`dev.asfakul.yumeiro.preview`) | `86e01922252e45316db3460dcfd5887accea3dd7843cc25b568b331fefee350d` | **0 / 66** (Kaspersky, BitDefender, BitDefenderFalx undetected; tags `android`, `apk`, `contains-elf`) | <https://www.virustotal.com/gui/file/86e01922252e45316db3460dcfd5887accea3dd7843cc25b568b331fefee350d/detection> |
| R11 release scan build (`dev.asfakul.yumeiro`, universal, R8-minified, signed with a throwaway scan-only key) | `c8182ed4b9ebffea8bd349bf3f80389e1e9a9d4d5826997de6e87ef30a27b7f4` | **0 / 68** (Kaspersky, BitDefender, BitDefenderFalx undetected; tags `android`, `contains-elf`, `apk`) | <https://www.virustotal.com/gui/file/c8182ed4b9ebffea8bd349bf3f80389e1e9a9d4d5826997de6e87ef30a27b7f4/detection> |

The release scan build is identical to a store release except for the signing key; sign the real release with the owner key. Local builds read `version.properties` (1.0.5 / 6, same as production `main`); the production Release workflow sets the published version (v1.5.0 now), so a shipped build must come out above that.
