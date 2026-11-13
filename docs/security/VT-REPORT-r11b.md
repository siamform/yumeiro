# Security scan, R11b (Add anything, restored extractors)

Date: 2026-11-13. Branch: `r11-final` (same head as `redesign-final`). Earlier review: [VT-REPORT-r11.md](VT-REPORT-r11.md) (R11 preview 0/66, release scan build 0/68).

## Builds scanned

| APK | SHA-256 |
|---|---|
| `Yumeiro-r11b-preview-universal.apk` | `5e7bbd69ef6be7c0fcd459c0bcc1dabfbd8013ff0b67a81bbb86337dd6cce3b0` |
| `Yumeiro-r11b-preview-arm64-v8a.apk` | `26147b0f2c53ed2c1cbd12d59204cb4b3dfb72ef7772a471f6d99ac9866479a3` |
| `Yumeiro-r11b-preview-armeabi-v7a.apk` | `f5bab793218788215b39f9a5a267d83e7f24cabe3ed8d89f7d394f71a8be4e26` |

## What changed since R11 and why it is safe

| Change | Risk review |
|---|---|
| Universal "Add anything" intake (new `VIEW`/`SEND` intent filters for repo, plugin and `.cs3`/`.apk` links) | Filters only open the in-app confirm sheet; nothing is installed without the user tapping Install. No new permission. |
| Restored CloudStream v4.5.2 extractors (Vidguard 1-3 and others) | Vidguard evaluates a site script with Rhino, which was already shipped in R11 (`compileOnly`, same version 1.8.1). Rhino runs with no Java access; no new native code. |
| `SubtitleHelperPlatform_jvmKt` shim, Compose keep rule | Pure Kotlin/R8 config; no new capability. |

## Local scans

| Check | Result |
|---|---|
| ClamAV 1.5.4, signatures daily 28182 (2026-11-13), 3,628,269 signatures, archive scan on | **0 infected** in all 3 APKs (257 MiB unpacked) |
| ZIP "Master Key" duplicate-entry check (the ClamAV bytecode rule timed out on the large APK, so it was repeated by hand) | 0 duplicate entries (1,768 / 1,750 / 1,750 entries) |
| Permissions (`aapt2 dump permissions`) | Same 19 as R11; no SMS, contacts, phone, location, camera, microphone, accounts, overlay or accessibility |
| Miner / SMS / contacts strings (`xmrig`, `coinhive`, `stratum+tcp`, `content://sms`, `ContactsContract`) | 0 |
| Phone id / location (`getImei`, `requestLocationUpdates`) | 0 |
| Dynamic code / shell / device admin (`DexClassLoader`, `Runtime.exec`, `DevicePolicyManager`) | 0. User-installed extensions still load through `PathClassLoader` after the safety check |
| Native libraries | 28 `.so` files in the universal APK: FFmpeg decoders (`libavcodec`, `libavutil`, `libswresample`, `libswscale`), Media3 extension, `libass` subtitles, QuickJS, zstd, AndroidX graphics/datastore. All are known open-source libraries |
| Hard-coded secrets (AWS, GitHub, Slack, private keys, Google API keys) | One Google API key: the Firebase client config of project `yumeiro-ab29e` (from `google-services.json`). Firebase ships this public identifier in every Android app by design; access is restricted by Firebase rules and app id. No other hits |

## VirusTotal

Fresh first submission, 2026-11-13 12:58 UTC.

| Build | SHA-256 | Detections | Report |
|---|---|---|---|
| R11b preview, universal | `5e7bbd69ef6be7c0fcd459c0bcc1dabfbd8013ff0b67a81bbb86337dd6cce3b0` | **0 / 66** (BitDefender, BitDefenderFalx, Avast, AVG, Avira undetected; tags `android`, `apk`, `contains-elf`) | <https://www.virustotal.com/gui/file/5e7bbd69ef6be7c0fcd459c0bcc1dabfbd8013ff0b67a81bbb86337dd6cce3b0/detection> |

Same result as R11 (0/66 preview, 0/68 release). The published v1.5.0 was 2/67.

## CodeQL is not an antivirus

CodeQL reads the source code on GitHub and points at lines a reviewer should look at. It never sees the APK, phones and
stores don't use it, and its alerts can't block an install or mark the app as malware. The high alerts on PR #27 were
RC4 decoding in two CloudStream extractors (the sites' own format) and JavaScript in the stream finder page (a site's own
player); both are reviewed in [AUDIT.md](AUDIT.md). The medium ones are "no certificate pinning" on hosts Yumeiro
doesn't control and WebView settings that are already off at runtime.
