package dev.asfakul.yumeiro.ui.watch

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import dev.asfakul.yumeiro.ui.screens.video.AudioTrackDialog
import dev.asfakul.yumeiro.ui.screens.video.AudioTrackInfo
import dev.asfakul.yumeiro.ui.screens.watch.WatchServer
import dev.asfakul.yumeiro.ui.screens.watch.WatchServerBox
import dev.asfakul.yumeiro.ui.screens.watch.WatchServerRow
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class SourcesRedesignUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun watchSourcesShowFactsFoldOthersAndPage() {
        val sub = WatchServerRow("Sub", Icons.Default.Mic, (0 until 25).map { WatchServer("Server $it", false, "srv_sub", detail = "1080p · HLS · 1.2 GB") {} })
        val dub = WatchServerRow("Dub · English", Icons.Default.Mic, (0 until 3).map { WatchServer("Dub $it", it == 1, "srv_dub", detail = "720p · MP4") {} })
        rule.setContent { MTPlayerTheme { Column(Modifier.verticalScroll(rememberScrollState())) { WatchServerBox("Episode 1", "hint", listOf(sub, dub)) } } }
        // The Dub group holds the playing source: it's open, Sub is folded.
        rule.onAllNodesWithTag("srv_dub").assertCountEquals(3)
        rule.onAllNodesWithTag("srv_sub").assertCountEquals(0)
        rule.onAllNodesWithTag("srv_dub_detail", useUnmergedTree = true).assertCountEquals(3)
        rule.onNodeWithText("Sub").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithTag("srv_sub").assertCountEquals(10)
        rule.onNodeWithText("Show 10 more of 15").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithTag("srv_sub").assertCountEquals(20)
        rule.onNodeWithText("Show 5 more", useUnmergedTree = true).assertExists()
    }

    @Test fun audioTracksGroupByLanguage() {
        val langs = listOf("en", "en", "hi", "hi", "ja", "ja", "ja")
        val tracks = langs.mapIndexed { i, l -> AudioTrackInfo(0, i, l, null, 2, 48000, isSelected = i == 4) }
        rule.setContent { MTPlayerTheme { AudioTrackDialog(tracks, { _, _ -> }, {}) } }
        rule.onAllNodesWithTag("audio_track_group").assertCountEquals(3)
        // Only Japanese (playing) is open.
        rule.onNodeWithTag("audio_track_item_4").assertIsDisplayed()
        rule.onAllNodesWithTag("audio_track_item_0").assertCountEquals(0)
    }
}
