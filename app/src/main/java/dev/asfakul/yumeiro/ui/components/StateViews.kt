/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

// region In-app messages (styled toasts)

/**
 * App-wide short messages. Shown by [AppMessageHost] as a styled pill above the bottom bar, with an
 * optional action (e.g. "Retry", "Open"). When no host is attached (early start-up, tests, a
 * separate activity) the message falls back to a system [Toast], so nothing is ever lost.
 * Safe to call from any thread.
 */
object AppMessages {
    enum class Kind { INFO, SUCCESS, ERROR, WARNING }
    data class Message(val id: Long, val text: String, val kind: Kind, val actionLabel: String?, val action: (() -> Unit)?, val durationMs: Long)

    private val seq = AtomicLong()
    private val _current = MutableStateFlow<Message?>(null)
    val current: StateFlow<Message?> = _current.asStateFlow()
    @Volatile internal var hosts = 0
    private val main by lazy { Handler(Looper.getMainLooper()) }

    fun show(
        context: android.content.Context?, text: String, kind: Kind = Kind.INFO,
        actionLabel: String? = null, action: (() -> Unit)? = null,
    ) {
        if (text.isBlank()) return
        val duration = if (actionLabel != null || kind == Kind.ERROR) 4_500L else 2_600L
        if (hosts > 0) {
            // Same text again (e.g. repeated taps) just restarts the timer instead of stacking.
            _current.value = Message(seq.incrementAndGet(), text.take(240), kind, actionLabel, action, duration)
        } else if (context != null) {
            val app = context.applicationContext
            main.post { runCatching { Toast.makeText(app, text, if (duration > 3_000) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show() } }
        }
    }

    fun success(context: android.content.Context?, text: String) = show(context, text, Kind.SUCCESS)
    fun info(context: android.content.Context?, text: String) = show(context, text, Kind.INFO)
    fun error(context: android.content.Context?, text: String, actionLabel: String? = null, action: (() -> Unit)? = null) =
        show(context, text, Kind.ERROR, actionLabel, action)

    fun dismiss(id: Long) { if (_current.value?.id == id) _current.value = null }
}

/** Renders [AppMessages] at the bottom of its parent box. Add once, near the app root. */
@Composable
fun BoxScope.AppMessageHost(bottomPadding: Dp = 96.dp) {
    DisposableEffect(Unit) { AppMessages.hosts++; onDispose { AppMessages.hosts-- } }
    val msg by AppMessages.current.collectAsState()
    LaunchedEffect(msg?.id) { msg?.let { delay(it.durationMs); AppMessages.dismiss(it.id) } }
    AnimatedVisibility(
        visible = msg != null,
        enter = fadeIn(tween(160)) + slideInVertically(androidx.compose.animation.core.spring(dampingRatio = 0.75f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)) { it / 2 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 },
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = bottomPadding),
    ) {
        val m = msg ?: return@AnimatedVisibility
        val (icon, tint) = when (m.kind) {
            AppMessages.Kind.SUCCESS -> Icons.Default.CheckCircle to SpotifyGreen
            AppMessages.Kind.ERROR -> Icons.Default.ErrorOutline to MaterialTheme.colorScheme.error
            AppMessages.Kind.WARNING -> Icons.Default.WarningAmber to Color(0xFFFFB74D)
            AppMessages.Kind.INFO -> Icons.Default.Info to TextSecondary
        }
        // One opaque, theme-aware surface: the old pill was always dark (unreadable in light mode)
        // and drew its fill inside a transparent Surface, so the shadow and edges broke on glass.
        val p = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current
        val toastShape = RoundedCornerShape(18.dp)
        val baseFill = if (p.isDark) DarkSurfaceContainerHigh else Color(0xFFFFFBF4)
        val fill = androidx.compose.ui.graphics.lerp(baseFill, tint, if (p.isDark) 0.10f else 0.06f)
        Surface(
            shape = toastShape, color = fill, contentColor = p.textPrimary, shadowElevation = 10.dp, tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Brush.horizontalGradient(listOf(tint.copy(alpha = 0.50f), dev.asfakul.yumeiro.ui.theme.BrandGold.copy(alpha = 0.14f)))),
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().testTag("app_message")
                .semantics { liveRegion = LiveRegionMode.Polite }
                .clickable { AppMessages.dismiss(m.id) },
        ) {
            Row(Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(m.text, color = p.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
                if (m.actionLabel != null && m.action != null) {
                    TextButton(onClick = { AppMessages.dismiss(m.id); runCatching { m.action.invoke() } }, modifier = Modifier.testTag("app_message_action")) {
                        Text(m.actionLabel, color = SpotifyGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                } else Spacer(Modifier.width(8.dp))
            }
        }
    }
}

// endregion

// region Skeletons

/** Animated shimmer used by skeleton placeholders while content loads. */
fun Modifier.shimmer(shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)): Modifier = composed {
    val base = GlassFill
    val highlight = TextPrimary.copy(alpha = 0.10f)
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "shimmer_x")
    clip(shape).background(
        Brush.linearGradient(
            listOf(base, highlight, base),
            start = Offset(x * 1600f - 600f, 0f), end = Offset(x * 1600f, 300f),
        ),
    )
}

@Composable
fun SkeletonBlock(width: Dp?, height: Dp, modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(6.dp)) {
    Box((if (width == null) modifier.fillMaxWidth() else modifier.width(width)).height(height).shimmer(shape))
}

/** Placeholder with the shape of a list row (icon + two lines). */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier, trailing: Boolean = false) {
    Surface(shape = dev.asfakul.yumeiro.ui.theme.Yume.shapeMd, color = GlassFill, modifier = modifier.fillMaxWidth().testTag("skeleton_row")) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SkeletonBlock(44.dp, 44.dp, shape = RoundedCornerShape(12.dp))
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
                onClick = primary.second, shape = RoundedCornerShape(24.dp),
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
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YumeIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack, modifier = Modifier.testTag("subpage_back"))
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            // Editorial overline: the page's kanji in the accent, then the brand (設定 · YUMEIRO).
            // The kanji is decorative, so TalkBack skips it; the title below is the heading.
            val kana = if (kanaEnabled()) androidx.compose.runtime.remember(title) { Kana.forTitle(title) } else null
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (kana != null) {
                    Text(kana, style = dev.asfakul.yumeiro.ui.theme.OverlineStyle, color = dev.asfakul.yumeiro.ui.theme.SpotifyGreen, maxLines = 1,
                        modifier = Modifier.testTag("subpage_kana").clearAndSetSemantics { })
                    Text("  ·  ", style = dev.asfakul.yumeiro.ui.theme.OverlineStyle, color = TextTertiary, modifier = Modifier.clearAndSetSemantics { })
                }
                Text("YUMEIRO", style = dev.asfakul.yumeiro.ui.theme.OverlineStyle, color = if (kana != null) TextTertiary else dev.asfakul.yumeiro.ui.theme.SpotifyGreen, maxLines = 1,
                    modifier = Modifier.testTag("yumeiro_mark").clearAndSetSemantics { })
            }
            Text(title, color = TextPrimary, style = dev.asfakul.yumeiro.ui.theme.PageTitleCompactStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp).testTag("subpage_title").semantics { heading() })
            subtitle?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
        }
        actions()
    }
}

// endregion


/** Yumeiro's own signature line shown on every page opened on top of a tab, so the app name is always visible. */
@Composable
fun YumeiroMark(modifier: Modifier = Modifier, light: Boolean = false) {
    Text(
        "Yumeiro",
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
                Text("You're offline · showing saved content", color = dev.asfakul.yumeiro.ui.theme.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
