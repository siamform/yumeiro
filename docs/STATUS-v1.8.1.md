# Yumeiro v1.8.1 status: done, remaining, future

Date: 2026-11-14. Branch `r14-round2`. Evidence: [evidence/round2/order1-drama.md](evidence/round2/order1-drama.md),
[evidence/round2/orders2-9.md](evidence/round2/orders2-9.md).

How it was tested: Robolectric unit and UI tests on the real app code (755 pass, 8 live tests
skipped by default), live network tests against the drama studios, and a full ffprobe decode of
one episode per visible studio. No physical phone or emulator was available in the build
sandbox (no KVM), so nothing below was confirmed on a real device.

## Done

| Order | Result | Proof |
|---|---|---|
| 1 Drama | Root cause found and fixed (CloudStream fixUrl rewrote drama ids). Every shown title resolves links for new and 1.8.0 ids; studios that cannot play are hidden; cross-studio fallback. | YumeiroDramaLiveTest, DramaHealthTest, full decode: DramaBox, ReelShort, NetShort, DramaWave, GoodShort, ShortsWave, StardustTV |
| 2 Quality | Player and download sheet share one list; engine levels for single-quality streams and local videos. | QualityRound2Test |
| 3 Loader | Neutral loader unless certain; shaped skeleton only after 2 identical outcomes. | LoaderRound2Test, Round13LogicTest |
| 4 Yumix | Section headers removed, extension search always shown. | YumixShowcaseTest |
| 5 Home/Library | History loads on app open; real likes, user playlists, measured play time. | LibraryRound2Test |
| 6 Heat | HeatGuard: thermal + battery saver + background throttling, capped extension calls and link gathering, Anime4K off when hot. | HeatGuardTest |
| 7 Crash logs | Saved on the phone, read/share/delete in Settings. | CrashLogTest |
| 8 Theme image | Bounds-probe bug fixed, ImageDecoder, accepted types and sizes shown. | BackdropImportTest |
| 9 Notifications | Reliability guide with live checks, maker steps and page fallbacks; paced prompt. | ReliabilityGuideTest, BackgroundWorkTest |

## Remaining (not proven yet)

- Real-device pass. Every order must still be confirmed on phones: a Xiaomi or Redmi (HyperOS), a Samsung (One UI 6+), a Tecno or Infinix, and one Pixel-like phone, plus Android TV. This sandbox has no emulator.
- Drama: ShortMax, DramaBite and FlexTV are hidden because their servers refuse playback (signed CDN links return 403, or the API is down). They come back automatically if their servers answer. A custom signer for DramaBite is the next attempt.
- Quality: the live YouTube test cannot run from the sandbox (YouTube blocks datacenter IPs). It needs a run on a home connection.
- Heat: measured only in code and tests. Next: `adb shell dumpsys thermalservice` and `dumpsys batterystats` during 30 minutes of video on a real phone, before and after.
- Notifications: the maker settings pages are well-known class names. Each one must be opened on a real phone of that brand, since makers rename them between versions (the guide falls back to App info if a page is missing).
- TV: the 1.8.1 TV preview APK builds (versionCode 16, all four ABIs), but it was not run on a TV or TV emulator.

## Future (proposed)

- Drama: own signer and player for DramaBite and ShortMax; downloads of whole drama seasons; vertical swipe-to-next like the studio apps.
- Quality: per-source quality memory ("this source is always 720p"), and an HDR-to-SDR tone-map level.
- Heat: show "Phone is warm, Yumeiro is saving power" in the player when HeatGuard steps in, with a setting to turn it off.
- Crash reports: optional one-tap "send to developer" by e-mail or GitHub issue (still never automatic).
- Notifications: detect after a week whether alerts actually arrived on time, and reopen the guide only when they did not.
- Library: listening stats page (per week, per artist) from the measured play time.
- Translations: Bangla first for the guides and Settings.
