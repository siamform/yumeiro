# BUG-7: player and IPTV issues (owner report, four items)

Status: **open, recorded 2026-11-16.** Nothing reproduced yet. Ledger row: BUG-7 (items 7a–7d).
Groups: G3 (video player), G6 (Live / IPTV "Play link"), G8 (downloads), G12 (stability: freezes).
Rule (same as BUG-1 to BUG-6): no fix before the Step A.5 note; not closed until reproduced, fixed at
the root, and proven on both phone emulators with a regression test that fails before and passes after.
7c and 7d are freezes, so they come first (stability before anything else).

## Owner report (verbatim)

> Bug Report / Yumeiro Player & IPTV Issues:
>  * Missing Download Button (Web URLs): When adding a website URL and playing a video inside the Yumeiro player, the download button does not appear.
>  * Hidden Offline Downloads (IPTV Play Link): When downloading a valid video URL from the IPTV 'Play Link' page, the file saves to internal offline storage and is not visible in the user's device gallery.
>  * App Freeze on Stream Format Mismatch: In the IPTV 'Play Link' options (DASH, HLS, etc.), if the provided video URL does not match the selected stream format, the player and the entire app hang/freeze, making all buttons unresponsive.
>  * App Freeze on Yumeiro Upscaling: Enabling or using the Yumeiro resolution scaling functionality causes the exact same app freeze and button unresponsiveness.

## Items and first leads (code reading only, unverified)

| Item | Report | First lead |
|---|---|---|
| 7a | No download button in the player for a video played from a website URL | Websites (`WebSitesScreen.kt`) hand the video to the player; check which player entry it uses and whether that path passes a download action (the button is driven by the caller). This is the existing Websites screen, not the gated "WebView sites" feature of Part 8. |
| 7b | Play link → Download saves to internal storage, not visible in the gallery | `StreamScreen.download()` → `OnlineDownloads.enqueue(…)`; check the save location and whether finished files are written to MediaStore (Movies/Yumeiro) like other downloads, or kept app-private. Must not move or lose existing downloads. |
| 7c | Play link with a hand-picked format (DASH, HLS…) that does not match the URL freezes the whole app | `StreamScreen` line ~287: a hand-picked format skips inspection and goes straight to `IncomingMedia.open`; the player then builds the forced media source type. Suspect blocking work or a retry loop on the main thread when the source fails to parse. Needs an ANR trace (`/data/anr`, main-thread stack) first. |
| 7d | Turning on Yumeiro upscaling freezes the app the same way | `applyEngineLevel` (`VideoPlayerModal.kt` ~2396) adds `Anime4kEffect` via `setVideoEffects`; suspect the effect pipeline set up or rebuilt on the main thread, or a GL deadlock with the surface. Needs an ANR trace. |

## Plan

1. Reproduce each on both phone emulators with a device test: 7c (mock server serving MP4 while DASH /
   HLS is forced; main-thread watchdog + ANR trace), 7d (test clip, upscaled level picked; watchdog),
   7b (download from a mock server, check MediaStore visibility), 7a (Websites → video → player, check
   the download button).
2. Step A.5 note per item (how other open-source players handle it; ideas only).
3. Root-cause fixes, each with a regression test that fails before and passes after; phone suite on both
   emulators at the milestone.
