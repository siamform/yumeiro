/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.extras

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.components.reducedMotion
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/** Where a tour stop needs the app to be before it can point at something. */
enum class TourPlace { HOME, MUSIC, VIDEOS_YUMIX, LIBRARY }

/**
 * One stop of the Explore tour. [target] is a key registered with [tourTarget] on a real control;
 * [advanceOnRoute] lets the user do the step themselves (tapping the highlighted tab moves on).
 * [menuStop] marks the source-selector stops: the user may open the real menu, and closing it moves on.
 */
data class TourStop(
    val id: String,
    val target: String?,
    val title: String,
    val body: String,
    val place: TourPlace,
    val advanceOnRoute: String? = null,
    val menuStop: Boolean = false,
)

/**
 * The Explore tour: a short walk over the real interface. Nothing here is a mock — each stop
 * highlights a live control, and the user can tap it. Skippable at every step; the outcome is
 * remembered by [SetupGuide] and the tour can be replayed from Settings → Setup guide.
 */
object ExploreTour {
    val stops: List<TourStop> = listOf(
        TourStop("home", "topbar_title", "Home is your starting point",
            "It greets you, shows what's waiting — new episodes, where you left off — and keeps every feature in spaces you can fold.", TourPlace.HOME),
        TourStop("tab_music", "nav_tab_music", "Music",
            "Your songs and YouTube Music live here. Tap Music to open it.", TourPlace.HOME, advanceOnRoute = "music"),
        TourStop("music_source", "topbar_title", "Choose where music comes from",
            "Tap the title to switch between On device, Online and Mix. Try it now.", TourPlace.MUSIC, menuStop = true),
        TourStop("tab_videos", "nav_tab_videos", "Videos and streaming",
            "Tap Videos. Extensions, YouTube, this phone's videos and Live TV are all in one place.", TourPlace.MUSIC, advanceOnRoute = "videos"),
        TourStop("video_source", "topbar_title", "Pick what to watch",
            "The title switches between Yumix, YouTube, Device and Live TV. Each keeps its own screen and search.", TourPlace.VIDEOS_YUMIX, menuStop = true),
        TourStop("yumix", "yumix_sources", "Extensions and sources",
            "Get sources from the market, or manage the ones you have. Yumix shows what they offer.", TourPlace.VIDEOS_YUMIX),
        TourStop("tab_library", "nav_tab_library", "Library",
            "Playlists, favourites, history, downloads and your manga shelf. Tap Library.", TourPlace.VIDEOS_YUMIX, advanceOnRoute = "library"),
        TourStop("actions", "topbar_actions", "Alerts and settings",
            "The bell has new episodes, downloads and reminders. Settings holds sources, network, look and sound.", TourPlace.LIBRARY),
        TourStop("finish", null, "You're ready",
            "Replay this tour or open the quick setup any time from Settings → Setup guide.", TourPlace.LIBRARY),
    )

    private val _step = MutableStateFlow<Int?>(null)
    val step: StateFlow<Int?> = _step.asStateFlow()
    /** Cheap, non-snapshot check so [tourTarget] costs nothing while the tour is closed. */
    @Volatile internal var active = false
        private set

    fun start() { active = true; _step.value = 0 }
    fun next() { val s = _step.value ?: return; _step.value = (s + 1).coerceAtMost(stops.lastIndex) }
    fun back() { val s = _step.value ?: return; if (s > 0) _step.value = s - 1 }

    /** Ends the tour. [completed] = reached the last stop; anything else counts as skipped. */
    fun finish(c: Context, completed: Boolean) {
        _step.value = null
        active = false
        TourTargets.bounds.clear()
        SetupGuide.setOnboarding(c, if (completed) SetupGuide.Onboarding.EXPLORED else SetupGuide.Onboarding.SKIPPED)
    }

    internal fun resetForTests() { _step.value = null; active = false; TourTargets.bounds.clear() }
}

/** Live positions of the controls the tour can point at (root coordinates). */
object TourTargets {
    internal val bounds = mutableStateMapOf<String, Rect>()
}

/** Registers this element as a tour target. Free while the tour is closed. */
fun Modifier.tourTarget(key: String): Modifier = this.onGloballyPositioned { coords ->
    if (!ExploreTour.active) return@onGloballyPositioned
    val r = if (coords.isAttached) coords.boundsInRoot() else Rect.Zero
    if (TourTargets.bounds[key] != r) TourTargets.bounds[key] = r
}

/**
 * The tour overlay. A dimmed scrim with a cut-out over the live control; the cut-out area is not
 * covered by anything, so taps reach the real control underneath. [onPrepare] moves the app to the
 * place a stop needs (it uses the app's own navigation). While a source menu is open the overlay
 * steps aside so the user can try it; closing the menu moves the tour on.
 */
@Composable
fun ExploreTourOverlay(
    currentRoute: String,
    sourceMenuOpen: Boolean,
    onPrepare: (TourPlace) -> Unit,
    onOpenSetup: () -> Unit,
) {
    val index by ExploreTour.step.collectAsState()
    val i = index ?: return
    val stop = ExploreTour.stops[i]
    val context = androidx.compose.ui.platform.LocalContext.current
    val p = LocalMtPalette.current
    val reduced = reducedMotion()
    val density = LocalDensity.current

    LaunchedEffect(i) { onPrepare(stop.place) }
    LaunchedEffect(i, currentRoute) { if (stop.advanceOnRoute != null && stop.advanceOnRoute == currentRoute) ExploreTour.next() }
    var sawMenu by remember(i) { mutableStateOf(false) }
    LaunchedEffect(sourceMenuOpen, i) {
        if (stop.menuStop && sourceMenuOpen) sawMenu = true
        if (stop.menuStop && !sourceMenuOpen && sawMenu) { sawMenu = false; ExploreTour.next() }
    }
    BackHandler { if (i > 0) ExploreTour.back() else ExploreTour.finish(context, completed = false) }
    if (sourceMenuOpen) return

    val pad = with(density) { 6.dp.toPx() }
    val raw = stop.target?.let { TourTargets.bounds[it] }?.takeIf { it.width > 1f && it.height > 1f }
    val hole = raw?.let { Rect(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad) }
    val spec = if (reduced) snap<Float>() else tween(320)
    val l by animateFloatAsState(hole?.left ?: 0f, spec, label = "tourL")
    val t by animateFloatAsState(hole?.top ?: 0f, spec, label = "tourT")
    val r by animateFloatAsState(hole?.right ?: 0f, spec, label = "tourR")
    val b by animateFloatAsState(hole?.bottom ?: 0f, spec, label = "tourB")
    val pulse = if (reduced) 0f else {
        val inf = rememberInfiniteTransition(label = "tourPulse")
        inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "tourPulseV").value
    }
    val scrim = Color.Black.copy(alpha = if (p.isDark) 0.66f else 0.52f)
    val corner = with(density) { 18.dp.toPx() }

    BoxWithConstraints(Modifier.fillMaxSize().testTag("explore_tour")) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(scrim)
            if (hole != null) {
                drawRoundRect(Color.Black, Offset(l, t), Size(r - l, b - t), CornerRadius(corner), blendMode = BlendMode.Clear)
                drawRoundRect(p.accent, Offset(l, t), Size(r - l, b - t), CornerRadius(corner), style = Stroke(2.dp.toPx()))
                if (pulse > 0f) {
                    val g = 10.dp.toPx() * pulse
                    drawRoundRect(
                        p.accent.copy(alpha = 0.55f * (1f - pulse)), Offset(l - g, t - g), Size(r - l + 2 * g, b - t + 2 * g),
                        CornerRadius(corner + g), style = Stroke(3.dp.toPx()),
                    )
                }
            }
        }
        // Blockers around the hole: the rest of the app is paused, the highlighted control is live.
        val block = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        if (hole == null) Box(Modifier.fillMaxSize().then(block))
        else {
            fun px(v: Float) = with(density) { v.coerceAtLeast(0f).toDp() }
            Box(Modifier.offset { IntOffset(0, 0) }.size(px(w), px(hole.top)).then(block))
            Box(Modifier.offset { IntOffset(0, hole.bottom.roundToInt()) }.size(px(w), px(h - hole.bottom)).then(block))
            Box(Modifier.offset { IntOffset(0, hole.top.roundToInt()) }.size(px(hole.left), px(hole.height)).then(block))
            Box(Modifier.offset { IntOffset(hole.right.roundToInt(), hole.top.roundToInt()) }.size(px(w - hole.right), px(hole.height)).then(block))
        }

        // Tooltip: below the hole when it sits in the top half, above it otherwise; centred when none.
        val gap = with(density) { 14.dp.toPx() }
        val below = hole == null || hole.center.y < h / 2
        val cardMod = when {
            hole == null -> Modifier.align(Alignment.Center)
            below -> Modifier.align(Alignment.TopCenter).padding(top = with(density) { (hole.bottom + gap).toDp() })
            else -> Modifier.align(Alignment.BottomCenter).padding(bottom = with(density) { (h - hole.top + gap).toDp() })
        }
        TourCard(
            stop = stop, index = i, total = ExploreTour.stops.size,
            modifier = cardMod.padding(horizontal = 20.dp),
            onNext = { ExploreTour.next() },
            onBack = { ExploreTour.back() },
            onSkip = { ExploreTour.finish(context, completed = false) },
            onDone = { ExploreTour.finish(context, completed = true) },
            onOpenSetup = { ExploreTour.finish(context, completed = true); onOpenSetup() },
        )
    }
}

@Composable
private fun TourCard(
    stop: TourStop, index: Int, total: Int, modifier: Modifier,
    onNext: () -> Unit, onBack: () -> Unit, onSkip: () -> Unit, onDone: () -> Unit, onOpenSetup: () -> Unit,
) {
    val p = LocalMtPalette.current
    val reduced = reducedMotion()
    val last = index == total - 1
    Column(
        modifier.widthIn(max = 440.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(p.surfaceContainerHigh).padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 10.dp)
            .testTag("tour_step_${stop.id}")
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                repeat(total) { k ->
                    Box(Modifier.height(4.dp).width(if (k == index) 18.dp else 6.dp).clip(CircleShape)
                        .background(if (k <= index) p.accent else p.textPrimary.copy(alpha = 0.14f)))
                }
            }
            Text("${index + 1} of $total", color = p.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
        }
        Spacer(Modifier.height(12.dp))
        AnimatedContent(
            targetState = stop,
            transitionSpec = { if (reduced) fadeIn(snap()) togetherWith fadeOut(snap()) else fadeIn(tween(220, 60)) togetherWith fadeOut(tween(120)) },
            label = "tourText",
        ) { s ->
            Column(Modifier.padding(end = 8.dp)) {
                Text(s.title, style = MaterialTheme.typography.titleLarge, color = p.textPrimary, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Text(s.body, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!last) TourText("Skip tour", p.textTertiary, "tour_skip", onSkip)
            else TourText("Open quick setup", p.accent, "tour_open_setup", onOpenSetup)
            Spacer(Modifier.weight(1f))
            if (index > 0 && !last) TourText("Back", p.textSecondary, "tour_back", onBack)
            Box(
                Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(50)).background(p.accent)
                    .clickable(role = Role.Button, onClick = if (last) onDone else onNext)
                    .testTag(if (last) "tour_done" else "tour_next").padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (last) "Done" else "Next", color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
        }
    }
}

@Composable
private fun TourText(label: String, color: Color, tag: String, onClick: () -> Unit) {
    Text(
        label, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp,
        modifier = Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onClick)
            .testTag(tag).padding(horizontal = 12.dp, vertical = 12.dp),
    )
}
