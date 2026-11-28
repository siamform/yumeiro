/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.intel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.asfakul.yumeiro.intel.Aspect
import dev.asfakul.yumeiro.intel.Discovery
import dev.asfakul.yumeiro.intel.MediaCategory
import dev.asfakul.yumeiro.intel.TasteSignals
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.components.FilterPill
import dev.asfakul.yumeiro.ui.components.YuSection
import dev.asfakul.yumeiro.ui.components.yuEntrance
import dev.asfakul.yumeiro.ui.components.yuPressScale
import dev.asfakul.yumeiro.ui.screens.online.PosterImage
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.yuRound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The shelf heading per category ("For you · Anime"). */
private fun MediaCategory.shelfTitle(): String = when (this) {
    MediaCategory.ANIME -> "Anime for you"
    MediaCategory.MOVIE -> "Movies for you"
    MediaCategory.SERIES -> "Series for you"
    MediaCategory.MANGA -> "Manga for you"
    MediaCategory.NOVEL -> "Novels for you"
    else -> "For you"
}

private fun MediaCategory.one(): String = when (this) {
    MediaCategory.MOVIE -> "Movie"
    MediaCategory.NOVEL -> "Novel"
    else -> label
}

private fun Aspect.reasonLabel(): String = when (this) {
    Aspect.CHARACTERS -> "characters"
    Aspect.THEMES -> "story"
    Aspect.SETTING -> "setting"
    Aspect.GENRE -> "genre"
    Aspect.STYLE -> "style"
    Aspect.CREATORS -> "creators"
}

/** One line of why: "Yandere (characters), Death Game, Survival (story)". */
internal fun Discovery.Rec.whyLine(n: Int = 3): String =
    reasons.take(n).groupBy { it.aspect }.entries.joinToString(", ") { (a, l) -> l.joinToString(", ") { it.name } + " (" + a.reasonLabel() + ")" }

/**
 * B20 S3: a "For you" shelf of one category. Builds (or reuses) the open profile's picks off the main thread;
 * draws nothing until there is an honest shelf (enough taste and real titles). Hold a card (or the remote's Menu
 * key) for why it was picked and "Not interested".
 */
@Composable
fun ForYouShelf(category: MediaCategory, onOpen: (Discovery.Rec) -> Unit, modifier: Modifier = Modifier, gutter: Dp = 0.dp) {
    val context = LocalContext.current
    val state by Discovery.state.collectAsState()
    val version by TasteSignals.version.collectAsState()
    LaunchedEffect(category, version / 5) {
        // Taste changes in small steps: every 5th change is enough to ask whether the shelf is due (it decides).
        runCatching { Discovery.refresh(context, category) }
    }
    val shelf = state[category] ?: return
    if (shelf.recs.isEmpty()) return
    var why by remember { mutableStateOf<Discovery.Rec?>(null) }
    Column(modifier.fillMaxWidth().testTag("for_you_${category.code}")) {
        val ask = LocalAssistantOpener.current
        YuSection(
            "推薦", "For you", category.shelfTitle(),
            modifier = Modifier.padding(horizontal = gutter),
            action = if (ask != null) "Ask AI" else null, onAction = ask?.let { { it(category) } }, actionTag = "for_you_ask",
        )
        shelf.crossFrom?.let { from ->
            Text("From your ${from.label.lowercase()} taste", color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = gutter).padding(bottom = 8.dp).testTag("for_you_cross"))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(shelf.recs, key = { _, r -> r.key }) { _, r ->
                RecCard(r, onClick = { Discovery.opened(context, r); onOpen(r) }, onMore = { why = r })
            }
        }
    }
    why?.let { r ->
        WhySheet(r, onDismiss = { why = null }, onOpen = { why = null; Discovery.opened(context, r); onOpen(r) }, onNotInterested = {
            why = null
            val index = shelf.recs.indexOfFirst { it.key == r.key }
            val before = Discovery.dismiss(context, r)
            AppMessages.show(context, "You won't see ${r.title} again", actionLabel = "Undo", action = { Discovery.undoDismiss(context, r, before, index) })
        })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecCard(r: Discovery.Rec, onClick: () -> Unit, onMore: () -> Unit, width: Dp = 112.dp, similar: Boolean = false, category: MediaCategory? = null) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Column(
        Modifier.width(width).yuEntrance("rec_" + r.key).yuPressScale(source).clip14()
            .then(if (focused) Modifier.border(2.dp, Color.White, yuRound(14)) else Modifier)
            .onPreviewKeyEvent { e -> if (e.key == Key.Menu && e.type == KeyEventType.KeyUp) { onMore(); true } else e.key == Key.Menu }
            .combinedClickable(interactionSource = source, indication = androidx.compose.material3.ripple(), onClick = onClick, onLongClick = onMore, onLongClickLabel = "Why this pick")
            .testTag("for_you_card").padding(bottom = 8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip14()) {
            PosterImage(r.cover, emptyMap(), Modifier.fillMaxSize())
            val label = when { r.gem -> "Hidden gem"; r.new -> "New"; else -> null }
            if (label != null) Row(
                Modifier.align(Alignment.TopStart).padding(6.dp).clip(yuRound(6)).background(Color.Black.copy(alpha = 0.66f)).padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (r.gem) Icon(Icons.Default.Diamond, null, tint = Color(0xFF8FE3FF), modifier = Modifier.size(10.dp).padding(end = 2.dp))
                Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(r.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
        Text(listOfNotNull(category?.one(), if (similar) "${r.match}% alike" else "${r.match}% match").joinToString(" · "), color = TextTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("for_you_match"))
    }
}

private fun Modifier.clip14() = clip(yuRound(14))

/** Why a pick is here: real shared tags (from AniList or TMDB), the seeds that led to it, and "Not interested". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WhySheet(r: Discovery.Rec, onDismiss: () -> Unit, onOpen: () -> Unit, onNotInterested: (() -> Unit)?, similarTo: String? = null) {
    val body: @Composable ColumnScope.(FocusRequester?) -> Unit = { first ->
        Text(r.title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val meta = listOfNotNull(r.year?.toString(), r.format?.lowercase()?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }, r.score.takeIf { it > 0 }?.let { "Score $it" }).joinToString(" · ")
        if (meta.isNotEmpty()) Text(meta, color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Text("Why it's here", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(
            if (similarTo != null) "${r.match}% alike. Shares with $similarTo:" else "${r.match}% taste match. Matches your taste for:",
            color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            r.reasons.forEach { x -> FilterPill("${x.name} · ${x.aspect.reasonLabel()}", selected = false, onClick = {}, testTag = "why_reason") }
        }
        if (r.because.isNotEmpty()) Text("Recommended by fans of " + r.because.joinToString(" and "), color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        if (r.gem) Text("Hidden gem: fewer people know it, and it's well rated.", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        if (r.new) Text("New: out in the last few months.", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Text(
            "Metadata match from " + (if (r.ids.anilist != null) "AniList tags" else "TMDB genres and keywords") +
                (if (similarTo != null) "." else ", weighed by what this profile watched and read.") + " Not AI.",
            color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp).testTag("why_source"),
        )
        Spacer(Modifier.height(12.dp))
        SheetAction(Icons.Default.PlayArrow, "Open", "why_open", first, onOpen)
        if (onNotInterested != null) SheetAction(Icons.Default.Block, "Not interested", "why_not_interested", null, onNotInterested)
    }
    if (LocalTvMode.current) {
        val first = remember { FocusRequester() }
        Dialog(onDismissRequest = onDismiss) {
            Column(Modifier.widthIn(max = 460.dp).clip(yuRound(20)).background(Color(0xFF16161C)).padding(20.dp).verticalScroll(rememberScrollState()).testTag("why_sheet")) { body(first) }
            LaunchedEffect(Unit) { repeat(20) { runCatching { first.requestFocus() }; kotlinx.coroutines.delay(60) } }
        }
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), dragHandle = dev.asfakul.yumeiro.ui.components.sheetDragHandle()) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).testTag("why_sheet")) { body(null) }
        }
    }
}

@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tag: String, focus: FocusRequester?, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(yuRound(14))
            .then(if (focused) Modifier.border(2.dp, Color.White, yuRound(14)) else Modifier)
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .testTag(tag).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = TextPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * B20 S3 completion prompt: a title found through For You and finished, never rated, asks once (Loved it / Liked it /
 * It was okay / Not for me / Skip). Shown on pages, never over playback; Skip or any answer ends it for good.
 */
@Composable
fun CompletionPrompt(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by TasteSignals.version.collectAsState()
    var entry by remember { mutableStateOf<TasteSignals.Entry?>(null) }
    LaunchedEffect(version) { entry = withContext(Dispatchers.IO) { runCatching { Discovery.promptFor(context) }.getOrNull() } }
    val e = entry ?: return
    val ref = TasteSignals.Ref(e.key, e.category, e.title, e.cover, e.year, e.ids)
    Column(
        modifier.fillMaxWidth().clip(yuRound(16)).background(Color.White.copy(alpha = 0.06f)).padding(14.dp).testTag("completion_prompt"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("You finished ${e.title}. How was it?", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = { entry = null; scope.launch(Dispatchers.IO) { TasteSignals.prompted(context, ref) } }, modifier = Modifier.testTag("completion_skip")) {
                Icon(Icons.Default.Close, "Skip", tint = TextSecondary)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TasteSignals.Feedback.entries.forEach { f ->
                FilterPill(f.label, selected = false, icon = f.icon(), testTag = "completion_${f.code}", onClick = {
                    entry = null
                    scope.launch(Dispatchers.IO) { TasteSignals.prompted(context, ref); TasteSignals.setFeedback(context, ref, f) }
                })
            }
        }
        Text("Only this profile's picks use it.", color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/**
 * B20 S3 "Similar by story" on an anime / manga / novel page: titles sharing its characters, story and setting tags
 * (AniList), with the shared tags on hold. Draws nothing when the title isn't matched or nothing is alike enough.
 */
@Composable
fun SimilarByStory(ref: TasteSignals.Ref?, modifier: Modifier = Modifier, gutter: Dp = 0.dp, onOpen: ((Discovery.Rec) -> Unit)? = LocalRecOpener.current) {
    if (ref == null || ref.adult || onOpen == null) return
    val context = LocalContext.current
    var recs by remember(ref.key) { mutableStateOf<List<Discovery.Rec>>(emptyList()) }
    LaunchedEffect(ref.key) { recs = runCatching { Discovery.similar(context, ref) }.getOrDefault(emptyList()) }
    if (recs.isEmpty()) return
    var why by remember { mutableStateOf<Discovery.Rec?>(null) }
    Column(modifier.fillMaxWidth().testTag("similar_by_story")) {
        YuSection("似", "Similar by story", "Alike in characters and story", modifier = Modifier.padding(horizontal = gutter), top = 16.dp)
        LazyRow(contentPadding = PaddingValues(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(recs, key = { _, r -> r.key }) { _, r -> RecCard(r, onClick = { Discovery.opened(context, r); onOpen(r) }, onMore = { why = r }, similar = true) }
        }
        Text(if (LocalTvMode.current) "Press Menu on a title to see what it shares." else "Hold a title to see what it shares.", color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = gutter).padding(top = 2.dp))
    }
    why?.let { r -> WhySheet(r, onDismiss = { why = null }, onOpen = { why = null; Discovery.opened(context, r); onOpen(r) }, onNotInterested = null, similarTo = ref.title) }
}

/** The app's pick opener (title pages read it; MTPlayerApp provides it). */
val LocalRecOpener = androidx.compose.runtime.staticCompositionLocalOf<((Discovery.Rec) -> Unit)?> { null }

/** B21: opens the assistant for a category (null = everything); provided by the app shell. */
val LocalAssistantOpener = androidx.compose.runtime.staticCompositionLocalOf<((MediaCategory?) -> Unit)?> { null }

/** Where a pick asks the installed sources by name (manga and novel homes read it once when they open). */
object IntelSearchPending {
    @Volatile var manga: String? = null
    @Volatile var novel: String? = null
    fun takeManga(): String? = manga.also { manga = null }
    fun takeNovel(): String? = novel.also { novel = null }
}

/**
 * B20 S3 Home: the best few picks of every category this profile has a taste for, interleaved (each card says its
 * kind). Categories keep their own taste; the mix only puts their shelves side by side.
 */
@Composable
fun ForYouMix(onOpen: (Discovery.Rec) -> Unit, modifier: Modifier = Modifier, gutter: Dp = 0.dp) {
    val context = LocalContext.current
    val state by Discovery.state.collectAsState()
    val version by TasteSignals.version.collectAsState()
    LaunchedEffect(version / 5) {
        // One after another (AniList calls are spaced anyway); each category decides whether it is due.
        Discovery.SHELVES.forEach { runCatching { Discovery.refresh(context, it) } }
    }
    val mixed = remember(state) {
        val lists = Discovery.SHELVES.mapNotNull { c -> state[c]?.recs?.take(4)?.takeIf { it.isNotEmpty() } }
        buildList { for (i in 0 until 4) lists.forEach { l -> l.getOrNull(i)?.let(::add) } }
    }
    if (mixed.isEmpty()) return
    var why by remember { mutableStateOf<Discovery.Rec?>(null) }
    Column(modifier.fillMaxWidth().testTag("for_you_home")) {
        val ask = LocalAssistantOpener.current
        YuSection("推薦", "For you", "Picked from your taste", modifier = Modifier.padding(horizontal = gutter),
            action = if (ask != null) "Ask AI" else null, onAction = ask?.let { { it(null) } }, actionTag = "for_you_ask")
        LazyRow(contentPadding = PaddingValues(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(mixed, key = { _, r -> r.category.code + r.key }) { _, r ->
                RecCard(r, onClick = { Discovery.opened(context, r); onOpen(r) }, onMore = { why = r }, category = r.category)
            }
        }
    }
    why?.let { r ->
        WhySheet(r, onDismiss = { why = null }, onOpen = { why = null; Discovery.opened(context, r); onOpen(r) }, onNotInterested = {
            why = null
            val index = state[r.category]?.recs?.indexOfFirst { it.key == r.key } ?: 0
            val before = Discovery.dismiss(context, r)
            AppMessages.show(context, "You won't see ${r.title} again", actionLabel = "Undo", action = { Discovery.undoDismiss(context, r, before, index) })
        })
    }
}
