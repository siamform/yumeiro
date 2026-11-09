/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import dev.asfakul.yumeiro.online.anime.AniList
import dev.asfakul.yumeiro.online.anime.MyAnimeList

/**
 * Step-by-step "how do I get this?" guides shown next to Sources, Tracking and the YouTube account.
 * Plain data so the wording is unit-tested (links, redirect values) and reused by every screen.
 *
 * Yumeiro ships no content and recommends no third-party repositories: the source guides explain
 * where links come from and what they look like, never which ones to add.
 */
data class SetupGuide(
    val id: String,
    val title: String,
    val intro: String,
    val steps: List<Step>,
    val tips: List<String> = emptyList(),
) {
    /** One numbered step. [link] opens in the browser; [copy] is a value the user pastes elsewhere. */
    data class Step(val text: String, val link: String? = null, val linkLabel: String? = null, val copy: String? = null)
}

object SetupGuides {

    // region sources

    val aniyomi = SetupGuide(
        id = "aniyomi",
        title = "How to get Aniyomi extensions",
        intro = "Anime extensions come from repositories run by the community. Yumeiro doesn't ship or recommend any, so you add the ones you trust.",
        steps = listOf(
            SetupGuide.Step("Find a repository. Extension projects list their repo link on their GitHub page or website, often under \"Add repo\" or \"Install\"."),
            SetupGuide.Step("Copy the link. Any of these work: a link ending in index.min.json, a GitHub repo page, a raw.githubusercontent.com link, or an aniyomi:// \"Add repo\" button (long-press it and copy)."),
            SetupGuide.Step("Tap Add next to Repositories and paste the link."),
            SetupGuide.Step("Under Available, tap the download icon next to the extensions you want. Filter by name or language code (en, ja, es…)."),
            SetupGuide.Step("Tap Trust on each new extension. Yumeiro checks its signing key, and the code doesn't run until you trust it."),
            SetupGuide.Step("Open Videos → Online. The new sources appear there and in search."),
        ),
        tips = listOf(
            "Repositories made for Aniyomi work as-is. Mihon / Tachiyomi repositories are for manga, so add those in the Mihon tab.",
            "If an extension stops working, check Source status. Sites change often, and the repo owner usually ships a fix as an update.",
            "Extensions run code on your phone. Only add repositories from people you trust.",
        ),
    )

    val mihon = SetupGuide(
        id = "mihon",
        title = "How to get Mihon manga extensions",
        intro = "Manga extensions use the Mihon / Tachiyomi format and come from community repositories. Yumeiro doesn't ship or recommend any.",
        steps = listOf(
            SetupGuide.Step("Find a repository on the extension project's GitHub page or website."),
            SetupGuide.Step("Copy its link: index.min.json, a GitHub repo page, a raw link, or a tachiyomi:// / mihon:// \"Add repo\" button (long-press it and copy)."),
            SetupGuide.Step("Tap Add next to Repositories and paste it."),
            SetupGuide.Step("Install extensions from Available, then tap Trust on each one."),
            SetupGuide.Step("Open the Manga section to browse and read."),
        ),
        tips = listOf(
            "Anime repositories belong in the Aniyomi tab.",
            "Only add repositories from people you trust.",
        ),
    )

    val stremio = SetupGuide(
        id = "stremio",
        title = "How to find Stremio add-ons",
        intro = "A Stremio add-on is a small web service. Each one has a manifest link ending in manifest.json, and that's all Yumeiro needs.",
        steps = listOf(
            SetupGuide.Step("Start with Cinemeta, Stremio's official catalog of movies and series: tap Add on its card below."),
            SetupGuide.Step("Find more in the community add-on catalog, or on an add-on's own website.", link = "https://stremio-addons.net", linkLabel = "Add-on catalog"),
            SetupGuide.Step("On the add-on's page, long-press Install (or Copy link) and copy it. https:// and stremio:// links both work."),
            SetupGuide.Step("Some add-ons need setup first. Open their Configure page in your browser, choose options, then copy the final install link."),
            SetupGuide.Step("Tap Add here and paste the link. Catalogs show in Videos → Online, and streams show when you open a title."),
        ),
        tips = listOf(
            "Torrent-only add-ons are skipped. Yumeiro plays HTTP streams and subtitles.",
            "If an add-on asks for a debrid or account key, that key lives inside its link. Treat the link like a password.",
        ),
    )

    val javascript = SetupGuide(
        id = "javascript",
        title = "How to add JavaScript sources",
        intro = "JavaScript sources use the Mangayomi format and run in a locked-down engine with no access to your files or other apps.",
        steps = listOf(
            SetupGuide.Step("Find a Mangayomi-format repository on its project's GitHub page. The link usually ends in index.json."),
            SetupGuide.Step("Copy the link. A mangayomi:// \"Add repo\" link works too."),
            SetupGuide.Step("Tap Add next to Repositories and paste it."),
            SetupGuide.Step("Install sources from Available. Manga sources show in Manga, and anime or video sources in Videos → Online."),
        ),
        tips = listOf("Only the JavaScript sources in a repository are used; Dart ones are skipped."),
    )

    val servers = SetupGuide(
        id = "servers",
        title = "How to connect your home server",
        intro = "Play your own movies, shows and music straight from your server. Nothing goes through a Yumeiro server.",
        steps = listOf(
            SetupGuide.Step("Find your server's address in its dashboard. Jellyfin and Emby usually use port 8096, e.g. http://192.168.1.10:8096. Plex uses 32400."),
            SetupGuide.Step("Connect your phone to the same Wi-Fi, or use the server's remote address if you've set one up."),
            SetupGuide.Step("Tap the button for your server type and enter the address. Jellyfin, Emby, WebDAV and SMB ask for your user name and password. Plex opens a Plex sign-in instead."),
            SetupGuide.Step("For DLNA / UPnP, just tap DLNA. Yumeiro finds servers on your Wi-Fi by itself."),
            SetupGuide.Step("WebDAV takes a full link (https://host/path). SMB takes smb://host/share, plus your Windows or NAS account."),
        ),
        tips = listOf(
            "Can't connect? Open the address in your phone's browser first. If that fails too, it's a network or firewall issue on the server.",
            "Passwords and tokens are encrypted on the device with the Android Keystore.",
        ),
    )

    val cloudstream = SetupGuide(
        id = "cloudstream",
        title = "How to get CloudStream extensions",
        intro = "CloudStream extensions (.cs3) come from community repositories. Yumeiro doesn't ship or recommend any.",
        steps = listOf(
            SetupGuide.Step("Find a repository link on the extension project's GitHub page or website. It usually ends in repo.json or plugins.json, or is a short code."),
            SetupGuide.Step("Tap ⋮ → Add repository and paste it, or browse the Extension market."),
            SetupGuide.Step("Install the extensions you want. Their titles show in Videos → Online."),
            SetupGuide.Step("Got a .cs3 file? Use ⋮ → Install from file instead."),
        ),
        tips = listOf("Only add repositories from people you trust. Extensions run code on your phone."),
    )

    // endregion

    // region tracking

    val anilist = SetupGuide(
        id = "anilist",
        title = "Set up AniList",
        intro = "AniList is free. Since Yumeiro has no server, you create your own free client ID once, and your sign-in goes straight to AniList.",
        steps = listOf(
            SetupGuide.Step("Create a free AniList account, or skip this if you already have one.", link = "https://anilist.co/signup", linkLabel = "Sign up"),
            SetupGuide.Step("Open AniList's developer settings and tap Create New Client.", link = "https://anilist.co/settings/developer", linkLabel = "Developer settings"),
            SetupGuide.Step("Name: Yumeiro (or anything). Redirect URL: paste this exact value.", copy = AniList.REDIRECT),
            SetupGuide.Step("Save, then copy the Client ID (a number)."),
            SetupGuide.Step("Back here, tap Set up on AniList and paste the ID."),
            SetupGuide.Step("Tap Sign in, then Approve. You'll come back to Yumeiro signed in."),
        ),
        tips = listOf("Episodes are marked as watched after about 85% of an episode."),
    )

    val myAnimeList = SetupGuide(
        id = "mal",
        title = "Set up MyAnimeList",
        intro = "MyAnimeList is free. You create your own free API client once, so there's no shared key to break.",
        steps = listOf(
            SetupGuide.Step("Create a free MyAnimeList account, or skip this if you already have one.", link = "https://myanimelist.net/register.php", linkLabel = "Sign up"),
            SetupGuide.Step("Open MyAnimeList's API settings and tap Create ID.", link = "https://myanimelist.net/apiconfig", linkLabel = "API settings"),
            SetupGuide.Step("App Type: choose Android. It can't be changed later, and it means no client secret is needed."),
            SetupGuide.Step("App Redirect URL: paste this exact value.", copy = MyAnimeList.REDIRECT),
            SetupGuide.Step("Homepage URL: any link, like the Yumeiro project page. Fill in the name and description, accept the terms and submit.", copy = DeveloperInfo.SOURCE),
            SetupGuide.Step("Open the new app (Edit) and copy its Client ID."),
            SetupGuide.Step("Back here, tap Set up on MyAnimeList, paste the ID, then tap Sign in and Allow."),
        ),
        tips = listOf("If sign-in says the redirect doesn't match, check the redirect URL in your MyAnimeList app for typos."),
    )

    val kitsu = SetupGuide(
        id = "kitsu",
        title = "Set up Kitsu",
        intro = "Kitsu needs no developer setup, just an account.",
        steps = listOf(
            SetupGuide.Step("Create a free Kitsu account with your e-mail.", link = "https://kitsu.app", linkLabel = "Open Kitsu"),
            SetupGuide.Step("If you joined with a social login, set a password in Kitsu's account settings first."),
            SetupGuide.Step("Back here, tap Sign in on Kitsu and enter the same e-mail and password."),
        ),
        tips = listOf("Your password goes to Kitsu once. Only the sign-in token is kept, encrypted."),
    )

    val trakt = SetupGuide(
        id = "trakt",
        title = "Set up Trakt",
        intro = "Trakt tracks the movies and shows you finish, from any source. Like AniList, it uses your own free API app.",
        steps = listOf(
            SetupGuide.Step("Create a free Trakt account, or skip this if you already have one.", link = "https://trakt.tv/auth/join", linkLabel = "Sign up"),
            SetupGuide.Step("Open Trakt's API apps and tap New Application.", link = "https://trakt.tv/oauth/applications/new", linkLabel = "New application"),
            SetupGuide.Step("Name: Yumeiro. Redirect URI: paste this exact value (Trakt may warn about it; save anyway). Leave allowed origins and the other fields empty.", copy = TRAKT_REDIRECT),
            SetupGuide.Step("Copy the Client ID, then tap Add Trakt API app here and paste it. New Trakt apps have no client secret; paste one only if your (older) app shows it."),
            SetupGuide.Step("Tap Sign in. Yumeiro shows a short code and opens trakt.tv/activate. Type the code there and approve."),
        ),
        tips = listOf("Signing in shows a short code; nothing secret is needed. If your app has a client secret it's stored encrypted on the device and only sent to Trakt.", "Yumeiro refreshes the sign-in before it expires (Trakt sign-ins last 7 days), so tracking keeps working."),
    )

    val simkl = SetupGuide(
        id = "simkl",
        title = "Simkl: anime, shows and movies in one list",
        intro = "Simkl tracks anime, TV and movies together and syncs with many other apps. Yumeiro has a built-in Simkl app, so you only sign in.",
        steps = listOf(
            SetupGuide.Step("Create a free Simkl account, or skip this if you already have one.", link = "https://simkl.com/", linkLabel = "Open Simkl"),
            SetupGuide.Step("Tap Sign in. Yumeiro shows a short code and opens simkl.com/pin. Type the code there and approve."),
            SetupGuide.Step("Done. Finished episodes and movies are marked watched on Simkl, matched by MyAnimeList, AniList, IMDb or TMDB id."),
        ),
        tips = listOf(
            "Using your own Simkl app is optional: create one at simkl.com/settings/developer (redirect URI urn:ietf:wg:oauth:2.0:oob), then tap Change key and paste its Client ID.",
            "Simkl sign-ins last 7 days and Yumeiro renews them on its own.",
        ),
    )

    val lastfm = SetupGuide(
        id = "lastfm",
        title = "Last.fm scrobbling",
        intro = "Scrobbling sends the songs you play to your Last.fm profile, for stats, charts and recommendations. Local files and streamed songs both count.",
        steps = listOf(
            SetupGuide.Step("Create a free Last.fm account, or skip this if you already have one.", link = "https://www.last.fm/join", linkLabel = "Sign up"),
            SetupGuide.Step("Tap Sign in. Last.fm opens in your browser; tap Yes, allow access. Yumeiro never sees your password."),
            SetupGuide.Step("Come back to Yumeiro. It notices the approval within a few seconds."),
        ),
        tips = listOf(
            "A song is scrobbled after you've listened to half of it or 4 minutes, whichever comes first (Last.fm's rule). Songs under 30 seconds don't count.",
            "Offline? Scrobbles wait on the device (up to 500, for 14 days) and are sent when you're back online.",
            "Incognito mode pauses scrobbling.",
        ),
    )

    // endregion

    val youtube = SetupGuide(
        id = "youtube",
        title = "YouTube sign-in: what it does",
        intro = "Yumeiro plays YouTube without an account. Sign in only if YouTube keeps saying \"Sign in to confirm you're not a bot\", or to play age-restricted videos.",
        steps = listOf(
            SetupGuide.Step("Tap Sign in with Google. Google's own sign-in page opens inside Yumeiro."),
            SetupGuide.Step("Sign in as usual, including 2-step verification. Yumeiro never sees your password."),
            SetupGuide.Step("When YouTube loads, Yumeiro saves the sign-in cookies, encrypted with the Android Keystore."),
            SetupGuide.Step("By default the account is used only when YouTube blocks a video. Everything else stays anonymous."),
            SetupGuide.Step("If Google says \"This browser or app may not be secure\", use Paste cookie instead. The next guide shows how."),
        ),
        tips = listOf(
            "Third-party apps aren't official YouTube clients. Google could limit an account used this way, so a spare account is safest.",
            "Sign out at any time. That deletes the saved cookies and clears the Google sign-in from Yumeiro's web view.",
            "Sign-in data is never included in backups.",
        ),
    )

    /** Paste-cookie fallback (Google may refuse the in-app sign-in page). */
    val youtubeCookie = SetupGuide(
        id = "youtube_cookie",
        title = "How to get your YouTube cookie",
        intro = "Use this when Google won't let you sign in inside Yumeiro. You sign in on a normal browser and copy the cookie it got. It takes about two minutes on a computer.",
        steps = listOf(
            SetupGuide.Step("On a computer, open a private window (Chrome: Ctrl+Shift+N, Firefox: Ctrl+Shift+P). A private window matters: YouTube changes the cookie of open tabs, and a private session is never reopened."),
            SetupGuide.Step("Sign in to YouTube in that window. A spare Google account is safest.", link = "https://www.youtube.com/", linkLabel = "youtube.com"),
            SetupGuide.Step("In the same tab, open youtube.com/robots.txt. This page is quiet, so the cookie won't change while you copy it.", link = "https://www.youtube.com/robots.txt", linkLabel = "robots.txt", copy = "https://www.youtube.com/robots.txt"),
            SetupGuide.Step("Press F12 to open Developer tools, choose the Network tab and reload the page (F5)."),
            SetupGuide.Step("Click the robots.txt request. Under Request Headers find \"cookie\", right-click its value and copy it. It is long and starts with something like VISITOR_INFO1_LIVE= or SID=."),
            SetupGuide.Step("Easier option: install the open-source extension Get cookies.txt LOCALLY (Chrome or Firefox), allow it in private windows, and export youtube.com as cookies.txt. Yumeiro reads that file's text too.", link = "https://github.com/kairi003/Get-cookies.txt-LOCALLY", linkLabel = "Get cookies.txt LOCALLY"),
            SetupGuide.Step("Close the private window without signing out. Signing out would cancel the cookie."),
            SetupGuide.Step("Send the text to your phone in a way only you can see (for example a note to yourself), paste it here and tap Check and save. Then delete the note."),
        ),
        tips = listOf(
            "Also accepted: Copy as cURL from Developer tools, JSON exports from cookie extensions, and an ArchiveTune or Metrolist token (the ***INNERTUBE COOKIE*** line).",
            "No computer? Firefox for Android supports the open-source Cookie-Editor add-on. Sign in to youtube.com in a private tab, open Cookie-Editor, choose Export, then Header string or Netscape, and paste the result here.",
            "This cookie works like your password. Never post it in chats, issues or screenshots. Anyone who has it can use your account.",
            "Yumeiro checks the cookie only with youtube.com, then stores it encrypted with the Android Keystore. It is never included in backups.",
            "If YouTube signs you out later, the cookie stops working. Copy a fresh one the same way.",
        ),
    )

    /** UnifiedPush via ntfy: free, open-source push so alerts arrive even when the phone is idle. */
    val unifiedPush = SetupGuide(
        id = "unifiedpush",
        title = "Reliable alerts with ntfy (free)",
        intro = "Android may hold back background checks for hours on some phones. UnifiedPush lets a free push app wake Yumeiro on time, so new-episode and news alerts aren't late. No Google services, no account and no fees.",
        steps = listOf(
            SetupGuide.Step("Install ntfy. It's free and open source. F-Droid has the build without Google services.", link = "https://f-droid.org/packages/io.heckel.ntfy/", linkLabel = "ntfy on F-Droid"),
            SetupGuide.Step("Or get ntfy from Google Play.", link = "https://play.google.com/store/apps/details?id=io.heckel.ntfy", linkLabel = "ntfy on Google Play"),
            SetupGuide.Step("Open ntfy once and allow its notification. ntfy keeps one light connection open for every app that uses it."),
            SetupGuide.Step("In Android settings → Apps → ntfy → Battery, choose Unrestricted. Do the same for Yumeiro if your phone is on dontkillmyapp.com.", link = "https://dontkillmyapp.com/", linkLabel = "dontkillmyapp.com"),
            SetupGuide.Step("Come back here, turn on Reliable alerts and pick ntfy. Yumeiro gets a private push address."),
            SetupGuide.Step("Tap Send test. A wake-up should arrive within seconds and the status shows the time."),
        ),
        tips = listOf(
            "How it works: Yumeiro asks the ntfy server to wake it every few hours, using ntfy's free scheduled delivery. Each wake-up runs the normal checks, and nothing else is sent.",
            "That's about 10 messages a day, far below ntfy.sh's free limit of 250. Nobody pays anything.",
            "Wake-up messages carry no personal data, and Yumeiro ignores their text. Your list and history never leave the phone.",
            "Running your own ntfy server? Set it as the default server in the ntfy app and Yumeiro uses it automatically.",
            "Other UnifiedPush apps can be picked too, but only ntfy servers support the scheduled wake-ups.",
        ),
    )

    const val TRAKT_REDIRECT = dev.asfakul.yumeiro.tracking.Trakt.REDIRECT

    val all: List<SetupGuide> = listOf(aniyomi, mihon, stremio, javascript, servers, cloudstream, anilist, myAnimeList, kitsu, trakt, youtube, youtubeCookie, unifiedPush)
}
