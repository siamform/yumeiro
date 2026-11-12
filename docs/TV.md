# Yumeiro on Android TV, Fire TV and TV boxes

One APK serves phones, tablets and TVs. The ten-foot layout is chosen at runtime, so there is no
second app to keep in sync, sign or update. (Hikari ships a separate TV UI inside one APK as well;
Yumeiro follows the same idea but reuses every phone screen with a TV shell instead of a parallel
set of screens.)

## Supported devices

| Item | Value |
|---|---|
| Minimum Android | 7.0 (API 24). Most TVs and sticks sold since 2017 run 7.1–11. |
| Target | API 36 |
| ABIs | `arm64-v8a`, `armeabi-v7a` (most TV sticks are 32-bit ARM even on 64-bit chips), and `universal` (every ABI in one file) |
| Launcher | `LEANBACK_LAUNCHER` intent filter plus a 320×180 banner (`drawable-xhdpi/tv_banner.png`); `touchscreen` and `leanback` are declared `required="false"` so the Play Store and sideload both accept TVs |
| Icons | Classic PNG launcher icons in every density, because Android 7.x cannot read adaptive icons |

Which APK to install: `Yumeiro-…-armeabi-v7a.apk` on almost every TV stick (Fire TV Stick, Mi Box,
Chromecast with Google TV run 32-bit userspace), `arm64-v8a` on newer phones, `universal` when unsure.
The in-app updater reads the device's ABI list and downloads the matching APK from the release
(falls back to universal).

## How TV mode is decided

`ui/tv/TvMode.kt`

1. Settings → Navigation bar → Layout: **Automatic** (default), **TV**, **Phone & tablet**.
2. Automatic means TV when the device reports UI mode television, the leanback feature, Fire TV or Google TV
   markers, or a screen of at least 360 dp that has no touchscreen. Chromebooks and desktop-mode
   phones (`android.hardware.type.pc`) keep the phone layout.
3. Forcing a mode is there for cheap boxes that run a phone image and report themselves wrongly.

Unit tested: `TvModeTest` (decision table), `TvLayoutTest` (rail, focus, remote keys, open-rail render).

## What changes in TV mode

| Area | Behaviour |
|---|---|
| Navigation | The bottom dock is replaced by a left rail (`TvNavRail.kt`). It is icon-only until focus moves onto it, then it shows labels. D-pad Left from the left edge of any page enters the rail; Right goes back to the content. |
| Focus | Every clickable shows a visible focus ring and lift (`PressIndication.kt`, focus-visible indication). Without this, D-pad users could not see where they were. |
| Video player | The surface holds focus while the controls are hidden. Left/Right seek ±10 s, Centre/Enter and media Play-Pause toggle, Up/Down show the controls, media FF/RW seek, Back first hides the controls and the second Back leaves. Controls stay up 5 s after the last key press (3.5 s on touch). The player opens with focus on play-pause. |
| Error card | The playback error card takes focus on **Retry**, and the faded transport buttons are no longer drawn through it. |
| Input change | Plugging in a keyboard or remote mid-video no longer restarts the video (`keyboard|keyboardHidden|navigation` are in `configChanges`). |

## Android 7–8 specifics (also apply to old phones)

| Topic | What Yumeiro does |
|---|---|
| Java 8+ APIs | Core-library desugaring (`java.time`, streams) |
| TLS | Android 7.0 does not trust Let's Encrypt's ISRG Root X1. The root is bundled in `res/raw` and trusted through `network_security_config.xml` (SHA-256 fingerprint checked against the published value). WebView on Android 7 ignores the network security config, so web-only sources that use Let's Encrypt may still fail inside the WebView there. |
| App lock | Android 7–8 have no `BiometricPrompt`; the lock falls back to the device's own PIN/pattern screen (`KeyguardManager.createConfirmDeviceCredentialIntent`). |
| Picture-in-picture | Android 8.0+ only, and only where the device declares the PiP feature (many TV boxes don't). Otherwise PiP is never offered (`PipController.isSupported`). |
| Notifications | Channels exist from 8.0; on 7.x notices use the default priority. |

## Not verified on real hardware

All checks so far ran on Robolectric (simulated Android 14/16 runtime with simulated remote key
events). These still need a real device:

- Rail and focus behaviour on a real Android TV 9–12 and a Fire TV Stick (Fire OS 7, Android 9 base).
- Playback performance on a 1 GB RAM 32-bit stick (decoder limits, 4K HEVC streams).
- Android 7.0 WebView with Let's Encrypt sites (see above).
- Voice search buttons on remotes (not handled; they open the system assistant).
