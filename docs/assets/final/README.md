# Final images: Yumeiro 1.9.0 release candidate

Screenshots taken by the device tests on CI emulators (labelled emulator, not real hardware), one per shipped feature that has a screen in the tests, plus a TV shot, a low-RAM shot and the changelog card. Queued features have no image. Features without a test screenshot are covered by the per-feature rows in docs/MASTER-CHECKLIST-FINAL.md.

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
| changelog-card-1.9.0.png | Changelog card | generated from CHANGELOG.md |
