# Commit file record (generated)

Generated 2026-11-15T04:33:35Z from `git log origin/r15-stability` plus commits that exist only on other branches. One section per commit: hash, date, subject, changed files (status + path). Screenshot-only paths are summarised as a count.

## 539a6c3 2026-11-07 initial commits
```
A	.env.example
A	.github/CODEOWNERS
A	.github/dependabot.yml
A	.github/extensions/compat.json
A	.github/extensions/sources.json
A	.github/feed/announcements.json
A	.github/market/sources.json
A	.github/pull_request_template.md
A	.github/release-signing-cert.sha256
A	.github/scripts/build_extension_market.py
A	.github/scripts/build_feed.py
A	.github/scripts/build_market.py
A	.github/scripts/compute-version.sh
A	.github/scripts/release-notes.sh
A	.github/scripts/sync_streams.py
A	.github/streams/sources.json
A	.github/workflows/app-feed.yml
A	.github/workflows/ci.yml
A	.github/workflows/codeql.yml
A	.github/workflows/device-check.yml
A	.github/workflows/extension-market.yml
A	.github/workflows/playlist-market.yml
A	.github/workflows/release.yml
A	.github/workflows/stream-sync.yml
A	.gitignore
A	.gitleaks.toml
A	DISCLAIMER.md
A	LICENSE
A	NOTICE
A	README.md
A	SECURITY.md
A	TRADEMARKS.md
A	app/.gitignore
A	app/build.gradle.kts
A	app/proguard-rules.pro
A	app/src/androidTest/java/dev/asfakul/mtrevanced/ExampleInstrumentedTest.kt
A	app/src/androidTest/java/dev/asfakul/mtrevanced/ExtensionCompatDeviceTest.kt
A	app/src/androidTest/java/dev/asfakul/mtrevanced/HeaderStreamDeviceTest.kt
A	app/src/androidTest/java/dev/asfakul/mtrevanced/OnlinePlaybackDeviceTest.kt
A	app/src/main/AndroidManifest.xml
A	app/src/main/java/com/lagradost/cloudstream3/CloudStreamApp.kt
A	app/src/main/java/com/lagradost/cloudstream3/CommonActivity.kt
A	app/src/main/java/com/lagradost/cloudstream3/MainActivity.kt
A	app/src/main/java/com/lagradost/cloudstream3/R.kt
A	app/src/main/java/com/lagradost/cloudstream3/actions/VideoClickAction.kt
A	app/src/main/java/com/lagradost/cloudstream3/network/CloudflareKiller.kt
A	app/src/main/java/com/lagradost/cloudstream3/network/DdosGuardKiller.kt
A	app/src/main/java/com/lagradost/cloudstream3/network/RequestsHelper.kt
A	app/src/main/java/com/lagradost/cloudstream3/plugins/Plugin.kt
A	app/src/main/java/com/lagradost/cloudstream3/plugins/PluginManager.kt
A	app/src/main/java/com/lagradost/cloudstream3/plugins/RepositoryManager.kt
A	app/src/main/java/com/lagradost/cloudstream3/syncproviders/AccountManager.kt
A	app/src/main/java/com/lagradost/cloudstream3/syncproviders/AuthAPI.kt
A	app/src/main/java/com/lagradost/cloudstream3/syncproviders/AuthRepo.kt
A	app/src/main/java/com/lagradost/cloudstream3/syncproviders/SyncAPI.kt
A	app/src/main/java/com/lagradost/cloudstream3/syncproviders/SyncRepo.kt
A	app/src/main/java/com/lagradost/cloudstream3/syncproviders/providers/AniListApi.kt
A	app/src/main/java/com/lagradost/cloudstream3/syncproviders/providers/MALApi.kt
A	app/src/main/java/com/lagradost/cloudstream3/syncproviders/providers/OtherApis.kt
A	app/src/main/java/com/lagradost/cloudstream3/syncproviders/providers/SimklApi.kt
A	app/src/main/java/com/lagradost/cloudstream3/ui/WatchType.kt
A	app/src/main/java/com/lagradost/cloudstream3/ui/home/HomeViewModel.kt
A	app/src/main/java/com/lagradost/cloudstream3/ui/player/PlayerCompat.kt
A	app/src/main/java/com/lagradost/cloudstream3/ui/result/ResultEpisode.kt
A	app/src/main/java/com/lagradost/cloudstream3/ui/settings/Globals.kt
A	app/src/main/java/com/lagradost/cloudstream3/ui/settings/SettingsFragment.kt
A	app/src/main/java/com/lagradost/cloudstream3/ui/settings/extensions/RepositoryData.kt
A	app/src/main/java/com/lagradost/cloudstream3/utils/AppContextUtils.kt
A	app/src/main/java/com/lagradost/cloudstream3/utils/DataStore.kt
A	app/src/main/java/com/lagradost/cloudstream3/utils/DataStoreHelper.kt
A	app/src/main/java/com/lagradost/cloudstream3/utils/Event.kt
A	app/src/main/java/com/lagradost/cloudstream3/utils/TextUtil.kt
A	app/src/main/java/com/lagradost/cloudstream3/utils/UIHelper.kt
A	app/src/main/java/com/lagradost/cloudstream3/utils/downloader/DownloadObjects.kt
A	app/src/main/java/com/lagradost/cloudstream3/utils/videoskip/SkipAPI.kt
A	app/src/main/java/dev/asfakul/mtrevanced/MTApplication.kt
A	app/src/main/java/dev/asfakul/mtrevanced/MainActivity.kt
A	app/src/main/java/dev/asfakul/mtrevanced/cast/CastSupport.kt
A	app/src/main/java/dev/asfakul/mtrevanced/data/model/MediaModels.kt
A	app/src/main/java/dev/asfakul/mtrevanced/data/repository/MediaRepository.kt
(+ 1 image files)
(… 247 more non-image files; run git show --name-status 539a6c3)
```
## 99278fa 2026-11-07 chore: move to siamform/yumeiro, cut Actions usage, new signing cert [skip ci]
```
M	.github/CODEOWNERS
M	.github/dependabot.yml
M	.github/extensions/sources.json
M	.github/market/sources.json
M	.github/pull_request_template.md
M	.github/release-signing-cert.sha256
A	.github/scripts/__pycache__/build_extension_market.cpython-314.pyc
A	.github/scripts/__pycache__/build_feed.cpython-314.pyc
A	.github/scripts/__pycache__/build_market.cpython-314.pyc
A	.github/scripts/__pycache__/sync_streams.cpython-314.pyc
M	.github/scripts/build_extension_market.py
M	.github/scripts/build_feed.py
M	.github/scripts/build_market.py
A	.github/scripts/data-changed.sh
M	.github/scripts/release-notes.sh
M	.github/scripts/sync_streams.py
M	.github/streams/sources.json
M	.github/workflows/app-feed.yml
M	.github/workflows/ci.yml
M	.github/workflows/codeql.yml
M	.github/workflows/device-check.yml
M	.github/workflows/extension-market.yml
M	.github/workflows/playlist-market.yml
M	.github/workflows/release.yml
M	.github/workflows/stream-sync.yml
M	DISCLAIMER.md
M	README.md
M	SECURITY.md
M	TRADEMARKS.md
M	app/build.gradle.kts
M	app/src/main/java/dev/asfakul/mtrevanced/notify/AppFeed.kt
M	app/src/main/java/dev/asfakul/mtrevanced/notify/Reminders.kt
M	app/src/main/java/dev/asfakul/mtrevanced/online/ExtensionMarket.kt
M	app/src/main/java/dev/asfakul/mtrevanced/security/TrustedSources.kt
M	app/src/main/java/dev/asfakul/mtrevanced/stream/PlaylistMarket.kt
M	app/src/main/java/dev/asfakul/mtrevanced/stream/PlaylistStore.kt
M	app/src/main/java/dev/asfakul/mtrevanced/ui/screens/settings/SettingsControls.kt
M	app/src/main/java/dev/asfakul/mtrevanced/ui/screens/settings/SettingsExtras.kt
M	app/src/main/java/dev/asfakul/mtrevanced/ui/screens/stream/PlaylistDetailScreen.kt
M	app/src/main/java/dev/asfakul/mtrevanced/ui/screens/stream/PlaylistMarketScreen.kt
M	app/src/main/java/dev/asfakul/mtrevanced/ui/screens/stream/StreamPlaylistUi.kt
M	app/src/test/java/dev/asfakul/mtrevanced/stream/PlaylistMarketTest.kt
M	app/src/test/java/dev/asfakul/mtrevanced/update/UpdaterLogicTest.kt
M	docs/GUIDE.md
M	docs/HANDOVER.md
M	docs/security/AUDIT.md
M	docs/security/SECRETS-SCAN.md
```
## dfd32ab 2026-11-07 refactor: rename mtrevanced to yumeiro everywhere [skip ci]
```
M	.github/CODEOWNERS
M	.github/workflows/device-check.yml
M	app/build.gradle.kts
M	app/proguard-rules.pro
R096	app/src/androidTest/java/dev/asfakul/mtrevanced/ExampleInstrumentedTest.kt	app/src/androidTest/java/dev/asfakul/yumeiro/ExampleInstrumentedTest.kt
R097	app/src/androidTest/java/dev/asfakul/mtrevanced/ExtensionCompatDeviceTest.kt	app/src/androidTest/java/dev/asfakul/yumeiro/ExtensionCompatDeviceTest.kt
R091	app/src/androidTest/java/dev/asfakul/mtrevanced/HeaderStreamDeviceTest.kt	app/src/androidTest/java/dev/asfakul/yumeiro/HeaderStreamDeviceTest.kt
R094	app/src/androidTest/java/dev/asfakul/mtrevanced/OnlinePlaybackDeviceTest.kt	app/src/androidTest/java/dev/asfakul/yumeiro/OnlinePlaybackDeviceTest.kt
M	app/src/main/AndroidManifest.xml
M	app/src/main/java/com/lagradost/cloudstream3/network/CloudflareKiller.kt
M	app/src/main/java/com/lagradost/cloudstream3/network/DdosGuardKiller.kt
M	app/src/main/java/com/lagradost/cloudstream3/network/RequestsHelper.kt
M	app/src/main/java/com/lagradost/cloudstream3/plugins/PluginManager.kt
M	app/src/main/java/com/lagradost/cloudstream3/plugins/RepositoryManager.kt
M	app/src/main/java/com/lagradost/cloudstream3/ui/home/HomeViewModel.kt
R064	app/src/main/java/dev/asfakul/mtrevanced/MTApplication.kt	app/src/main/java/dev/asfakul/yumeiro/MTApplication.kt
R069	app/src/main/java/dev/asfakul/mtrevanced/MainActivity.kt	app/src/main/java/dev/asfakul/yumeiro/MainActivity.kt
R099	app/src/main/java/dev/asfakul/mtrevanced/cast/CastSupport.kt	app/src/main/java/dev/asfakul/yumeiro/cast/CastSupport.kt
R098	app/src/main/java/dev/asfakul/mtrevanced/data/model/MediaModels.kt	app/src/main/java/dev/asfakul/yumeiro/data/model/MediaModels.kt
R098	app/src/main/java/dev/asfakul/mtrevanced/data/repository/MediaRepository.kt	app/src/main/java/dev/asfakul/yumeiro/data/repository/MediaRepository.kt
R099	app/src/main/java/dev/asfakul/mtrevanced/lyrics/EmbeddedLyrics.kt	app/src/main/java/dev/asfakul/yumeiro/lyrics/EmbeddedLyrics.kt
R098	app/src/main/java/dev/asfakul/mtrevanced/lyrics/Lyrics.kt	app/src/main/java/dev/asfakul/yumeiro/lyrics/Lyrics.kt
R099	app/src/main/java/dev/asfakul/mtrevanced/lyrics/TrackDetailsRepository.kt	app/src/main/java/dev/asfakul/yumeiro/lyrics/TrackDetailsRepository.kt
R097	app/src/main/java/dev/asfakul/mtrevanced/network/Connectivity.kt	app/src/main/java/dev/asfakul/yumeiro/network/Connectivity.kt
R099	app/src/main/java/dev/asfakul/mtrevanced/network/DnsMessage.kt	app/src/main/java/dev/asfakul/yumeiro/network/DnsMessage.kt
R098	app/src/main/java/dev/asfakul/mtrevanced/network/NetworkClient.kt	app/src/main/java/dev/asfakul/yumeiro/network/NetworkClient.kt
R099	app/src/main/java/dev/asfakul/mtrevanced/network/NetworkSettings.kt	app/src/main/java/dev/asfakul/yumeiro/network/NetworkSettings.kt
R099	app/src/main/java/dev/asfakul/mtrevanced/network/SmartDns.kt	app/src/main/java/dev/asfakul/yumeiro/network/SmartDns.kt
R097	app/src/main/java/dev/asfakul/mtrevanced/notify/AppFeed.kt	app/src/main/java/dev/asfakul/yumeiro/notify/AppFeed.kt
R096	app/src/main/java/dev/asfakul/mtrevanced/notify/AppNotifier.kt	app/src/main/java/dev/asfakul/yumeiro/notify/AppNotifier.kt
R098	app/src/main/java/dev/asfakul/mtrevanced/notify/DiscoveryWorker.kt	app/src/main/java/dev/asfakul/yumeiro/notify/DiscoveryWorker.kt
R099	app/src/main/java/dev/asfakul/mtrevanced/notify/Inbox.kt	app/src/main/java/dev/asfakul/yumeiro/notify/Inbox.kt
R098	app/src/main/java/dev/asfakul/mtrevanced/notify/NotifySettings.kt	app/src/main/java/dev/asfakul/yumeiro/notify/NotifySettings.kt
R099	app/src/main/java/dev/asfakul/mtrevanced/notify/Reminders.kt	app/src/main/java/dev/asfakul/yumeiro/notify/Reminders.kt
R098	app/src/main/java/dev/asfakul/mtrevanced/online/AccessLadder.kt	app/src/main/java/dev/asfakul/yumeiro/online/AccessLadder.kt
R096	app/src/main/java/dev/asfakul/mtrevanced/online/AdultContent.kt	app/src/main/java/dev/asfakul/yumeiro/online/AdultContent.kt
R099	app/src/main/java/dev/asfakul/mtrevanced/online/ExtensionGuard.kt	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionGuard.kt
R099	app/src/main/java/dev/asfakul/mtrevanced/online/ExtensionManager.kt	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionManager.kt
R098	app/src/main/java/dev/asfakul/mtrevanced/online/ExtensionMarket.kt	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionMarket.kt
R099	app/src/main/java/dev/asfakul/mtrevanced/online/ExtensionModels.kt	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionModels.kt
R089	app/src/main/java/dev/asfakul/mtrevanced/online/NewEpisodesWorker.kt	app/src/main/java/dev/asfakul/yumeiro/online/NewEpisodesWorker.kt
R099	app/src/main/java/dev/asfakul/mtrevanced/online/OnlineBookmarks.kt	app/src/main/java/dev/asfakul/yumeiro/online/OnlineBookmarks.kt
R099	app/src/main/java/dev/asfakul/mtrevanced/online/OnlineHistory.kt	app/src/main/java/dev/asfakul/yumeiro/online/OnlineHistory.kt
R090	app/src/main/java/dev/asfakul/mtrevanced/online/OnlinePlayback.kt	app/src/main/java/dev/asfakul/yumeiro/online/OnlinePlayback.kt
R099	app/src/main/java/dev/asfakul/mtrevanced/online/OnlineRepository.kt	app/src/main/java/dev/asfakul/yumeiro/online/OnlineRepository.kt
R096	app/src/main/java/dev/asfakul/mtrevanced/online/OnlineRuntime.kt	app/src/main/java/dev/asfakul/yumeiro/online/OnlineRuntime.kt
R097	app/src/main/java/dev/asfakul/mtrevanced/online/OnlineSession.kt	app/src/main/java/dev/asfakul/yumeiro/online/OnlineSession.kt
R094	app/src/main/java/dev/asfakul/mtrevanced/online/OnlineSettings.kt	app/src/main/java/dev/asfakul/yumeiro/online/OnlineSettings.kt
R098	app/src/main/java/dev/asfakul/mtrevanced/online/ProviderOverrides.kt	app/src/main/java/dev/asfakul/yumeiro/online/ProviderOverrides.kt
R096	app/src/main/java/dev/asfakul/mtrevanced/online/StarterExtensions.kt	app/src/main/java/dev/asfakul/yumeiro/online/StarterExtensions.kt
R092	app/src/main/java/dev/asfakul/mtrevanced/online/anime/AniList.kt	app/src/main/java/dev/asfakul/yumeiro/online/anime/AniList.kt
R098	app/src/main/java/dev/asfakul/mtrevanced/online/anime/AniSkip.kt	app/src/main/java/dev/asfakul/yumeiro/online/anime/AniSkip.kt
R096	app/src/main/java/dev/asfakul/mtrevanced/online/anime/AnimeIds.kt	app/src/main/java/dev/asfakul/yumeiro/online/anime/AnimeIds.kt
R091	app/src/main/java/dev/asfakul/mtrevanced/online/anime/AnimeSync.kt	app/src/main/java/dev/asfakul/yumeiro/online/anime/AnimeSync.kt
R097	app/src/main/java/dev/asfakul/mtrevanced/online/download/DownloadWorker.kt	app/src/main/java/dev/asfakul/yumeiro/online/download/DownloadWorker.kt
R099	app/src/main/java/dev/asfakul/mtrevanced/online/download/HlsDownloader.kt	app/src/main/java/dev/asfakul/yumeiro/online/download/HlsDownloader.kt
R098	app/src/main/java/dev/asfakul/mtrevanced/online/download/OnlineDownloads.kt	app/src/main/java/dev/asfakul/yumeiro/online/download/OnlineDownloads.kt
R097	app/src/main/java/dev/asfakul/mtrevanced/online/subtitles/OpenSubtitles.kt	app/src/main/java/dev/asfakul/yumeiro/online/subtitles/OpenSubtitles.kt
R099	app/src/main/java/dev/asfakul/mtrevanced/online/web/BrowserCheck.kt	app/src/main/java/dev/asfakul/yumeiro/online/web/BrowserCheck.kt
R096	app/src/main/java/dev/asfakul/mtrevanced/online/web/HeadlessCapture.kt	app/src/main/java/dev/asfakul/yumeiro/online/web/HeadlessCapture.kt
R098	app/src/main/java/dev/asfakul/mtrevanced/player/MusicPlayerManager.kt	app/src/main/java/dev/asfakul/yumeiro/player/MusicPlayerManager.kt
R096	app/src/main/java/dev/asfakul/mtrevanced/player/PipController.kt	app/src/main/java/dev/asfakul/yumeiro/player/PipController.kt
R097	app/src/main/java/dev/asfakul/mtrevanced/player/PlaybackDiagnostics.kt	app/src/main/java/dev/asfakul/yumeiro/player/PlaybackDiagnostics.kt
R099	app/src/main/java/dev/asfakul/mtrevanced/player/PlaybackHealthMonitor.kt	app/src/main/java/dev/asfakul/yumeiro/player/PlaybackHealthMonitor.kt
R098	app/src/main/java/dev/asfakul/mtrevanced/player/PlayerSettings.kt	app/src/main/java/dev/asfakul/yumeiro/player/PlayerSettings.kt
R098	app/src/main/java/dev/asfakul/mtrevanced/player/SleepTimer.kt	app/src/main/java/dev/asfakul/yumeiro/player/SleepTimer.kt
R099	app/src/main/java/dev/asfakul/mtrevanced/player/StreamRecovery.kt	app/src/main/java/dev/asfakul/yumeiro/player/StreamRecovery.kt
R099	app/src/main/java/dev/asfakul/mtrevanced/player/equalizer/EqualizerManager.kt	app/src/main/java/dev/asfakul/yumeiro/player/equalizer/EqualizerManager.kt
R096	app/src/main/java/dev/asfakul/mtrevanced/player/equalizer/EqualizerModels.kt	app/src/main/java/dev/asfakul/yumeiro/player/equalizer/EqualizerModels.kt
R096	app/src/main/java/dev/asfakul/mtrevanced/security/IntegrityGuard.kt	app/src/main/java/dev/asfakul/yumeiro/security/IntegrityGuard.kt
R098	app/src/main/java/dev/asfakul/mtrevanced/security/SecretBox.kt	app/src/main/java/dev/asfakul/yumeiro/security/SecretBox.kt
R098	app/src/main/java/dev/asfakul/mtrevanced/security/TrustedSources.kt	app/src/main/java/dev/asfakul/yumeiro/security/TrustedSources.kt
R094	app/src/main/java/dev/asfakul/mtrevanced/service/PlaybackService.kt	app/src/main/java/dev/asfakul/yumeiro/service/PlaybackService.kt
R095	app/src/main/java/dev/asfakul/mtrevanced/service/VideoPlaybackService.kt	app/src/main/java/dev/asfakul/yumeiro/service/VideoPlaybackService.kt
R097	app/src/main/java/dev/asfakul/mtrevanced/stream/IncomingMedia.kt	app/src/main/java/dev/asfakul/yumeiro/stream/IncomingMedia.kt
R099	app/src/main/java/dev/asfakul/mtrevanced/stream/M3uParser.kt	app/src/main/java/dev/asfakul/yumeiro/stream/M3uParser.kt
R098	app/src/main/java/dev/asfakul/mtrevanced/stream/PlatformRules.kt	app/src/main/java/dev/asfakul/yumeiro/stream/PlatformRules.kt
R098	app/src/main/java/dev/asfakul/mtrevanced/stream/PlaylistMarket.kt	app/src/main/java/dev/asfakul/yumeiro/stream/PlaylistMarket.kt
R099	app/src/main/java/dev/asfakul/mtrevanced/stream/PlaylistStore.kt	app/src/main/java/dev/asfakul/yumeiro/stream/PlaylistStore.kt
R097	app/src/main/java/dev/asfakul/mtrevanced/stream/StreamInterceptors.kt	app/src/main/java/dev/asfakul/yumeiro/stream/StreamInterceptors.kt
(… 132 more non-image files; run git show --name-status dfd32ab)
```
## 5002f90 2026-11-07 docs: add privacy policy and terms of use [skip ci]
```
M	DISCLAIMER.md
A	PRIVACY.md
M	README.md
A	TERMS.md
```
## 5480de5 2026-11-08 fix(ci): make gradlew executable (#6)
```
M	gradlew
```
## 6dd7f21 2026-11-07 chore: replace old dev-asfakul account with siamform
```
M	NOTICE
M	app/src/androidTest/java/dev/asfakul/yumeiro/ExampleInstrumentedTest.kt
M	app/src/androidTest/java/dev/asfakul/yumeiro/ExtensionCompatDeviceTest.kt
M	app/src/androidTest/java/dev/asfakul/yumeiro/HeaderStreamDeviceTest.kt
M	app/src/androidTest/java/dev/asfakul/yumeiro/OnlinePlaybackDeviceTest.kt
M	app/src/main/java/dev/asfakul/yumeiro/MTApplication.kt
M	app/src/main/java/dev/asfakul/yumeiro/MainActivity.kt
M	app/src/main/java/dev/asfakul/yumeiro/cast/CastSupport.kt
M	app/src/main/java/dev/asfakul/yumeiro/data/model/MediaModels.kt
M	app/src/main/java/dev/asfakul/yumeiro/data/repository/MediaRepository.kt
M	app/src/main/java/dev/asfakul/yumeiro/lyrics/EmbeddedLyrics.kt
M	app/src/main/java/dev/asfakul/yumeiro/lyrics/Lyrics.kt
M	app/src/main/java/dev/asfakul/yumeiro/lyrics/TrackDetailsRepository.kt
M	app/src/main/java/dev/asfakul/yumeiro/network/Connectivity.kt
M	app/src/main/java/dev/asfakul/yumeiro/network/DnsMessage.kt
M	app/src/main/java/dev/asfakul/yumeiro/network/NetworkClient.kt
M	app/src/main/java/dev/asfakul/yumeiro/network/NetworkSettings.kt
M	app/src/main/java/dev/asfakul/yumeiro/network/SmartDns.kt
M	app/src/main/java/dev/asfakul/yumeiro/notify/AppFeed.kt
M	app/src/main/java/dev/asfakul/yumeiro/notify/AppNotifier.kt
M	app/src/main/java/dev/asfakul/yumeiro/notify/DiscoveryWorker.kt
M	app/src/main/java/dev/asfakul/yumeiro/notify/Inbox.kt
M	app/src/main/java/dev/asfakul/yumeiro/notify/NotifySettings.kt
M	app/src/main/java/dev/asfakul/yumeiro/notify/Reminders.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/AccessLadder.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/AdultContent.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionGuard.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionManager.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionMarket.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionModels.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/NewEpisodesWorker.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineBookmarks.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineHistory.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlinePlayback.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineRepository.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineRuntime.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineSession.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineSettings.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/ProviderOverrides.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/StarterExtensions.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/anime/AniList.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/anime/AniSkip.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/anime/AnimeIds.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/anime/AnimeSync.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/download/DownloadWorker.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/download/HlsDownloader.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/download/OnlineDownloads.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/subtitles/OpenSubtitles.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/web/BrowserCheck.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/web/HeadlessCapture.kt
M	app/src/main/java/dev/asfakul/yumeiro/player/MusicPlayerManager.kt
M	app/src/main/java/dev/asfakul/yumeiro/player/PipController.kt
M	app/src/main/java/dev/asfakul/yumeiro/player/PlaybackDiagnostics.kt
M	app/src/main/java/dev/asfakul/yumeiro/player/PlaybackHealthMonitor.kt
M	app/src/main/java/dev/asfakul/yumeiro/player/PlayerSettings.kt
M	app/src/main/java/dev/asfakul/yumeiro/player/SleepTimer.kt
M	app/src/main/java/dev/asfakul/yumeiro/player/StreamRecovery.kt
M	app/src/main/java/dev/asfakul/yumeiro/player/equalizer/EqualizerManager.kt
M	app/src/main/java/dev/asfakul/yumeiro/player/equalizer/EqualizerModels.kt
M	app/src/main/java/dev/asfakul/yumeiro/security/IntegrityGuard.kt
M	app/src/main/java/dev/asfakul/yumeiro/security/SecretBox.kt
M	app/src/main/java/dev/asfakul/yumeiro/security/TrustedSources.kt
M	app/src/main/java/dev/asfakul/yumeiro/service/PlaybackService.kt
M	app/src/main/java/dev/asfakul/yumeiro/service/VideoPlaybackService.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/IncomingMedia.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/M3uParser.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/PlatformRules.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/PlaylistMarket.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/PlaylistStore.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/StreamInterceptors.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/StreamMediaFactory.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/StreamModels.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/StreamProbe.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/StreamRefreshers.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/StreamRepository.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/StreamResolver.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/StreamSniffer.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/WebPageExtractor.kt
M	app/src/main/java/dev/asfakul/yumeiro/subtitles/OnlineSubtitles.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/components/Artwork.kt
(… 116 more non-image files; run git show --name-status 6dd7f21)
```
## 32607b9 2026-11-07 ci: run data workflows every 3 days
```
M	.github/extensions/sources.json
M	.github/market/sources.json
M	.github/streams/sources.json
M	.github/workflows/app-feed.yml
M	.github/workflows/extension-market.yml
M	.github/workflows/playlist-market.yml
M	.github/workflows/stream-sync.yml
M	README.md
M	app/src/main/java/dev/asfakul/yumeiro/stream/PlaylistStore.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/stream/PlaylistDetailScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/stream/PlaylistMarketScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/stream/StreamPlaylistUi.kt
M	docs/GUIDE.md
M	docs/HANDOVER.md
```
## dc4e469 2026-11-07 chore(deps): bump jsoup 1.24.1, guava 33.8.0, fragment 1.9.1; hold robolectric at 4.16.1
```
M	.github/dependabot.yml
M	app/build.gradle.kts
M	gradle/libs.versions.toml
```
## 4404ac1 2026-11-07 ci(security): drop committed pycache, pass device-check input via env, embed updater token only for private repo
```
D	.github/scripts/__pycache__/build_extension_market.cpython-314.pyc
D	.github/scripts/__pycache__/build_feed.cpython-314.pyc
D	.github/scripts/__pycache__/build_market.cpython-314.pyc
D	.github/scripts/__pycache__/sync_streams.cpython-314.pyc
M	.github/workflows/device-check.yml
M	.github/workflows/release.yml
M	.gitignore
M	docs/GUIDE.md
```
## e8bc2b3 2026-11-08 Merge pull request #7 from siamform/chore/hardening
```
```
## 1ad118e 2026-11-08 Fix black screen during source failover; scroll memory, tab swipe, hold-swipe PiP, joined dock, daily reminder, developer card (#8)
```
M	app/src/main/java/dev/asfakul/yumeiro/notify/Reminders.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionGuard.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineSession.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/components/MTBottomBar.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/components/ScrollMemory.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/components/TabSwipe.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/MTPlayerApp.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/home/HomeScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/library/LibraryScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/library/VideoLibraryScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/music/MusicScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineHomeScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlinePlayerUi.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineRowScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/SettingsExtras.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/SettingsScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/splash/SplashScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/stream/PlaylistDetailScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/stream/PlaylistMarketScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/stream/StreamScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/VideoPlayerGestures.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/VideoPlayerModal.kt
A	app/src/main/res/drawable/ic_brand_facebook.xml
A	app/src/main/res/drawable/ic_brand_github.xml
A	app/src/main/res/drawable/ic_brand_instagram.xml
A	app/src/main/res/drawable/ic_brand_linkedin.xml
M	app/src/test/java/dev/asfakul/yumeiro/notify/RemindersTest.kt
M	app/src/test/java/dev/asfakul/yumeiro/revamp/RevampLogicTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/ui/NavigationFlowTest.kt
```
## fb718c7 2026-11-08 Fix captcha/blocked loop, player black screen, WebView crashes; finger-following tab pager (#9)
```
M	app/src/main/java/com/lagradost/cloudstream3/network/CloudflareKiller.kt
M	app/src/main/java/com/lagradost/cloudstream3/network/DdosGuardKiller.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlinePlayback.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineSession.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/download/DownloadWorker.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/web/BrowserCheck.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/web/HeadlessCapture.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/web/SafeWebView.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/components/TabSwipe.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/MTPlayerApp.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/BrowserCheckHost.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/stream/PageStreamFinderDialog.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/VideoPlayerModal.kt
A	app/src/main/res/layout/yumeiro_player_view.xml
A	app/src/test/java/dev/asfakul/yumeiro/online/web/BrowserCheckTest.kt
M	app/src/test/java/dev/asfakul/yumeiro/ui/AppNavigationCrashTest.kt
M	docs/HANDOVER.md
```
## 562c6e3 2026-11-08 Offline downloads: series folders, auto-delete timer, YouTube video and music in every quality; website link replaces old portfolio (#10)
```
M	README.md
M	app/src/main/java/dev/asfakul/yumeiro/MTApplication.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/download/DownloadNaming.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/download/DownloadSettings.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/download/DownloadWorker.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/download/MediaMux.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/download/OnlineDownloads.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/download/YtDownloadOptions.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/download/YtDownloads.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/library/VideoLibraryScreen.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/DownloadSheets.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineDetailScreen.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/DownloadSettingsCard.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/SettingsExtras.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/SettingsScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/watch/WatchScreens.kt
A	app/src/test/java/dev/asfakul/yumeiro/online/download/DownloadNamingTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/online/download/YtDownloadOptionsTest.kt
M	docs/GUIDE.md
M	docs/HANDOVER.md
```
## 0e5cc1a 2026-11-08 Downloads UX + notification fix + roadmap (#11)
```
M	app/src/main/java/dev/asfakul/yumeiro/MainActivity.kt
M	app/src/main/java/dev/asfakul/yumeiro/notify/AppFeed.kt
M	app/src/main/java/dev/asfakul/yumeiro/notify/AppNotifier.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/download/DownloadGroups.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/download/DownloadSettings.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/download/DownloadWorker.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/download/OnlineDownloads.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/navigation/Screen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/MTPlayerApp.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/screens/downloads/DownloadPrompt.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/screens/downloads/DownloadsScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/extras/InboxScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/home/HomeScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/library/VideoLibraryScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/DownloadSheets.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlinePlayerUi.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/DownloadSettingsCard.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/SettingsControls.kt
M	app/src/test/java/dev/asfakul/yumeiro/notify/AppFeedTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/online/download/DownloadGroupsTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/ui/downloads/DownloadUiScreenshotTest.kt
M	docs/HANDOVER.md
A	docs/ROADMAP.md
```
## 352dc28 2026-11-09 feat: universal sources + v1.0.5 stability and polish (replaces #13) (#14)
```
M	.env.example
A	.github/codeql/suppressions.jq
M	.github/workflows/codeql.yml
M	.github/workflows/release.yml
M	NOTICE
M	README.md
M	app/build.gradle.kts
M	app/proguard-rules.pro
M	app/src/main/AndroidManifest.xml
A	app/src/main/assets/js/mangayomi_runtime.js
M	app/src/main/java/dev/asfakul/yumeiro/MTApplication.kt
M	app/src/main/java/dev/asfakul/yumeiro/MainActivity.kt
A	app/src/main/java/dev/asfakul/yumeiro/backup/Backup.kt
M	app/src/main/java/dev/asfakul/yumeiro/data/repository/MediaRepository.kt
A	app/src/main/java/dev/asfakul/yumeiro/network/AutoBypassInterceptor.kt
M	app/src/main/java/dev/asfakul/yumeiro/network/Connectivity.kt
M	app/src/main/java/dev/asfakul/yumeiro/network/NetworkClient.kt
M	app/src/main/java/dev/asfakul/yumeiro/network/NetworkSettings.kt
M	app/src/main/java/dev/asfakul/yumeiro/network/SmartDns.kt
M	app/src/main/java/dev/asfakul/yumeiro/notify/AppNotifier.kt
M	app/src/main/java/dev/asfakul/yumeiro/notify/DiscoveryWorker.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/EpisodeMatcher.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionManager.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineHistory.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineRepository.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineRuntime.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineSession.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/SearchRanker.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/anime/AnimeSync.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/web/AdFilter.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/web/HeadlessCapture.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/web/SafeWebView.kt
A	app/src/main/java/dev/asfakul/yumeiro/player/BlackFrameWatchdog.kt
A	app/src/main/java/dev/asfakul/yumeiro/player/ScreenWake.kt
A	app/src/main/java/dev/asfakul/yumeiro/security/LockKey.kt
A	app/src/main/java/dev/asfakul/yumeiro/security/Privacy.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/Bridges.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/SourceHub.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/js/JsBridge.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/js/JsCrypto.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/js/JsEngine.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/js/JsProviders.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/js/JsSources.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/manga/MangaLibrary.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/manga/MangaProvider.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/servers/DlnaTree.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/servers/HomeServers.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/servers/JellyfinProvider.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/servers/PlexProvider.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/servers/SmbTree.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/servers/TreeProvider.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/servers/WebDavTree.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/stremio/StremioAddons.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/stremio/StremioProvider.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/tachiyomi/AniyomiProvider.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/tachiyomi/MihonMangaProvider.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/tachiyomi/TachiyomiExtensions.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/tachiyomi/TachiyomiRuntime.kt
A	app/src/main/java/dev/asfakul/yumeiro/stream/AdFilterDataSource.kt
A	app/src/main/java/dev/asfakul/yumeiro/stream/HlsAdFilter.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/StreamMediaFactory.kt
A	app/src/main/java/dev/asfakul/yumeiro/tracking/Kitsu.kt
A	app/src/main/java/dev/asfakul/yumeiro/tracking/Trakt.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/components/MediaComponents.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/components/StateViews.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/components/TsukiDialog.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/navigation/Screen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/MTPlayerApp.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/downloads/DownloadPrompt.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/downloads/DownloadsScreen.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/screens/extras/AppLockGate.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/extras/InboxScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/extras/PermissionCenter.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/extras/ReminderHost.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/screens/library/LibraryDesign.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/library/LibraryScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/library/VideoLibraryScreen.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/screens/manga/MangaScreens.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/ExtensionsScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineHomeScreen.kt
(+ 1 image files)
(… 145 more non-image files; run git show --name-status 352dc28)
```
## 3018a36 2026-11-09 feat: settings redesign, setup guides, YouTube account + paste cookie, free UnifiedPush alerts (#15)
```
M	.github/codeql/suppressions.jq
M	app/build.gradle.kts
M	app/src/main/AndroidManifest.xml
M	app/src/main/java/dev/asfakul/yumeiro/notify/DiscoveryWorker.kt
A	app/src/main/java/dev/asfakul/yumeiro/notify/PushWake.kt
M	app/src/main/java/dev/asfakul/yumeiro/notify/Reminders.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/NewEpisodesWorker.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineRuntime.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/navigation/Screen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/MTPlayerApp.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/extras/ReminderHost.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/screens/extras/SupportMessage.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/extras/SupportScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/ExtensionsScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/OnlineSettingsCard.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/PrivacyTrackingBackup.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/PushAlertsCard.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/SettingsCatalog.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/SettingsControls.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/SettingsDesign.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/SettingsScreen.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/SetupGuides.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/YouTubeAccountScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/sources/SourcesHubScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/youtube/YouTubeScreens.kt
M	app/src/main/java/dev/asfakul/yumeiro/youtube/NewPipeDownloader.kt
M	app/src/main/java/dev/asfakul/yumeiro/youtube/YouTube.kt
A	app/src/main/java/dev/asfakul/yumeiro/youtube/YtAccount.kt
A	app/src/main/java/dev/asfakul/yumeiro/youtube/YtAuthHash.kt
A	app/src/main/java/dev/asfakul/yumeiro/youtube/YtCookiePaste.kt
A	app/src/test/java/dev/asfakul/yumeiro/notify/PushPlanTest.kt
M	app/src/test/java/dev/asfakul/yumeiro/notify/RemindersTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/ui/settings/PasteCookieAndPushScreenshotTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/ui/settings/SettingsCatalogTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/youtube/YtAccountTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/youtube/YtCookiePasteTest.kt
M	docs/HANDOVER.md
M	docs/INSPIRATION.md
M	docs/ROADMAP.md
M	docs/security/AUDIT.md
M	gradle/libs.versions.toml
```
## ef54fff 2026-11-09 fix: broken extension links, extension updater + safety check, site sign-in, audio and subtitles (#16)
```
M	app/build.gradle.kts
M	app/proguard-rules.pro
M	app/src/main/java/com/lagradost/cloudstream3/plugins/PluginManager.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionManager.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionReview.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionScanner.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineRepository.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineRuntime.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/SafeRequests.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/StarterExtensions.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/UrlFix.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/web/BrowserCheck.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/web/SiteLogins.kt
A	app/src/main/java/dev/asfakul/yumeiro/player/TrackPicks.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/AdFilterDataSource.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/StreamMediaFactory.kt
A	app/src/main/java/dev/asfakul/yumeiro/stream/SubtitleSupport.kt
A	app/src/main/java/dev/asfakul/yumeiro/stream/YumeiroRenderersFactory.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/components/ErrorHelp.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/MTPlayerApp.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/BrowserCheckHost.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/ExtensionMarketScreen.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/ExtensionReviewHost.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/ExtensionsScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineGetStarted.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlinePlayerUi.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/VideoPlayerDialogs.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/VideoPlayerModal.kt
A	app/src/test/java/dev/asfakul/yumeiro/online/ExtensionScannerTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/online/SiteAccessTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/online/UrlFixTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/stream/SubtitleAudioTest.kt
M	docs/HANDOVER.md
M	docs/ROADMAP.md
A	docs/licenses/NextLib-FFmpeg.md
M	docs/security/AUDIT.md
M	docs/security/DEPENDENCIES.md
M	gradle/libs.versions.toml
```
## f7182be 2026-11-09 feat: SubDL/SubSource subtitles, external download managers, ad shiel… (#17)
```
M	.github/codeql/suppressions.jq
M	README.md
M	app/src/main/AndroidManifest.xml
M	app/src/main/java/dev/asfakul/yumeiro/notify/AppNotifier.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineSettings.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/download/DownloadSettings.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/download/ExternalDownloader.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/download/OnlineDownloads.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/subtitles/OpenSubtitles.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/subtitles/SubDL.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/subtitles/SubSource.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/subtitles/SubtitleArchive.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/subtitles/SubtitleHit.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/subtitles/SubtitleHub.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/subtitles/SubtitleLanguages.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/web/AdShield.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/web/BrowserCheck.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/web/HeadlessCapture.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/web/SafeWebView.kt
A	app/src/main/java/dev/asfakul/yumeiro/subtitles/SubtitleSitesProvider.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/BrowserCheckHost.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlinePlayerUi.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/DownloadSettingsCard.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/OnlineSettingsCard.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/SettingsCatalog.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/YouTubeAccountScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/stream/PageStreamFinderDialog.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/OnlineSubtitleDialog.kt
M	app/src/main/java/dev/asfakul/yumeiro/update/UpdateNotifier.kt
A	app/src/main/res/raw/keep.xml
D	app/src/main/res/raw/yumeiro_chime.ogg
A	app/src/main/res/raw/yumeiro_tsuki.ogg
M	app/src/main/res/xml/network_security_config.xml
A	app/src/test/java/dev/asfakul/yumeiro/online/download/ExternalDownloaderTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/online/subtitles/SubtitleKeyScopeTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/online/subtitles/SubtitleSitesTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/online/web/AdShieldTest.kt
M	docs/GUIDE.md
M	docs/HANDOVER.md
M	docs/INSPIRATION.md
M	docs/ROADMAP.md
A	docs/proposals/TORRENT.md
M	docs/security/AUDIT.md
```
## 7587b90 2026-11-10 feat: Simkl, Last.fm scrobbling, LRCLIB lyrics, Trakt without secret, airing alerts server (#19)
```
M	.env.example
M	.github/codeql/suppressions.jq
M	.github/workflows/codeql.yml
M	.github/workflows/release.yml
A	.github/workflows/server.yml
M	PRIVACY.md
M	app/build.gradle.kts
A	app/src/main/java/dev/asfakul/yumeiro/lyrics/LrcLib.kt
M	app/src/main/java/dev/asfakul/yumeiro/lyrics/Lyrics.kt
M	app/src/main/java/dev/asfakul/yumeiro/lyrics/TrackDetailsRepository.kt
A	app/src/main/java/dev/asfakul/yumeiro/notify/CloudAlerts.kt
M	app/src/main/java/dev/asfakul/yumeiro/notify/DiscoveryWorker.kt
M	app/src/main/java/dev/asfakul/yumeiro/notify/PushWake.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/anime/AnimeIds.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/anime/AnimeSync.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/subtitles/OpenSubtitles.kt
M	app/src/main/java/dev/asfakul/yumeiro/player/MusicPlayerManager.kt
A	app/src/main/java/dev/asfakul/yumeiro/scrobble/LastFm.kt
A	app/src/main/java/dev/asfakul/yumeiro/scrobble/LastFmSignature.kt
A	app/src/main/java/dev/asfakul/yumeiro/scrobble/ScrobbleRules.kt
A	app/src/main/java/dev/asfakul/yumeiro/scrobble/Scrobbler.kt
A	app/src/main/java/dev/asfakul/yumeiro/tracking/Simkl.kt
M	app/src/main/java/dev/asfakul/yumeiro/tracking/Trakt.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlinePlayerUi.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/player/NowPlayingScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/OnlineSettingsCard.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/PrivacyTrackingBackup.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/SetupGuides.kt
M	app/src/main/res/xml/network_security_config.xml
A	app/src/test/java/dev/asfakul/yumeiro/lyrics/LrcLibTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/notify/CloudAlertsPlanTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/online/OpenSubtitlesParamsTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/scrobble/LastFmTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/scrobble/ScrobbleTrackerTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/scrobble/TrackNamesTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/tracking/SimklTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/tracking/TraktAuthTest.kt
M	docs/HANDOVER.md
M	docs/INSPIRATION.md
M	docs/ROADMAP.md
A	docs/SERVER.md
M	docs/security/AUDIT.md
A	server/.gitignore
A	server/package-lock.json
A	server/package.json
A	server/schema.sql
A	server/src/index.ts
A	server/src/logic.ts
A	server/test/logic.test.ts
A	server/tsconfig.json
A	server/wrangler.toml
```
## d49ffd3 2026-11-10 chore(server): update wrangler to fix undici and sharp alerts; monthly grouped npm updates (#21)
```
M	.github/dependabot.yml
M	docs/HANDOVER.md
M	docs/SERVER.md
M	server/package-lock.json
M	server/package.json
```
## 3b7d1c7 2026-11-10 feat(sources): markets for every format, working alerts, yumeiro.click links (#22)
```
A	.github/scripts/build_source_market.py
M	.github/workflows/extension-market.yml
M	PRIVACY.md
M	README.md
M	app/src/main/java/dev/asfakul/yumeiro/notify/CloudAlerts.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/SourceMarket.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/tachiyomi/TachiyomiExtensions.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/SettingsExtras.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/SetupGuides.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/sources/SourceHealthScreen.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/screens/sources/SourceMarketSection.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/sources/SourcesHubScreen.kt
M	app/src/test/java/dev/asfakul/yumeiro/notify/CloudAlertsPlanTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/sources/SourceMarketTest.kt
M	docs/DEVELOPMENT.md
M	docs/DISTRIBUTION.md
M	docs/GUIDE.md
M	docs/HANDOVER.md
M	docs/ROADMAP.md
M	docs/SERVER.md
M	docs/UPDATE-universal-sources.md
M	fastlane/metadata/android/en-US/full_description.txt
M	server/schema.sql
M	server/src/index.ts
M	server/src/logic.ts
M	server/test/logic.test.ts
M	server/wrangler.toml
```
## af8349e 2026-11-10 feat(mihon): extension lib 1.6 and the new store index (Keiyoushi) (#23)
```
M	.github/scripts/build_source_market.py
M	README.md
M	app/build.gradle.kts
M	app/proguard-rules.pro
A	app/src/main/java/app/cash/quickjs/QuickJs.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/tachiyomi/MemoStore.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/tachiyomi/MihonMangaProvider.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/tachiyomi/TachiyomiExtensions.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/tachiyomi/TachiyomiIndex.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/BrowserCheckHost.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/SetupGuides.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/stream/PageStreamFinderDialog.kt
M	app/src/main/java/eu/kanade/tachiyomi/network/NetworkHelper.kt
R087	app/src/main/java/eu/kanade/tachiyomi/network/interceptor/ChallengeInterceptor.kt	app/src/main/java/eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt
M	app/src/main/java/eu/kanade/tachiyomi/source/CatalogueSource.kt
M	app/src/main/java/eu/kanade/tachiyomi/source/ConfigurableSource.kt
R052	app/src/main/java/eu/kanade/tachiyomi/source/MangaSource.kt	app/src/main/java/eu/kanade/tachiyomi/source/Source.kt
M	app/src/main/java/eu/kanade/tachiyomi/source/SourceFactory.kt
M	app/src/main/java/eu/kanade/tachiyomi/source/model/SChapter.kt
M	app/src/main/java/eu/kanade/tachiyomi/source/model/SChapterImpl.kt
M	app/src/main/java/eu/kanade/tachiyomi/source/model/SManga.kt
M	app/src/main/java/eu/kanade/tachiyomi/source/model/SMangaImpl.kt
A	app/src/main/java/eu/kanade/tachiyomi/source/model/SMangaUpdate.kt
M	app/src/main/java/eu/kanade/tachiyomi/source/online/HttpSource.kt
M	app/src/main/java/eu/kanade/tachiyomi/source/online/ResolvableSource.kt
A	app/src/test/java/dev/asfakul/yumeiro/sources/tachiyomi/KeiyoushiLinkageTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/sources/tachiyomi/TachiyomiIndexTest.kt
A	app/src/test/resources/keiyoushi-index.pb
A	app/src/test/resources/keiyoushi-lib16-refs.json
M	docs/HANDOVER.md
M	docs/ROADMAP.md
M	gradle/libs.versions.toml
```
## ff7cb19 2026-11-13 feat: Yumeiro redesign R1 to R11b: TV mode, Add anything, Yumix, pinch zoom, subtitle style, security fixes (#27)
```
M	.github/codeql/suppressions.jq
M	.github/dependabot.yml
M	.github/scripts/build_extension_market.py
M	.github/scripts/release-notes.sh
M	.github/workflows/release.yml
M	.gitignore
M	.gitleaks.toml
A	CHANGELOG.md
M	README.md
M	app/build.gradle.kts
M	app/proguard-rules.pro
M	app/src/main/AndroidManifest.xml
A	app/src/main/java/com/lagradost/cloudstream3/extractors/GMPlayer.kt
A	app/src/main/java/com/lagradost/cloudstream3/extractors/Okrulink.kt
A	app/src/main/java/com/lagradost/cloudstream3/extractors/VidSrcExtractor.kt
A	app/src/main/java/com/lagradost/cloudstream3/extractors/VidSrcTo.kt
A	app/src/main/java/com/lagradost/cloudstream3/extractors/Vidguard.kt
A	app/src/main/java/com/lagradost/cloudstream3/extractors/Vidplay.kt
A	app/src/main/java/com/lagradost/cloudstream3/utils/SubtitleHelperPlatformJvm.kt
M	app/src/main/java/dev/asfakul/yumeiro/MTApplication.kt
M	app/src/main/java/dev/asfakul/yumeiro/MainActivity.kt
A	app/src/main/java/dev/asfakul/yumeiro/OsCompat.kt
M	app/src/main/java/dev/asfakul/yumeiro/backup/Backup.kt
A	app/src/main/java/dev/asfakul/yumeiro/data/PlayCounts.kt
M	app/src/main/java/dev/asfakul/yumeiro/data/repository/MediaRepository.kt
M	app/src/main/java/dev/asfakul/yumeiro/network/Connectivity.kt
M	app/src/main/java/dev/asfakul/yumeiro/notify/AppNotifier.kt
M	app/src/main/java/dev/asfakul/yumeiro/notify/DiscoveryWorker.kt
A	app/src/main/java/dev/asfakul/yumeiro/notify/FcmPush.kt
M	app/src/main/java/dev/asfakul/yumeiro/notify/Inbox.kt
M	app/src/main/java/dev/asfakul/yumeiro/notify/NotifySettings.kt
M	app/src/main/java/dev/asfakul/yumeiro/notify/PushWake.kt
M	app/src/main/java/dev/asfakul/yumeiro/notify/Reminders.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/AudioLanguages.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionManager.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionMarket.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionModels.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionScanner.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/NewEpisodesWorker.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/OnboardingGate.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineBookmarks.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineRepository.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineSession.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/ReleaseTags.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/anime/AniList.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/download/DownloadWorker.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/download/OnlineDownloads.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/download/SongDownloads.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/web/SafeWebView.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/web/SavedSites.kt
A	app/src/main/java/dev/asfakul/yumeiro/player/AudioQuality.kt
M	app/src/main/java/dev/asfakul/yumeiro/player/PipController.kt
M	app/src/main/java/dev/asfakul/yumeiro/player/PlaybackDiagnostics.kt
A	app/src/main/java/dev/asfakul/yumeiro/player/PlaybackFacts.kt
A	app/src/main/java/dev/asfakul/yumeiro/player/SeekMath.kt
M	app/src/main/java/dev/asfakul/yumeiro/player/StreamRecovery.kt
A	app/src/main/java/dev/asfakul/yumeiro/player/SubtitleStyleSettings.kt
A	app/src/main/java/dev/asfakul/yumeiro/player/VideoZoom.kt
M	app/src/main/java/dev/asfakul/yumeiro/player/equalizer/EqualizerModels.kt
M	app/src/main/java/dev/asfakul/yumeiro/security/IntegrityGuard.kt
M	app/src/main/java/dev/asfakul/yumeiro/security/Privacy.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/Bridges.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/SourceHub.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/UniversalAdd.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/drama/DramaCatalog.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/drama/OnShortCatalog.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/drama/YumeiroDrama.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/js/JsProviders.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/manga/MangaLibrary.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/manga/MangaProvider.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/tachiyomi/MihonMangaProvider.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/tachiyomi/TachiyomiExtensions.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/IncomingMedia.kt
A	app/src/main/java/dev/asfakul/yumeiro/stream/LinkHosts.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/StreamMediaFactory.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/StreamResolver.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/components/Artwork.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/components/ErrorHelp.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/components/Kana.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/components/MTBottomBar.kt
(+ 13 image files)
(… 328 more non-image files; run git show --name-status ff7cb19)
```
## df94556 2026-11-13 Yumix: search box in the extension filter sheet
```
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/YumixFilter.kt
A	app/src/test/java/dev/asfakul/yumeiro/ui/screens/online/YumixFilterSearchTest.kt
M	app/src/test/java/dev/asfakul/yumeiro/ui/screens/online/YumixShowcaseTest.kt
```
## bba9041 2026-11-13 Video details: real size, duration and audio for streams
```
M	app/src/main/java/dev/asfakul/yumeiro/player/PlaybackFacts.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/StreamMediaFactory.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/components/MediaComponents.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/VideoPlayerDialogs.kt
M	app/src/test/java/dev/asfakul/yumeiro/player/PlaybackFactsTest.kt
M	app/src/test/java/dev/asfakul/yumeiro/ui/screens/online/YumixShowcaseTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/ui/screens/video/VideoDetailRowsTest.kt
```
## 93074cb 2026-11-13 Backup v2: staged restore, backups on phone storage, auto backup, reset center, saved pages cache
```
M	app/src/main/AndroidManifest.xml
M	app/src/main/java/dev/asfakul/yumeiro/MTApplication.kt
A	app/src/main/java/dev/asfakul/yumeiro/backup/AutoBackupWorker.kt
M	app/src/main/java/dev/asfakul/yumeiro/backup/Backup.kt
A	app/src/main/java/dev/asfakul/yumeiro/backup/BackupStore.kt
A	app/src/main/java/dev/asfakul/yumeiro/backup/PendingRestore.kt
A	app/src/main/java/dev/asfakul/yumeiro/backup/ResetCenter.kt
A	app/src/main/java/dev/asfakul/yumeiro/backup/Restart.kt
A	app/src/main/java/dev/asfakul/yumeiro/cache/DataCache.kt
A	app/src/main/java/dev/asfakul/yumeiro/cache/ImageCacheSize.kt
A	app/src/main/java/dev/asfakul/yumeiro/cache/OnlineCodec.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionManager.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineRepository.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/tachiyomi/TachiyomiExtensions.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/MTPlayerApp.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/BackupCenterScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/PrivacyTrackingBackup.kt
```
## 79f6d96 2026-11-13 Tests: backup v2 round trip, staged restore, resets, prune, saved pages cache, codec
```
A	app/src/test/java/dev/asfakul/yumeiro/backup/BackupV2Test.kt
```
## c87aa2d 2026-11-13 Sources redesign: quality, release, format, size and audio on every source; only the playing group opens; Show more paging; audio and subtitle tracks grouped by language; no more i icons
```
A	app/src/main/java/dev/asfakul/yumeiro/online/SourceFacts.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/components/MediaComponents.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/components/StateViews.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/components/TsukiDialog.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineHomeHero.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlinePlayerUi.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/player/NowPlayingScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/SettingsScreen.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/TrackGroups.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/VideoPlayerControls.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/VideoPlayerDialogs.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/watch/StreamWatchPanel.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/watch/WatchKit.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/watch/WatchScreens.kt
```
## 265c907 2026-11-13 Tests: source facts, playing-group-only expansion, Show more paging, language track groups
```
A	app/src/test/java/dev/asfakul/yumeiro/online/SourceFactsTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/ui/watch/SourceListLogicTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/ui/watch/SourcesRedesignUiTest.kt
```
## 1188650 2026-11-13 feat: separate Yumeiro (phone) and Yumeiro TV apps as product flavors
```
M	.github/pull_request_template.md
M	.github/workflows/ci.yml
M	.github/workflows/codeql.yml
M	.github/workflows/device-check.yml
M	.github/workflows/release.yml
M	README.md
M	app/build.gradle.kts
M	app/src/main/AndroidManifest.xml
A	app/src/main/java/dev/asfakul/yumeiro/Edition.kt
M	app/src/main/java/dev/asfakul/yumeiro/MTApplication.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/StreamMediaFactory.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/SettingsPersonalize.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/tv/TvMode.kt
M	app/src/main/java/dev/asfakul/yumeiro/update/GitHubReleaseClient.kt
M	app/src/main/java/dev/asfakul/yumeiro/update/UpdateManager.kt
A	app/src/tv/AndroidManifest.xml
M	docs/DEVELOPMENT.md
M	docs/EXTENSION-COMPAT.md
M	docs/GUIDE.md
M	docs/HANDOVER.md
```
## 9986ba0 2026-11-13 chore: v1.7.0, changelog, TV/phone doc, single Settings on TV home, updater edition tests
```
M	CHANGELOG.md
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/home/HomeScreen.kt
M	app/src/test/java/dev/asfakul/yumeiro/update/UpdaterLogicTest.kt
A	docs/TV-AND-PHONE.md
M	version.properties
```
## ce61d87 2026-11-13 docs: changelog backup details
```
M	CHANGELOG.md
```
## ebc70b7 2026-11-13 test: edition test for both apps; CI runs it in the TV app
```
M	.github/workflows/ci.yml
A	app/src/test/java/dev/asfakul/yumeiro/EditionTest.kt
```
## 5348319 2026-11-13 docs: handover for v1.7.0 (two apps, backup v2, cache, sources)
```
M	docs/HANDOVER.md
```
## b2ae9e6 2026-11-13 fix: CodeQL, SHA-256 cache file names, no Android cloud backup in the TV app
```
M	app/src/main/java/dev/asfakul/yumeiro/cache/DataCache.kt
M	app/src/tv/AndroidManifest.xml
```
## 4ac963f 2026-11-13 test: v1.7.0 mock shots (backup center, sources, audio by language) into docs/screenshots/v1.7.0
```
A	app/src/test/java/dev/asfakul/yumeiro/ui/watch/R12MockShotsTest.kt
(+ 3 image files)
```
## 1b29918 2026-11-14 feat(music): live codec details in Now Playing and Song info; remove the download icon from online music rows (long-press downloads)
```
M	CHANGELOG.md
M	app/src/main/java/dev/asfakul/yumeiro/data/model/MediaModels.kt
A	app/src/main/java/dev/asfakul/yumeiro/player/AudioCodecInfo.kt
M	app/src/main/java/dev/asfakul/yumeiro/player/MusicPlayerManager.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/music/MusicMix.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/player/NowPlayingScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/youtube/YtMusicBrowse.kt
A	app/src/test/java/dev/asfakul/yumeiro/player/AudioCodecInfoTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/player/MusicCodecUiTest.kt
M	app/src/test/java/dev/asfakul/yumeiro/ui/real/RealAppShotsTest.kt
```
## ce6e640 2026-11-14 fix(security): clear main-branch CodeQL alerts
```
M	.github/codeql/suppressions.jq
M	app/src/main/AndroidManifest.xml
M	app/src/main/java/dev/asfakul/yumeiro/network/DnsMessage.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionManager.kt
M	app/src/tv/AndroidManifest.xml
M	docs/security/AUDIT.md
```
## 18cd213 2026-11-14 fix(security): TV manifest allowBackup=false; justify DoT cert-trust false positive
```
M	.github/codeql/suppressions.jq
M	app/src/tv/AndroidManifest.xml
M	docs/security/AUDIT.md
```
## 429288f 2026-11-14 test(backup): run Backup v2 tests on Android 7, 10, 14 and 16
```
M	app/src/test/java/dev/asfakul/yumeiro/backup/BackupV2Test.kt
```
## 79bd56f 2026-11-14 docs: complete v1.7.0 changelog (security, fixes, tests, developers)
```
M	CHANGELOG.md
```
## 7f6c9ce 2026-11-14 docs: changelog wording
```
M	CHANGELOG.md
```
## 4fa8d64 2026-11-14 test: v1.7.0 Now Playing codec mock shot
```
M	app/src/test/java/dev/asfakul/yumeiro/ui/watch/R12MockShotsTest.kt
(+ 1 image files)
```
## d028db3 2026-11-14 v1.7.0: bring back the legacy (#30)
```
```
## 366a5f6 2026-11-14 v1.8.0: player first (#31)
```
M	.github/workflows/release.yml
M	CHANGELOG.md
M	README.md
M	app/build.gradle.kts
M	app/src/main/AndroidManifest.xml
A	app/src/main/java/dev/asfakul/yumeiro/DeviceClass.kt
M	app/src/main/java/dev/asfakul/yumeiro/MTApplication.kt
M	app/src/main/java/dev/asfakul/yumeiro/notify/AppNotifier.kt
M	app/src/main/java/dev/asfakul/yumeiro/notify/DiscoveryWorker.kt
A	app/src/main/java/dev/asfakul/yumeiro/notify/NotifyPacer.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/AudioLanguages.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/EpisodeStills.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionMarket.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineRepository.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineSession.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/OpeningKinds.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/web/BrowserCheck.kt
A	app/src/main/java/dev/asfakul/yumeiro/player/Anime4kEffect.kt
A	app/src/main/java/dev/asfakul/yumeiro/player/LastPlayed.kt
M	app/src/main/java/dev/asfakul/yumeiro/player/MusicPlayerManager.kt
A	app/src/main/java/dev/asfakul/yumeiro/player/QualityEngine.kt
A	app/src/main/java/dev/asfakul/yumeiro/player/VideoPrefs.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/drama/YumeiroDrama.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/YumeiroRenderersFactory.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/components/HideOnScroll.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/MTPlayerApp.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/home/HomeScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/library/LibraryScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/manga/MangaScreens.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/music/MusicScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/ExtensionMarketScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineComponents.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineDetailScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineHomeScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineHuePage.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlinePlayerUi.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineRowScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/YumixFilter.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/BackupCenterScreen.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/QualityAudioSettings.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/SettingsScreen.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/PlayerInfoOverlay.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/VideoPlayerControls.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/VideoPlayerDialogs.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/VideoPlayerModal.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/watch/WatchKit.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/watch/WatchScreens.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/youtube/YtMusicBrowse.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/theme/DreamBackdrop.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/viewmodel/MainViewModel.kt
M	app/src/main/java/dev/asfakul/yumeiro/watch/WatchTogether.kt
M	app/src/main/java/dev/asfakul/yumeiro/youtube/YouTubePlayback.kt
M	app/src/main/java/dev/asfakul/yumeiro/youtube/YouTubeStreams.kt
M	app/src/main/java/dev/asfakul/yumeiro/youtube/YtHistory.kt
A	app/src/main/java/dev/asfakul/yumeiro/youtube/music/YmEndless.kt
A	app/src/main/res/layout/yumeiro_player_view_surface.xml
M	app/src/test/java/dev/asfakul/yumeiro/backup/BackupV2Test.kt
M	app/src/test/java/dev/asfakul/yumeiro/online/SiteAccessTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/round13/Round13JsonTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/round13/Round13LogicTest.kt
M	app/src/test/java/dev/asfakul/yumeiro/ui/AppNavigationCrashTest.kt
M	app/src/test/java/dev/asfakul/yumeiro/ui/OnlinePagesTest.kt
M	app/src/test/java/dev/asfakul/yumeiro/update/UpdaterLogicTest.kt
M	docs/DISTRIBUTION.md
M	docs/HANDOVER.md
M	docs/PUSH-FCM.md
M	docs/TV-AND-PHONE.md
M	gradle/libs.versions.toml
M	version.properties
```
## 073041d 2026-11-14 v1.8.1: every title plays (#32)
```
M	CHANGELOG.md
M	README.md
M	app/build.gradle.kts
M	app/src/main/java/dev/asfakul/yumeiro/MTApplication.kt
M	app/src/main/java/dev/asfakul/yumeiro/data/repository/MediaRepository.kt
A	app/src/main/java/dev/asfakul/yumeiro/diagnostics/CrashLog.kt
A	app/src/main/java/dev/asfakul/yumeiro/notify/ReliabilityGuide.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionGuard.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineRepository.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineSession.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OpeningKinds.kt
A	app/src/main/java/dev/asfakul/yumeiro/player/HeatGuard.kt
A	app/src/main/java/dev/asfakul/yumeiro/player/ListeningTime.kt
M	app/src/main/java/dev/asfakul/yumeiro/player/MusicPlayerManager.kt
M	app/src/main/java/dev/asfakul/yumeiro/player/QualityEngine.kt
A	app/src/main/java/dev/asfakul/yumeiro/sources/drama/DramaHealth.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/drama/OnShortCatalog.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/drama/YumeiroDrama.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/extras/PermissionCenter.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/screens/extras/ReliabilityGuideDialog.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/extras/ReminderHost.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/home/HomeSections.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/library/LibraryDesign.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/library/LibraryScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineHomeScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlinePlayerUi.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/YumixFilter.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/BackdropSettings.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/CrashReportsCard.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/SettingsCatalog.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/SettingsControls.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/SettingsScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/PlayerInfoOverlay.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/VideoPlayerModal.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/watch/WatchScreens.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/theme/DreamBackdrop.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/viewmodel/MainViewModel.kt
M	app/src/test/java/dev/asfakul/yumeiro/round13/Round13LogicTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/round2/BackdropImportTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/round2/CrashLogTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/round2/HeatGuardTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/round2/LibraryRound2Test.kt
A	app/src/test/java/dev/asfakul/yumeiro/round2/LoaderRound2Test.kt
A	app/src/test/java/dev/asfakul/yumeiro/round2/QualityRound2Test.kt
A	app/src/test/java/dev/asfakul/yumeiro/round2/ReliabilityGuideTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/sources/drama/DramaHealthTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/sources/drama/YumeiroDramaLiveTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/ui/r13/R13MockShotsTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/ui/r14/R14MockShotsTest.kt
A	docs/STATUS-v1.8.1.md
A	docs/evidence/round2/order1-drama.md
A	docs/evidence/round2/orders2-9.md
M	version.properties
```
## d13e6b8 2026-11-14 R15 phase 1+2: remove main-thread regex, bound memory, codec-aware YouTube switch
```
M	.github/workflows/device-check.yml
A	app/src/androidTest/java/dev/asfakul/yumeiro/PlayerSwitchDeviceTest.kt
A	app/src/androidTest/java/dev/asfakul/yumeiro/StabilityStressDeviceTest.kt
M	app/src/main/java/com/lagradost/cloudstream3/extractors/VidSrcExtractor.kt
M	app/src/main/java/com/lagradost/cloudstream3/network/DdosGuardKiller.kt
M	app/src/main/java/com/lagradost/cloudstream3/plugins/RepositoryManager.kt
M	app/src/main/java/com/lagradost/cloudstream3/ui/home/HomeViewModel.kt
M	app/src/main/java/dev/asfakul/yumeiro/MTApplication.kt
M	app/src/main/java/dev/asfakul/yumeiro/cache/OnlineCodec.kt
M	app/src/main/java/dev/asfakul/yumeiro/data/repository/MediaRepository.kt
A	app/src/main/java/dev/asfakul/yumeiro/diagnostics/CrashExport.kt
M	app/src/main/java/dev/asfakul/yumeiro/diagnostics/CrashLog.kt
A	app/src/main/java/dev/asfakul/yumeiro/diagnostics/DevStrictMode.kt
A	app/src/main/java/dev/asfakul/yumeiro/diagnostics/MemoryWatch.kt
M	app/src/main/java/dev/asfakul/yumeiro/lyrics/Lyrics.kt
M	app/src/main/java/dev/asfakul/yumeiro/network/NetworkClient.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionManager.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionMarket.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionModels.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/ExtensionScanner.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlinePlayback.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineRepository.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/ReleaseTags.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/UrlFix.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/download/ExternalDownloader.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/download/HlsDownloader.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/subtitles/SubDL.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/subtitles/SubSource.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/subtitles/SubtitleArchive.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/subtitles/SubtitleHub.kt
M	app/src/main/java/dev/asfakul/yumeiro/player/QualityEngine.kt
A	app/src/main/java/dev/asfakul/yumeiro/player/VideoCodecSupport.kt
M	app/src/main/java/dev/asfakul/yumeiro/scrobble/ScrobbleRules.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/UniversalAdd.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/drama/DramaHealth.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/drama/OnShortCatalog.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/js/JsBridge.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/js/JsProviders.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/servers/TreeProvider.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/stremio/StremioProvider.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/tachiyomi/AniyomiProvider.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/tachiyomi/TachiyomiExtensions.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/LinkHosts.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/PlaylistMarket.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/PlaylistStore.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/WebPageExtractor.kt
M	app/src/main/java/dev/asfakul/yumeiro/subtitles/OnlineSubtitles.kt
M	app/src/main/java/dev/asfakul/yumeiro/subtitles/SubtitleSitesProvider.kt
A	app/src/main/java/dev/asfakul/yumeiro/text/RegexCache.kt
A	app/src/main/java/dev/asfakul/yumeiro/text/RegexWarmup.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/components/Kana.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/MTPlayerApp.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/home/HomeScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/music/MusicScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineHomeHero.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/YumixFilter.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/CrashReportsCard.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/SettingsCatalog.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/stream/StreamPlaylistUi.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/stream/StreamScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/VideoPlayerModal.kt
M	app/src/main/java/dev/asfakul/yumeiro/update/GitHubReleaseClient.kt
M	app/src/main/java/dev/asfakul/yumeiro/youtube/YouTubePlayback.kt
M	app/src/main/java/dev/asfakul/yumeiro/youtube/YouTubeStreams.kt
M	app/src/main/java/dev/asfakul/yumeiro/youtube/YtDiscovery.kt
M	app/src/main/java/dev/asfakul/yumeiro/youtube/music/YmParser.kt
A	app/src/test/java/dev/asfakul/yumeiro/r15/KanaAndCachesTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/r15/RegexGuardTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/r15/YouTubePickVideoTest.kt
A	docs/STABILITY-R15.md
A	docs/proposals/OPEN-SOURCE-COMPARISON.md
```
## 3e2114e 2026-11-14 R15 phase 3: live feeds never stop or fall back to stale data
```
M	.github/workflows/device-check.yml
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineRepository.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/components/MediaComponents.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineHomeScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineHuePage.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/YumixFeed.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/player/NowPlayingScreen.kt
A	app/src/test/java/dev/asfakul/yumeiro/ui/screens/online/LiveFeedTest.kt
```
## 8259ff3 2026-11-14 R15 phase 2/4: safe quality switch, View downloads → floating player, new video → full screen
```
A	app/src/main/java/dev/asfakul/yumeiro/player/PlayerSurfaceRules.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/MTPlayerApp.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/downloads/DownloadPrompt.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/stream/StreamScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/VideoPlayerModal.kt
A	app/src/test/java/dev/asfakul/yumeiro/r15/PlayerSurfaceRulesTest.kt
A	docs/SYSTEM-DESIGN.md
```
## 0afd322 2026-11-14 R15 phase 4: device test for View downloads → floating player and new video → full screen
```
M	.github/workflows/device-check.yml
A	app/src/androidTest/java/dev/asfakul/yumeiro/SurfaceModeDeviceTest.kt
```
## 474f7df 2026-11-14 R15 phase 2+3: drama resilience, quality parity, LiveFeed store for every feed
```
M	.github/workflows/device-check.yml
M	app/src/androidTest/java/dev/asfakul/yumeiro/PlayerSwitchDeviceTest.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineRepository.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineSession.kt
M	app/src/main/java/dev/asfakul/yumeiro/player/QualityEngine.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/drama/DramaCatalog.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/drama/DramaHealth.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/drama/OnShortCatalog.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/drama/YumeiroDrama.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/components/ScrollMemory.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/feed/LiveFeed.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/home/HomeOnline.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/home/HomeScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/stream/LiveTvHub.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/stream/StreamScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/VideoPlayerModal.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/youtube/YouTubeScreens.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/youtube/YtMusicBrowse.kt
A	app/src/test/java/dev/asfakul/yumeiro/r15/DramaResilienceTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/r15/LivePagedTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/r15/QualityParityTest.kt
M	app/src/test/java/dev/asfakul/yumeiro/sources/drama/DramaHealthTest.kt
M	app/src/test/java/dev/asfakul/yumeiro/sources/drama/YumeiroDramaLiveTest.kt
A	scripts/device-check.sh
```
## 47ba0a0 2026-11-14 R15 phase 5: forced downloading (IDM-style), pinned extensions
```
A	app/src/androidTest/java/dev/asfakul/yumeiro/ForceDownloadDeviceTest.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineRepository.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/PinnedExtensions.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/download/DownloadNaming.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/download/DownloadWorker.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/download/ExternalDownloader.kt
A	app/src/main/java/dev/asfakul/yumeiro/online/download/ForceGrab.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/download/OnlineDownloads.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineHomeScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlinePlayerUi.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/YumixFilter.kt
M	app/src/test/java/dev/asfakul/yumeiro/online/download/ExternalDownloaderTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/r15/ForceGrabTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/r15/PinnedExtensionsTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/ui/screens/online/PinnedExtensionsUiTest.kt
M	scripts/device-check.sh
```
## 5b05512 2026-11-14 R15 ADR-006: PlaybackSession owns the player; recovery clocks and routing as tested state machines
```
A	app/src/androidTest/java/dev/asfakul/yumeiro/PlaybackSessionDeviceTest.kt
M	app/src/main/java/dev/asfakul/yumeiro/MTApplication.kt
M	app/src/main/java/dev/asfakul/yumeiro/player/PlaybackHealthMonitor.kt
A	app/src/main/java/dev/asfakul/yumeiro/player/PlaybackSession.kt
A	app/src/main/java/dev/asfakul/yumeiro/player/RecoveryRouter.kt
A	app/src/main/java/dev/asfakul/yumeiro/player/SourceWatch.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/StreamMediaFactory.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/VideoPlayerModal.kt
M	app/src/main/java/dev/asfakul/yumeiro/watch/WatchTogether.kt
A	app/src/test/java/dev/asfakul/yumeiro/r15/PlaybackSessionTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/r15/RecoveryStateTest.kt
M	docs/SYSTEM-DESIGN.md
A	docs/evidence/round3/drama-live-b21.txt
A	docs/evidence/round3/force-dash-plan-live.txt
A	docs/evidence/round3/force-segmented-live.txt
M	scripts/device-check.sh
```
## 5a1c9ec 2026-11-14 R15 audit + device-run fixes: engine-level switch stall, crash report sharing, hot-path regex
```
M	app/src/androidTest/java/dev/asfakul/yumeiro/ExtensionCompatDeviceTest.kt
M	app/src/androidTest/java/dev/asfakul/yumeiro/ForceDownloadDeviceTest.kt
A	app/src/androidTest/java/dev/asfakul/yumeiro/HangDumpRule.kt
M	app/src/androidTest/java/dev/asfakul/yumeiro/OnlinePlaybackDeviceTest.kt
M	app/src/androidTest/java/dev/asfakul/yumeiro/PlaybackSessionDeviceTest.kt
M	app/src/androidTest/java/dev/asfakul/yumeiro/PlayerSwitchDeviceTest.kt
M	app/src/androidTest/java/dev/asfakul/yumeiro/StabilityStressDeviceTest.kt
M	app/src/androidTest/java/dev/asfakul/yumeiro/SurfaceModeDeviceTest.kt
M	app/src/main/AndroidManifest.xml
M	app/src/main/java/dev/asfakul/yumeiro/lyrics/TrackDetailsRepository.kt
M	app/src/main/java/dev/asfakul/yumeiro/network/SmartDns.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/EpisodeMatcher.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineRepository.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/SearchRanker.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/anime/AnimeIds.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/download/DownloadNaming.kt
M	app/src/main/java/dev/asfakul/yumeiro/scrobble/ScrobbleRules.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/IncomingMedia.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/LinkHosts.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/components/MediaComponents.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/components/SearchSuggest.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/equalizer/EqualizerScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/music/MusicMix.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/CrashReportsCard.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/stream/StreamScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/VideoPlayerModal.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/watch/StreamWatchPanel.kt
M	app/src/main/java/dev/asfakul/yumeiro/update/UpdateManager.kt
M	app/src/main/java/dev/asfakul/yumeiro/update/ui/UpdateFormat.kt
M	app/src/main/java/dev/asfakul/yumeiro/youtube/YtDiscovery.kt
M	app/src/main/java/dev/asfakul/yumeiro/youtube/YtWatch.kt
A	app/src/main/res/xml/shared_files.xml
A	app/src/test/java/dev/asfakul/yumeiro/r15/CrashShareTest.kt
M	scripts/device-check.sh
```
## b0e596d 2026-11-14 R15 device run 43626650174 fixes: foreground-service crash, audio-switch deadlock, start-up stalls
```
M	app/src/androidTest/java/dev/asfakul/yumeiro/ExtensionCompatDeviceTest.kt
M	app/src/androidTest/java/dev/asfakul/yumeiro/ForceDownloadDeviceTest.kt
M	app/src/androidTest/java/dev/asfakul/yumeiro/StabilityStressDeviceTest.kt
M	app/src/main/java/dev/asfakul/yumeiro/MTApplication.kt
M	app/src/main/java/dev/asfakul/yumeiro/diagnostics/MemoryWatch.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/web/BrowserCheck.kt
M	app/src/main/java/dev/asfakul/yumeiro/service/VideoPlaybackService.kt
A	app/src/main/java/dev/asfakul/yumeiro/startup/PrefsWarmup.kt
A	app/src/main/java/dev/asfakul/yumeiro/stream/StarvationSafeLoadControl.kt
M	app/src/main/java/dev/asfakul/yumeiro/stream/StreamMediaFactory.kt
A	app/src/test/java/dev/asfakul/yumeiro/r15/StartupGuardsTest.kt
A	app/src/test/java/dev/asfakul/yumeiro/r15/StarvationLoadControlTest.kt
M	scripts/device-check.sh
```
## 0575711 2026-11-14 CrashExport: publish via IS_PENDING so Downloads sees real size; c_ test opens source menu; onScreen diagnostics
```
M	app/src/androidTest/java/dev/asfakul/yumeiro/ForceDownloadDeviceTest.kt
M	app/src/androidTest/java/dev/asfakul/yumeiro/OnlinePlaybackDeviceTest.kt
M	app/src/androidTest/java/dev/asfakul/yumeiro/StabilityStressDeviceTest.kt
M	app/src/main/java/dev/asfakul/yumeiro/diagnostics/CrashExport.kt
```
## c692b70 2026-11-14 Startup: warm the remaining first-frame prefs and FFmpeg off main; APK cleanup on IO
```
M	app/src/main/java/dev/asfakul/yumeiro/startup/PrefsWarmup.kt
M	app/src/main/java/dev/asfakul/yumeiro/update/UpdateManager.kt
```
## 1eb1b65 2026-11-14 Stress test returns to the tab bar each round; StarterExtensions prefs off main
```
M	app/src/androidTest/java/dev/asfakul/yumeiro/StabilityStressDeviceTest.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/StarterExtensions.kt
```
## 7f528fe 2026-11-14 Stress test: report what keeps Compose busy instead of relaunching
```
M	app/src/androidTest/java/dev/asfakul/yumeiro/StabilityStressDeviceTest.kt
```
## ec8ca95 2026-11-14 Warm every pattern-holding class off main (enforced by test); stress test names the busy states
```
M	app/src/androidTest/java/dev/asfakul/yumeiro/StabilityStressDeviceTest.kt
M	app/src/main/java/dev/asfakul/yumeiro/text/RegexWarmup.kt
M	app/src/test/java/dev/asfakul/yumeiro/r15/RegexGuardTest.kt
A	docs/evidence/round3/device-runs.md
```
## fd446ac 2026-11-14 Device test: D-pad focus on Home and Yumix must settle
```
M	app/src/androidTest/java/dev/asfakul/yumeiro/StabilityStressDeviceTest.kt
```
## 9a3e375 2026-11-14 Carousels stop auto-advancing while focused, touched or with reduced motion
```
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/home/HomeSections.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineHomeHero.kt
```
## 48bcfb4 2026-11-14 Focus-settle test counts rendered frames, not only snapshot applies
```
M	app/src/androidTest/java/dev/asfakul/yumeiro/StabilityStressDeviceTest.kt
```
## 4540f1f 2026-11-14 Evidence: focus-settle runs 43646522316 and 43648850998
```
M	docs/evidence/round3/device-runs.md
```
## 2be78be 2026-11-14 Player time row reads 00:00 until the length is known
```
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/VideoPlayerControls.kt
A	app/src/test/java/dev/asfakul/yumeiro/r15/DurationLabelTest.kt
```
## d99d623 2026-11-14 Device tests: a dead manual source pick rotates again; no --:-- while opening
```
M	app/src/androidTest/java/dev/asfakul/yumeiro/OnlinePlaybackDeviceTest.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineSession.kt
```
## 82a5996 2026-11-14 Device test: a tab swipe settling under D-pad focus must not spin
```
M	app/src/androidTest/java/dev/asfakul/yumeiro/StabilityStressDeviceTest.kt
```
## 27a0e9d 2026-11-14 Tab pager takes no nested scroll from its pages and never rests between pages
```
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/MTPlayerApp.kt
```
## 2ba28e1 2026-11-14 Stress test fails on any endless recomposition, even one Back clears
```
M	app/src/androidTest/java/dev/asfakul/yumeiro/StabilityStressDeviceTest.kt
```
## 3d5cdc7 2026-11-15 docs: R15 status table, system design and roadmap updates
```
M	docs/HANDOVER.md
A	docs/R15-STATUS.md
M	docs/ROADMAP.md
M	docs/STABILITY-R15.md
M	docs/SYSTEM-DESIGN.md
M	docs/evidence/round3/device-runs.md
```
## 61bf92b 2026-11-15 test(device): drama feed appears and an episode reaches first frame
```
A	app/src/androidTest/java/dev/asfakul/yumeiro/DramaPlaybackDeviceTest.kt
M	scripts/device-check.sh
```
## 0cb815a 2026-11-15 test(device): drama test opens the first-run gate like Skip; targeted runs no longer cancel full runs
```
M	.github/workflows/device-check.yml
M	app/src/androidTest/java/dev/asfakul/yumeiro/DramaPlaybackDeviceTest.kt
```
## aad43ca 2026-11-15 docs: drama device evidence and full 7-class run after the tab-pager fix
```
M	docs/R15-STATUS.md
M	docs/evidence/round3/device-runs.md
```
## b231133 2026-11-15 diag: YuStart timeline from tap to first frame, gate wait log, drama test timings
```
M	app/src/androidTest/java/dev/asfakul/yumeiro/DramaPlaybackDeviceTest.kt
M	app/src/main/java/dev/asfakul/yumeiro/online/OnlineSession.kt
A	app/src/main/java/dev/asfakul/yumeiro/player/StartTimeline.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/drama/OnShortCatalog.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/drama/YumeiroDrama.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/VideoPlayerModal.kt
```
## 8a16df5 2026-11-15 drama: user requests jump ahead of health probes, shared requests are promoted, episode 1 links prefetched on open
```
M	app/src/main/java/dev/asfakul/yumeiro/sources/drama/DramaHealth.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/drama/OnShortCatalog.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/drama/YumeiroDrama.kt
A	app/src/test/java/dev/asfakul/yumeiro/r15/GateLaneTest.kt
A	docs/INVENTORY.md
M	scripts/device-check.sh
```
## 0fc4acc 2026-11-15 drama: probes start only on an idle host; player start marks; gate logs who held the slots
```
M	app/src/main/java/dev/asfakul/yumeiro/sources/drama/OnShortCatalog.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/video/VideoPlayerModal.kt
```
## 8971a7b 2026-11-15 ci: repeat and shard inputs for flake hunting; stall watchdog labels render/harness/app; drama start budget
```
M	.github/workflows/device-check.yml
M	app/src/androidTest/java/dev/asfakul/yumeiro/DramaPlaybackDeviceTest.kt
M	app/src/main/java/dev/asfakul/yumeiro/diagnostics/MemoryWatch.kt
M	scripts/device-check.sh
```
## da1a3a9 2026-11-15 test(phase3): screen-position device test for live feeds; card identity semantics
```
A	app/src/androidTest/java/dev/asfakul/yumeiro/FeedPositionDeviceTest.kt
A	app/src/main/java/dev/asfakul/yumeiro/ui/feed/FeedCard.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/home/HomeScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineComponents.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineFeed.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/stream/LiveTvHub.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/youtube/YouTubeScreens.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/youtube/YtMusicBrowse.kt
M	scripts/device-check.sh
```
## 2f7e9b2 2026-11-15 test: device tests for the quality list and pinned extensions; manual phone/TV test scripts
```
A	app/src/androidTest/java/dev/asfakul/yumeiro/PinnedExtensionsDeviceTest.kt
A	app/src/androidTest/java/dev/asfakul/yumeiro/QualityListDeviceTest.kt
A	docs/MANUAL-TESTS.md
M	scripts/device-check.sh
```
## 205a4c8 2026-11-15 test(phase3): real system swipes, scroll to the first cards, say where the app was on a missed tap
```
M	app/src/androidTest/java/dev/asfakul/yumeiro/FeedPositionDeviceTest.kt
```
## e1410e7 2026-11-15 test: shared first-run helper (grant, Skip the welcome guide, wait for tabs) for the new device tests
```
M	app/src/androidTest/java/dev/asfakul/yumeiro/FeedPositionDeviceTest.kt
A	app/src/androidTest/java/dev/asfakul/yumeiro/FirstRun.kt
M	app/src/androidTest/java/dev/asfakul/yumeiro/PinnedExtensionsDeviceTest.kt
M	app/src/androidTest/java/dev/asfakul/yumeiro/QualityListDeviceTest.kt
```
## 1192f84 2026-11-15 perf: replace live GPU blur on feed cards and the watch page with a tiny decoded poster
```
M	app/src/androidTest/java/dev/asfakul/yumeiro/FeedPositionDeviceTest.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineComponents.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineFeed.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/watch/WatchScreens.kt
```
## c1c555d 2026-11-15 test: wait for shell input to finish, open feeds via Home shortcuts, visible-node taps, full screen dumps and shots
```
M	app/src/androidTest/java/dev/asfakul/yumeiro/FeedPositionDeviceTest.kt
M	app/src/androidTest/java/dev/asfakul/yumeiro/FirstRun.kt
M	app/src/androidTest/java/dev/asfakul/yumeiro/PinnedExtensionsDeviceTest.kt
M	app/src/androidTest/java/dev/asfakul/yumeiro/QualityListDeviceTest.kt
```
## c093f81 2026-11-15 fix: Yumix explore crash, drama gate lanes; tests wait for the welcome guide
```
M	app/src/androidTest/java/dev/asfakul/yumeiro/FirstRun.kt
M	app/src/androidTest/java/dev/asfakul/yumeiro/StabilityStressDeviceTest.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/drama/OnShortCatalog.kt
M	app/src/main/java/dev/asfakul/yumeiro/sources/drama/YumeiroDrama.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineHomeScreen.kt
M	app/src/test/java/dev/asfakul/yumeiro/r15/GateLaneTest.kt
M	scripts/device-check.sh
```
## 15d8e33 2026-11-15 test: wait for the splash, close app prompts, scroll to shortcut cards, open a Live TV playlist grid
```
M	app/src/androidTest/java/dev/asfakul/yumeiro/FeedPositionDeviceTest.kt
M	app/src/androidTest/java/dev/asfakul/yumeiro/FirstRun.kt
M	app/src/androidTest/java/dev/asfakul/yumeiro/PinnedExtensionsDeviceTest.kt
```
## 374b3c1 2026-11-15 test: count only placed feed cards; scroll vertical lists only
```
M	app/src/androidTest/java/dev/asfakul/yumeiro/FeedPositionDeviceTest.kt
M	app/src/androidTest/java/dev/asfakul/yumeiro/FirstRun.kt
```
## 5016dbc 2026-11-15 test: a key seen twice is not a vanished card; small screens fit 3 video cards
```
M	app/src/androidTest/java/dev/asfakul/yumeiro/FeedPositionDeviceTest.kt
```
## befbd13 2026-11-15 chore: ignore secrets/ and service-account JSON files
```
M	.gitignore
```
## db40142 2026-11-15 test(phase3): card identity includes its shelf/grid slot; dump both snapshots on a problem
```
M	app/src/androidTest/java/dev/asfakul/yumeiro/FeedPositionDeviceTest.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/feed/FeedCard.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/home/HomeScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineComponents.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineHomeHero.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineHomeScreen.kt
M	app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/OnlineHuePage.kt
```
