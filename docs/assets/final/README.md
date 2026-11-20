# Final images: Yumeiro 1.9.0 release candidate

Screenshots taken by the device tests on CI emulators (labelled emulator, not real hardware). Content in the shots is
test data (a local test clip, a MockWebServer Vega repository, a fake Telegram channel); nothing is a mock-up of an
unshipped feature. Queued features have no image.

## Headline features

| File | Feature | Shows | Taken by |
|---|---|---|---|
| tv34-profiles-who-is-watching.png | Profiles and locks | "Who's watching?" on TV, a locked "Me" and a "Kid" profile | ProfilesDeviceTest, tv-api34 emulator (device-check run 44674391443) |
| lowram-profiles-who-is-watching.png | Profiles and locks (low-RAM) | the same screen on the 2 GB phone, light theme | ProfilesDeviceTest, api33-lowram emulator (run 44676427965) |
| tv34-profiles-pin-pad.png | Profiles and locks | PIN pad with "Forgot?" | ProfilesDeviceTest, tv-api34 emulator (run 44674391443) |
| (none) | Private downloads | no image: Private is screenshot-blocked on purpose (FLAG_SECURE), so every capture of it is black (runs 44673996183, 44674391443). Covered by ProfilesDeviceTest (Pass on all four targets) and OMT-45 | — |
| tv34-accent-ao.png | Accent colours | Appearance card with the Ao (blue) accent selected | ProfilesDeviceTest (accentTapChangesTheTheme), tv-api34 emulator (run 44676237678) |
| phone-hold-for-2x.png | Hold for 2x | player while the finger is held: the "2x »" pill at the top | LocalPlayerDeviceTest k_, api36 emulator (run 44673996183) |
| phone-dns-fallback.png | DNS fallback | Settings → Network with "Automatic DNS fallback" on (restored after the shot) | ReleaseShotsDeviceTest b_, api36 emulator (run 44673996183) |
| tv34-proxy.png | Own proxy | Proxy card, HTTP, an address typed (not saved) | ReleaseShotsDeviceTest a_, tv-api34 emulator (run 44676237678) |
| phone-telegram-channel.png | Telegram and share links | a public channel (test data from the mock server), no login | TelegramPublicDeviceTest, api36 emulator (run 44598214395) |
| lowram-script-repos.png | Script repos | a Vega repository (MockWebServer, localhost) with 2 providers available | ReleaseShotsDeviceTest c_, api33-lowram emulator (run 44673996183) |
| tv34-filter-sheet-no-keyboard.png | TV keyboard fix (FINAL-39) | "Show videos from" sheet open on TV, no keyboard on screen | PinnedExtensionsDeviceTest, tv-api34 emulator (run 44674391443) |
| changelog-card-1.9.0.png | Changelog card | the 1.9.0 headline list | `python3 scripts/changelog_card.py` (from CHANGELOG.md) |

## Earlier final-test images

| File | Shows | Taken by |
|---|---|---|
| phone-feed-home.png | Faster start: Home feed shows at once | LaunchFeed / FeedDeviceTest, api36 emulator (device-check run 44617327290) |
| phone-music-feed.png | Music feed | FeedDeviceTest, api36 emulator (device-check run 44617327290) |
| phone-video-source-menu.png | "Show videos from" source menu | FeedDeviceTest, api36 emulator (device-check run 44617327290) |
| phone-extensions-setting.png | Extensions load behind, quiet card (ADR-014) | Adr014DeviceTest, api36 emulator (device-check run 44617327290) |
| phone-player-more-menu.png | Player: more menu (speed, boost, external player) | LocalPlayerDeviceTest, api36 emulator (device-check run 44640073858) |
| phone-player-sleep-timer.png | Player: sleep timer | LocalPlayerDeviceTest, api36 emulator (device-check run 44640073858) |
| phone-player-fullscreen.png | Player: fullscreen | LocalPlayerDeviceTest, api36 emulator (device-check run 44640073858) |
| phone-manga-duplicates.png | Manga: duplicate titles merged | MangaDuplicateDeviceTest, api36 emulator (device-check run 44640073858) |
| phone-manga-tall-page.png | Manga: tall pages | MangaTallPageDeviceTest, api36 emulator (device-check run 44640073858) |
| phone-music-access.png | Music: access dialog | MusicAccessDeviceTest, api36 emulator (device-check run 44643028088) |
| phone-music-offline.png | Music mode offline | MusicModeNetworkDeviceTest, api36 emulator (device-check run 44643028088) |
| tv34-privacy-clear.png | TV (Android 14): privacy, clear watch history | DialogsDeviceTest, tv-api34 emulator (device-check run 44640840216) |
| tv34-crash-reports.png | TV (Android 14): crash reports | CrashUiDeviceTest, tv-api34 emulator (device-check run 44640840216) |
| lowram-feed-home.png | Low-RAM phone (Android 13): Home feed | FeedDeviceTest, api33-lowram emulator (device-check run 44617327290) |
