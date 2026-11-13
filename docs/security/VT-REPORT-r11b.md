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

The automated upload was blocked by VirusTotal's reCAPTCHA (two rounds solved, both rejected), and the hash is not yet in VirusTotal. To finish, upload the universal APK by hand at <https://www.virustotal.com/gui/home/upload> and check:
<https://www.virustotal.com/gui/file/5e7bbd69ef6be7c0fcd459c0bcc1dabfbd8013ff0b67a81bbb86337dd6cce3b0/detection>

R11 (same permissions, same native libraries, same Rhino) scored 0/66 and 0/68, and the R11b code changes add no pattern the engines flagged in v1.5.0.
