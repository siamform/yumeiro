/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import androidx.compose.material.icons.outlined.Lightbulb
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.theme.DarkSurfaceContainerHigh
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import dev.asfakul.yumeiro.ui.theme.yuRound

// region In-app messages (styled toasts)

/**
 * App-wide short messages. Shown by [AppMessageHost] as a styled pill above the bottom bar, with an
 * optional action (e.g. "Retry", "Open"). When no host is attached (early start-up, tests, a
 * separate activity) the message falls back to a system [Toast], so nothing is ever lost.
 * Safe to call from any thread.
 */
object AppMessages {
    enum class Kind { INFO, SUCCESS, ERROR, WARNING }
    data class Message(val id: Long, val text: String, val kind: Kind, val actionLabel: String?, val action: (() -> Unit)?, val durationMs: Long, val quiet: Boolean = false)

    private val seq = AtomicLong()
    private val _current = MutableStateFlow<Message?>(null)
    val current: StateFlow<Message?> = _current.asStateFlow()
    @Volatile internal var hosts = 0
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** G10 ER-29: a [quiet] message waiting for the one on screen to close (latest only). */
    @Volatile private var waiting: Message? = null

    /**
     * A message the app posts on its own (e.g. an extension's held promotional window): it never replaces
     * a message on screen, it waits until that one closes (like SnackbarHostState's queue).
     */
    fun showQuiet(context: android.content.Context?, text: String, actionLabel: String? = null, action: (() -> Unit)? = null) =
        post(context, text, Kind.INFO, actionLabel, action, quiet = true)

    fun show(
        context: android.content.Context?, text: String, kind: Kind = Kind.INFO,
        actionLabel: String? = null, action: (() -> Unit)? = null,
    ) = post(context, text, kind, actionLabel, action, quiet = false)

    private fun post(
        context: android.content.Context?, text: String, kind: Kind,
        actionLabel: String?, action: (() -> Unit)?, quiet: Boolean,
    ) {
        if (text.isBlank()) return
        // FINAL-42 (run 44669673771, tv-api34): the Tachiyomi "add repository" job posted its error from a
        // worker thread. Writing the state there resumed the host's collector on that worker, which ran a
        // Compose frame off the main thread (CalledFromWrongThreadException, slot table left broken, crash
        // when the screen was destroyed). The check-then-set of [waiting] and [_current] below also raced
        // between threads. All message state now changes on the main thread only.
        if (Looper.myLooper() !== Looper.getMainLooper()) {
            main.post { post(context, text, kind, actionLabel, action, quiet) }
            return
        }
        val duration = if (actionLabel != null || kind == Kind.ERROR) 4_500L else 2_600L
        if (hosts > 0 && quiet && _current.value != null) {
            waiting = Message(seq.incrementAndGet(), text.take(240), kind, actionLabel, action, duration, quiet = true)
        } else if (hosts > 0) {
            // FINAL-13 (run 44617327290, api36 P1): an app message ("Ready to watch: … installed") replaced
            // an extension's quiet card with its "Show" action, and that card never came back. A quiet
            // card with an action that is pushed off the screen waits and shows again after this one.
            val cur = _current.value
            if (!quiet && cur != null && cur.quiet && cur.actionLabel != null && waiting == null && cur.text != text.take(240)) waiting = cur
            // Same text again (e.g. repeated taps) just restarts the timer instead of stacking.
            _current.value = Message(seq.incrementAndGet(), text.take(240), kind, actionLabel, action, duration, quiet = quiet)
        } else if (context != null) {
            android.util.Log.i("YuMsg", "no message host; shown as a system toast: ${text.take(80)}")
            val app = context.applicationContext
            main.post { runCatching { Toast.makeText(app, text, if (duration > 3_000) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show() } }
        }
    }

    fun success(context: android.content.Context?, text: String) = show(context, text, Kind.SUCCESS)
    fun info(context: android.content.Context?, text: String) = show(context, text, Kind.INFO)
    fun error(context: android.content.Context?, text: String, actionLabel: String? = null, action: (() -> Unit)? = null) =
        show(context, text, Kind.ERROR, actionLabel, action)

    fun dismiss(id: Long) {
        if (Looper.myLooper() !== Looper.getMainLooper()) { main.post { dismiss(id) }; return }
        if (_current.value?.id != id) return
        val next = waiting; waiting = null
        _current.value = next?.copy(id = seq.incrementAndGet())
    }

    /** Drops a waiting quiet message with this text (its reason went away before it could show). */
    fun dropWaiting(text: String) {
        if (Looper.myLooper() !== Looper.getMainLooper()) { main.post { dropWaiting(text) }; return }
        if (waiting?.text == text.take(240)) waiting = null
    }
}

/** Renders [AppMessages] at the bottom of its parent box. Add once, near the app root. */
@Composable
fun BoxScope.AppMessageHost(bottomPadding: Dp = 96.dp) {
    DisposableEffect(Unit) { AppMessages.hosts++; onDispose { AppMessages.hosts-- } }
    val msg by AppMessages.current.collectAsState()
    // G10 ER-22: honour Android's "Time to take action" setting, like material3 SnackbarHost and
    // material-components Snackbar do; a fixed 4.5 s hid the Retry action from users who need longer.
    val a11y = androidx.compose.ui.platform.LocalAccessibilityManager.current
    LaunchedEffect(msg?.id) {
        msg?.let {
            val ms = a11y?.calculateRecommendedTimeoutMillis(
                it.durationMs, containsIcons = true, containsText = true, containsControls = it.action != null,
            ) ?: it.durationMs
            delay(ms); AppMessages.dismiss(it.id)
        }
    }
    // ST-21: never over the in-app floating video window (its close button took no tap under the pill).
    val floatEdges by FloatingPlayerArea.edges.collectAsState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val navBarDp = (WindowInsets.navigationBars.getBottom(density) / density.density).toInt()
    val bottom = FloatingPlayerArea.messageBottom(bottomPadding.value.toInt(), navBarDp, floatEdges).dp
    AnimatedVisibility(
        visible = msg != null,
        enter = fadeIn(tween(160)) + slideInVertically(androidx.compose.animation.core.spring(dampingRatio = 0.75f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)) { it / 2 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 },
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = bottom),
    ) {
        val m = msg ?: return@AnimatedVisibility
        val (icon, tint) = when (m.kind) {
            AppMessages.Kind.SUCCESS -> Icons.Default.CheckCircle to SpotifyGreen
            AppMessages.Kind.ERROR -> Icons.Default.ErrorOutline to MaterialTheme.colorScheme.error
            AppMessages.Kind.WARNING -> Icons.Default.WarningAmber to dev.asfakul.yumeiro.ui.theme.WarningTone
            AppMessages.Kind.INFO -> androidx.compose.material.icons.Icons.Outlined.Lightbulb to TextSecondary
        }
        // One opaque, theme-aware surface: the old pill was always dark (unreadable in light mode)
        // and drew its fill inside a transparent Surface, so the shadow and edges broke on glass.
        val p = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current
        val toastShape = yuRound(18)
        val baseFill = if (p.isDark) DarkSurfaceContainerHigh else Color(0xFFFFFBF4)
        val fill = androidx.compose.ui.graphics.lerp(baseFill, tint, if (p.isDark) 0.10f else 0.06f)
        Surface(
            shape = toastShape, color = fill, contentColor = p.textPrimary, shadowElevation = 10.dp, tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Brush.horizontalGradient(listOf(tint.copy(alpha = 0.50f), dev.asfakul.yumeiro.ui.theme.BrandGold.copy(alpha = 0.14f)))),
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().testTag("app_message")
                .semantics { liveRegion = LiveRegionMode.Polite }
                .clickable { AppMessages.dismiss(m.id) },
        ) {
            // G10 (device 43891331959, api33-lowram 393 dp): a long message with an action button beside it
            // ran past three lines and was cut ("…allow it in Android settings"). Long text with an action
            // now stacks the action under the text (Material snackbar rule for long actions).
            val stacked = m.actionLabel != null && m.action != null && m.text.length > STACK_ACTION_AFTER
            val Action: @Composable () -> Unit = {
                if (m.actionLabel != null && m.action != null) {
                    TextButton(onClick = { AppMessages.dismiss(m.id); runCatching { m.action.invoke() } }, modifier = Modifier.testTag("app_message_action")) {
                        Text(m.actionLabel, color = SpotifyGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
            Column(Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = if (stacked) 2.dp else 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(m.text, color = p.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = if (stacked) 5 else 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
                    if (!stacked && m.actionLabel != null && m.action != null) Action() else if (!stacked) Spacer(Modifier.width(8.dp))
                }
                if (stacked) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { Action() }
            }
        }
    }
}

/** Message length (characters) above which an action button goes under the text instead of beside it. */
internal const val STACK_ACTION_AFTER = 56

// endregion

// region Skeletons

/**
 * SKELETON-2 (owner list 32): the shared skeleton timing. A skeleton fades in only after
 * [APPEAR_DELAY_MS], so content that is already cached (and arrives within a frame or two) never
 * flashes a placeholder first; then one sweep per [SWEEP_MS]. With Remove animations / reduced
 * motion the placeholder is a still block (no sweep, no fade).
 */
object Skeletons {
    const val APPEAR_DELAY_MS = 120L
    const val FADE_MS = 180
    const val SWEEP_MS = 1100
    /** Width of the light band, px. */
    const val BAND_PX = 600f
}

/**
 * Animated shimmer used by skeleton placeholders while content loads. SKELETON-2: the band is placed
 * in window coordinates, so every block on a page shows one continuous sweep (before, each block ran
 * its own band); the phase is read only while drawing, so a skeleton page redraws but never
 * recomposes per frame (it recomposed 60 times a second before, costly on low-RAM phones).
 */
fun Modifier.shimmer(shape: androidx.compose.ui.graphics.Shape = yuRound(8)): Modifier = composed {
    val base = GlassFill
    val highlight = TextPrimary.copy(alpha = 0.10f)
    val reduced = reducedMotion()
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(reduced) {
        kotlinx.coroutines.delay(Skeletons.APPEAR_DELAY_MS)
        if (reduced) appear.snapTo(1f) else appear.animateTo(1f, tween(Skeletons.FADE_MS))
    }
    val phase = if (reduced) null else rememberInfiniteTransition(label = "shimmer")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(Skeletons.SWEEP_MS, easing = LinearEasing), RepeatMode.Restart), label = "shimmer_x")
    val left = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val screenW = with(androidx.compose.ui.platform.LocalDensity.current) {
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    this
        .onGloballyPositioned { left.floatValue = it.positionInWindow().x }
        .graphicsLayer { alpha = appear.value }
        .clip(shape)
        .drawBehind {
            drawRect(base)
            val t = phase?.value ?: return@drawBehind
            val x = t * (screenW + Skeletons.BAND_PX) - Skeletons.BAND_PX - left.floatValue
            drawRect(Brush.linearGradient(listOf(Color.Transparent, highlight, Color.Transparent), start = Offset(x, 0f), end = Offset(x + Skeletons.BAND_PX, Skeletons.BAND_PX / 2f)))
        }
}

@Composable
fun SkeletonBlock(width: Dp?, height: Dp, modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = yuRound(6)) {
    Box((if (width == null) modifier.fillMaxWidth() else modifier.width(width)).height(height).shimmer(shape))
}

/** SKELETON-2: a cover (poster / manga / novel) with two title lines, for grid cells while a feed loads. */
@Composable
fun CoverSkeleton(modifier: Modifier = Modifier, aspect: Float = 0.7f) {
    Column(modifier.fillMaxWidth().testTag("skeleton_cover")) {
        Box(Modifier.fillMaxWidth().aspectRatio(aspect).shimmer(yuRound(12)))
        Spacer(Modifier.height(6.dp))
        SkeletonBlock(null, 11.dp, Modifier.padding(end = 12.dp))
        Spacer(Modifier.height(4.dp))
        SkeletonBlock(56.dp, 9.dp)
    }
}

/** SKELETON-2: [rows] list-row placeholders (chat lists, chapter lists, search results). */
@Composable
fun SkeletonList(rows: Int = 6, modifier: Modifier = Modifier, trailing: Boolean = false) {
    Column(modifier.fillMaxWidth().testTag("skeleton_list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(rows) { SkeletonRow(trailing = trailing) }
    }
}

/** SKELETON-2: a titled row of covers (per-source search results, home shelves). */
@Composable
fun CoverRowSkeleton(count: Int = 4, coverWidth: Dp = 104.dp, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("skeleton_cover_row")) {
        SkeletonBlock(140.dp, 14.dp, Modifier.padding(start = 16.dp, bottom = 10.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(count) { CoverSkeleton(Modifier.width(coverWidth)) }
        }
    }
}

/** SKELETON-2: a title page (cover beside title lines, a description, then a chapter / episode list). */
@Composable
fun CoverDetailSkeleton(rows: Int = 6, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(16.dp).testTag("skeleton_detail"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row {
            Box(Modifier.width(110.dp).aspectRatio(0.7f).shimmer(yuRound(12)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBlock(null, 18.dp)
                SkeletonBlock(140.dp, 12.dp)
                SkeletonBlock(100.dp, 12.dp)
                Spacer(Modifier.height(6.dp))
                SkeletonBlock(120.dp, 36.dp, shape = RoundedCornerShape(50))
            }
        }
        SkeletonBlock(null, 12.dp)
        SkeletonBlock(null, 12.dp)
        SkeletonBlock(220.dp, 12.dp)
        Spacer(Modifier.height(4.dp))
        SkeletonList(rows)
    }
}

/** How many skeleton cells a grid shows: a first page when empty, one short row when adding more. Pure. */
fun skeletonCells(hasItems: Boolean): Int = if (hasItems) 3 else 9

/** Placeholder with the shape of a list row (icon + two lines). */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier, trailing: Boolean = false) {
    Surface(shape = dev.asfakul.yumeiro.ui.theme.Yume.shapeMd, color = GlassFill, modifier = modifier.fillMaxWidth().testTag("skeleton_row")) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SkeletonBlock(44.dp, 44.dp, shape = yuRound(12))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBlock(null, 14.dp, Modifier.padding(end = 48.dp))
                SkeletonBlock(120.dp, 10.dp)
            }
            if (trailing) { Spacer(Modifier.width(12.dp)); SkeletonBlock(72.dp, 32.dp, shape = RoundedCornerShape(50)) }
        }
    }
}

// endregion

// region Empty / error / not-found states

/**
 * Full-width state message for empty lists, errors and missing content. [primary] is the main
 * recovery action (Retry, Go back, Browse); [secondary] an optional alternative.
 */
@Composable
fun StateMessage(
    icon: ImageVector,
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    tint: Color = SpotifyGreen,
    primary: Pair<String, () -> Unit>? = null,
    secondary: Pair<String, () -> Unit>? = null,
    testTag: String = "state_message",
    /** Show "Try this" suggestions derived from the message (for error states). */
    suggestions: Boolean = icon == androidx.compose.material.icons.Icons.Default.ErrorOutline,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp).testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Moon motif: a soft halo ring with the state icon, then the gold stroke under the title.
        Box(
            Modifier.size(84.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(tint.copy(alpha = 0.20f), tint.copy(alpha = 0.04f))))
                .border(1.dp, rememberMoonHairline(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, color = TextPrimary, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.width(22.dp).height(dev.asfakul.yumeiro.ui.theme.Yume.moonStroke).clip(CircleShape).background(tint.copy(alpha = 0.8f)))
        message?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
        }
        if (suggestions) {
            val tips = androidx.compose.runtime.remember(title, message) { ErrorHelp.suggestionsFor("$title ${message.orEmpty()}") }
            Spacer(Modifier.height(14.dp))
            ErrorSuggestions(tips, Modifier.widthIn(max = 420.dp), onDark = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current.isDark)
        }
        if (primary != null) {
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = primary.second, shape = yuRound(24),
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
                modifier = Modifier.heightIn(min = 48.dp).testTag("${testTag}_primary"),
            ) { Text(primary.first, fontWeight = FontWeight.Bold) }
        }
        secondary?.let { (label, onClick) ->
            TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp).testTag("${testTag}_secondary")) { Text(label, color = TextSecondary, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// endregion

// region Sub-page header

/** Header for pages opened on top of a tab (playlist, market): back button, title, subtitle, actions. */
@Composable
fun SubPageHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    handleBack: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (handleBack) BackHandler(onBack = onBack)
    // Z-HEADER: a page with many header icons (Extensions has seven) used to squeeze the title to one letter.
    // The title keeps at least SubPageHeaderLayout.MIN_TITLE; when the icons don't fit beside it they move to a
    // second line under the title, aligned to the end, so every page keeps a readable name.
    androidx.compose.ui.layout.Layout(
        modifier = modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
        content = {
        YumeIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack, modifier = Modifier.testTag("subpage_back"))
        Column(Modifier.padding(start = 4.dp)) {
            // Editorial overline: the page's kanji in the accent, then the brand (設定 · YUMEIRO).
            // The kanji is decorative, so TalkBack skips it; the title below is the heading.
            val kana = if (kanaEnabled()) androidx.compose.runtime.remember(title) { Kana.forTitle(title) } else null
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (kana != null) {
                    Text(kana, style = dev.asfakul.yumeiro.ui.theme.OverlineStyle, color = dev.asfakul.yumeiro.ui.theme.SpotifyGreen, maxLines = 1,
                        modifier = Modifier.testTag("subpage_kana").clearAndSetSemantics { })
                    Text("  ·  ", style = dev.asfakul.yumeiro.ui.theme.OverlineStyle, color = TextTertiary, modifier = Modifier.clearAndSetSemantics { })
                }
                Text(stringResource(R.string.yumeiro_3), style = dev.asfakul.yumeiro.ui.theme.OverlineStyle, color = if (kana != null) TextTertiary else dev.asfakul.yumeiro.ui.theme.SpotifyGreen, maxLines = 1,
                    modifier = Modifier.testTag("yumeiro_mark").clearAndSetSemantics { })
            }
            Text(title, color = TextPrimary, style = dev.asfakul.yumeiro.ui.theme.PageTitleCompactStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp).testTag("subpage_title").semantics { heading() })
            subtitle?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
        }
        Row(Modifier.testTag("subpage_actions"), verticalAlignment = Alignment.CenterVertically) { actions() }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val width = constraints.maxWidth
        val back = measurables[0].measure(loose)
        val acts = measurables[2].measure(loose.copy(maxWidth = width))
        val minTitle = SubPageHeaderLayout.MIN_TITLE.roundToPx()
        val wrap = SubPageHeaderLayout.wraps(width, back.width, acts.width, minTitle)
        val titleMax = (if (wrap) width - back.width else width - back.width - acts.width).coerceAtLeast(0)
        val title = measurables[1].measure(loose.copy(maxWidth = titleMax))
        val line1 = maxOf(back.height, title.height, if (wrap) 0 else acts.height)
        val height = (if (wrap) line1 + acts.height else line1).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            back.placeRelative(0, (line1 - back.height) / 2)
            title.placeRelative(back.width, (line1 - title.height) / 2)
            if (wrap) acts.placeRelative(width - acts.width, line1)
            else acts.placeRelative(width - acts.width, (line1 - acts.height) / 2)
        }
    }
}

/** Pure rule for [SubPageHeader]: icons go to a second line when they would leave the title less than [MIN_TITLE]. */
object SubPageHeaderLayout {
    val MIN_TITLE = 136.dp
    fun wraps(width: Int, back: Int, actions: Int, minTitle: Int): Boolean = actions > 0 && width - back - actions < minTitle
}

// endregion


/** Yumeiro's own signature line shown on every page opened on top of a tab, so the app name is always visible. */
@Composable
fun YumeiroMark(modifier: Modifier = Modifier, light: Boolean = false) {
    Text(
        stringResource(R.string.app_name),
        color = if (light) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f) else dev.asfakul.yumeiro.ui.theme.SpotifyGreen,
        fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.6.sp, maxLines = 1,
        modifier = modifier.testTag("yumeiro_mark"),
    )
}

/** Slim "you're offline" pill shown at the top of the app while there is no internet. */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    // Sits at the bottom of sub-pages so it never covers a header, back button or title.
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(50),
            color = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current.surfaceContainerHigh.copy(alpha = 0.96f),
            border = androidx.compose.foundation.BorderStroke(1.dp, dev.asfakul.yumeiro.ui.theme.SpotifyGreen.copy(alpha = 0.25f)),
            modifier = Modifier.testTag("offline_banner"),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(androidx.compose.material.icons.Icons.Default.CloudOff, null, tint = dev.asfakul.yumeiro.ui.theme.SpotifyGreen, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.youre_offline_showing_saved_content), color = dev.asfakul.yumeiro.ui.theme.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
