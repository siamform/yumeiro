/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
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
    val websites = SetupGuide(
        id = "websites",
        title = tr(R.string.watch_any_website_in_yumeiros_player, "Watch any website in Yumeiro's player"),
        intro = tr(R.string.save_a_video_site_once_it, "Save a video site once. It opens in Yumeiro's page browser with pop-ups and ad overlays blocked; when its player starts a video, Yumeiro picks up the stream and plays it in its own player (speed, subtitles, background play, downloads)."),
        steps = listOf(
            SetupGuide.Step(tr(R.string.tap_add_website_and_type_the, "Tap Add website and type the address, for example archive.org.")),
            SetupGuide.Step(tr(R.string.open_the_site_find_a_video, "Open the site, find a video and press play on the page.")),
            SetupGuide.Step(tr(R.string.detected_streams_appear_at_the_bottom, "Detected streams appear at the bottom. Tap one to play it in Yumeiro.")),
        ),
        tips = listOf(
            tr(R.string.sign_in_on_the_page_if, "Sign in on the page if the site asks; cookies stay inside Yumeiro's browser."),
            tr(R.string.drm_protected_services_netflix_prime_video, "DRM-protected services (Netflix, Prime Video and others) only play in their own apps. Yumeiro never bypasses protection."),
        ),
    )

    /** TG-PAGE-2: the Telegram page's own help (it showed the Websites guide). */
    val telegram = SetupGuide(
        id = "telegram",
        title = tr(R.string.watch_telegram_channels_in_yumeiro, "Watch Telegram channels in Yumeiro"),
        intro = tr(R.string.public_channels_play_without_an_account, "Public channels play without an account: Yumeiro reads the channel's public preview at t.me and plays its video posts in its own player."),
        steps = listOf(
            SetupGuide.Step(tr(R.string.type_a_public_channel_as_name, "Type a public channel as @name or paste its t.me link, then tap Add.")),
            SetupGuide.Step(tr(R.string.open_the_channel_and_tap_a, "Open the channel and tap a video post to play it.")),
            SetupGuide.Step(tr(R.string.for_your_chats_groups_and_private, "For your chats, groups and private channels you have joined, tap Sign in. Telegram sends you the login code.")),
        ),
        tips = listOf(
            tr(R.string.yumeiro_never_sees_your_password_and, "Yumeiro never sees your password and never joins a chat for you; private content needs your own membership."),
            tr(R.string.a_channel_without_a_public_preview, "A channel without a public preview can only be watched from an account that is allowed to see it."),
        ),
    )


    // region sources

    val aniyomi = SetupGuide(
        id = "aniyomi",
        title = tr(R.string.how_to_get_aniyomi_extensions, "How to get Aniyomi extensions"),
        intro = tr(R.string.anime_extensions_come_from_repositories_run, "Anime extensions come from repositories run by the community. Yumeiro doesn't ship any, but Discover lists the ones found on GitHub so you can pick one you trust."),
        steps = listOf(
            SetupGuide.Step(tr(R.string.open_discover_below_repositories_are_ranked, "Open Discover below. Repositories are ranked by GitHub stars, recent updates and size; sort them or search by name, language (en, ja…) or a source you know.")),
            SetupGuide.Step(tr(R.string.tap_add_on_one_you_trust, "Tap Add on one you trust. That's it; no links to copy.")),
            SetupGuide.Step("Have a link instead? Tap Add next to Your repositories and paste it: index.min.json, a GitHub repo page, a raw.githubusercontent.com link, or an aniyomi:// \"Add repo\" button (long-press it and copy)."),
            SetupGuide.Step(tr(R.string.under_available_tap_the_download_icon, "Under Available, tap the download icon next to the extensions you want. Filter by name or language code (en, ja, es…).")),
            SetupGuide.Step(tr(R.string.tap_trust_on_each_new_extension, "Tap Trust on each new extension. Yumeiro checks its signing key, and the code doesn't run until you trust it.")),
            SetupGuide.Step(tr(R.string.open_videos_online_the_new_sources, "Open Videos → Online. The new sources appear there and in search.")),
        ),
        tips = listOf(
            tr(R.string.repositories_made_for_aniyomi_work_as, "Repositories made for Aniyomi work as-is. Mihon / Tachiyomi repositories are for manga, so add those in the Mihon tab."),
            tr(R.string.if_an_extension_stops_working_check, "If an extension stops working, check Source status. Sites change often, and the repo owner usually ships a fix as an update."),
            tr(R.string.extensions_run_code_on_your_phone, "Extensions run code on your phone. Only add repositories from people you trust."),
        ),
    )

    val mihon = SetupGuide(
        id = "mihon",
        title = tr(R.string.how_to_get_mihon_manga_extensions, "How to get Mihon manga extensions"),
        intro = tr(R.string.manga_extensions_use_the_mihon_tachiyomi, "Manga extensions use the Mihon / Tachiyomi format and come from community repositories. Yumeiro doesn't ship any; Discover lists the ones found on GitHub."),
        steps = listOf(
            SetupGuide.Step(tr(R.string.open_discover_below_sort_or_search, "Open Discover below, sort or search, and tap Add on a repository you trust.")),
            SetupGuide.Step("Or tap Add next to Your repositories and paste a link: repo.json, index.min.json, index.pb, a GitHub repo page, a raw link, or a tachiyomi:// / mihon:// \"Add repo\" button (long-press it and copy)."),
            SetupGuide.Step(tr(R.string.install_extensions_from_available_extensions_sig, "Install extensions from Available. Extensions signed with the key the repository publishes (Keiyoushi does) are trusted right away; others need a Trust tap.")),
            SetupGuide.Step(tr(R.string.open_the_manga_section_to_browse, "Open the Manga section to browse and read.")),
        ),
        tips = listOf(
            tr(R.string.anime_repositories_belong_in_the_aniyomi, "Anime repositories belong in the Aniyomi tab."),
            tr(R.string.only_add_repositories_from_people_you, "Only add repositories from people you trust."),
        ),
    )

    val stremio = SetupGuide(
        id = "stremio",
        title = tr(R.string.how_to_find_stremio_add_ons, "How to find Stremio add-ons"),
        intro = "A Stremio add-on is a small web service. Each one has a manifest link ending in manifest.json, and that's all Yumeiro needs.",
        steps = listOf(
            SetupGuide.Step(tr(R.string.start_with_cinemeta_stremios_official_catalog, "Start with Cinemeta, Stremio's official catalog of movies and series: tap Add on its card below.")),
            SetupGuide.Step(tr(R.string.open_discover_stremios_featured_add_ons, "Open Discover: Stremio's featured add-ons and the community catalog, each one checked. Filter by Movie, Series, Anime or TV and tap Add.")),
            SetupGuide.Step(tr(R.string.want_one_that_isnt_listed_find, "Want one that isn't listed? Find it in the community catalog or on its own website."), link = "https://stremio-addons.net", linkLabel = tr(R.string.add_on_catalog, "Add-on catalog")),
            SetupGuide.Step("On the add-on's page, long-press Install (or Copy link) and copy it. https:// and stremio:// links both work."),
            SetupGuide.Step(tr(R.string.some_add_ons_need_setup_first, "Some add-ons need setup first. Open their Configure page in your browser, choose options, then copy the final install link.")),
            SetupGuide.Step(tr(R.string.tap_add_here_and_paste_the, "Tap Add here and paste the link. Catalogs show in Videos → Online, and streams show when you open a title.")),
        ),
        tips = listOf(
            tr(R.string.torrent_only_add_ons_are_skipped, "Torrent-only add-ons are skipped. Yumeiro plays HTTP streams and subtitles."),
            tr(R.string.if_an_add_on_asks_for, "If an add-on asks for a debrid or account key, that key lives inside its link. Treat the link like a password."),
        ),
    )

    val javascript = SetupGuide(
        id = "javascript",
        title = tr(R.string.how_to_add_javascript_sources, "How to add JavaScript sources"),
        intro = tr(R.string.javascript_sources_use_the_mangayomi_format, "JavaScript sources use the Mangayomi format and run in a locked-down engine with no access to your files or other apps."),
        steps = listOf(
            SetupGuide.Step(tr(R.string.open_discover_below_and_tap_add, "Open Discover below and tap Add on a Mangayomi repository you trust. Manga and anime indexes are listed separately.")),
            SetupGuide.Step("Or tap Add next to Your repositories and paste a link (usually ending in index.json). A mangayomi:// \"Add repo\" link works too."),
            SetupGuide.Step(tr(R.string.install_sources_from_available_manga_sources, "Install sources from Available. Manga sources show in Manga, and anime or video sources in Videos → Online.")),
        ),
        tips = listOf("Only the JavaScript sources in a repository are used; Dart ones are skipped."),
    )

    val servers = SetupGuide(
        id = "servers",
        title = tr(R.string.how_to_connect_your_home_server, "How to connect your home server"),
        intro = tr(R.string.play_your_own_movies_shows_and, "Play your own movies, shows and music straight from your server. Nothing goes through a Yumeiro server."),
        steps = listOf(
            SetupGuide.Step("Find your server's address in its dashboard. Jellyfin and Emby usually use port 8096, e.g. http://192.168.1.10:8096. Plex uses 32400."),
            SetupGuide.Step(tr(R.string.connect_your_phone_to_the_same, "Connect your phone to the same Wi-Fi, or use the server's remote address if you've set one up.")),
            SetupGuide.Step(tr(R.string.tap_the_button_for_your_server, "Tap the button for your server type and enter the address. Jellyfin, Emby, WebDAV and SMB ask for your user name and password. Plex opens a Plex sign-in instead.")),
            SetupGuide.Step(tr(R.string.for_dlna_upnp_just_tap_dlna, "For DLNA / UPnP, just tap DLNA. Yumeiro finds servers on your Wi-Fi by itself.")),
            SetupGuide.Step("WebDAV takes a full link (https://host/path). SMB takes smb://host/share, plus your Windows or NAS account."),
        ),
        tips = listOf(
            tr(R.string.cant_connect_open_the_address_in, "Can't connect? Open the address in your phone's browser first. If that fails too, it's a network or firewall issue on the server."),
            tr(R.string.passwords_and_tokens_are_encrypted_on, "Passwords and tokens are encrypted on the device with the Android Keystore."),
        ),
    )

    val cloudstream = SetupGuide(
        id = "cloudstream",
        title = tr(R.string.how_to_get_cloudstream_extensions, "How to get CloudStream extensions"),
        intro = tr(R.string.cloudstream_extensions_cs3_come_from_community, "CloudStream extensions (.cs3) come from community repositories. Yumeiro doesn't ship or recommend any."),
        steps = listOf(
            SetupGuide.Step("Find a repository link on the extension project's GitHub page or website. It usually ends in repo.json or plugins.json, or is a short code."),
            SetupGuide.Step(tr(R.string.tap_add_repository_and_paste_it, "Tap ⋮ → Add repository and paste it, or browse the Extension market.")),
            SetupGuide.Step(tr(R.string.install_the_extensions_you_want_their, "Install the extensions you want. Their titles show in Videos → Online.")),
            SetupGuide.Step(tr(R.string.got_a_cs3_file_use_install, "Got a .cs3 file? Use ⋮ → Install from file instead.")),
        ),
        tips = listOf("Only add repositories from people you trust. Extensions run code on your phone."),
    )

    // endregion

    // region tracking

    val anilist = SetupGuide(
        id = "anilist",
        title = tr(R.string.set_up_anilist, "Set up AniList"),
        intro = tr(R.string.anilist_is_free_since_yumeiro_has, "AniList is free. Since Yumeiro has no server, you create your own free client ID once, and your sign-in goes straight to AniList."),
        steps = listOf(
            SetupGuide.Step(tr(R.string.create_a_free_anilist_account_or, "Create a free AniList account, or skip this if you already have one."), link = "https://anilist.co/signup", linkLabel = tr(R.string.sign_up, "Sign up")),
            SetupGuide.Step(tr(R.string.open_anilists_developer_settings_and_tap, "Open AniList's developer settings and tap Create New Client."), link = "https://anilist.co/settings/developer", linkLabel = tr(R.string.developer_settings, "Developer settings")),
            SetupGuide.Step("Name: Yumeiro (or anything). Redirect URL: paste this exact value.", copy = AniList.REDIRECT),
            SetupGuide.Step(tr(R.string.save_then_copy_the_client_id, "Save, then copy the Client ID (a number).")),
            SetupGuide.Step(tr(R.string.back_here_tap_set_up_on, "Back here, tap Set up on AniList and paste the ID.")),
            SetupGuide.Step(tr(R.string.tap_sign_in_then_approve_youll, "Tap Sign in, then Approve. You'll come back to Yumeiro signed in.")),
        ),
        tips = listOf("Episodes are marked as watched after about 85% of an episode."),
    )

    val myAnimeList = SetupGuide(
        id = "mal",
        title = "Set up MyAnimeList",
        intro = "MyAnimeList is free. You create your own free API client once, so there's no shared key to break.",
        steps = listOf(
            SetupGuide.Step("Create a free MyAnimeList account, or skip this if you already have one.", link = "https://myanimelist.net/register.php", linkLabel = tr(R.string.sign_up, "Sign up")),
            SetupGuide.Step("Open MyAnimeList's API settings and tap Create ID.", link = "https://myanimelist.net/apiconfig", linkLabel = tr(R.string.api_settings, "API settings")),
            SetupGuide.Step(tr(R.string.app_type_choose_android_it_cant, "App Type: choose Android. It can't be changed later, and it means no client secret is needed.")),
            SetupGuide.Step(tr(R.string.app_redirect_url_paste_this_exact, "App Redirect URL: paste this exact value."), copy = MyAnimeList.REDIRECT),
            SetupGuide.Step(tr(R.string.homepage_url_any_link_like_the, "Homepage URL: any link, like the Yumeiro project page. Fill in the name and description, accept the terms and submit."), copy = DeveloperInfo.SOURCE),
            SetupGuide.Step(tr(R.string.open_the_new_app_edit_and, "Open the new app (Edit) and copy its Client ID.")),
            SetupGuide.Step("Back here, tap Set up on MyAnimeList, paste the ID, then tap Sign in and Allow."),
        ),
        tips = listOf("If sign-in says the redirect doesn't match, check the redirect URL in your MyAnimeList app for typos."),
    )

    val kitsu = SetupGuide(
        id = "kitsu",
        title = tr(R.string.set_up_kitsu, "Set up Kitsu"),
        intro = tr(R.string.kitsu_needs_no_developer_setup_just, "Kitsu needs no developer setup, just an account."),
        steps = listOf(
            SetupGuide.Step(tr(R.string.create_a_free_kitsu_account_with, "Create a free Kitsu account with your e-mail."), link = "https://kitsu.app", linkLabel = tr(R.string.open_kitsu, "Open Kitsu")),
            SetupGuide.Step(tr(R.string.if_you_joined_with_a_social, "If you joined with a social login, set a password in Kitsu's account settings first.")),
            SetupGuide.Step(tr(R.string.back_here_tap_sign_in_on, "Back here, tap Sign in on Kitsu and enter the same e-mail and password.")),
        ),
        tips = listOf("Your password goes to Kitsu once. Only the sign-in token is kept, encrypted."),
    )

    val trakt = SetupGuide(
        id = "trakt",
        title = tr(R.string.set_up_trakt, "Set up Trakt"),
        intro = tr(R.string.trakt_tracks_the_movies_and_shows, "Trakt tracks the movies and shows you finish, from any source. Like AniList, it uses your own free API app."),
        steps = listOf(
            SetupGuide.Step(tr(R.string.create_a_free_trakt_account_or, "Create a free Trakt account, or skip this if you already have one."), link = "https://trakt.tv/auth/join", linkLabel = tr(R.string.sign_up, "Sign up")),
            SetupGuide.Step(tr(R.string.open_trakts_api_apps_and_tap, "Open Trakt's API apps and tap New Application."), link = "https://trakt.tv/oauth/applications/new", linkLabel = tr(R.string.new_application, "New application")),
            SetupGuide.Step("Name: Yumeiro. Redirect URI: paste this exact value (Trakt may warn about it; save anyway). Leave allowed origins and the other fields empty.", copy = TRAKT_REDIRECT),
            SetupGuide.Step(tr(R.string.copy_the_client_id_then_tap, "Copy the Client ID, then tap Add Trakt API app here and paste it. New Trakt apps have no client secret; paste one only if your (older) app shows it.")),
            SetupGuide.Step("Tap Sign in. Yumeiro shows a short code and opens trakt.tv/activate. Type the code there and approve."),
        ),
        tips = listOf("Signing in shows a short code; nothing secret is needed. If your app has a client secret it's stored encrypted on the device and only sent to Trakt.", tr(R.string.yumeiro_refreshes_the_sign_in_before, "Yumeiro refreshes the sign-in before it expires (Trakt sign-ins last 7 days), so tracking keeps working.")),
    )

    val simkl = SetupGuide(
        id = "simkl",
        title = "Simkl: anime, shows and movies in one list",
        intro = tr(R.string.simkl_tracks_anime_tv_and_movies, "Simkl tracks anime, TV and movies together and syncs with many other apps. Yumeiro has a built-in Simkl app, so you only sign in."),
        steps = listOf(
            SetupGuide.Step(tr(R.string.create_a_free_simkl_account_or, "Create a free Simkl account, or skip this if you already have one."), link = "https://simkl.com/", linkLabel = tr(R.string.open_simkl, "Open Simkl")),
            SetupGuide.Step("Tap Sign in. Yumeiro shows a short code and opens simkl.com/pin. Type the code there and approve."),
            SetupGuide.Step("Done. Finished episodes and movies are marked watched on Simkl, matched by MyAnimeList, AniList, IMDb or TMDB id."),
        ),
        tips = listOf(
            "Using your own Simkl app is optional: create one at simkl.com/settings/developer (redirect URI urn:ietf:wg:oauth:2.0:oob), then tap Change key and paste its Client ID.",
            tr(R.string.simkl_sign_ins_last_7_days, "Simkl sign-ins last 7 days and Yumeiro renews them on its own."),
        ),
    )

    val lastfm = SetupGuide(
        id = "lastfm",
        title = tr(R.string.last_fm_scrobbling, "Last.fm scrobbling"),
        intro = tr(R.string.scrobbling_sends_the_songs_you_play, "Scrobbling sends the songs you play to your Last.fm profile, for stats, charts and recommendations. Local files and streamed songs both count."),
        steps = listOf(
            SetupGuide.Step(tr(R.string.create_a_free_last_fm_account, "Create a free Last.fm account, or skip this if you already have one."), link = "https://www.last.fm/join", linkLabel = tr(R.string.sign_up, "Sign up")),
            SetupGuide.Step(tr(R.string.tap_sign_in_last_fm_opens, "Tap Sign in. Last.fm opens in your browser; tap Yes, allow access. Yumeiro never sees your password.")),
            SetupGuide.Step(tr(R.string.come_back_to_yumeiro_it_notices, "Come back to Yumeiro. It notices the approval within a few seconds.")),
        ),
        tips = listOf(
            tr(R.string.a_song_is_scrobbled_after_youve, "A song is scrobbled after you've listened to half of it or 4 minutes, whichever comes first (Last.fm's rule). Songs under 30 seconds don't count."),
            tr(R.string.offline_scrobbles_wait_on_the_device, "Offline? Scrobbles wait on the device (up to 500, for 14 days) and are sent when you're back online."),
            tr(R.string.incognito_mode_pauses_scrobbling, "Incognito mode pauses scrobbling."),
        ),
    )

    // endregion

    val youtube = SetupGuide(
        id = "youtube",
        title = tr(R.string.youtube_sign_in_what_it_does, "YouTube sign-in: what it does"),
        intro = tr(R.string.yumeiro_plays_youtube_without_an_account, "Yumeiro plays YouTube without an account. Sign in only if YouTube keeps saying \"Sign in to confirm you're not a bot\", or to play age-restricted videos."),
        steps = listOf(
            SetupGuide.Step(tr(R.string.tap_sign_in_with_google_googles, "Tap Sign in with Google. Google's own sign-in page opens inside Yumeiro.")),
            SetupGuide.Step(tr(R.string.sign_in_as_usual_including_2, "Sign in as usual, including 2-step verification. Yumeiro never sees your password.")),
            SetupGuide.Step(tr(R.string.when_youtube_loads_yumeiro_saves_the, "When YouTube loads, Yumeiro saves the sign-in cookies, encrypted with the Android Keystore.")),
            SetupGuide.Step(tr(R.string.by_default_the_account_is_used, "By default the account is used only when YouTube blocks a video. Everything else stays anonymous.")),
            SetupGuide.Step(tr(R.string.if_google_says_this_browser_or, "If Google says \"This browser or app may not be secure\", use Paste cookie instead. The next guide shows how.")),
        ),
        tips = listOf(
            tr(R.string.third_party_apps_arent_official_youtube, "Third-party apps aren't official YouTube clients. Google could limit an account used this way, so a spare account is safest."),
            tr(R.string.sign_out_at_any_time_that, "Sign out at any time. That deletes the saved cookies and clears the Google sign-in from Yumeiro's web view."),
            tr(R.string.sign_in_data_is_never_included, "Sign-in data is never included in backups."),
        ),
    )

    /** Paste-cookie fallback (Google may refuse the in-app sign-in page). */
    val youtubeCookie = SetupGuide(
        id = "youtube_cookie",
        title = tr(R.string.how_to_get_your_youtube_cookie, "How to get your YouTube cookie"),
        intro = tr(R.string.use_this_when_google_wont_let, "Use this when Google won't let you sign in inside Yumeiro. You sign in on a normal browser and copy the cookie it got. It takes about two minutes on a computer."),
        steps = listOf(
            SetupGuide.Step(tr(R.string.on_a_computer_open_a_private, "On a computer, open a private window (Chrome: Ctrl+Shift+N, Firefox: Ctrl+Shift+P). A private window matters: YouTube changes the cookie of open tabs, and a private session is never reopened.")),
            SetupGuide.Step("Sign in to YouTube in that window. A spare Google account is safest.", link = "https://www.youtube.com/", linkLabel = "youtube.com"),
            SetupGuide.Step("In the same tab, open youtube.com/robots.txt. This page is quiet, so the cookie won't change while you copy it.", link = "https://www.youtube.com/robots.txt", linkLabel = "robots.txt", copy = "https://www.youtube.com/robots.txt"),
            SetupGuide.Step(tr(R.string.press_f12_to_open_developer_tools, "Press F12 to open Developer tools, choose the Network tab and reload the page (F5).")),
            SetupGuide.Step("Click the robots.txt request. Under Request Headers find \"cookie\", right-click its value and copy it. It is long and starts with something like VISITOR_INFO1_LIVE= or SID=."),
            SetupGuide.Step(tr(R.string.easier_option_install_the_open_source, "Easier option: install the open-source extension Get cookies.txt LOCALLY (Chrome or Firefox), allow it in private windows, and export youtube.com as cookies.txt. Yumeiro reads that file's text too."), link = "https://github.com/kairi003/Get-cookies.txt-LOCALLY", linkLabel = tr(R.string.get_cookies_txt_locally, "Get cookies.txt LOCALLY")),
            SetupGuide.Step(tr(R.string.close_the_private_window_without_signing, "Close the private window without signing out. Signing out would cancel the cookie.")),
            SetupGuide.Step(tr(R.string.send_the_text_to_your_phone, "Send the text to your phone in a way only you can see (for example a note to yourself), paste it here and tap Check and save. Then delete the note.")),
        ),
        tips = listOf(
            tr(R.string.also_accepted_copy_as_curl_from, "Also accepted: Copy as cURL from Developer tools, JSON exports from cookie extensions, and an ArchiveTune or Metrolist token (the ***INNERTUBE COOKIE*** line)."),
            tr(R.string.no_computer_firefox_for_android_supports, "No computer? Firefox for Android supports the open-source Cookie-Editor add-on. Sign in to youtube.com in a private tab, open Cookie-Editor, choose Export, then Header string or Netscape, and paste the result here."),
            tr(R.string.this_cookie_works_like_your_password, "This cookie works like your password. Never post it in chats, issues or screenshots. Anyone who has it can use your account."),
            tr(R.string.yumeiro_checks_the_cookie_only_with, "Yumeiro checks the cookie only with youtube.com, then stores it encrypted with the Android Keystore. It is never included in backups."),
            tr(R.string.if_youtube_signs_you_out_later, "If YouTube signs you out later, the cookie stops working. Copy a fresh one the same way."),
        ),
    )

    /** UnifiedPush via ntfy: free, open-source push so alerts arrive even when the phone is idle. */
    val unifiedPush = SetupGuide(
        id = "unifiedpush",
        title = tr(R.string.reliable_alerts_with_ntfy_free, "Reliable alerts with ntfy (free)"),
        intro = tr(R.string.android_may_hold_back_background_checks, "Android may hold back background checks for hours on some phones. UnifiedPush lets a free push app wake Yumeiro on time, so new-episode and news alerts aren't late. No Google services, no account and no fees."),
        steps = listOf(
            SetupGuide.Step(tr(R.string.install_ntfy_its_free_and_open, "Install ntfy. It's free and open source. F-Droid has the build without Google services."), link = "https://f-droid.org/packages/io.heckel.ntfy/", linkLabel = "ntfy on F-Droid"),
            SetupGuide.Step(tr(R.string.or_get_ntfy_from_google_play, "Or get ntfy from Google Play."), link = "https://play.google.com/store/apps/details?id=io.heckel.ntfy", linkLabel = "ntfy on Google Play"),
            SetupGuide.Step(tr(R.string.open_ntfy_once_and_allow_its, "Open ntfy once and allow its notification. ntfy keeps one light connection open for every app that uses it.")),
            SetupGuide.Step(tr(R.string.in_android_settings_apps_ntfy_battery, "In Android settings → Apps → ntfy → Battery, choose Unrestricted. Do the same for Yumeiro if your phone is on dontkillmyapp.com."), link = "https://dontkillmyapp.com/", linkLabel = "dontkillmyapp.com"),
            SetupGuide.Step(tr(R.string.come_back_here_turn_on_reliable, "Come back here, turn on Reliable alerts and pick ntfy. Yumeiro gets a private push address.")),
            SetupGuide.Step(tr(R.string.tap_send_test_a_wake_up, "Tap Send test. A wake-up should arrive within seconds and the status shows the time.")),
        ),
        tips = listOf(
            tr(R.string.how_it_works_yumeiro_asks_the, "How it works: Yumeiro asks the ntfy server to wake it every few hours, using ntfy's free scheduled delivery. Each wake-up runs the normal checks, and nothing else is sent."),
            tr(R.string.thats_about_10_messages_a_day, "That's about 10 messages a day, far below ntfy.sh's free limit of 250. Nobody pays anything."),
            tr(R.string.wake_up_messages_carry_no_personal, "Wake-up messages carry no personal data, and Yumeiro ignores their text. Your list and history never leave the phone."),
            tr(R.string.running_your_own_ntfy_server_set, "Running your own ntfy server? Set it as the default server in the ntfy app and Yumeiro uses it automatically."),
            tr(R.string.other_unifiedpush_apps_can_be_picked, "Other UnifiedPush apps can be picked too, but only ntfy servers support the scheduled wake-ups."),
        ),
    )

    const val TRAKT_REDIRECT = dev.asfakul.yumeiro.tracking.Trakt.REDIRECT

    val all: List<SetupGuide> = listOf(aniyomi, mihon, stremio, javascript, servers, cloudstream, anilist, myAnimeList, kitsu, trakt, youtube, youtubeCookie, unifiedPush)
}
