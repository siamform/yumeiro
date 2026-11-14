# Narrow, reviewed CodeQL suppressions (rule + exact path). Everything else is reported as-is.
# Each entry is justified in docs/security/AUDIT.md ("CodeQL suppressions").
#  1. Weak-crypto rules on interop code that must match third-party formats bit-for-bit:
#     - sources/js/JsCrypto.kt: CryptoJS AES-CBC + EVP_BytesToKey(MD5) used by websites.
#     - eu/kanade/**: vendored Tachiyomi/Aniyomi API; generateId() = MD5 must match upstream ids.
#     - youtube/YtAuthHash.kt: SAPISIDHASH is SHA-1 by YouTube's definition (request signing, not storage).
#     - scrobble/LastFmSignature.kt: Last.fm's api_sig is MD5 by Last.fm's definition (request signing).
#     - com/lagradost/cloudstream3/extractors/{Vidplay,VidSrcTo}.kt: CloudStream v4.5.2 extractors (shipped
#       because extensions call them); the sites encode their stream ids with RC4, so decoding must use RC4.
#  2. Certificate pinning on hosts Yumeiro does not control (Kitsu/Trakt APIs rotate certs;
#     Plex is user-hosted; the UnifiedPush server in notify/PushWake.kt is whatever the user's
#     push app uses, e.g. ntfy.sh or self-hosted; youtube/YtAccount.kt checks a pasted cookie with
#     youtube.com, whose certificates Google rotates; online/subtitles/{OpenSubtitles,SubDL,SubSource}.kt
#     call third-party subtitle APIs behind CDNs that rotate certificates, HTTPS-only in
#     network_security_config.xml, and the user's key never follows a redirect off the site
#     (SubtitleNet.keyScoped); tracking/Simkl.kt, scrobble/LastFm.kt and lyrics/LrcLib.kt call
#     third-party APIs (Simkl, Last.fm, LRCLIB) that rotate certificates, HTTPS-only as above;
#     notify/CloudAlerts.kt calls our own Worker on Cloudflare, whose edge certificates Cloudflare
#     issues and rotates automatically (only an endpoint + AniList ids are sent), HTTPS-only.
#     A stale pin would silently break them, so system CAs only.
#  3. JavaScript in the Google sign-in WebView (ui/screens/settings/YouTubeAccountScreen.kt): Google's own
#     sign-in page does not work without it. Only accounts.google.com / youtube.com are loaded, file and
#     content access are off, non-http schemes are blocked (SafeWebView.harden).
#     Same rule in the captcha / site sign-in page (ui/screens/online/BrowserCheckHost.kt): Cloudflare,
#     Turnstile, hCaptcha and site logins are JavaScript. No JS bridge, file and content access off,
#     navigations / pop-ups / sub-requests filtered by AdShield, the pop-up probe runs without JavaScript.
#  4. The stream finder page (ui/screens/stream/PageStreamFinderDialog.kt) opens a site's own video player,
#     which is JavaScript. Its bridge "MTFinder" exposes only two @JavascriptInterface methods that take a
#     string (a media URL to record, a DRM system name filtered to 40 safe chars); no other Java is reachable
#     (minSdk > 17). File and content access off, non-http schemes blocked, ads filtered (SafeWebView, AdFilter).
#     Same for the off-screen stream capture (online/web/HeadlessCapture.kt, same "MTFinder" bridge shape) and the
#     background site check (online/web/BrowserCheck.kt, no bridge): both load a site's own JavaScript page.
#  5. More third-party hosts for rule 2 (same reasoning, HTTPS-only, system CAs): AniList / AniSkip / ARM id
#     mapping (online/anime/{AniList,AniSkip,AnimeIds}.kt), YouTube Music (youtube/music/YtMusicApi.kt), Firebase
#     Cloud Messaging via Google (notify/FcmPush.kt), the extension market repo lists on GitHub / user repos
#     (online/ExtensionMarket.kt), user-pasted links (sources/UniversalAdd.kt) and subtitle search
#     (subtitles/OnlineSubtitles.kt).
#  6. websettings-allow-content-access on WebViews whose settings are set false where they are created
#     (page.settings.allowFileAccess/allowContentAccess = false, plus SafeWebView.harden). The query treats
#     later reads of a WebView held in a variable / Compose state as new, unconfigured WebViews.
#  7. java/unsafe-cert-trust in network/DnsMessage.kt (DNS-over-TLS): the socket comes from the system
#     SSLSocketFactory (system CAs, no custom TrustManager), sets endpointIdentificationAlgorithm = "HTTPS" via
#     setSSLParameters before the handshake and then checks the name with the system HostnameVerifier. The
#     query's barrier doesn't follow Kotlin property setters through to the later socket reads.
def suppressed:
  (.ruleId // .rule.id) as $r
  | (.locations[0].physicalLocation.artifactLocation.uri // "") as $u
  | ((($r == "java/weak-cryptographic-algorithm") or ($r == "java/potentially-weak-cryptographic-algorithm"))
      and ($u | test("^app/src/main/java/(eu/kanade/|dev/asfakul/yumeiro/sources/js/JsCrypto\\.kt$|dev/asfakul/yumeiro/youtube/YtAuthHash\\.kt$|dev/asfakul/yumeiro/scrobble/LastFmSignature\\.kt$|com/lagradost/cloudstream3/extractors/(Vidplay|VidSrcTo)\\.kt$)")))
    or (($r == "java/android/missing-certificate-pinning")
      and ($u | test("^app/src/main/java/dev/asfakul/yumeiro/(tracking/(Kitsu|Trakt|Simkl)|scrobble/LastFm|lyrics/LrcLib|sources/servers/PlexProvider|notify/(PushWake|CloudAlerts)|youtube/YtAccount|online/subtitles/(OpenSubtitles|SubDL|SubSource)|online/anime/(AniList|AniSkip|AnimeIds)|youtube/music/YtMusicApi|notify/FcmPush|online/ExtensionMarket|sources/UniversalAdd|subtitles/OnlineSubtitles)\\.kt$")))
    or (($r == "java/android/websettings-javascript-enabled")
      and (($u == "app/src/main/java/dev/asfakul/yumeiro/ui/screens/settings/YouTubeAccountScreen.kt")
        or ($u == "app/src/main/java/dev/asfakul/yumeiro/ui/screens/online/BrowserCheckHost.kt")
        or ($u == "app/src/main/java/dev/asfakul/yumeiro/ui/screens/stream/PageStreamFinderDialog.kt")
        or ($u == "app/src/main/java/dev/asfakul/yumeiro/online/web/HeadlessCapture.kt")
        or ($u == "app/src/main/java/dev/asfakul/yumeiro/online/web/BrowserCheck.kt")))
    or (($r == "java/android/webview-addjavascriptinterface")
      and (($u == "app/src/main/java/dev/asfakul/yumeiro/ui/screens/stream/PageStreamFinderDialog.kt")
        or ($u == "app/src/main/java/dev/asfakul/yumeiro/online/web/HeadlessCapture.kt")))
    or (($r == "java/android/websettings-allow-content-access")
      and ($u | test("^app/src/main/java/dev/asfakul/yumeiro/(ui/screens/stream/PageStreamFinderDialog|ui/screens/settings/YouTubeAccountScreen|online/web/HeadlessCapture)\\.kt$")))
    or ((($r == "java/weak-cryptographic-algorithm") or ($r == "java/potentially-weak-cryptographic-algorithm"))
      and ($u == "app/src/main/java/dev/asfakul/yumeiro/online/download/HlsDownloader.kt"))
    or (($r == "java/unsafe-cert-trust")
      and ($u == "app/src/main/java/dev/asfakul/yumeiro/network/DnsMessage.kt"));
.runs |= map(.results |= map(select(suppressed | not)))
