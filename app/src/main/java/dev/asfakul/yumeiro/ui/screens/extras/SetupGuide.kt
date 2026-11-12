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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.animation.togetherWith
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
    private const val KEY_ONBOARDING = "onboarding"

    /** What the user chose on the welcome screen. Remembered so Yumeiro never asks twice. */
    enum class Onboarding { NONE, EXPLORED, SKIPPED }
    private val _onboarding = MutableStateFlow(Onboarding.NONE)
    val onboarding: StateFlow<Onboarding> = _onboarding.asStateFlow()

    fun setOnboarding(c: Context, value: Onboarding) {
        _onboarding.value = value
        prefs(c).edit().putString(KEY_ONBOARDING, value.name).putBoolean(KEY_SEEN, true).apply()
    }

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
        _onboarding.value = runCatching { Onboarding.valueOf(p.getString(KEY_ONBOARDING, null) ?: "NONE") }.getOrDefault(Onboarding.NONE)
    }

    /** True on the first launch (and never under unit tests, where it would cover every screen). */
    fun shouldAutoShow(c: Context): Boolean =
        android.os.Build.FINGERPRINT != "robolectric" && !prefs(c).getBoolean(KEY_SEEN, false)

    /** Opens the welcome screen, or straight to the quick-setup checklist. */
    fun show(checklist: Boolean = false) { startOnChecklist = checklist; _open.value = true }
    internal var startOnChecklist = false
        private set

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

    internal fun resetForTests() { _open.value = false; _visited.value = emptySet(); _cardHidden.value = false; _onboarding.value = Onboarding.NONE; loaded = false }
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
 * The first-run welcome. Two clear choices — Explore Yumeiro (a short tour over the real
 * interface, see [ExploreTour]) or Skip for now — plus the quick-setup checklist for people who
 * want to get the essentials done. Nothing is forced; the choice is remembered.
 * [onAction] performs a checklist step (opens the real screen or asks for the real permission).
 */
@Composable
fun SetupGuideOverlay(onAction: (SetupStep) -> Unit, onExplore: () -> Unit = { ExploreTour.start() }) {
    val context = LocalContext.current
    val p = LocalMtPalette.current
    var page by rememberSaveable { mutableIntStateOf(if (SetupGuide.startOnChecklist) 1 else 0) }
    val status = rememberSetupStatus()
    val skip = {
        if (SetupGuide.onboarding.value == SetupGuide.Onboarding.NONE) SetupGuide.setOnboarding(context, SetupGuide.Onboarding.SKIPPED)
        SetupGuide.close(context)
    }
    BackHandler { if (page > 0) page = 0 else skip() }

    Column(
        Modifier.fillMaxSize().background(p.background).statusBarsPadding().navigationBarsPadding().testTag("setup_guide"),
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = page, modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                if (dev.asfakul.yumeiro.ui.components.isReducedMotion(context)) androidx.compose.animation.fadeIn(androidx.compose.animation.core.snap()) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.snap())
                else (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260, 80)) + androidx.compose.animation.slideInVertically { it / 12 }) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140))
            },
            label = "setupPage",
        ) { pg ->
            if (pg == 0) WelcomePage(
                onExplore = { SetupGuide.close(context); onExplore() },
                onSkip = skip,
                onChecklist = { page = 1 },
            ) else Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    ChecklistPage(status) { step ->
                        SetupGuide.markVisited(context, step)
                        SetupGuide.close(context)
                        onAction(step)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Back", color = p.textSecondary, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { page = 0 }.testTag("setup_back").padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(50)).background(p.accent)
                            .clickable(role = Role.Button) { skip() }
                            .testTag("setup_done").padding(horizontal = 28.dp, vertical = 15.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Start using Yumeiro", color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) }
                }
            }
        }
    }
}

@Composable
private fun WelcomePage(onExplore: () -> Unit, onSkip: () -> Unit, onChecklist: () -> Unit) {
    val p = LocalMtPalette.current
    val reduced = dev.asfakul.yumeiro.ui.components.reducedMotion()
    // The mark breathes in once; nothing loops.
    var shown by remember { androidx.compose.runtime.mutableStateOf(reduced) }
    androidx.compose.runtime.LaunchedEffect(Unit) { shown = true }
    val markScale by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else 0.86f, if (reduced) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(520, easing = dev.asfakul.yumeiro.ui.components.YuMotion.EmphasizedDecelerate), label = "mark")
    val textAlpha by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else 0f, if (reduced) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(420, 160), label = "welcomeText")
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            item {
                Box(
                    Modifier.fillMaxWidth().height(168.dp)
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
                        modifier = Modifier.size(96.dp).scale(markScale).clip(RoundedCornerShape(28.dp)),
                    )
                }
                Column(Modifier.fillMaxWidth().alpha(textAlpha)) {
                    Text(
                        "◆ 夢色 YUMEIRO", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "New to Yumeiro?", style = MaterialTheme.typography.headlineMedium, color = p.textPrimary, fontWeight = FontWeight.ExtraBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Explore and discover what you can do here.",
                        style = MaterialTheme.typography.bodyLarge, color = p.textSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(22.dp))
                    Text(
                        "Already familiar with Yumeiro?", style = MaterialTheme.typography.titleMedium, color = p.textPrimary, fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "You can skip and start using the app.",
                        style = MaterialTheme.typography.bodyMedium, color = p.textSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(50)).background(p.accent)
                    .clickable(role = Role.Button, onClick = onExplore).testTag("setup_explore").padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Explore Yumeiro", color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(50))
                    .border(1.dp, p.textPrimary.copy(alpha = 0.16f), RoundedCornerShape(50))
                    .clickable(role = Role.Button, onClick = onSkip).testTag("setup_skip").padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Skip for now", color = p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            Spacer(Modifier.height(6.dp))
            Text(
                "Set up the essentials instead", color = p.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onChecklist)
                    .testTag("setup_open_checklist").padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun ChecklistPage(status: SetupStatus, onAction: (SetupStep) -> Unit) {
    val p = LocalMtPalette.current
    LazyColumn(Modifier.fillMaxSize().testTag("setup_checklist"), state = dev.asfakul.yumeiro.ui.components.ScrollMemory.list("setup_checklist"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
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
            Text(step.detail, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (done) "Done" else step.action,
            color = if (done) p.textTertiary else p.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1,
        )
    }
}
