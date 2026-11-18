# G6-SP1 — Music shows no way to allow music access (Android 13+ and older)

Status: Implemented — unverified. Fail-before CI 44230037374 (ab/sp1-before): MusicAccessPromptTest red ("no card"); the other reds there are the pre-00c4ded LrcLibMockedTest copy. Group G6 Music (local scan, Android 13+ storage).

## Problem (read in code, 2026-11-18)

- `MusicScreen` shows `PermissionPromptCard` only when `!stats.hasStoragePermission && stats.permissionPermanentlyDenied`.
  Every caller passes `permanentlyDenied = false` (`MTPlayerApp` startup and the launcher result), so the card is never shown.
- `hasStoragePermission` comes from `hasMediaPermission()`, which is true when either READ_MEDIA_AUDIO or READ_MEDIA_VIDEO is granted.
  On Android 13+ the system asks for "music and audio" and "photos and videos" in two separate dialogs.
  A user who allows videos but not music counts as "granted" for Music.
- Result: if the user denies music access, the Tracks tab says "No audio files detected — Place MP3… and tap Rescan".
  Rescan can never find anything. The app gives no path to allow access except a Settings page the user must find on their own.

## Competitors (ideas only, read at the exact tag)

| App | Version | What it does |
|---|---|---|
| Auxio | v3.6.3 `home/HomeFragment.kt` | The indexer returns `NoAudioPermissionException`. Home shows "no permission" with a Grant button that launches READ_AUDIO only. |
| Gramophone | 1.1.2 `ui/MainActivity.kt` | Checks `hasAudioPermission()` (READ_MEDIA_AUDIO on 13+). If denied, it shows a toast and opens app details settings (then finishes). |

Both check the audio permission itself, not "any media". Neither treats video access as music access.

## Fix (least risk)

1. `PermissionCenter.audioAllowed(c)`: READ_MEDIA_AUDIO on 33+, READ_EXTERNAL_STORAGE below.
2. `MusicScreen(audioAccess: Boolean?)`, where null means not read yet:
   - The card shows when `audioAccess == false`. The old permanently-denied condition is kept.
   - With no access, the Tracks tab does not show the "place files and Rescan" empty view; the card says what to do.
3. `MTPlayerApp` reads `audioAllowed` off the main thread at start, on every resume (back from Android settings), and after each permission result.
   - If the user asked from the card and Android said no, it uses `PermissionCenter.explainDenied(…, "Music access")` (same ER-11 flow as notifications).
   - Denied once: "not allowed, tap again". Blocked: "Open settings" action.
4. The card text no longer says "100% offline" (wrong since streaming), and is about music only.

No change to scanning, MediaStore queries, the startup request, or videos.

## Action → reaction

| Action | Before | After |
|---|---|---|
| Deny music, allow videos (13+) | "No audio files detected… Rescan"; Rescan finds nothing | Card "Music access is off" + Allow access |
| Tap Allow access, allow | (no card) | Dialog → granted → scan → songs appear, card gone |
| Tap Allow access, deny (first time) | — | Message "Music access not allowed. Tap again…" |
| Tap Allow access, blocked | — | Message with "Open settings" → Android app settings |
| Allow in Android settings, come back | Card never shown; scan only after restart | Resume re-reads access, card gone, scan runs |
| Access granted | Normal list | Unchanged |

## Pros / cons

- Pro: a denied user can recover in one tap. It matches the per-permission split Android 13 introduced. It reuses ER-11 messaging.
- Pro: no change to data, scan, or the startup flow.
- Con: one extra binder permission check per resume, run off the main thread (X2-2 lesson).
- Con: Android 14 "partial" photo/video selection does not apply to audio, so there is nothing to add for READ_MEDIA_VISUAL_USER_SELECTED.

## Tests

- Unit (Robolectric, fail-before → pass-after): MusicScreen with `audioAccess=false` and empty tracks shows `permission_prompt_card`.
  Tapping `grant_permission_button` calls onRequestPermission. With `audioAccess=true`, no card.
- Emulator (both phones): MusicAccessDeviceTest via scripts/device-check.sh phases.
  - Phase a: video granted, audio revoked by the script → card shown, no crash.
  - Phase b: audio granted → card gone, songs pushed by shell are listed.
