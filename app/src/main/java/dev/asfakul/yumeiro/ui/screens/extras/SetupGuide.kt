/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.extras

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One thing the setup guide helps with. Status is read from the real feature, never faked. */
enum class SetupStep(val title: String, val detail: String, val action: String, val icon: ImageVector) {
    MEDIA("Play your music and videos", "Allow media access so Yumeiro can find songs and videos on this phone.", "Allow", Icons.Default.MusicNote),
    EXTENSIONS("Add Yumix sources", "Install extensions for anime, movies and shows. They appear in Videos → Yumix.", "Add sources", Icons.Default.Extension),
    MANGA("Add manga sources", "Install Mihon or JavaScript sources to read manga, manhwa and comics.", "Add manga", Icons.AutoMirrored.Filled.MenuBook),
    SEARCH("Search for a video", "One search box looks through every Yumix source at once.", "Search", Icons.Default.Search),
    DNS("Set up DNS", "Smart DNS opens sites your network blocks. Pick a provider in Network.", "Open Network", Icons.Default.Dns),
    NOTIFICATIONS("Turn on notifications", "Get new-episode alerts, download progress and reminders.", "Turn on", Icons.Default.Notifications),
    LIVE("Watch Live TV", "Add an IPTV / M3U playlist or pick one from the playlist market.", "Open Live TV", Icons.Default.LiveTv),
    EQUALIZER("Tune the sound", "Equalizer, bass boost and 3D presets for every song.", "Equalizer", Icons.Default.GraphicEq),
}

/** Live status of every [SetupStep]. */
@Immutable
data class SetupStatus(val done: Set<SetupStep>) {
    val count: Int get() = done.size
    val total: Int get() = SetupStep.entries.size
    val complete: Boolean get() = count >= total
}

/**
 * First-run guide state. Shown once after the splash (the user can skip), and can be reopened any
 * time from Home or Settings → Help & about. Stored in its own prefs file; nothing else changes.
 */
object SetupGuide {
    private const val PREFS = "setup_guide"
    private const val KEY_SEEN = "seen"
    private const val KEY_VISITED = "visited"
    private const val KEY_CARD_HIDDEN = "card_hidden"

    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()
    private val _visited = MutableStateFlow<Set<String>>(emptySet())
    private val _cardHidden = MutableStateFlow(false)
    val cardHidden: StateFlow<Boolean> = _cardHidden.asStateFlow()
    val visited: StateFlow<Set<String>> = _visited.asStateFlow()
    private var loaded = false

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(c)
        _visited.value = p.getStringSet(KEY_VISITED, emptySet()).orEmpty()
        _cardHidden.value = p.getBoolean(KEY_CARD_HIDDEN, false)
    }

    /** True on the first launch (and never under unit tests, where it would cover every screen). */
    fun shouldAutoShow(c: Context): Boolean =
        android.os.Build.FINGERPRINT != "robolectric" && !prefs(c).getBoolean(KEY_SEEN, false)

    fun show() { _open.value = true }

    fun close(c: Context) {
        _open.value = false
        prefs(c).edit().putBoolean(KEY_SEEN, true).apply()
    }

    fun markVisited(c: Context, step: SetupStep) {
        load(c)
        val next = _visited.value + step.name
        _visited.value = next
        prefs(c).edit().putStringSet(KEY_VISITED, next).apply()
    }

    fun hideCard(c: Context) {
        _cardHidden.value = true
        prefs(c).edit().putBoolean(KEY_CARD_HIDDEN, true).apply()
    }

    internal fun resetForTests() { _open.value = false; _visited.value = emptySet(); _cardHidden.value = false; loaded = false }
}

/** Reads every step's status from the feature that owns it. */
@Composable
fun rememberSetupStatus(): SetupStatus {
    val context = LocalContext.current
    remember { SetupGuide.load(context); true }
    val visited by SetupGuide.visited.collectAsState()
    val ext by dev.asfakul.yumeiro.online.ExtensionManager.state.collectAsState()
    val hub by dev.asfakul.yumeiro.sources.SourceHub.state.collectAsState()
    val dns by dev.asfakul.yumeiro.network.NetworkSettings.dns.collectAsState()
    // Permission state has no flow; re-read whenever the visited set changes or the guide opens.
    val open by SetupGuide.open.collectAsState()
    val media = remember(visited, open) { PermissionCenter.mediaAllowed(context) }
    val notifications = remember(visited, open) { PermissionCenter.notificationsAllowed(context) }
    return remember(visited, ext.installed, hub.generation, dns, media, notifications) {
        val done = buildSet {
            if (media) add(SetupStep.MEDIA)
            if (ext.installed.isNotEmpty()) add(SetupStep.EXTENSIONS)
            if (dev.asfakul.yumeiro.sources.SourceHub.mangaProviders().isNotEmpty() || SetupStep.MANGA.name in visited) add(SetupStep.MANGA)
            if (SetupStep.SEARCH.name in visited) add(SetupStep.SEARCH)
            if (dns.usesAlternative || SetupStep.DNS.name in visited) add(SetupStep.DNS)
            if (notifications) add(SetupStep.NOTIFICATIONS)
            if (SetupStep.LIVE.name in visited) add(SetupStep.LIVE)
            if (SetupStep.EQUALIZER.name in visited) add(SetupStep.EQUALIZER)
        }
        SetupStatus(done)
    }
}

/**
 * The full-screen first-run guide: Welcome → Where things are → Set up the essentials.
 * [onAction] performs a step (opens the real screen or asks for the real permission); the guide
 * closes itself first so the user lands on that screen.
 */
@Composable
fun SetupGuideOverlay(onAction: (SetupStep) -> Unit) {
    val context = LocalContext.current
    val p = LocalMtPalette.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    val status = rememberSetupStatus()
    BackHandler { if (page > 0) page-- else SetupGuide.close(context) }

    Column(
        Modifier.fillMaxSize().background(p.background).statusBarsPadding().navigationBarsPadding().testTag("setup_guide"),
    ) {
        // Top: progress dots + Skip (always available).
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                repeat(3) { i ->
                    Box(Modifier.height(4.dp).width(if (i == page) 28.dp else 12.dp).clip(CircleShape)
                        .background(if (i <= page) p.accent else p.textPrimary.copy(alpha = 0.12f)))
                }
            }
            Text(
                "Skip", color = p.textSecondary, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button) { SetupGuide.close(context) }
                    .testTag("setup_skip").padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (page) {
                0 -> WelcomePage()
                1 -> MapPage()
                else -> ChecklistPage(status) { step ->
                    SetupGuide.markVisited(context, step)
                    SetupGuide.close(context)
                    onAction(step)
                }
            }
        }
        // Bottom: one primary button.
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (page > 0) {
                Text(
                    "Back", color = p.textSecondary, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { page-- }.testTag("setup_back").padding(horizontal = 18.dp, vertical = 14.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(50)).background(p.accent)
                    .clickable(role = Role.Button) { if (page < 2) page++ else SetupGuide.close(context) }
                    .testTag("setup_next").padding(horizontal = 28.dp, vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (page) { 0 -> "Show me around"; 1 -> "Next: set up"; else -> "Start using Yumeiro" },
                    color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    val p = LocalMtPalette.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        item {
            // The real app mark on one soft gold glow — the only decoration on this page.
            Box(
                Modifier.fillMaxWidth().height(156.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            listOf(p.accent.copy(alpha = if (p.isDark) 0.22f else 0.16f), androidx.compose.ui.graphics.Color.Transparent),
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(dev.asfakul.yumeiro.R.drawable.ic_app_logo),
                    contentDescription = "Yumeiro",
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Welcome to Yumeiro", style = MaterialTheme.typography.displaySmall, color = p.textPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Music, videos, live TV and manga — from this phone and online — in one app.",
                style = MaterialTheme.typography.bodyLarge, color = p.textSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(26.dp))
            val tiles = listOf(
                Triple(Icons.Default.LibraryMusic, "Music", "This phone + YouTube Music"),
                Triple(Icons.Default.AutoAwesome, "Yumix", "Anime, movies & shows from extensions"),
                Triple(Icons.Default.LiveTv, "Live TV", "IPTV and M3U playlists"),
                Triple(Icons.AutoMirrored.Filled.MenuBook, "Manga", "Manga, manhwa & comics"),
            )
            tiles.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    row.forEachIndexed { k, (icon, title, text) ->
                        if (k > 0) Spacer(Modifier.width(10.dp))
                        Column(
                            Modifier.weight(1f).heightIn(min = 118.dp).clip(RoundedCornerShape(20.dp))
                                .background(p.surfaceContainerHigh.copy(alpha = if (p.isDark) 0.75f else 1f)).padding(16.dp),
                        ) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(p.accent), contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = p.onAccent, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = p.textPrimary)
                            Spacer(Modifier.height(2.dp))
                            Text(text, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Takes about a minute. Skip anytime — reopen it from Home or Settings.",
                style = MaterialTheme.typography.bodySmall, color = p.textTertiary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MapPage() {
    val p = LocalMtPalette.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
        item {
            Text("Where things are", style = MaterialTheme.typography.headlineLarge, color = p.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text("Four places at the bottom of the screen.", style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
            Spacer(Modifier.height(18.dp))
        }
        items(
            listOf(
                Triple(Icons.Default.Home, "Home", "Continue where you stopped, new episodes, moods, new arrivals and every feature in one grid."),
                Triple(Icons.Default.LibraryMusic, "Music", "Pick a mode: On device, Online (YouTube Music) or Mix of both."),
                Triple(Icons.Default.VideoLibrary, "Videos", "Pick a mode: Yumix (your extensions), YouTube, Device or Live TV."),
                Triple(Icons.Default.Bookmarks, "Library", "Playlists, favourites, history, downloads and your manga shelf."),
                Triple(Icons.Default.Settings, "Settings & inbox", "Top-right on every tab: alerts, sources, DNS, look & feel."),
            )
        ) { (icon, title, text) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(18.dp))
                    .background(p.surfaceContainerHigh.copy(alpha = if (p.isDark) 0.7f else 1f)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(p.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = p.accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = p.textPrimary, fontWeight = FontWeight.Bold)
                    Text(text, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun ChecklistPage(status: SetupStatus, onAction: (SetupStep) -> Unit) {
    val p = LocalMtPalette.current
    LazyColumn(Modifier.fillMaxSize().testTag("setup_checklist"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        item {
            Text("Set up the essentials", style = MaterialTheme.typography.headlineLarge, color = p.textPrimary, modifier = Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.height(6.dp))
            Text("${status.count} of ${status.total} ready. Tap any step to do it now.", style = MaterialTheme.typography.bodyMedium, color = p.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.height(14.dp))
        }
        items(SetupStep.entries, key = { it.name }) { step -> SetupStepRow(step, step in status.done) { onAction(step) } }
    }
}

@Composable
fun SetupStepRow(step: SetupStep, done: Boolean, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(shape)
            .background(p.surfaceContainerHigh.copy(alpha = if (p.isDark) 0.7f else 1f))
            .border(1.dp, if (done) p.accent.copy(alpha = 0.35f) else p.textPrimary.copy(alpha = 0.05f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("setup_step_${step.name.lowercase()}")
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (done) p.accent else p.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(if (done) Icons.Default.Check else step.icon, null, tint = if (done) p.onAccent else p.accent, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(step.title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(step.detail, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (done) "Done" else step.action,
            color = if (done) p.textTertiary else p.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1,
        )
    }
}
