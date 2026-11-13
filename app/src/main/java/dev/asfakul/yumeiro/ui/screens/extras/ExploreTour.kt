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
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.ui.composed
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
import kotlinx.coroutines.delay
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
    /** What the tutor asks the user to try, shown as a "Your turn" line. Null = read-only stop. */
    val tryIt: String? = null,
)

/**
 * The Explore tour: a short, tutor-style walk over the real interface. Nothing here is a mock —
 * each stop highlights a live control (scrolled into view first), says what it is and, where it
 * makes sense, asks the user to try it; doing so moves the lesson on. Skippable at every step;
 * the outcome is remembered by [SetupGuide] and the tour can be replayed from Settings → Setup guide.
 * Round 9: the copy now matches what is on screen (icon-only bottom bar, "See more", the source
 * pill), and the spotlight hugs the exact control instead of the whole header.
 */
object ExploreTour {
    val stops: List<TourStop> = listOf(
        TourStop("home", "topbar_title", "Hi, I'm Yume — your guide",
            "This is Home. The greeting follows the time of day, and everything waiting for you — new episodes, where you left off — gathers below it. Two minutes, and you'll know your way around.", TourPlace.HOME),
        TourStop("spaces", "home_spaces_grid", "Spaces: every feature, one tap away",
            "Each tile opens a part of Yumeiro — Yumix, YouTube, Manga, Live TV and more. The small line under a tile tells you its state, like \"Add sources\".", TourPlace.HOME),
        TourStop("spaces_more", "home_spaces_more", "More shortcuts live here",
            "See more unfolds the rest in place — theme, DNS, tracking, network… — and See less folds them away again.", TourPlace.HOME,
            tryIt = "Your turn: tap See more"),
        TourStop("tab_music", "nav_tab_music", "Music",
            "The note icon in the bar at the bottom opens Music: songs on this phone and YouTube Music, side by side.", TourPlace.HOME,
            advanceOnRoute = "music", tryIt = "Your turn: tap the note icon"),
        TourStop("music_source", "topbar_source_pill", "Choose where music comes from",
            "This pill beside the title switches between On device, Online and Mix. Every source keeps its own lists and search.", TourPlace.MUSIC,
            menuStop = true, tryIt = "Your turn: tap the pill, then pick one"),
        TourStop("tab_videos", "nav_tab_videos", "Videos and streaming",
            "The play icon opens Videos: extensions, YouTube, this phone's videos and Live TV in one place.", TourPlace.MUSIC,
            advanceOnRoute = "videos", tryIt = "Your turn: tap the play icon"),
        TourStop("video_source", "topbar_source_pill", "Pick what to watch",
            "Same pill, new choices: Yumix, YouTube, Device and Live TV. Each keeps its own screen and search.", TourPlace.VIDEOS_YUMIX,
            menuStop = true, tryIt = "Tap the pill to look — or Next to carry on"),
        TourStop("yumix", "yumix_sources", "Extensions and sources",
            "The shop opens the Extension Market — anime, movies, Asian and short dramas. The puzzle piece manages the ones you've installed. Yumix then mixes what they offer.", TourPlace.VIDEOS_YUMIX),
        TourStop("tab_library", "nav_tab_library", "Library",
            "Playlists, favourites, history, downloads and your manga shelf.", TourPlace.VIDEOS_YUMIX,
            advanceOnRoute = "library", tryIt = "Your turn: tap the library icon"),
        TourStop("actions", "topbar_actions", "Search, alerts and settings",
            "The magnifier searches the tab you're on. The bell collects new episodes, downloads and reminders. The gear opens Settings — sources, network, look and sound.", TourPlace.LIBRARY),
        TourStop("finish", null, "You're ready",
            "That's the whole map. Replay this tour or open the quick setup any time from Settings → Setup guide.", TourPlace.LIBRARY),
    )

    private val _step = MutableStateFlow<Int?>(null)
    val step: StateFlow<Int?> = _step.asStateFlow()
    /** Cheap, non-snapshot check so [tourTarget] costs nothing while the tour is closed. */
    @Volatile internal var active = false
        private set

    fun start() { active = true; _step.value = 0 }
    fun next() { val s = _step.value ?: return; _step.value = (s + 1).coerceAtMost(stops.lastIndex) }
    /** Moves on only if the tour is still on stop [id] (a tutor step the user just completed). */
    fun advanceIfAt(id: String) { val s = _step.value ?: return; if (stops.getOrNull(s)?.id == id) next() }
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
    internal var root by mutableStateOf(Size.Zero)
}

/**
 * Registers this element as a tour target. Free while the tour is closed; when its stop comes up
 * the element is scrolled into view first so the spotlight never points off screen.
 */
fun Modifier.tourTarget(key: String): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val step by ExploreTour.step.collectAsState()
    LaunchedEffect(step) {
        val s = step ?: return@LaunchedEffect
        if (ExploreTour.stops.getOrNull(s)?.target == key) { delay(260); runCatching { requester.bringIntoView() } }
    }
    this.bringIntoViewRequester(requester).onGloballyPositioned { coords ->
        if (!ExploreTour.active) return@onGloballyPositioned
        val r = if (coords.isAttached) coords.boundsInRoot() else Rect.Zero
        if (TourTargets.bounds[key] != r) TourTargets.bounds[key] = r
    }
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
    // Move on only when the user reaches the destination during this stop (not when Back lands
    // on a stop whose destination is already showing).
    var lastRoute by remember(i) { mutableStateOf(currentRoute) }
    LaunchedEffect(i, currentRoute) {
        if (currentRoute != lastRoute) {
            lastRoute = currentRoute
            if (stop.advanceOnRoute != null && stop.advanceOnRoute == currentRoute) ExploreTour.next()
        }
    }
    var sawMenu by remember(i) { mutableStateOf(false) }
    LaunchedEffect(sourceMenuOpen, i) {
        if (stop.menuStop && sourceMenuOpen) sawMenu = true
        if (stop.menuStop && !sourceMenuOpen && sawMenu) { sawMenu = false; ExploreTour.next() }
    }
    BackHandler { if (i > 0) ExploreTour.back() else ExploreTour.finish(context, completed = false) }
    if (sourceMenuOpen) return

    val pad = with(density) { 6.dp.toPx() }
    val raw = stop.target?.let { TourTargets.bounds[it] }?.takeIf { it.width > 1f && it.height > 1f }
    val screen = TourTargets.root
    // Clamp to the screen: the ring used to spill past the right edge on edge-hugging controls.
    val hole = raw?.let {
        val edge = with(density) { 4.dp.toPx() }
        val maxX = if (screen.width > 0f) screen.width - edge else Float.MAX_VALUE
        val maxY = if (screen.height > 0f) screen.height - edge else Float.MAX_VALUE
        Rect((it.left - pad).coerceAtLeast(edge), (it.top - pad).coerceAtLeast(edge), (it.right + pad).coerceAtMost(maxX), (it.bottom + pad).coerceAtMost(maxY))
    }?.takeIf { it.width > 8f && it.height > 8f }
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

    BoxWithConstraints(Modifier.fillMaxSize().testTag("explore_tour").onGloballyPositioned { TourTargets.root = Size(it.size.width.toFloat(), it.size.height.toFloat()) }) {
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
            pointerX = hole?.center?.x, pointerUp = below,
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
    pointerX: Float?, pointerUp: Boolean,
    onNext: () -> Unit, onBack: () -> Unit, onSkip: () -> Unit, onDone: () -> Unit, onOpenSetup: () -> Unit,
) {
    val p = LocalMtPalette.current
    val reduced = reducedMotion()
    val density = LocalDensity.current
    val last = index == total - 1
    var cardLeft by remember { mutableStateOf(0f) }
    var cardWidth by remember { mutableStateOf(0f) }
    val notch = 9.dp
    val shape = RoundedCornerShape(24.dp)
    Column(modifier.widthIn(max = 440.dp).fillMaxWidth()) {
        // A small notch points from the card to the spotlight, like a tutor's finger.
        @Composable fun Notch(up: Boolean) {
            if (pointerX == null || cardWidth <= 0f) return
            Canvas(Modifier.fillMaxWidth().height(notch)) {
                val x = (pointerX - cardLeft).coerceIn(28.dp.toPx(), cardWidth - 28.dp.toPx())
                val n = notch.toPx()
                val path = androidx.compose.ui.graphics.Path().apply {
                    if (up) { moveTo(x - n, n); lineTo(x, 0f); lineTo(x + n, n) } else { moveTo(x - n, 0f); lineTo(x, n); lineTo(x + n, 0f) }
                    close()
                }
                drawPath(path, p.surfaceContainerHigh)
            }
        }
        if (pointerUp) Notch(true)
        Column(
            Modifier.fillMaxWidth()
                .onGloballyPositioned { c -> cardLeft = c.boundsInRoot().left; cardWidth = c.size.width.toFloat() }
                .clip(shape).background(p.surfaceContainerHigh)
                .border(1.dp, p.accent.copy(alpha = if (p.isDark) 0.28f else 0.36f), shape)
                .padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 10.dp)
                .testTag("tour_step_${stop.id}")
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The guide: a small 夢 seal in the theme accent.
                Box(
                    Modifier.size(30.dp).clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(p.accent, p.accent.copy(alpha = 0.6f)))),
                    contentAlignment = Alignment.Center,
                ) { Text("夢", color = p.onAccent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Yume · guide", color = p.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(total) { k ->
                            val wdt by animateFloatAsState(if (k == index) 16f else 5f, if (reduced) snap() else tween(260), label = "tourDot")
                            Box(Modifier.height(4.dp).width(wdt.dp).clip(CircleShape)
                                .background(if (k <= index) p.accent else p.textPrimary.copy(alpha = 0.16f)))
                        }
                    }
                }
                Text("${index + 1}/$total", color = p.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
            }
            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                targetState = stop,
                transitionSpec = { if (reduced) fadeIn(snap()) togetherWith fadeOut(snap()) else fadeIn(tween(240, 60)) togetherWith fadeOut(tween(120)) },
                label = "tourText",
            ) { s ->
                Column(Modifier.padding(end = 8.dp)) {
                    Text(s.title, style = MaterialTheme.typography.titleLarge, color = p.textPrimary, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    Text(s.body, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
                    if (s.tryIt != null) {
                        Spacer(Modifier.height(10.dp))
                        val bob = if (reduced) 0f else {
                            val inf = rememberInfiniteTransition(label = "tourTry")
                            inf.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "tourTryV").value
                        }
                        Row(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(p.accent.copy(alpha = if (p.isDark) 0.14f else 0.12f))
                                .padding(horizontal = 10.dp, vertical = 7.dp).testTag("tour_try_${s.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Icon(
                                androidx.compose.material.icons.Icons.Default.TouchApp, null, tint = p.accent,
                                modifier = Modifier.size(18.dp).graphicsLayer { translationY = (if (pointerUp) -3f else 3f) * bob * density.density },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(s.tryIt, color = p.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
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
        if (!pointerUp) Notch(false)
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
