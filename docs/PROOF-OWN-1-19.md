# Proof table: owner items OWN-1..19 (release 2.1.0)

Owner 2026-11-23: "the last 19 bugs are the most important, they must be fixed"; older lists too, except
extensions and Live TV m3u8 files (those come after the release). TV is left for later.

Words: Pass (run ID) = a test passed in that run. Unit = unit test in CI. Device = emulator test.
"Owner test" = only a real phone or a real account can close it; the code part is done.

| ID | What | Fix | Proof | Status |
|---|---|---|---|---|
| OWN-1 | Pages close or cut off (double tap) | NavGuard, flag own1_nav_guard | Unit Own1Own6OpenTest; device HomeNavigationDeviceTest pass 45045030577 | Pass (unit 45057299595; Back-after-reopen bug fixed in 3a18b985) |
| OWN-2 | Online stops after skipping the tour | Cloudflare DNS default (with OWN-7) | Unit Own7CloudflareDefaultTest | Pass in CI; home network: owner test OMT-83 |
| OWN-3 | Telegram sign-in with own API ID / hash | Built, flag f02_telegram_login on | Unit Own3TelegramKeysTest | Code done; full sign-in needs a real account (OMT-63) |
| OWN-4 | Vega / SkyStream links in the add box | UniversalAdd fallback | Unit Own4RepoDialogTest | Extensions: after the release (owner) |
| OWN-5 | Vega repos: installed count + shortcut | Extensions card | Unit Own5ScriptSourcesListTest | Extensions: after the release (owner) |
| OWN-6 | Yumix -> details slow | Page opens at once, loads behind | Unit Own1Own6OpenTest | Pass in CI; feel: OMT-89 |
| OWN-7 | Cloudflare as default network | DoH default, flag own7_cloudflare_default | Unit Own7CloudflareDefaultTest | Pass in CI |
| OWN-8 | Pick quality / subs / audio before download | Batch subtitle language | Unit Own8BatchSubLanguageTest; device PopupQualityDeviceTest pass 45045030577 | Pass |
| OWN-9 | Official Yumeiro source, none installed | Yumeiro Classics + Drama, now one source (OWN-13) | Unit Own13YumeiroHubTest | Pass in CI |
| OWN-10 | TeraBox / DiskWala | Share origin, retry, human check page | Unit TeraBoxApiTest | Code done; signed-in play needs a TeraBox account (owner) |
| OWN-11 | YouTube audio-only download | Popup always offers Video / Audio only | Unit Own11YtAudioChoiceTest | Pass in CI (OMT-87 on a phone) |
| OWN-12 | PIN reset questions; Remove PIN asks for current PIN | flag own12_pin_guard | Unit Own12PinGuardTest; device Own12PinGuardDeviceTest (run 45051247401) | Pass, both phones (api36 + api33 low-RAM) |
| OWN-13 | One all-in-one Yumeiro source | YumeiroHub, flag own13_yumeiro_hub | Unit Own13YumeiroHubTest, E1CatalogTest; device DramaPlaybackDeviceTest: Not checked (onshort.net unreachable from CI) | Unit Pass (45057299595); device Not checked |
| OWN-14 | Close animation like open | One enter/exit pair | Unit Own14PageMotionTest; device HomeNavigationDeviceTest pass 45045030577 | Pass |
| OWN-15 | Locked profile cold start stuck on logo | Skip second logo, safe decide/open | Unit Own15GateLogoTest; device ProfileColdStartDeviceTest pass 45045030577 (both phones) | Pass |
| OWN-16 | Upcoming: dynamic filters, search, filter icon | UpcomingFilter | Unit Own16UpcomingFilterTest; device F22CountryDeviceTest pass 45045030577, UpcomingDeviceTest pass 45047541362 (both phones) | Pass |
| OWN-17 | Cross-check of the lists | docs/OPEN-ITEMS-2.1.0.md + this file | - | Done |
| OWN-18 | Download popup: size, quality, sub / dub | DownloadSize | Unit Own18DownloadSizeTest; device PopupQualityDeviceTest pass 45045030577 | Pass |
| OWN-19 | Full screen: only main buttons | TopBarFit.essentials | Unit Own19PlayerEssentialsTest; device LocalPlayerDeviceTest.h_own19 pass 45048938663 (12/12) | Pass |

Not in 2.1.0, with reason:
- Built-in torrent engine stays off: B31FrameDeviceTest seek took 7.8 s (api36) and 13 s (api33-lowram) on the
  Sintel web seed, budget 5 s (run 45047541362). The limit is not raised.
- Extensions (OWN-4, OWN-5 device checks, ExtensionsDeviceTest e13 "five feeds") and Live TV m3u8: after the
  release, owner order 2026-11-23.
- TV runs: after the release, owner order 2026-11-23.


## Later runs (2026-11-23)

- 45051247401: Own12PinGuardDeviceTest OK on api36 and api33 low-RAM (after the first-run settle fix).
- 45049508477: Nf1EpisodeStatusDeviceTest skipped on api36 (onshort.net unreachable from CI); the low-RAM leg failed to build (Maven download error, not our code). NF1 on device: Not checked (onshort.net down). Unit tests cover it.
- 45057299595 (3a18b985): full CI green. 415 test classes, 1944 unit tests, 0 failed, 11 skipped (opt-in live-network and screenshot tests). Every Own* class passed.
- 45062571312 (38a9f6f5, Live TV store keeps version 2): CI green, 1944 unit tests, 0 failed.
- Upgrade v2.0.0 -> 2.1.0: phone PASS 45067511656 (0 lost, resumed, fatal 0, verifier 0); 45061470350 had failed on playlists.json version 2 -> 3 (fixed in 38a9f6f5). TV 45061494331: install Success, fatal 0, verifier 0; data compare Not checked (no root on the android-tv image).
