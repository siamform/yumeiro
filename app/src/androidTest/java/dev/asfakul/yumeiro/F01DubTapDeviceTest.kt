/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionReview
import dev.asfakul.yumeiro.online.ExtensionSources
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.OnlineRuntime
import dev.asfakul.yumeiro.online.ReleaseTags
import dev.asfakul.yumeiro.online.VersionHint
import dev.asfakul.yumeiro.ui.screens.online.OnlineDetailScreen
import dev.asfakul.yumeiro.ui.screens.online.PosterCard
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F-01 / F-16 device check: real anime extensions from the market, a real search. A result the extension
 * marks Dubbed gets a language badge on its poster; tapping the badge (phone) opens the title with the Dub
 * version already chosen, not the default one. NBUG-5 tie: the episode list shown is the Dub list.
 * No anime extension reachable, or no dubbed result → skipped (Blocked), not passed.
 */
@RunWith(AndroidJUnit4::class)
class F01DubTapDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private fun log(s: String) = Log.i("F01", s)

    private fun findDubbed(): OnlineItem? = runBlocking {
        ExtensionManager.start(ctx); ExtensionManager.ensureLoaded(ctx)
        val market = runCatching { ExtensionSources.all(ctx, force = true).items }.getOrDefault(emptyList())
        val anime = market.filter {
            it.primary && !it.isNsfw && !it.isDown && it.apiVersion <= OnlineRuntime.API_VERSION &&
                it.tvTypes.any { t -> t.equals("Anime", true) } && it.language?.startsWith("en") != false
        }.sortedByDescending { it.verified }.take(6)
        for (c in anime) if (ExtensionManager.provider(c.name) == null) runCatching { ExtensionManager.install(ctx, c, ExtensionReview.Mode.AUTO) }
        val names = anime.map { it.name }.toSet()
        val apis = ExtensionManager.providers().filter { it.name in names || it.supportedTypes.any { t -> t.name == "Anime" } }
        log("anime providers=${apis.map { it.name }}")
        if (apis.isEmpty()) return@runBlocking null
        for (q in listOf("One Piece", "Frieren", "Solo Leveling", "Dandadan", "Blue Lock")) {
            val updates = ArrayList<OnlineRepository.SearchUpdate>()
            withTimeoutOrNull(60_000) { OnlineRepository.search(q, apis).collect { synchronized(updates) { updates += it } } }
            val hit = updates.flatMap { it.items }.firstOrNull { item ->
                item.dub.isNotEmpty() && item.tags.firstOrNull { it.kind == ReleaseTags.Kind.LANGUAGE }?.let { VersionHint.wantFor(it.label) } == "dub"
            }
            log("q=$q results=${updates.sumOf { it.items.size }} dubbed=${hit?.name}@${hit?.api}")
            if (hit != null) return@runBlocking hit
        }
        null
    }

    @Test fun dubBadgeOpensTheDubVersion() {
        assumeTrue("TV keeps one focus stop per card (no badge tap)", !Edition.isTv)
        val item = findDubbed()
        assumeTrue("no dubbed anime result from any market extension (network / market)", item != null)
        item!!
        var open by mutableStateOf(false)
        rule.setContent {
            MTPlayerTheme {
                if (!open) PosterCard(item, onClick = { open = true })
                else OnlineDetailScreen(item.api, item.url, onBack = {}, onOpenTitle = {}, onOpenExtensions = {})
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("poster_version_tag", true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithTag("poster_version_tag", true)[0].performClick()
        rule.waitUntil(5_000) { open }
        // Version chips: shown when the title has more than one version.
        val chips = runCatching {
            rule.waitUntil(90_000) { rule.onAllNodesWithTag("online_detail_play", true).fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithTag("online_detail_error", true).fetchSemanticsNodes().isNotEmpty() }
            rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("version chip") { n ->
                n.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.startsWith("online_version_") == true
            }, true).fetchSemanticsNodes()
        }.getOrDefault(emptyList())
        val labels = chips.map { it.config[SemanticsProperties.TestTag].removePrefix("online_version_") to (it.config.getOrElseNullable(SemanticsProperties.Selected) { null } == true) }
        log("F01_RESULT title=${item.name} api=${item.api} versions=$labels")
        assertTrue("detail did not open: ${rule.onAllNodesWithTag("online_detail_error", true).fetchSemanticsNodes().size} errors", rule.onAllNodesWithTag("online_detail_play", true).fetchSemanticsNodes().isNotEmpty())
        assumeTrue("the title has one version only: $labels", labels.size >= 2)
        val selected = labels.filter { it.second }.map { it.first }
        assertTrue("the Dub badge tap did not choose the Dub version: selected=$selected of $labels", selected.size == 1 && selected[0].contains("dub", ignoreCase = true))
        log("F01_RESULT PASS selected=${selected[0]}")
    }
}
