/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionMarket
import dev.asfakul.yumeiro.online.OnlineErrors
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.OnlineRuntime
import dev.asfakul.yumeiro.online.RemoteExtension
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.components.SkeletonRow
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.launch

/** Categories offered on first run, in order. */
internal val PICK_CATEGORIES = listOf("Anime", "Movies", "TV", "Asian drama", "Live", "Cartoon", "Documentary")

/** Top-ranked, working, compatible extensions of one category (best copy only). */
internal fun topPicks(all: List<RemoteExtension>, category: String, n: Int): List<RemoteExtension> =
    all.asSequence()
        .filter { it.primary && !it.isNsfw && !it.isDown && it.apiVersion <= OnlineRuntime.API_VERSION && category in it.categories }
        .sortedWith(compareByDescending<RemoteExtension> { it.score }.thenBy { if (it.rank > 0) it.rank else Int.MAX_VALUE })
        .take(n).toList()

/**
 * First run (nothing installed): Yumeiro installs one top-rated Movies, Anime and Sports extension
 * ([dev.asfakul.yumeiro.online.StarterExtensions]); this screen shows if those are removed or offline.
 * The user picks what they watch;
 * suggestions are the market's top-ranked extensions per category.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GetStarted(onOpenMarket: () -> Unit, onAddManually: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extState by ExtensionManager.state.collectAsState()
    var catalog by remember { mutableStateOf<ExtensionMarket.Catalog?>(null) }
    var loading by remember { mutableStateOf(true) }
    var category by rememberSaveable { mutableStateOf("Anime") }
    val chosen = remember { mutableStateListOf<String>() }
    var installing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        catalog = runCatching { ExtensionMarket.load(context) }.getOrNull()
        loading = false
    }
    val all = catalog?.extensions.orEmpty()
    val categories = remember(all) { PICK_CATEGORIES.filter { c -> topPicks(all, c, 1).isNotEmpty() } }
    val picks = remember(all, category) { topPicks(all, category, 8) }
    val byKey = remember(all) { all.associateBy { it.key } }

    fun installChosen() {
        val list = chosen.mapNotNull { byKey[it] }
        if (list.isEmpty()) return
        installing = true
        scope.launch {
            var ok = 0; val failed = mutableListOf<String>()
            for (r in list) runCatching { ExtensionManager.install(context, r) }.onSuccess { ok++ }.onFailure { failed += "${r.name}: ${OnlineErrors.friendly(it)}" }
            OnlineRepository.clearCaches()
            installing = false
            if (failed.isEmpty()) AppMessages.success(context, "Installed $ok extension${if (ok == 1) "" else "s"}")
            else if (ok > 0) AppMessages.error(context, "Installed $ok of ${list.size}. Skipped ${failed.size}: ${failed.first()}${if (failed.size > 1) " (+${failed.size - 1} more)" else ""}")
            else AppMessages.error(context, "Couldn't add ${failed.first()}${if (failed.size > 1) " (+${failed.size - 1} more)" else ""}")
        }
    }

    LazyColumn(Modifier.fillMaxSize().testTag("online_empty"), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 170.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item("intro") {
            Column {
                Box(Modifier.size(52.dp).clip(CircleShape).background(SpotifyGreen.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Extension, null, tint = SpotifyGreen, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("Pick what you watch", color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Online videos come from extensions made by the community. Choose a few — these are the most popular, recently updated ones in the market.",
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onOpenMarket, modifier = Modifier.testTag("online_empty_primary")) { Text("Browse market", color = TextPrimary) }
                    OutlinedButton(onClick = onAddManually, modifier = Modifier.testTag("online_empty_secondary")) { Text("Add manually", color = TextPrimary) }
                }
            }
        }
        when {
            loading -> items(4) { SkeletonRow(trailing = true) }
            categories.isEmpty() -> item("none") {
                Text("Suggestions aren't available right now. Open the market or add a repository link.", color = TextTertiary, fontSize = 12.sp, modifier = Modifier.testTag("online_picks_unavailable"))
            }
            else -> {
                item("cats") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.forEach { c -> OnlineChip(c, c == category, { category = c }, Modifier.testTag("online_pick_cat_$c")) }
                    }
                }
                items(picks, key = { "pick_" + it.key }) { ext ->
                    val on = ext.key in chosen
                    val installed = extState.installed.any { it.key == ext.key }
                    PickCard(ext, selected = on || installed, enabled = !installed && !installing) { if (on) chosen.remove(ext.key) else chosen.add(ext.key) }
                }
                item("legal") {
                    Text(
                        "Yumeiro doesn't host, own or check what extensions show. Use only sources you're allowed to watch where you live.",
                        color = TextTertiary, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        if (chosen.isNotEmpty()) item("install") {
            Button(
                onClick = { installChosen() }, enabled = !installing,
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent), shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("online_pick_install"),
            ) {
                if (installing) CircularProgressIndicator(color = OnAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                else Text("Install ${chosen.size} extension${if (chosen.size == 1) "" else "s"}", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PickCard(ext: RemoteExtension, selected: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp), color = if (selected) SpotifyGreen.copy(alpha = 0.14f) else GlassFill,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(enabled = enabled, onClick = onToggle).testTag("online_pick_${ext.internalName}"),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ExtensionIcon(ext.iconUrl?.replace("%size%", "128"), 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ext.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(ext.language?.uppercase(), ext.categories.take(3).joinToString(", "), ext.repoName).filter { it.isNotBlank() }.joinToString(" · "),
                    color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ext.stars?.takeIf { it > 0 }?.let {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFC94D), modifier = Modifier.size(12.dp))
                        Text(" $it", color = TextSecondary, fontSize = 11.sp)
                        Spacer(Modifier.width(8.dp))
                    }
                    if (ext.updated > 0) Text("updated ${relativeAge(ext.updated)}", color = TextSecondary, fontSize = 11.sp)
                }
            }
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(if (selected) SpotifyGreen else Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) { if (selected) Icon(Icons.Default.Check, null, tint = OnAccent, modifier = Modifier.size(16.dp)) }
        }
    }
}

/** "3d ago", "5h ago", "2mo ago". */
internal fun relativeAge(at: Long, now: Long = System.currentTimeMillis()): String {
    val m = ((now - at) / 60_000).coerceAtLeast(0)
    return when {
        m < 60 -> "${m.coerceAtLeast(1)}m ago"
        m < 60 * 24 -> "${m / 60}h ago"
        m < 60 * 24 * 30 -> "${m / 1440}d ago"
        m < 60 * 24 * 365 -> "${m / 43200}mo ago"
        else -> "${m / 525600}y ago"
    }
}
