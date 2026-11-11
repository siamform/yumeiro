/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.home

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.OnlineBookmarks
import dev.asfakul.yumeiro.ui.components.YuMotion
import dev.asfakul.yumeiro.ui.components.reducedMotion
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

/**
 * Which Home sections the user folded away. Remembered across launches in its own prefs file
 * ("home_layout"); nothing is removed, a folded section keeps its header and can be reopened.
 */
object HomeLayout {
    private const val PREFS = "home_layout"
    private const val KEY = "collapsed"
    private val _collapsed = MutableStateFlow<Set<String>>(emptySet())
    val collapsed: StateFlow<Set<String>> = _collapsed.asStateFlow()
    private var loaded = false

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        _collapsed.value = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty()
    }

    fun toggle(c: Context, key: String) {
        load(c)
        val next = if (key in _collapsed.value) _collapsed.value - key else _collapsed.value + key
        _collapsed.value = next
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, next).apply()
    }

    internal fun resetForTests() { _collapsed.value = emptySet(); loaded = false }
}

/** Section header that folds its section (chevron) and optionally opens the full list. */
@Composable
internal fun FoldHeader(
    key: String,
    title: String,
    subtitle: String?,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onMore: (() -> Unit)? = null,
    moreLabel: String = "See all",
    modifier: Modifier = Modifier,
) {
    val p = LocalMtPalette.current
    val turn by animateFloatAsState(if (collapsed) -90f else 0f, YuMotion.move(reducedMotion(), 250), label = "fold")
    Row(
        modifier.fillMaxWidth().padding(top = 24.dp, bottom = if (collapsed) 2.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClickLabel = if (collapsed) "Show $title" else "Hide $title", onClick = onToggle)
                .semantics { stateDescription = if (collapsed) "Collapsed" else "Expanded" }
                .testTag("home_fold_$key")
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f, fill = false)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.KeyboardArrowDown, null, tint = p.textTertiary, modifier = Modifier.size(22.dp).rotate(turn))
        }
        if (onMore != null && !collapsed) Text(
            moreLabel, color = p.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onMore).padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
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

/**
 * Hero carousel (M3 "hero" layout: one large item and a peek of the next,
 * https://m3.material.io/components/carousel/guidelines) of series with new or next episodes.
 */
@Composable
internal fun EpisodeCarousel(cards: List<EpisodeCard>, onOpen: (EpisodeCard) -> Unit) {
    val p = LocalMtPalette.current
    val reduced = reducedMotion()
    val pager = rememberPagerState { cards.size }
    Column(Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(end = if (cards.size > 1) 44.dp else 0.dp),
            pageSpacing = 10.dp,
            key = { cards[it].bookmark.key },
            modifier = Modifier.fillMaxWidth().testTag("home_next_episodes"),
        ) { page ->
            val card = cards[page]
            val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            Box(
                Modifier.graphicsLayer {
                    if (!reduced) { val s = 1f - 0.06f * offset; scaleX = s; scaleY = s; alpha = 1f - 0.35f * offset }
                }
            ) { EpisodeHero(card) { onOpen(card) } }
        }
        if (cards.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                repeat(cards.size) { i ->
                    val sel = i == pager.currentPage
                    val w by animateDpAsState(if (sel) 22.dp else 6.dp, YuMotion.move(reduced, 250), label = "dot")
                    Box(Modifier.padding(horizontal = 3.dp).height(6.dp).width(w).clip(CircleShape)
                        .background(if (sel) p.accent else p.textPrimary.copy(alpha = 0.16f)))
                }
            }
        }
    }
}

@Composable
private fun EpisodeHero(card: EpisodeCard, onClick: () -> Unit) {
    val p = LocalMtPalette.current
    val b = card.bookmark
    val shape = RoundedCornerShape(28.dp)
    val episode = card.episodeLine
    val meta = listOfNotNull(card.foundAt?.let { "Found ${shortDate(it)}" }, b.api).joinToString(" · ")
    Box(
        Modifier.fillMaxWidth().height(212.dp).clip(shape)
            .background(p.surfaceContainerHigh)
            .clickable(role = Role.Button, onClickLabel = "Open ${b.name}", onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(b.name, episode, if (card.fresh) "New" else b.status.label, meta).joinToString(", ")
            }
            .testTag("home_episode_${b.name}"),
    ) {
        // The series' own cover, softly behind everything (no invented artwork).
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (p.isDark) 0.30f else 0.18f }) {
            dev.asfakul.yumeiro.ui.screens.online.PosterImage(b.posterUrl, b.posterHeaders, Modifier.fillMaxSize())
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(p.surfaceContainerHigh.copy(alpha = 0.35f), p.surfaceContainerHigh.copy(alpha = 0.92f), p.surfaceContainerHigh))
            )
        )
        Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(18.dp))
                    .background(p.textPrimary.copy(alpha = 0.06f))
            ) { dev.asfakul.yumeiro.ui.screens.online.PosterImage(b.posterUrl, b.posterHeaders, Modifier.fillMaxSize()) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Text(
                    if (card.fresh) (if (b.newEpisodes == 1) "New episode" else "${b.newEpisodes} new") else b.status.label,
                    color = if (card.fresh) p.onAccent else p.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (card.fresh) p.accent else p.textPrimary.copy(alpha = 0.08f))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(b.name, style = MaterialTheme.typography.titleLarge, color = p.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (episode != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(episode, style = MaterialTheme.typography.bodyMedium, color = if (card.fresh) p.accent else p.textSecondary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(2.dp))
                Text(meta, style = MaterialTheme.typography.bodySmall, color = p.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(if (card.fresh) p.accent else p.textPrimary.copy(alpha = 0.10f))
                        .padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = if (card.fresh) p.onAccent else p.textPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (card.fresh) "Watch now" else "Episodes", color = if (card.fresh) p.onAccent else p.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * No episode data: say why and offer the real next step. With no Yumix sources the step is to
 * add one (setup); otherwise to follow a series.
 */
@Composable
internal fun EpisodesEmpty(hasSources: Boolean, onFind: () -> Unit, onAddSources: () -> Unit) {
    val p = LocalMtPalette.current
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(p.surfaceContainerHigh.copy(alpha = if (p.isDark) 0.6f else 1f))
            .border(1.dp, p.textPrimary.copy(alpha = 0.06f), shape)
            .testTag("home_episodes_empty")
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(p.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(if (hasSources) Icons.Default.NotificationsActive else Icons.Default.Storefront, null, tint = p.accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (hasSources) "No episode reminders yet" else "Add a Yumix source first",
                    style = MaterialTheme.typography.titleMedium, color = p.textPrimary, fontWeight = FontWeight.Bold,
                )
                Text(
                    if (hasSources) "Add a series to My List with reminders on. When a new episode is found, it shows up here."
                    else "Episodes come from extensions you install. Add one, then follow a series.",
                    style = MaterialTheme.typography.bodySmall, color = p.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(p.accent)
                .clickable(role = Role.Button, onClick = if (hasSources) onFind else onAddSources)
                .testTag("home_episodes_action")
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (hasSources) Icons.Default.AutoAwesome else Icons.Default.Storefront, null, tint = p.onAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (hasSources) "Find a series" else "Add sources", color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
    }
}

// ---- Spaces: the feature shortcuts, grouped ----------------------------------------------------

/** A group of Home shortcuts ("Watch", "Listen", "Read & keep"). */
@Immutable
internal data class Space(val key: String, val title: String, val summary: String, val icon: ImageVector)

/**
 * One space: a header (folds the group) and its shortcuts as a row of tiles. The first
 * [visible] tiles show; "Show more" reveals the rest with a size animation.
 */
@Composable
internal fun SpaceGroup(
    space: Space,
    collapsed: Boolean,
    onToggle: () -> Unit,
    columns: Int,
    tiles: List<@Composable (Modifier) -> Unit>,
) {
    val p = LocalMtPalette.current
    val shape = RoundedCornerShape(24.dp)
    val turn by animateFloatAsState(if (collapsed) -90f else 0f, YuMotion.move(reducedMotion(), 250), label = "spaceFold")
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(shape)
            .background(p.surfaceContainerHigh.copy(alpha = if (p.isDark) 0.45f else 0.7f))
            .animateContentSize(YuMotion.move(reducedMotion(), 280))
            .testTag("home_space_${space.key}"),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .clickable(role = Role.Button, onClickLabel = if (collapsed) "Show ${space.title}" else "Hide ${space.title}", onClick = onToggle)
                .semantics { stateDescription = if (collapsed) "Collapsed" else "Expanded" }
                .testTag("home_space_toggle_${space.key}")
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(space.icon, null, tint = p.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(space.title, style = MaterialTheme.typography.titleMedium, color = p.textPrimary, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(8.dp))
            Text(space.summary, style = MaterialTheme.typography.bodySmall, color = p.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Default.KeyboardArrowDown, null, tint = p.textTertiary, modifier = Modifier.size(22.dp).rotate(turn))
        }
        if (!collapsed) {
            Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tiles.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { t -> t(Modifier.weight(1f).fillMaxHeight()) }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** Small quiet line under the greeting that says what is waiting (from real data only). */
@Composable
internal fun ContextLine(text: String) {
    val p = LocalMtPalette.current
    Text(
        text, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 12.dp).testTag("home_context_line"),
    )
}
