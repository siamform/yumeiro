/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.sources

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.sources.ecosystem.EcosystemRegistry
import dev.asfakul.yumeiro.sources.servers.HomeServers
import dev.asfakul.yumeiro.sources.stremio.StremioAddons
import dev.asfakul.yumeiro.sources.tachiyomi.TachiyomiExtensions
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.yuRound

/**
 * GROUP D: the Source page is a flow of pages instead of a pill tab row with a second tab row inside.
 *
 *  Sources (overview: one row per ecosystem, live counts)
 *   └ Aniyomi / Mihon / JavaScript  (Installed)  ─┬ Browse        (search, install)
 *                                                  └ Repositories  (+ add, Discover)
 *   └ Stremio (Your add-ons) ─ Get add-ons (Discover)
 *   └ Script repos, Home servers (one page each)
 *   └ CloudStream → the existing Extensions page (when the host passes it)
 *
 * Back walks up one page; only the overview leaves the Source page. Old `initialTab` shortcuts
 * (0 Aniyomi … 5 Script repos) open that page with the overview underneath.
 */
enum class SourcePage(val tag: String, val title: String) {
    HOME("home", "Sources"),
    ANIYOMI("aniyomi", "Aniyomi"),
    MIHON("mihon", "Mihon"),
    STREMIO("stremio", "Stremio"),
    JS("js", "JavaScript"),
    SERVERS("servers", "Home servers"),
    SCRIPTS("scripts", "Script repos"),
}

data class SourceRoute(val page: SourcePage, val part: HubPart? = null) {
    /** Test tag of the page body: `src_route_aniyomi`, `src_route_aniyomi_browse`… */
    val tag: String get() = "src_route_" + page.tag + (part?.let { "_" + it.tag } ?: "")
    val key: String get() = page.name + "/" + (part?.name ?: "")

    companion object {
        fun parse(key: String): SourceRoute? {
            val page = runCatching { SourcePage.valueOf(key.substringBefore('/')) }.getOrNull() ?: return null
            val part = key.substringAfter('/', "").takeIf { it.isNotEmpty() }?.let { p -> runCatching { HubPart.valueOf(p) }.getOrNull() }
            return SourceRoute(page, part)
        }
    }
}

object SourcesNav {
    /** Old tab order kept for shortcuts and deep links (`sources?tab=1` = Mihon). */
    private val legacy = listOf(SourcePage.ANIYOMI, SourcePage.MIHON, SourcePage.STREMIO, SourcePage.JS, SourcePage.SERVERS, SourcePage.SCRIPTS)
    val home = SourceRoute(SourcePage.HOME)

    fun start(tab: Int): List<SourceRoute> = legacy.getOrNull(tab)?.let { listOf(home, SourceRoute(it)) } ?: listOf(home)

    fun push(stack: List<SourceRoute>, to: SourceRoute): List<SourceRoute> = when {
        to.page == SourcePage.HOME -> listOf(home)
        stack.lastOrNull() == to -> stack
        // A page of another ecosystem replaces everything above the overview (no endless chains).
        to.part == null -> listOf(home, to)
        stack.lastOrNull()?.page == to.page && stack.last().part != null -> stack.dropLast(1) + to
        else -> stack + to
    }

    /** Null when the overview is on top: the caller leaves the Source page. */
    fun pop(stack: List<SourceRoute>): List<SourceRoute>? = if (stack.size <= 1) null else stack.dropLast(1)

    fun save(stack: List<SourceRoute>): List<String> = stack.map { it.key }
    fun restore(keys: List<String>): List<SourceRoute> = keys.mapNotNull { SourceRoute.parse(it) }.ifEmpty { listOf(home) }

    /** Page help ("?") text, also shown on TV where long paragraphs would push the list off screen. */
    const val HOME_HELP = "Yumeiro plays from many kinds of sources. Each row opens its own page:\n\n" +
        "• CloudStream: extensions for movies, series, anime and live TV.\n" +
        "• Aniyomi and Mihon: anime and manga extensions from repositories you add.\n" +
        "• Stremio: add-ons with catalogs and streams.\n" +
        "• JavaScript: Mangayomi-format sources in a sandbox.\n" +
        "• Script repos: Vega provider repositories.\n" +
        "• Home servers: Jellyfin, Emby, Plex, DLNA, WebDAV and SMB.\n\n" +
        "+ adds anything: paste a repository, add-on or server link and Yumeiro works out what it is."
}

/** One overview row (whole row is the button; 72 dp tall, focusable on TV). */
@Composable
internal fun SourcePageRow(
    tag: String, icon: ImageVector, title: String, about: String, status: String, attention: String? = null,
    focus: FocusRequester? = null, onOpen: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Surface(
        onClick = onOpen,
        shape = yuRound(18),
        color = GlassFill,
        border = if (focused) BorderStroke(2.dp, SpotifyGreen) else null,
        interactionSource = src,
        modifier = Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier).testTag(tag),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Surface(shape = yuRound(12), color = SpotifyGreen.copy(alpha = 0.14f), modifier = Modifier.size(40.dp)) {}
                Icon(icon, null, tint = SpotifyGreen, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(about, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text(status, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (attention != null) {
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = yuRound(8), color = dev.asfakul.yumeiro.ui.theme.WarnTone.copy(alpha = 0.16f)) {
                            Text(attention, color = dev.asfakul.yumeiro.ui.theme.WarnTone, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextTertiary)
        }
    }
}

/** A row that opens a sub-page (Browse, Repositories, Get add-ons) from an ecosystem page. */
@Composable
internal fun SubPageLink(tag: String, title: String, hint: String, onOpen: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Surface(onClick = onOpen, shape = yuRound(16), color = GlassFill, border = if (focused) BorderStroke(2.dp, SpotifyGreen) else null,
        interactionSource = src, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(hint, color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextTertiary)
        }
    }
}

private fun count(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

/** The overview page body. [lastOpened] gets focus back when the user returns (remote / keyboard). */
@Composable
internal fun SourcesHome(lastOpened: SourcePage?, onOpenCloudStream: (() -> Unit)?, onOpen: (SourcePage) -> Unit) {
    val tachi by TachiyomiExtensions.state.collectAsState()
    val js by dev.asfakul.yumeiro.sources.js.JsSources.state.collectAsState()
    val addons by StremioAddons.addons.collectAsState()
    val servers by HomeServers.servers.collectAsState()
    val scripts by EcosystemRegistry.state.collectAsState()
    val scriptProblems by EcosystemRegistry.problems.collectAsState()
    val focus = remember { SourcePage.entries.associateWith { FocusRequester() } }
    LaunchedEffect(lastOpened) { if (lastOpened != null) runCatching { focus.getValue(lastOpened).requestFocus() } }

    fun tachiStatus(kind: TachiyomiExtensions.Kind): Pair<String, String?> {
        val inst = tachi.installed.filter { it.kind == kind }
        val repos = tachi.repos.count { it.kind == kind }
        if (inst.isEmpty() && repos == 0) return "Not set up" to null
        val untrusted = inst.count { !it.trusted || it.error != null }
        return "${count(inst.size, "extension")} · ${count(repos, "repository", "repositories")}" to (if (untrusted > 0) "$untrusted to check" else null)
    }

    LazyColumn(Modifier.fillMaxSize().testTag("src_home"), state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("src_home"),
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item("h_ext") { SectionTitle("Extensions") }
        if (onOpenCloudStream != null) item("cloudstream") {
            SourcePageRow("src_page_cloudstream", Icons.Filled.Movie, "CloudStream", "Movies, series, anime and live TV", "Extensions and repositories", onOpen = onOpenCloudStream)
        }
        item("aniyomi") {
            val (s, a) = tachiStatus(TachiyomiExtensions.Kind.ANIME)
            SourcePageRow("src_page_aniyomi", Icons.Filled.Extension, "Aniyomi", "Anime extensions", s, a, focus.getValue(SourcePage.ANIYOMI)) { onOpen(SourcePage.ANIYOMI) }
        }
        item("mihon") {
            val (s, a) = tachiStatus(TachiyomiExtensions.Kind.MANGA)
            SourcePageRow("src_page_mihon", Icons.Filled.MenuBook, "Mihon", "Manga extensions", s, a, focus.getValue(SourcePage.MIHON)) { onOpen(SourcePage.MIHON) }
        }
        item("js") {
            val s = if (js.installed.isEmpty() && js.repos.isEmpty()) "Not set up" else "${count(js.installed.size, "source")} · ${count(js.repos.size, "repository", "repositories")}"
            SourcePageRow("src_page_js", Icons.Filled.Code, "JavaScript", "Mangayomi-format sources", s, null, focus.getValue(SourcePage.JS)) { onOpen(SourcePage.JS) }
        }
        item("h_add") { SectionTitle("Add-ons and scripts") }
        item("stremio") {
            val s = if (addons.isEmpty()) "Not set up" else count(addons.size, "add-on")
            SourcePageRow("src_page_stremio", Icons.Filled.Hub, "Stremio", "Catalogs and streams", s, null, focus.getValue(SourcePage.STREMIO)) { onOpen(SourcePage.STREMIO) }
        }
        item("scripts") {
            val s = if (scripts.repos.isEmpty()) "Not set up" else "${count(scripts.installed.size, "source")} · ${count(scripts.repos.size, "repository", "repositories")}"
            val bad = scripts.installed.count { scriptProblems[it.key] != null }
            SourcePageRow("src_page_scripts", Icons.Filled.Terminal, "Script repos", "Vega provider repositories", s, if (bad > 0) "$bad not loaded" else null, focus.getValue(SourcePage.SCRIPTS)) { onOpen(SourcePage.SCRIPTS) }
        }
        item("h_own") { SectionTitle("Your media") }
        item("servers") {
            val s = if (servers.isEmpty()) "None added" else count(servers.size, "server")
            SourcePageRow("src_page_servers", Icons.Filled.Dns, "Home servers", "Jellyfin, Emby, Plex, DLNA, WebDAV, SMB", s, null, focus.getValue(SourcePage.SERVERS)) { onOpen(SourcePage.SERVERS) }
        }
    }
}
