# Group 9 audit: Settings (R17)

Scope (master handover, Group 9): every setting from code with its default, persistence, migration,
real effect and reset; backup/restore and exclusions; theme and custom image (accepted types,
example shown); notification settings, Player info overlay, "Quality and audio"; walk the tree
with UI Automator and assert each effect; image picker with JPEG, PNG, WebP, HEIC, huge, tiny,
corrupt.

Branch `r15-stability`. Labels: unit = JVM/Robolectric, emulator = CI device-check, CI = GitHub
Actions. Status words as in `docs/DONE-LEDGER.md`.

## Method

1. Every `SharedPreferences` read in `app/src/main/java` was listed by a script (key, type,
   default, file and line; table at the end). Values read through non-literal keys are not in
   the table; the 40 `SettingsToggleRow` switches were listed separately and each has a consumer
   outside the settings UI (static check: the setter's StateFlow or pref is read by player,
   network, notification or download code).
2. Backup (`backup/Backup.kt`), restore (`PendingRestore`) and resets (`ResetCenter`) were read
   against that list: which section each value travels in and which reset clears it.
3. Device: `SettingsWalkDeviceTest` flips every switch on 12 pages, the process is killed
   (`scripts/device-check.sh` two phases) and phase b checks each one kept its value.
   `BackdropImportDeviceTest` imports real pictures through MediaStore.

## Problems found and fixed

| # | Problem | Root cause | Fix | Test |
|---|---|---|---|---|
| SE-1 | "Reset settings" says "Your library, sources and extensions stay" but erased music playlists, favourite songs and videos, device resume points, recently played, play counts, saved sites, listening time, Jump back in, the unsent Last.fm queue and source prefs. A Library-only restore left playlists and favourites out; a Settings-only restore replaced them. "Clear watch and search history" left device resume points, recently played songs, Jump back in and online searches | Two separate, incomplete lists (Backup `libraryPrefs`, ResetCenter `keep`) at whole-file level, while `mt_player_music_prefs` / `mt_player_video_prefs` hold library data next to settings; restore replaced whole files | One table `backup/PrefSections` gives the section of every value (file + key); export, stage, restore (replaces only the restored sections' keys) and both resets use it (de40e78) | `r17/SettingsResetTest` (4) |
| SE-2 | A 1000 × 100 picture was accepted as backdrop although the page says at least 200 × 200 px | `importPhoto` refused only when both sides were under 200 | Either side (commit "refuse a backdrop photo under 200 px…") | `BackdropImportDeviceTest` strip case |
| SE-3 | Typing a setting's own name in Settings search found nothing for 42 of 51 switches and actions | `SettingsCatalog.search` only looked at page title, summary and keywords, and every word must match | Each page lists the names of its switches and actions (`Category.settings`); a test scans the code so a new switch must be added | `r17/SettingsSearchCoverageTest` (4) |
| SE-4 | A cut-off / damaged JPEG was saved as the backdrop (grey lower half) | ImageDecoder refused it (SOURCE_INCOMPLETE) and `decodeScaled` fell through to BitmapFactory, which decodes partial data silently | DecodeException SOURCE_INCOMPLETE / SOURCE_MALFORMED_DATA → `BrokenPicture` → "That picture couldn't be opened: the file is damaged or incomplete. Try another copy." (no BitmapFactory retry) | `BackdropImportDeviceTest` corrupt.jpg (run 43880703410 showed the bug) |

## Checked and not a bug (or kept on purpose)

- Secrets never travel: `online_secrets`, `yt_account`, `site_logins`, `mt_oauth_guard`, FCM and
  UnifiedPush state, updater and backup bookkeeping are excluded from backups and kept by every
  reset (BK-1, `BackupV2Test#deviceBoundPrefsNeverTravel`).
- `privacy` (incognito, app lock, hide in recents) is device-bound: not in backups and not cleared
  by "Reset settings". Kept: a reset should not remove the app lock. Owner may decide otherwise.
- Resets are staged and applied at the next launch before anything loads
  (`MTApplication` → `PendingRestore.applyIfAny` before `PrefsWarmup`), so no in-memory copy
  writes an old value back.
- Restore of a backup made before R17: pref files are matched by name and key, so old backups
  restore into the right sections without a format change (no migration needed).
- Backdrop picture accepted types are shown on the page (JPEG, PNG, WebP, GIF first frame, BMP,
  HEIC/HEIF on Android 9+, AVIF on Android 12+, up to 60 MB, at least 200 × 200 px).

## Verification

| Item | Evidence | Label | Status |
|---|---|---|---|
| SE-1 | `SettingsResetTest` | CI | 4/4 in CI 43877618176 and 43881282527 |
| SE-2, S26 image types | `BackdropImportDeviceTest` | emulator | run 43880703410 (both phones): JPEG, PNG, WebP, HEIC, AVIF, GIF, BMP, huge (720×540 saved), tiny/strip refused (200 px), text-as-jpg "isn't supported", 64 MB "over 60 MB" all as expected; corrupt.jpg (a.jpg cut at 9000 bytes) was ACCEPTED → SE-4 fixed; rerun 43891331959 PASS problems=0 on both phones |
| S27 every switch persists across a kill | `SettingsWalkDeviceTest` | emulator | PASS run 43880703410: `SW_RESULT PASS verified=31` on api33-lowram and api36 (all 31 switches flipped, app killed, value kept). Earlier 43875418000 / 43877623222 were test errors, fixed |
| SE-3, S23 search | `SettingsSearchCoverageTest` | CI | 4/4 in CI 43881282527 (1086 tests, 0 failures) |
| Player info overlay, Resume playback, Anime4K (real effect on the player) | `PlayerSettingsEffectDeviceTest` a/b/c | emulator | PASS run 43880703410: PFX_RESULT PASS a, b, c on api33-lowram and api36 |
| Notification switches (6 kinds, master, chime): real effect on Android's active notifications | `NotifySettingsEffectDeviceTest` a/b/c | emulator | PASS run 43880703410: NFX_RESULT PASS a, b, c on both phones |
| S1–S22 switch effects (static map) | `scripts/settings-effects.py` → docs/evidence/round3/settings-effects.md: 41 switch rows; the generator follows `Obj.setX` setters (34); the 7 inline ones were read by hand: notification master/kinds/chime (NotifySettingsEffectDeviceTest), Instant alerts (`FcmPush.start` reads Key.FCM), In-app reminders (`Reminders.due`), Background checks + Wi-Fi only (`BackgroundWork.apply` cancels/schedules, `constraints` UNMETERED), Save loaded pages (`DataCache.read/write` gate), Reliable alerts (UnifiedPush card) | review | done |
| Effects with no test before: background checks, Wi-Fi only, reminders, page cache, instant alerts | `BackgroundSettingsEffectDeviceTest` a–e | emulator | PASS a–e both phones, 43891331959 |
| Remaining switches: backup_auto, keep_default, adult_private, yt_to_phone, cc_default, bg_play, auto_pip, lyrics_online, skip_intro, auto_skip, push_alerts (SE-5) | More/PlayerExtras/OnlineSwitches/PushAlerts effect tests | emulator | PASS both phones, 43904330191 and 43920651380 (16/16) |
| Choice rows (SE-6): qa_speed, qa_quality, qa_renderer, qa_decoder, qa_player, backup_auto_days (device); cache_fresh, cache_keep, cache_max, cookie_days, qa_audio, backup_auto_keep (unit); qa_gpu_tier → OMT-16 (real GPU) | `ChoiceSettingsEffectDeviceTest`, `ChoiceSettingsEffectTest` | unit + emulator | unit 6/6 CI 43953320911; device a–f PASS both phones 43974089878 (b closed by one tap after ST-21 fix); qa_gpu_tier → OMT-16 |
| Backdrop intensity slider (`backdrop_intensity`) | `BackdropIntensityEffectTest` (scrim and glow follow the value; kept and clamped) | unit | 3/3 CI 43953320911 |

## Preference inventory (generated)

Class: library / sources / setting / app state (in the settings section of backups, reset with
settings) / device-only. Generated from code on 2026-11-16 by a regex over `get<Type>("key",
default)`; one row per read.

| Pref file | Key | Type | Default | Class (backup / reset) | Read at |
|---|---|---|---|---|---|
| `app_feed` | `last_at` | Long | 0L | app state (settings section) | `notify/AppFeed.kt:87` |
| `background_work,discovery_state` | `catch_up_at` | Long | 0L | app state (settings section) | `notify/DiscoveryWorker.kt:82` |
| `background_work,discovery_state` | `first_seen_at` | Long | now | app state (settings section) | `notify/DiscoveryWorker.kt:152` |
| `background_work,discovery_state` | `last_run_at` | Long | 0L | app state (settings section) | `notify/DiscoveryWorker.kt:130` |
| `background_work,discovery_state` | `nudge_at` | Long | 0L | app state (settings section) | `notify/DiscoveryWorker.kt:209` |
| `background_work,discovery_state` | `push_wake_at` | Long | 0L | app state (settings section) | `notify/DiscoveryWorker.kt:106` |
| `background_work,discovery_state` | `schedule_version` | Int | 0 | app state (settings section) | `notify/DiscoveryWorker.kt:62` |
| `backup_meta` | `auto_code` | Boolean | false | device-only (not in backup, kept by every reset) | `backup/PendingRestore.kt:175` |
| `backup_meta` | `auto_days` | Int | 1 | device-only (not in backup, kept by every reset) | `backup/PendingRestore.kt:171` |
| `backup_meta` | `auto_enabled` | Boolean | true | device-only (not in backup, kept by every reset) | `backup/PendingRestore.kt:169` |
| `backup_meta` | `auto_keep` | Int | 5 | device-only (not in backup, kept by every reset) | `backup/PendingRestore.kt:173` |
| `backup_meta` | `folder_uri` | String | null | device-only (not in backup, kept by every reset) | `backup/PendingRestore.kt:184` |
| `backup_meta` | `last_auto_at` | Long | 0 | device-only (not in backup, kept by every reset) | `backup/PendingRestore.kt:177` |
| `backup_meta` | `last_auto_fp` | String | null | device-only (not in backup, kept by every reset) | `backup/PendingRestore.kt:178` |
| `backup_meta` | `last_backup_at` | Long | 0 | device-only (not in backup, kept by every reset) | `backup/PendingRestore.kt:180` |
| `backup_meta` | `last_restore_text` | String | null | device-only (not in backup, kept by every reset) | `backup/PendingRestore.kt:157` |
| `backup_meta` | `last_restore_text` | String | null | device-only (not in backup, kept by every reset) | `backup/PendingRestore.kt:160` |
| `backup_meta` | `pending_redownload` | Boolean | false | device-only (not in backup, kept by every reset) | `backup/PendingRestore.kt:163` |
| `backup_meta` | `pending_tachiyomi` | String | "[]" | device-only (not in backup, kept by every reset) | `backup/PendingRestore.kt:164` |
| `backup_meta` | `show_restore_note` | Boolean | false | device-only (not in backup, kept by every reset) | `backup/PendingRestore.kt:155` |
| `browser_check` | `ua` | String | null | app state (settings section) | `online/web/BrowserCheck.kt:109` |
| `browser_check` | `ua_build` | String | null | app state (settings section) | `online/web/BrowserCheck.kt:110` |
| `cloud_alerts` | `failures` | Int | 0 | app state (settings section) | `notify/CloudAlerts.kt:84` |
| `cloud_alerts` | `fp` | String | null | app state (settings section) | `notify/CloudAlerts.kt:72` |
| `cloud_alerts` | `ids` | String | null | app state (settings section) | `notify/CloudAlerts.kt:56` |
| `cloud_alerts` | `retry_at` | Long | 0L | app state (settings section) | `notify/CloudAlerts.kt:53` |
| `cloud_alerts` | `synced_at` | Long | 0L | app state (settings section) | `notify/CloudAlerts.kt:72` |
| `data_cache` | `cookie_days` | Int | 30 | app state (settings section) | `cache/DataCache.kt:67` |
| `data_cache` | `enabled` | Boolean | true | app state (settings section) | `cache/DataCache.kt:60` |
| `data_cache` | `fresh_hours` | Int | 6 | app state (settings section) | `cache/DataCache.kt:62` |
| `data_cache` | `keep_days` | Int | 7 | app state (settings section) | `cache/DataCache.kt:65` |
| `data_cache` | `last_cookie_clear` | Long | 0 | app state (settings section) | `cache/DataCache.kt:82` |
| `data_cache` | `last_janitor` | Long | 0 | app state (settings section) | `cache/DataCache.kt:79` |
| `data_cache` | `max_mb` | Int | 128 | app state (settings section) | `cache/DataCache.kt:68` |
| `download_settings` | `ask_first` | Boolean | true | setting | `online/download/DownloadSettings.kt:64` |
| `download_settings` | `auto_delete_days` | Int | 0 | setting | `online/download/DownloadSettings.kt:59` |
| `download_settings` | `downloader` | String | null | setting | `online/download/DownloadSettings.kt:66` |
| `download_settings` | `keep_default` | Boolean | false | setting | `online/download/DownloadSettings.kt:62` |
| `download_settings` | `parallel` | Int | 2 | setting | `online/download/DownloadSettings.kt:61` |
| `download_settings` | `quality` | Int | 0 | setting | `online/download/DownloadSettings.kt:60` |
| `download_settings` | `show_tips` | Boolean | true | setting | `online/download/DownloadSettings.kt:65` |
| `download_settings` | `yt_to_phone` | Boolean | true | setting | `online/download/DownloadSettings.kt:63` |
| `extension_popups` | `card_$extKey` | Long | 0L | setting | `online/ExtensionPopups.kt:152` |
| `extension_popups` | `mode` | String | null | setting | `online/ExtensionPopups.kt:83` |
| `fcm_push` | `failures` | Int | 0 | device-only (not in backup, kept by every reset) | `notify/FcmPush.kt:185` |
| `fcm_push` | `fid` | String | null | device-only (not in backup, kept by every reset) | `notify/FcmPush.kt:207` |
| `fcm_push` | `fid` | String | null | device-only (not in backup, kept by every reset) | `notify/FcmPush.kt:99` |
| `fcm_push` | `fp` | String | null | device-only (not in backup, kept by every reset) | `notify/FcmPush.kt:176` |
| `fcm_push` | `retry_at` | Long | 0L | device-only (not in backup, kept by every reset) | `notify/FcmPush.kt:177` |
| `fcm_push` | `synced_at` | Long | 0L | device-only (not in backup, kept by every reset) | `notify/FcmPush.kt:176` |
| `fcm_push` | `topics` | StringSet | emptySet( | device-only (not in backup, kept by every reset) | `notify/FcmPush.kt:155` |
| `fcm_push` | `topics` | StringSet | emptySet( | device-only (not in backup, kept by every reset) | `notify/FcmPush.kt:210` |
| `fcm_push` | `topics` | StringSet | null | device-only (not in backup, kept by every reset) | `notify/FcmPush.kt:207` |
| `home_layout` | `hidden_v5` | StringSet | emptySet( | setting | `ui/screens/home/HomeSections.kt:107` |
| `home_servers` | `device` | String | null | sources | `sources/servers/HomeServers.kt:68` |
| `lyrics_online` | `enabled` | Boolean | true | setting | `lyrics/LrcLib.kt:41` |
| `lyrics_online` | `miss:$key` | Long | 0L | setting | `lyrics/LrcLib.kt:120` |
| `manga_home,manga_reader` | `source` | String | null | library; setting | `ui/screens/manga/MangaScreens.kt:139` |
| `manga_home,manga_reader` | `vertical` | Boolean | true | library; setting | `ui/screens/manga/MangaScreens.kt:496` |
| `mt_appearance` | `ambient_artwork` | Boolean | true | setting | `ui/theme/ThemeSettings.kt:68` |
| `mt_appearance` | `amoled_black` | Boolean | false | setting | `ui/theme/ThemeSettings.kt:67` |
| `mt_appearance` | `backdrop_intensity` | Float | 1f | setting | `ui/theme/DreamBackdrop.kt:74` |
| `mt_appearance` | `backdrop_photo_version` | Long | 1L | setting | `ui/theme/DreamBackdrop.kt:75` |
| `mt_appearance` | `backdrop_style` | String | BackdropStyle.SCENE.name | setting | `ui/theme/DreamBackdrop.kt:73` |
| `mt_appearance` | `kana_accents` | Boolean | true | setting | `ui/theme/ThemeSettings.kt:69` |
| `mt_appearance` | `theme_mode` | String | ThemeMode.SYSTEM.name | setting | `ui/theme/ThemeSettings.kt:66` |
| `mt_network` | `dns_auto` | Boolean | true | setting | `network/NetworkSettings.kt:101` |
| `mt_network` | `dns_bypass_hosts` | StringSet | emptySet( | setting | `network/NetworkSettings.kt:104` |
| `mt_network` | `dns_mode` | String | null | setting | `network/NetworkSettings.kt:100` |
| `mt_network` | `dns_provider` | String | null | setting | `network/NetworkSettings.kt:98` |
| `mt_network` | `dns_transport` | String | null | setting | `network/NetworkSettings.kt:99` |
| `mt_player` | `auto_pip` | Boolean | true | setting | `player/PlayerSettings.kt:38` |
| `mt_player` | `back_action` | String | null | setting | `player/PlayerSettings.kt:40` |
| `mt_player` | `back_action_v2` | String | null | setting | `player/PlayerSettings.kt:40` |
| `mt_player` | `background_play` | Boolean | true | setting | `player/PlayerSettings.kt:37` |
| `mt_player` | `captions_default` | Boolean | true | setting | `player/PlayerSettings.kt:39` |
| `mt_player` | `subtitle_language` | String | "eng" | setting | `player/PlayerSettings.kt:41` |
| `mt_player_eq_prefs` | `$KEY_BAND_PREFIX$bandIndex` | Int | 0 | setting | `player/equalizer/EqualizerManager.kt:133` |
| `mt_player_eq_prefs` | `$KEY_BAND_PREFIX${band.bandIndex}` | Int | band.levelMb.toInt( | setting | `player/equalizer/EqualizerManager.kt:83` |
| `mt_player_eq_prefs` | `eq_bass_boost` | Int | 0 | setting | `player/equalizer/EqualizerManager.kt:72` |
| `mt_player_eq_prefs` | `eq_enabled` | Boolean | false | setting | `player/equalizer/EqualizerManager.kt:70` |
| `mt_player_eq_prefs` | `eq_preset` | String | "Normal" | setting | `player/equalizer/EqualizerManager.kt:71` |
| `mt_player_eq_prefs` | `eq_virtualizer` | Int | 0 | setting | `player/equalizer/EqualizerManager.kt:73` |
| `mt_player_music_prefs,mt_player_video_prefs` | `favorite_track_ids` | StringSet | emptySet( | library; setting | `data/repository/MediaRepository.kt:191` |
| `mt_player_music_prefs,mt_player_video_prefs` | `favorite_video_ids` | StringSet | emptySet( | library | `data/repository/MediaRepository.kt:106` |
| `mt_player_music_prefs,mt_player_video_prefs` | `hidden_video_folders` | StringSet | emptySet( | library; setting | `data/repository/MediaRepository.kt:111` |
| `mt_player_music_prefs,mt_player_video_prefs` | `play_counts_json` | String | null | library; setting | `data/repository/MediaRepository.kt:233` |
| `mt_player_music_prefs,mt_player_video_prefs` | `playlists_json` | String | null | library; setting | `data/repository/MediaRepository.kt:209` |
| `mt_player_music_prefs,mt_player_video_prefs` | `recently_played_ids` | String | null | library; setting | `data/repository/MediaRepository.kt:198` |
| `mt_streams` | `history` | String | null | setting | `stream/StreamRepository.kt:45` |
| `mt_subtitle_style` | `bold` | Boolean | false | setting | `player/SubtitleStyleSettings.kt:69` |
| `mt_subtitle_style` | `color` | String | null | setting | `player/SubtitleStyleSettings.kt:67` |
| `mt_subtitle_style` | `edge` | String | null | setting | `player/SubtitleStyleSettings.kt:68` |
| `mt_subtitle_style` | `keep_file` | Boolean | true | setting | `player/SubtitleStyleSettings.kt:71` |
| `mt_subtitle_style` | `lift` | Float | 0f | setting | `player/SubtitleStyleSettings.kt:70` |
| `mt_subtitle_style` | `size` | Float | 1f | setting | `player/SubtitleStyleSettings.kt:66` |
| `mt_subtitle_style` | `system` | Boolean | true | setting | `player/SubtitleStyleSettings.kt:65` |
| `mt_updater` | `auto_check` | Boolean | true | device-only (not in backup, kept by every reset) | `update/UpdatePreferences.kt:16` |
| `mt_updater` | `download_id` | Long | -1L | device-only (not in backup, kept by every reset) | `update/UpdatePreferences.kt:49` |
| `mt_updater` | `download_release` | String | null | device-only (not in backup, kept by every reset) | `update/UpdatePreferences.kt:53` |
| `mt_updater` | `download_silent` | Boolean | false | device-only (not in backup, kept by every reset) | `update/UpdatePreferences.kt:57` |
| `mt_updater` | `last_check` | Long | 0L | device-only (not in backup, kept by every reset) | `update/UpdatePreferences.kt:33` |
| `mt_updater` | `pending_updated_version` | String | null | device-only (not in backup, kept by every reset) | `update/UpdatePreferences.kt:29` |
| `mt_updater` | `snooze_until` | Long | 0L | device-only (not in backup, kept by every reset) | `update/UpdatePreferences.kt:43` |
| `mt_updater` | `snooze_version` | String | null | device-only (not in backup, kept by every reset) | `update/UpdatePreferences.kt:43` |
| `new_episodes` | `last_check_at` | Long | 0L | app state (settings section) | `online/NewEpisodesWorker.kt:51` |
| `notify_pacer` | `last_shown` | Long | 0L | app state (settings section) | `notify/NotifyPacer.kt:66` |
| `notify_pacer` | `last_shown` | Long | 0L | app state (settings section) | `notify/NotifyPacer.kt:83` |
| `notify_pacer` | `queue` | String | null | app state (settings section) | `notify/NotifyPacer.kt:132` |
| `online` | `online_search_history` | String | "" | library | `ui/screens/online/OnlineSearchScreen.kt:272` |
| `online_secrets,online_settings` | `adult` | Boolean | false | device-only (not in backup, kept by every reset); setting | `online/OnlineSettings.kt:60` |
| `online_secrets,online_settings` | `adult_private` | Boolean | true | device-only (not in backup, kept by every reset); setting | `online/OnlineSettings.kt:61` |
| `online_secrets,online_settings` | `auto_skip` | Boolean | false | device-only (not in backup, kept by every reset); setting | `online/OnlineSettings.kt:63` |
| `online_secrets,online_settings` | `skip_intro` | Boolean | true | device-only (not in backup, kept by every reset); setting | `online/OnlineSettings.kt:62` |
| `online_secrets,online_settings` | `subtitle_lang` | String | null | device-only (not in backup, kept by every reset); setting | `online/OnlineSettings.kt:64` |
| `permission_center` | `notif_prompt_count` | Int | 0 | app state (settings section) | `ui/screens/extras/PermissionCenter.kt:110` |
| `permission_center` | `notif_prompt_count` | Int | 0 | app state (settings section) | `ui/screens/extras/PermissionCenter.kt:116` |
| `permission_center` | `notif_prompt_version` | Int | -1 | app state (settings section) | `ui/screens/extras/PermissionCenter.kt:110` |
| `pinned_extensions` | `pinned` | String | null | sources | `online/PinnedExtensions.kt:29` |
| `play_counts` | `entries` | String | null | library | `data/PlayCounts.kt:54` |
| `privacy` | `hide_recents` | Boolean | false | device-only (not in backup, kept by every reset) | `security/Privacy.kt:54` |
| `privacy` | `incognito` | Boolean | false | device-only (not in backup, kept by every reset) | `security/Privacy.kt:51` |
| `privacy` | `lock` | Boolean | false | device-only (not in backup, kept by every reset) | `security/Privacy.kt:52` |
| `privacy` | `lock_after` | Int | 60 | device-only (not in backup, kept by every reset) | `security/Privacy.kt:53` |
| `provider_overrides` | `url:$api` | String | null | sources | `online/ProviderOverrides.kt:26` |
| `provider_overrides` | `url:${api.name}` | String | null | sources | `online/ProviderOverrides.kt:44` |
| `push_wake` | `enabled` | Boolean | false | device-only (not in backup, kept by every reset) | `notify/PushWake.kt:140` |
| `push_wake` | `error` | String | null | device-only (not in backup, kept by every reset) | `notify/PushWake.kt:147` |
| `push_wake` | `last_wake_at` | Long | 0L | device-only (not in backup, kept by every reset) | `notify/PushWake.kt:145` |
| `push_wake` | `pending` | StringSet | emptySet( | device-only (not in backup, kept by every reset) | `notify/PushWake.kt:219` |
| `push_wake` | `refill_at` | Long | 0L | device-only (not in backup, kept by every reset) | `notify/PushWake.kt:237` |
| `push_wake` | `schedule` | String | null | device-only (not in backup, kept by every reset) | `notify/PushWake.kt:144` |
| `push_wake` | `schedule` | String | null | device-only (not in backup, kept by every reset) | `notify/PushWake.kt:258` |
| `reliability_guide` | `shown` | Int | 0 | app state (settings section) | `notify/ReliabilityGuide.kt:190` |
| `reliability_guide` | `shown` | Int | 0 | app state (settings section) | `notify/ReliabilityGuide.kt:192` |
| `reliability_guide` | `shown_at` | Long | 0L | app state (settings section) | `notify/ReliabilityGuide.kt:190` |
| `reliability_guide` | `vendor_done` | Boolean | false | app state (settings section) | `notify/ReliabilityGuide.kt:174` |
| `reminders` | `follow_${social.name}` | Int | 0 | library | `notify/Reminders.kt:110` |
| `reminders` | `follow_${social.name}` | Int | 0 | library | `notify/Reminders.kt:113` |
| `reminders` | `follow_clicks` | Int | 0 | library | `notify/Reminders.kt:109` |
| `reminders` | `follow_clicks` | Int | 0 | library | `notify/Reminders.kt:92` |
| `reminders` | `installed_at` | Long | now | library | `notify/Reminders.kt:89` |
| `reminders` | `last_${k.name}` | Long | -1L | library | `notify/Reminders.kt:91` |
| `reminders` | `last_any` | Long | 0L | library | `notify/Reminders.kt:90` |
| `reminders` | `star_clicks` | Int | 0 | library | `notify/Reminders.kt:115` |
| `reminders` | `star_clicks` | Int | 0 | library | `notify/Reminders.kt:93` |
| `scrobble` | `enabled` | Boolean | true | library | `scrobble/Scrobbler.kt:46` |
| `scrobble` | `queue` | String | "[]" | library | `scrobble/Scrobbler.kt:47` |
| `scrobble` | `queue` | String | "[]" | library | `scrobble/Scrobbler.kt:82` |
| `setup_guide` | `card_hidden` | Boolean | false | app state (settings section) | `ui/screens/extras/SetupGuide.kt:135` |
| `setup_guide` | `onboarding` | String | null | app state (settings section) | `ui/screens/extras/SetupGuide.kt:136` |
| `setup_guide` | `seen` | Boolean | false | app state (settings section) | `ui/screens/extras/SetupGuide.kt:141` |
| `setup_guide` | `visited` | StringSet | emptySet( | app state (settings section) | `ui/screens/extras/SetupGuide.kt:134` |
| `site_logins` | `hosts` | StringSet | emptySet( | device-only (not in backup, kept by every reset) | `online/web/SiteLogins.kt:28` |
| `starter_extensions` | `done_v1` | Boolean | false | app state (settings section) | `online/StarterExtensions.kt:38` |
| `starter_extensions` | `tries_v1` | Int | 0 | app state (settings section) | `online/StarterExtensions.kt:40` |
| `tachiyomi_bridge` | `trusted` | StringSet | null | sources | `sources/tachiyomi/TachiyomiExtensions.kt:135` |
| `tachiyomi_bridge` | `trusted_v2` | String | null | sources | `sources/tachiyomi/TachiyomiExtensions.kt:132` |
| `watch_prefs` | `auto_next` | Boolean | true | setting | `ui/screens/watch/WatchKit.kt:106` |
| `watch_prefs` | `auto_play` | Boolean | true | setting | `ui/screens/watch/WatchKit.kt:105` |
| `watch_prefs` | `preferred_audio` | String | null | setting | `online/AudioLanguages.kt:207` |
| `yt_account` | `mode` | String | null | device-only (not in backup, kept by every reset) | `youtube/YtAccount.kt:56` |
| `yt_account` | `since` | Long | 0L | device-only (not in backup, kept by every reset) | `youtube/YtAccount.kt:55` |
| `yt_history` | `items` | String | null | library | `youtube/YtHistory.kt:28` |
| `yt_topics` | `shuffle` | Long | 0L | app state (settings section) | `youtube/YtDiscovery.kt:154` |
| `yt_topics` | `shuffle` | Long | 0L | app state (settings section) | `youtube/YtDiscovery.kt:157` |
| `yu_audio_quality` | `mobile` | String | null | setting | `player/AudioQuality.kt:49` |
| `yu_audio_quality` | `wifi` | String | null | setting | `player/AudioQuality.kt:48` |
| `yu_last_played` | `audio` | String | null | library | `player/LastPlayed.kt:38` |
| `yu_last_played` | `yt` | String | null | library | `player/LastPlayed.kt:39` |
| `yu_nav` | `shortcuts` | String | null | setting | `ui/navigation/NavPrefs.kt:114` |
| `yu_nav` | `tabs` | String | null | setting | `ui/navigation/NavPrefs.kt:113` |
| `yu_sites` | `sites` | String | null | library | `online/web/SavedSites.kt:38` |
| `yu_sort` | `tracks` | String | null | setting | `ui/viewmodel/MainViewModel.kt:128` |
| `yu_sort` | `videos` | String | null | setting | `ui/viewmodel/MainViewModel.kt:133` |
| `yu_torrent` | `server` | String | "" | setting | `stream/LinkHosts.kt:167` |
| `yu_video_prefs` | `anime4k` | Boolean | false | setting | `player/VideoPrefs.kt:102` |
| `yu_video_prefs` | `audio` | String | null | setting | `player/VideoPrefs.kt:98` |
| `yu_video_prefs` | `controls` | String | null | setting | `player/VideoPrefs.kt:105` |
| `yu_video_prefs` | `decoder` | String | null | setting | `player/VideoPrefs.kt:100` |
| `yu_video_prefs` | `default_player` | String | null | setting | `player/VideoPrefs.kt:104` |
| `yu_video_prefs` | `gpu_tier` | String | null | setting | `player/VideoPrefs.kt:103` |
| `yu_video_prefs` | `hidden_controls` | String | null | setting | `player/VideoPrefs.kt:106` |
| `yu_video_prefs` | `info_fields` | String | null | setting | `player/VideoPrefs.kt:109` |
| `yu_video_prefs` | `info_overlay` | Boolean | false | setting | `player/VideoPrefs.kt:108` |
| `yu_video_prefs` | `quality` | String | null | setting | `player/VideoPrefs.kt:97` |
| `yu_video_prefs` | `renderer` | String | null | setting | `player/VideoPrefs.kt:101` |
| `yu_video_prefs` | `resume` | Boolean | true | setting | `player/VideoPrefs.kt:107` |
| `yu_video_prefs` | `speed` | Float | 1f | setting | `player/VideoPrefs.kt:99` |
| `yumeiro_crashlog` | `last_exit` | Long | 0L | app state (settings section) | `diagnostics/CrashLog.kt:142` |
| `yumeiro_drama` | `enabled` | Boolean | true | app state (settings section) | `sources/drama/YumeiroDrama.kt:57` |
| `yumeiro_listening` | `music_ms` | Long | 0L | library | `player/ListeningTime.kt:40` |
| `yumeiro_tv_mode` | `layout` | String | null | device-only (not in backup, kept by every reset) | `ui/tv/TvMode.kt:40` |

Note: the settings bug IDs were first written ST-1 / ST-2 (commit de40e78, ST-2 commit). They clashed with the older stability rows ST-1 … ST-20, so they are SE-1 / SE-2 from now on.

Observation (not changed, owner product call): a kind switch that is off (e.g. "New episodes")
drops the item completely, also from the inbox (`AppNotifier.post` returns before `Inbox.add`),
while "Show notifications" off keeps items in the inbox. The rows' text does not promise either way.

## Close-out (2026-11-17)

Switch effects 34/34 and choice rows (SE-6) have tests with device (emulator) or CI evidence;
`ChoiceSettingsEffectDeviceTest` a–f PASS on both phones (43974089878). qa_gpu_tier needs a real GPU:
owner test OMT-16. Group 9 is closed for agent work.


## v1.8.2 full audit (2026-11-18, master prompt §7, owner rule §13a)

Head at the read: d3c46a0 (settings/backup/network code unchanged since the R17 close). Scope added by the
owner on 2026-11-18: NBUG-7 (restore after "Clear all data": extensions listed but not usable; duplicate
names across repositories and ecosystems) and NBUG-9 (fresh install: nothing online loads until DNS is set
to Cloudflare). Skills: architecture-designer (ADR-021 before code), kotlin-specialist (every Kotlin change),
rea when stuck. UI: no visual change planned; if one becomes needed, ui-ux-pro-max is read first.

How the files were read (honest note): logic files line by line. Compose UI files were read with pure
layout lines (imports, `modifier =`, colours, sizes, closing brackets) filtered out, so every state read,
setter, side effect, coroutine and dialog was read but not every padding value. SettingsDesign.kt (visual
building blocks) and the long help texts in UserGuide.kt / SetupGuides.kt were read for claims about
behaviour only (each claim about backup, DNS and extensions was checked against code).

### Files and lines reviewed

| File | Lines | Notes |
|---|---|---|
| network/NetworkSettings.kt, NetworkClient.kt, AutoBypassInterceptor.kt, SmartDns.kt, Connectivity.kt, DnsMessage.kt, BoundedRead.kt | 1–126, 1–97, 1–53, 1–265, 1–72, 1–195, 1–24 | DNS config, client, auto bypass (sinkhole + markBlocked), resolver chain, online flag, wire DNS |
| backup/Backup.kt, PrefSections.kt, PendingRestore.kt, BackupStore.kt, AutoBackupWorker.kt, ResetCenter.kt, Restart.kt | all | sections, export/stage/apply, auto backups, resets, restart |
| ui/screens/settings/BackupCenterScreen.kt | 1–497 (logic) | restore flow, safety backup, section picker |
| online/ExtensionManager.kt | start, repairMissing, ensureLoaded, install*, gate, installBytes, uninstall (≈125–430) | CloudStream engine |
| online/ExtensionReview.kt, ExtensionScanner.kt (scan) | all / 173–215 | AUTO/IF_RISKY/ALWAYS, DANGER rules |
| sources/tachiyomi/TachiyomiExtensions.kt | trust (≈120–143), start, reinstallAfterRestore (≈145–172), install, load | Aniyomi / Mihon bridge |
| sources/js/JsSources.kt | 40–170 | Mangayomi JS lists, install, engine |
| sources/SourceHub.kt uniqueName, sources/stremio/StremioAddons.kt file | 71–78, 85 | duplicate names, add-on list |
| ui/screens/settings/*.kt (21 files) | all (logic filter above) | every setting row and action |

### Data flow (backup and restore of sources)

```
Backup.export(sections) ─ prefs.json (PrefSections per value) + files/ (sectionOf per path) ─ zip
  files/extensions/state.json → SOURCES     files/extensions/plugins/*.cs3 → EXTENSION_FILES
  files/tachiyomi/*.ext → EXTENSION_FILES   tachiyomi_bridge prefs (repos, trusted_v2 sealed) → SOURCES
  files/js/** → EXTENSION_FILES (isCode)    files/stremio/addons.json → SOURCES
Restore: Backup.stage(chosen; default without EXTENSION_FILES) → Restart.now → PendingRestore.applyIfAny
  (Application.onCreate) → ExtensionManager.start → ensureLoaded → repairMissing (once per process)
  → TachiyomiExtensions.start → reinstallAfterRestore (once per process) · JsSources.start (no repair)
```

### Action → reaction

| Action | Expected reaction | Checked by |
|---|---|---|
| Default backup → Clear all data → restore | CloudStream, Aniyomi/Mihon, JS and Stremio sources listed and usable after the downloads | **G9-1** JS lists lost; **G9-2** no retry |
| First launch after the restore has no network (or the repo times out once) | extensions downloaded when the network is back | **G9-2** (was: only at the next app start) |
| Restored extension the user had accepted after a safety warning | downloaded again (same file, already accepted) | **G9-5** (was: refused silently on every start) |
| Same extension from two repositories, one uninstalled | the other keeps working | **G9-7** (was: shared file deleted) |
| Fresh install, ISP resolver failing | online content loads with the default setting | NBUG-9 probe (below) |
| Aniyomi / Mihon extension restored on a new phone | listed; signed by its repo's key → trusted; others need one Trust tap | G9-4: by design (Mihon `Preference.appStateKey("trusted_extensions")`, not in backups) |

### Findings register

| ID | Finding | Root cause (file:line at d3c46a0) | Fix (ADR-021) | Test (fails on the old code) |
|---|---|---|---|---|
| G9-1 | A default backup (Extension files unticked) loses every Mangayomi JS repository and installed JS source; after a restore the JS list is empty and nothing downloads it again | `Backup.isCode` (96–100) counts every `js/` path as code, so `js/repos.json` and `js/sources.json` go to EXTENSION_FILES (`sectionOf` 86) although `js` is in `dataDirs` (69, meant for SOURCES); JsSources has no repair for a missing `.js` | lists → SOURCES, only `js/*.js` is code; JsSources downloads missing code of listed sources (same check as install) | unit `G9SettingsBatchTest#g9_1_jsListsTravelWithSources`; device `RestoreAfterClearDeviceTest` c (JS repo and source back, code present) |
| G9-2 | After a restore, extensions that could not be downloaded at the first start (no network yet, DNS failing, one timeout) stay listed but missing until the app is closed and opened again; no message | `ExtensionManager.start` (128–139) calls `repairMissing` once per process; `repairMissing` (158–162) returns at once when offline; `TachiyomiExtensions.start` (145–152) / `reinstallAfterRestore` (161) the same | one restore-repair loop per ecosystem: run at start, again when `Connectivity.online` turns true, and with back-off (30 s, 2 min, then 10 min; at most 12 tries per process) while anything is missing | device `RestoreAfterClearDeviceTest` c (repo answers 503 to the first request → extension back without a restart) |
| G9-5 | An extension the user accepted after a safety warning is refused on every start after a restore, silently | `repairMissing` (169) uses `ExtensionReview.Mode.AUTO`; `autoDecision` refuses DANGER/unreadable | repair asks the user (IF_RISKY) when the review host is on screen; otherwise it waits for the next try; No = not asked again in this process. Not silent either way (a restored hash comes from a user-picked file, so it is no proof) | device `RestoreAfterClearDeviceTest` c (flagged fixture: asked, Yes → back) |
| G9-7 | The same CloudStream extension installed from two repositories shares one file; uninstalling one copy (or a failed load/update of one) deletes the other's file | `installBytes` (385–386) names the file `internalName-sha12.cs3`; `uninstall` (425), failed load (401–402) and update clean-up (405) delete it without checking other entries | each installed entry gets its own file (`<name>-<sha12>-<keyhash8>.cs3`; providers are unregistered by file path) and a plugin file is deleted only when no other installed entry uses it | device `ExtensionSharedFileDeviceTest` (two repos, uninstall one, other still loaded) |
| G9-8 | TV only (YF-TV-1): Yumix → "Show videos from" opens half-height; every extension row below the second is off-screen, and a remote cannot drag the sheet up, so the pin of a lower extension can not be reached | `YumixFilter.kt` 89: `ModalBottomSheet` with the default sheet state (partially expanded first) | TV opens the sheet fully (`skipPartiallyExpanded = Edition.isTv`); the list scrolls inside it. Phones unchanged | device `PinnedExtensionsDeviceTest` on TV: fails on the old code on tv-api34 44339783655 and tv-api24 44359805223 (`no visible online_filter_pin_Yumeiro Drama`, screenshot) |
| G9-9 | Below Android 9 a damaged or cut-off backdrop picture is accepted and saved (grey where the data ends); SE-4 (fe643e2) was only fixed on the ImageDecoder path (API 28+) | `DreamBackdrop.kt` 167–175 (`decodeScaled`, BitmapFactory path): BitmapFactory returns a bitmap for a truncated JPEG/PNG and reports nothing | on the BitmapFactory path, check the file is complete before decoding: JPEG must reach its end marker (FFD9) after the first scan, PNG its IEND chunk; otherwise `BrokenPicture` (same message as API 28+) | unit `G9BrokenPictureLegacyTest` (Robolectric API 27); device `BackdropImportDeviceTest` on tv-api24 fails on the old code (44368883691: `corrupt.jpg: accepted`) |
| NBUG-9 | Not reproduced (emulator): with the system resolver failing, the fresh-install setting loads everything | — (see NBUG-9 probe below) | none (no change without a reproduction) | probe `Nbug9DnsProbeDeviceTest` 44312163084; owner test OMT-31 |

IDs: G9-3 (Aniyomi/Mihon restore download also once per process) is part of G9-2; G9-4 is by design
(below); G9-6 (Privacy → Clear cache kept pages in memory) was a candidate, disproved by reading the code.

Checked and not a finding (this audit): `tachiyomi_bridge` `trusted_v2` is sealed with the Keystore, so it is
empty after Clear all data / on a new phone (G9-4, matches Mihon; restored extensions from a repository with a
signing key are trusted at once). Duplicate display names across ecosystems: `SourceHub.uniqueName` adds the
language, then a number. `Restart.now` exits right after `BackupStore.save` → `recordBackup` uses `apply()`, so
"last backup" time can be lost (cosmetic; the restore markers use `commit()`): → UNVERIFIED-QUEUE, stability-v2
(no deterministic test). Settings home `rememberStatuses` reads prefs already warmed by `PrefsWarmup`. Privacy → Clear cache
clears saved pages in memory too (`OnlineRepository.clearCaches` → `DataCache.invalidateAll` → `clearMemory`);
it leaves `externalCacheDir` (share temp files only), which Reset → Clear cache empties: kept.

### NBUG-9 probe (44312163084, head 34d99b8, api33-lowram and api36)

The emulator's resolver was broken the way a failing ISP resolver is (Private DNS strict to
`nbug9-broken-resolver.invalid`: every system lookup fails with UnknownHostException after ~4.2 s, network
not validated, app still "online"). Real HTTPS requests (music.youtube.com, gstatic generate_204,
api.github.com) through the app's client:

| Phase | fresh install (System DNS + auto bypass) | Cloudflare, fallback | Cloudflare, always |
|---|---|---|---|
| baseline | 3/3, 1.1–1.7 s, SYSTEM | 3/3, SYSTEM | 3/3, ALTERNATIVE |
| broken system DNS | **3/3**, 9.5–11.1 s total (~4.5 s per new host, ALTERNATIVE) | 3/3, 14.3–14.7 s | 3/3, 1.5 s |

Result: not reproduced. The default setting recovers through the encrypted-DNS bypass; the only cost is the
system resolver's own time-out on the first request to each host. Shapes the emulator cannot produce: a
resolver that answers a public block-page address whose TLS handshake hangs (`AutoBypassInterceptor.
looksLikeBlocking` does not count read / handshake time-outs, so no bypass; code-read hypothesis H1, not
tested), and a resolver that hangs much longer than 4 s. No code change without a reproduction; owner test
OMT-31 records the owner's network (UNVERIFIED-QUEUE NBUG-9).

### ADRs

ADR-021 (this audit).

### Verification

| Item | Fail-before | Pass-after | Label |
|---|---|---|---|
| G9-1 (unit) | CI 44312331848 (ab/g9-before = b51424b): 1194 tests, 1 failed = exactly `G9SettingsBatchTest#g9_1_jsListsTravelWithSources` | pending | unit |
| G9-1, G9-2, G9-5 (device, pm clear recipe) | 44315631356, both phones: `RAC c G9-2 FAIL · G9-5 FAIL · G9-1 FAIL` (after 240 s both extensions still missing, JS repo/source/code gone) | pending | emulator |
| G9-7 (device) | 44318262848 (old code + test fix 989b6f1), both phones: one shared file `InternetArchiveProvider-dd70f0e9d706.cs3`, uninstalling b deleted it, a missing, 0 providers. (44315631356: test fault, method returned Int → "should be void") | pending | emulator |
| NBUG-9 | probe 44312163084 (both phones): not reproduced | — | emulator; owner network → OMT-31 |
| Group regression | — | pending | emulator, TV emulator |
