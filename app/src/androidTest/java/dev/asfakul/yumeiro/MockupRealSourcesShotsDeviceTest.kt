/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.sources.Bridges
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.sources.js.JsSources
import dev.asfakul.yumeiro.sources.novel.NovelSources
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.theme.Palettes
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GROUP Z mockups, round 2: the source pages with real, public source lists in the dark theme. Manga from
 * Mangayomi-format JavaScript sources (kodjodevf/mangayomi-extensions: MangaDex, Webtoons, Mangapill), novels
 * from an LNReader plugin (Royal Road), plus the splash and the Sources overview. Each shot asserts what it
 * claims. Log tag YuReal.
 */
@RunWith(AndroidJUnit4::class)
class MockupRealSourcesShotsDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private val tv = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    private val form get() = if (tv) "tv" else "phone"
    private fun log(s: String) = Log.i("YuReal", s)
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun count(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().size
    private fun gridItems() = runCatching { rule.onAllNodesWithTag("manga_grid", true).onFirst().onChildren().fetchSemanticsNodes().size }.getOrDefault(0)
    private fun textCount(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().size
    private fun hasText(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitTag(tag: String, ms: Long = 20_000) = runCatching { rule.waitUntil(ms) { has(tag) }; true }.getOrDefault(false)
    private fun pause(ms: Long) { val until = SystemClock.uptimeMillis() + ms; runCatching { rule.waitUntil(ms + 5_000) { SystemClock.uptimeMillis() > until } } }
    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { CompositionLocalProvider(LocalTvMode provides tv) { MTPlayerTheme(palette = Palettes.Dark) { FirstRun.AppBackground { content() } } } }
    }
    private fun snap(name: String, shows: Boolean) {
        rule.waitForIdle()
        FirstRun.shot("real_${form}_$name")
        log("REAL_SHOT ${form}_$name ${if (shows) "ok" else "missing"}")
        assertTrue("mockup $name does not show what it claims", shows)
    }

    @Test fun a_splash() {
        show { dev.asfakul.yumeiro.ui.screens.splash.SplashScreen(onSplashFinished = {}) }
        pause(1_100)
        snap("splash", has("splash_screen_root"))
    }

    @Test fun b_sources() {
        show { dev.asfakul.yumeiro.ui.screens.sources.SourcesHubScreen(onBack = {}) }
        assertTrue(waitTag("src_home")); pause(2_000)
        snap("sources", has("src_home"))
    }

    @Test fun c_manga() {
        Bridges.start(ctx.applicationContext)
        runBlocking { JsSources.addRepo(ctx, MANGAYOMI).getOrThrow() }
        val all = runBlocking { JsSources.available(ctx) }
        val want = all.filter { it.name in MANGA && it.raw["lang"]?.toString()?.trim('"') == "en" && it.raw["itemType"]?.toString() == "0" && it.raw["sourceCodeLanguage"]?.toString() == "1" }
        log("manga entries: ${want.map { it.name }}")
        want.forEach { e -> runBlocking { JsSources.install(ctx, e) }.onFailure { log("install fail ${e.name}: ${it.message}") } }
        runCatching { rule.waitUntil(30_000) { SourceHub.mangaProviders().any { it.name == "Webtoons" || it.name == "MangaDex" } } }
        val pick = SourceHub.mangaProviders().let { ps -> ps.firstOrNull { it.name == "Webtoons" } ?: ps.firstOrNull { it.name == "MangaDex" } ?: ps.first() }
        log("manga providers: ${SourceHub.mangaProviders().map { it.name }} pick=${pick.name}")
        ctx.getSharedPreferences("manga_home", 0).edit().putString("source", pick.id).commit()
        show { dev.asfakul.yumeiro.ui.screens.manga.MangaHomeScreen(onBack = {}, onOpenTitle = {}, onOpenSources = {}) }
        assertTrue(waitTag("manga_home"))
        runCatching { rule.waitUntil(60_000) { gridItems() >= 4 } }
        pause(8_000)
        snap("manga", gridItems() >= 1)
        val sl = SourceHub.mangaProviders().firstOrNull { it.name == "Mangapill" }
        if (sl != null) {
            ctx.getSharedPreferences("manga_home", 0).edit().putString("source", sl.id).commit()
        }
    }

    @Test fun d_mangaSearch() {
        Bridges.start(ctx.applicationContext)
        runCatching { rule.waitUntil(30_000) { SourceHub.mangaProviders().isNotEmpty() } }
        val sl = SourceHub.mangaProviders().let { ps -> ps.firstOrNull { it.name == "Mangapill" } ?: ps.firstOrNull { it.name == "MangaDex" } } ?: return
        ctx.getSharedPreferences("manga_home", 0).edit().putString("source", sl.id).commit()
        show { dev.asfakul.yumeiro.ui.screens.manga.MangaHomeScreen(onBack = {}, onOpenTitle = {}, onOpenSources = {}) }
        assertTrue(waitTag("manga_home")); pause(2_000)
        rule.onAllNodesWithTag("manga_search_open", true).onFirst().performClick(); rule.waitForIdle()
        assertTrue(waitTag("manga_search"))
        rule.onAllNodesWithTag("manga_search", true).onFirst().performTextInput("Solo Leveling")
        runCatching { rule.waitUntil(60_000) { textCount("Solo Leveling") >= 2 } }
        FirstRun.shell("input keyevent 111"); pause(8_000)
        snap("manga_search", textCount("Solo Leveling") >= 2)
    }

    @Test fun e_novel() {
        runBlocking { NovelSources.addRepo(ctx, LNREADER).getOrThrow() }
        val p = runBlocking { NovelSources.available(ctx) }.first { it.id == "royalroad" }
        runBlocking { NovelSources.install(ctx, p) }.getOrThrow()
        show { dev.asfakul.yumeiro.ui.screens.novel.NovelHomeScreen(onBack = {}, onOpenNovel = {}, onOpenSources = {}) }
        assertTrue(waitTag("novel_home"))
        runCatching { rule.waitUntil(60_000) { count("novel_cover") >= 3 || has("novel_banner") } }
        pause(8_000)
        snap("novel", has("novel_home") && (count("novel_cover") > 0 || has("novel_banner")))
    }

    companion object {
        const val MANGAYOMI = "https://raw.githubusercontent.com/kodjodevf/mangayomi-extensions/main/index.json"
        const val LNREADER = "https://raw.githubusercontent.com/LNReader/lnreader-plugins/plugins/v3.0.0/.dist/plugins.min.json"
        val MANGA = setOf("MangaDex", "Webtoons", "Mangapill", "Weeb Central")
    }
}
