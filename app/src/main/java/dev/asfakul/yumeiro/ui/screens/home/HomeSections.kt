/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.home

import android.content.Context
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.OnlineBookmarks
import dev.asfakul.yumeiro.ui.components.Hanko
import dev.asfakul.yumeiro.ui.components.KanjiMark
import dev.asfakul.yumeiro.ui.components.YuMotion
import dev.asfakul.yumeiro.ui.components.reducedMotion
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.SectionTitleStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

/**
 * Which Home sections the user switched off in "Customize home". Remembered in its own prefs file
 * ("home_layout"). Nothing folds any more: a section is either on Home or not, and every one of
 * them can be turned back on from the same sheet.
 */
object HomeLayout {
    private const val PREFS = "home_layout"
    private const val KEY = "hidden_v5"
    private val _hidden = MutableStateFlow<Set<String>>(emptySet())
    val hidden: StateFlow<Set<String>> = _hidden.asStateFlow()
    private var loaded = false

    /** The sections that can be switched off, in page order: key to label. */
    val sections = listOf(
        "episodes" to "New episodes",
        "spaces" to "Your spaces",
        "moods" to "Mood of the hour",
        "new" to "New arrivals",
        "most" to "Most played",
        "yt" to "On YouTube for you",
        "random" to "Yumix random",
    )

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        _hidden.value = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty()
    }

    fun toggle(c: Context, key: String) {
        load(c)
        val next = if (key in _hidden.value) _hidden.value - key else _hidden.value + key
        _hidden.value = next
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, next).apply()
    }

    internal fun resetForTests() { _hidden.value = emptySet(); loaded = false }
}

// ---- New episodes carousel ------------------------------------------------------------------

/** One real carousel entry, built only from what My List and the inbox know about a series. */
@Immutable
internal data class EpisodeCard(
    val bookmark: OnlineBookmarks.Bookmark,
    /** When Yumeiro found the newest episode (inbox time of the new-episode alert), if known. */
    val foundAt: Long?,
) {
    val fresh: Boolean get() = bookmark.newEpisodes > 0

    /** "Episode 12 is out", "Episodes 11–12 are out", "24 episodes", or null when never checked. */
    val episodeLine: String? get() {
        val total = bookmark.episodes
        val n = bookmark.newEpisodes
        return when {
            n > 0 && total > 0 && n == 1 -> "Episode $total is out"
            n > 0 && total > 0 -> "Episodes ${total - n + 1}–$total are out"
            n > 0 -> if (n == 1) "1 new episode" else "$n new episodes"
            total > 0 -> if (total == 1) "1 episode available" else "$total episodes available"
            else -> null
        }
    }
}

/** Builds carousel entries: series with new episodes first, then the ones you are watching. */
internal fun episodeCards(bookmarks: List<OnlineBookmarks.Bookmark>, inbox: List<dev.asfakul.yumeiro.notify.Inbox.Item>): List<EpisodeCard> {
    val fresh = bookmarks.filter { it.newEpisodes > 0 }.sortedByDescending { it.newEpisodes }
    val watching = bookmarks.filter { it.newEpisodes == 0 && it.status == OnlineBookmarks.Status.WATCHING }.sortedByDescending { it.addedAt }
    val alerts = inbox.filter { it.kind == dev.asfakul.yumeiro.notify.Inbox.Kind.EPISODE }
    return (fresh + watching).distinctBy { it.key }.take(12).map { b ->
        // NewEpisodesWorker posts ids as "ep-<bookmark key>-<hour>"; the newest one dates the find.
        val at = if (b.newEpisodes > 0) alerts.filter { it.id.startsWith("ep-${b.key}-") }.maxOfOrNull { it.at } else null
        EpisodeCard(b, at)
    }
}

private fun shortDate(ms: Long): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ms))

/** What one hero slide shows. Built from real data only (My List, inbox, extension rows). */
@Immutable
internal data class HeroSlide(
    val key: String,
    val name: String,
    val poster: String?,
    val headers: Map<String, String>,
    /** Small label over the title: "New episode", "Watching", "Latest". */
    val badge: String,
    val fresh: Boolean,
    val line: String?,
    val meta: String,
    val cta: String,
)

internal fun EpisodeCard.toSlide() = HeroSlide(
    key = bookmark.key, name = bookmark.name, poster = bookmark.posterUrl, headers = bookmark.posterHeaders,
    badge = if (fresh) (if (bookmark.newEpisodes == 1) "New episode" else "${bookmark.newEpisodes} new") else bookmark.status.label,
    fresh = fresh, line = episodeLine,
    meta = listOfNotNull(foundAt?.let { "Found ${shortDate(it)}" }, bookmark.api).joinToString(" · "),
    cta = if (fresh) "Watch now" else "Episodes",
)

/** Series with new or next episodes, as an auto-advancing hero carousel. */
@Composable
internal fun EpisodeCarousel(cards: List<EpisodeCard>, onOpen: (EpisodeCard) -> Unit) {
    val slides = remember(cards) { cards.map { it.toSlide() } }
    HeroCarousel(slides, tag = "home_next_episodes") { i -> onOpen(cards[i]) }
}

/**
 * M3 "hero" carousel (one large item and a peek of the next,
 * https://m3.material.io/components/carousel/guidelines). Every slide is a button. It advances by
 * itself every 6 s, pauses while touched, and stands still with reduced motion on.
 */
@Composable
internal fun HeroCarousel(slides: List<HeroSlide>, tag: String, onOpen: (Int) -> Unit) {
    val p = LocalMtPalette.current
    val reduced = reducedMotion()
    val pager = rememberPagerState { slides.size }
    var held by remember { mutableStateOf(false) }
    val auto = remember { android.os.Build.FINGERPRINT != "robolectric" }
    LaunchedEffect(slides.size, held, reduced) {
        if (!auto || reduced || held || slides.size < 2) return@LaunchedEffect
        while (true) {
            delay(6_000)
            if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % slides.size)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(end = if (slides.size > 1) 40.dp else 0.dp),
            pageSpacing = 10.dp,
            key = { slides[it].key },
            modifier = Modifier.fillMaxWidth().testTag(tag)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false); held = true
                        waitForUpOrCancellation(); held = false
                    }
                },
        ) { page ->
            val s = slides[page]
            val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            Box(Modifier.graphicsLayer { if (!reduced) { val k = 1f - 0.05f * offset; scaleX = k; scaleY = k; alpha = 1f - 0.3f * offset } }) {
                HeroSlideCard(s) { onOpen(page) }
            }
        }
        if (slides.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
                repeat(slides.size) { i ->
                    val sel = i == pager.currentPage
                    val w by animateDpAsState(if (sel) 18.dp else 5.dp, YuMotion.move(reduced, 250), label = "dot")
                    Box(Modifier.padding(horizontal = 2.5.dp).height(5.dp).width(w).clip(CircleShape)
                        .background(if (sel) p.accent else p.textPrimary.copy(alpha = 0.16f)))
                }
            }
        }
    }
}

@Composable
private fun HeroSlideCard(s: HeroSlide, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.fillMaxWidth().height(184.dp).clip(shape)
            .background(p.surfaceContainerHigh)
            .border(1.dp, p.textPrimary.copy(alpha = 0.06f), shape)
            .clickable(role = Role.Button, onClickLabel = "Open ${s.name}", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(s.name, s.line, s.badge, s.meta).joinToString(", ") }
            .testTag("home_episode_${s.name}"),
    ) {
        // The title's own cover, softly behind everything (no invented artwork).
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (p.isDark) 0.28f else 0.16f }) {
            dev.asfakul.yumeiro.ui.screens.online.PosterImage(s.poster, s.headers, Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(p.surfaceContainerHigh.copy(alpha = 0.3f), p.surfaceContainerHigh.copy(alpha = 0.94f), p.surfaceContainerHigh))))
        KanjiMark(if (s.fresh) "新" else "続", Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 4.dp), size = 72.sp, alpha = 0.07f)
        Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)).background(p.textPrimary.copy(alpha = 0.06f))) {
                dev.asfakul.yumeiro.ui.screens.online.PosterImage(s.poster, s.headers, Modifier.fillMaxSize())
                if (s.fresh) Hanko(modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Text(
                    s.badge.uppercase(), color = if (s.fresh) p.accent else p.textTertiary, fontSize = 10.sp, letterSpacing = 1.sp,
                    fontWeight = FontWeight.ExtraBold, maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(s.name, style = SectionTitleStyle, color = p.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (s.line != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(s.line, style = MaterialTheme.typography.bodySmall, color = if (s.fresh) p.textPrimary else p.textSecondary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (s.meta.isNotBlank()) Text(s.meta, style = MaterialTheme.typography.bodySmall, color = p.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(if (s.fresh) p.accent else p.textPrimary.copy(alpha = 0.10f))
                        .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = if (s.fresh) p.onAccent else p.textPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(s.cta, color = if (s.fresh) p.onAccent else p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * No episode data: say why and offer the real next step. With no Yumix sources the step is to
 * add one; otherwise to follow a series. Compact, one row.
 */
@Composable
internal fun EpisodesEmpty(hasSources: Boolean, onFind: () -> Unit, onAddSources: () -> Unit) {
    val p = LocalMtPalette.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(p.surfaceContainerHigh.copy(alpha = if (p.isDark) 0.6f else 1f))
            .border(1.dp, p.textPrimary.copy(alpha = 0.06f), shape)
            .testTag("home_episodes_empty")
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(p.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(if (hasSources) Icons.Default.NotificationsActive else Icons.Default.Storefront, null, tint = p.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (hasSources) "No episode reminders yet" else "Add a Yumix source first",
                style = MaterialTheme.typography.titleSmall, color = p.textPrimary,
            )
            Text(
                if (hasSources) "Follow a series with reminders on. New episodes land here."
                else "Episodes come from the extensions you install.",
                style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(p.accent)
                .clickable(role = Role.Button, onClick = if (hasSources) onFind else onAddSources)
                .testTag("home_episodes_action")
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (hasSources) Icons.Default.AutoAwesome else Icons.Default.Storefront, null, tint = p.onAccent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (hasSources) "Find" else "Add", color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
    }
}

/** Small quiet line under the greeting that says what is waiting (from real data only). */
@Composable
internal fun ContextLine(text: String) {
    val p = LocalMtPalette.current
    Text(
        text, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 4.dp).testTag("home_context_line"),
    )
}

/** Shared tile colour for quiet surfaces on Home. */
@Composable
internal fun tileFill(): Color {
    val p = LocalMtPalette.current
    return p.surfaceContainerHigh.copy(alpha = if (p.isDark) 0.62f else 1f)
}
