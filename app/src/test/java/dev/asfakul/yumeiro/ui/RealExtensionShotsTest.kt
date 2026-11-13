/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dev.asfakul.yumeiro.ui.theme.pageBackground
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.lagradost.cloudstream3.APIHolder
import dev.asfakul.yumeiro.data.model.VideoItem
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionMarket
import dev.asfakul.yumeiro.online.ExtensionReview
import dev.asfakul.yumeiro.online.RemoteExtension
import dev.asfakul.yumeiro.ui.screens.online.ExtensionsScreen
import dev.asfakul.yumeiro.ui.screens.online.OnlineDetailScreen
import dev.asfakul.yumeiro.ui.screens.online.OnlineSearchScreen
import dev.asfakul.yumeiro.ui.screens.video.VideoPlayerModal
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Real screens for the website and store listing, driven through the real Internet Archive
 * extension (Luna712's InternetArchiveProvider, the app's default suggestion) against archive.org.
 *
 * Robolectric runs on the JVM and cannot open DEX, so the extension's .cs3 is converted once with
 * dex2jar and loaded from that jar (the app itself always uses PathClassLoader). Skipped unless
 * YUMEIRO_IA_JAR points at the converted jar; needs network. See docs/redesign/HANDOVER-REDESIGN.md.
 *
 * The player shot: Robolectric cannot decode video, so the TextureView stays empty. A real frame
 * of Big Buck Bunny (© Blender Foundation, CC BY 3.0, YUMEIRO_BBB_FRAME) is placed in the player's
 * content frame, and a caption cue is set on the player's own SubtitleView, so the controls, the
 * caption style and the caption position above the seek bar are the app's real ones.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-night-xxhdpi")
class RealExtensionShotsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context get() = rule.activity.applicationContext
    private val jar: String? = System.getenv("YUMEIRO_IA_JAR")

    @Before fun setUp() {
        assumeTrue("set YUMEIRO_IA_JAR to run the real-extension shots", jar != null && File(jar).exists())
        File(context.filesDir, "extensions").deleteRecursively()
        ExtensionManager.resetForTests(); ExtensionMarket.resetForTests()
        dev.asfakul.yumeiro.network.Connectivity.overrideForTests(true)
        val jarUrl = File(jar!!).toURI().toURL()
        ExtensionManager.loaderFactory = { f, parent -> java.net.URLClassLoader(arrayOf(jarUrl, f.toURI().toURL()), parent) }
        runBlocking {
            ExtensionManager.install(
                context,
                RemoteExtension(
                    internalName = "InternetArchiveProvider", name = "InternetArchiveProvider", version = 22,
                    url = "https://raw.githubusercontent.com/Luna712/Luna712-CloudStream-Extensions/builds/InternetArchiveProvider.cs3",
                    authors = listOf("Luna712"), description = "Watch content from the Internet Archive at archive.org",
                    tvTypes = listOf("Others"), language = "en",
                    iconUrl = "https://www.google.com/s2/favicons?domain=archive.org&sz=%size%",
                    repoUrl = ExtensionManager.DEFAULT_REPO, repoName = "Luna712's CloudStream extension repository",
                ),
                ExtensionReview.Mode.AUTO,
            )
        }
    }

    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(ms: Long = 30_000, cond: () -> Boolean) = rule.waitUntil(ms) { cond() }
    private fun pause(ms: Long) { val end = System.currentTimeMillis() + ms; while (System.currentTimeMillis() < end) { rule.waitForIdle(); Thread.sleep(100) } }

    private fun shot(name: String) {
        rule.waitForIdle()
        val dir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 95, it)
        }
    }

    private fun bigBuckBunny() = runBlocking {
        val api = APIHolder.allProviders.first { it.name == "Internet Archive" }
        api.search("Big Buck Bunny", 1)!!.items.first { it.name.equals("Big Buck Bunny", true) }
    }

    @Test fun searchTitleAndExtensions() {
        var page by androidx.compose.runtime.mutableStateOf("search")
        var url by androidx.compose.runtime.mutableStateOf("")
        rule.setContent {
            MTPlayerTheme {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().pageBackground().statusBarsPadding()) {
                        when (page) {
                            "search" -> OnlineSearchScreen(onBack = {}, onOpenTitle = {}, onOpenExtensions = {})
                            "title" -> OnlineDetailScreen(api = "Internet Archive", url = url, onBack = {}, onOpenTitle = {}, onOpenExtensions = {})
                            else -> ExtensionsScreen(onBack = {}, onOpenMarket = {})
                        }
                }
            }
        }
        waitFor { has("online_search_input") }
        rule.onNodeWithTag("online_search_input").performTextInput("Big Buck Bunny")
        rule.onNodeWithTag("online_search_input").performImeAction()
        waitFor { has("online_search_results") }
        pause(6_000) // posters
        shot("real_search_big_buck_bunny")

        url = bigBuckBunny().url
        page = "title"
        waitFor { has("online_detail_title") || has("online_detail_error") }
        pause(6_000)
        shot("real_title_big_buck_bunny")

        page = "extensions"
        pause(4_000)
        shot("real_extensions")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-land-night-xxhdpi")
    fun playerWithCaptions() {
        val frame = System.getenv("YUMEIRO_BBB_FRAME")
        assumeTrue("set YUMEIRO_BBB_FRAME to a Big Buck Bunny frame", frame != null && File(frame).exists())
        val video = VideoItem(
            id = "bbb", title = "Big Buck Bunny", durationMs = 596_000L,
            uri = "https://archive.org/download/BigBuckBunny_124/Content/big_buck_bunny_720p_surround.mp4",
            width = 1280, height = 720, resolution = "720p", folderName = "Internet Archive", playbackPositionMs = 65_000L,
        )
        rule.setContent { MTPlayerTheme { VideoPlayerModal(video = video, onClose = {}, onProgressUpdate = { _, _, _ -> }) } }
        waitFor { has("video_player_root") }
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread {
            val pv = find(rule.activity.window.decorView) ?: error("no PlayerView")
            val content = pv.findViewById<androidx.media3.ui.AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
            content.setAspectRatio(16f / 9f)
            content.addView(android.widget.ImageView(rule.activity).apply {
                setImageBitmap(android.graphics.BitmapFactory.decodeFile(frame))
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            }, 1, android.widget.FrameLayout.LayoutParams(-1, -1))
            pv.subtitleView?.setCues(listOf(androidx.media3.common.text.Cue.Builder().setText("[birds singing]").build()))
        }
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        shot("real_player_captions")
    }

    private fun find(v: android.view.View): androidx.media3.ui.PlayerView? {
        if (v is androidx.media3.ui.PlayerView) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
        return null
    }
}
