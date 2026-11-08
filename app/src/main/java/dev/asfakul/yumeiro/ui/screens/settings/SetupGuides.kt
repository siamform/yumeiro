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
            SetupGuide.Step("Name: Yumeiro. Redirect URI: paste this exact value. Leave the other fields empty and save.", copy = TRAKT_REDIRECT),
            SetupGuide.Step("Copy the Client ID and Client Secret, then tap Add Trakt API app here and paste both."),
            SetupGuide.Step("Tap Sign in. Yumeiro shows a short code and opens trakt.tv/activate. Type the code there and approve."),
        ),
        tips = listOf("Keep the client secret private. It's stored encrypted on the device and never leaves it except to talk to Trakt."),
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
        ),
        tips = listOf(
            "Third-party apps aren't official YouTube clients. Google could limit an account used this way, so a spare account is safest.",
            "Sign out at any time. That deletes the saved cookies and clears the Google sign-in from Yumeiro's web view.",
            "Sign-in data is never included in backups.",
        ),
    )

    const val TRAKT_REDIRECT = "urn:ietf:wg:oauth:2.0:oob"

    val all: List<SetupGuide> = listOf(aniyomi, mihon, stremio, javascript, servers, cloudstream, anilist, myAnimeList, kitsu, trakt, youtube)
}
