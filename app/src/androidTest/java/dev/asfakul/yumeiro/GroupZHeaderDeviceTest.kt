package dev.asfakul.yumeiro

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Z-HEADER: the Extensions page header (seven icons) squeezed its title to one letter on a phone. Every sub-page
 * header must keep a readable title, however many icons it has, and still show every icon.
 */
@RunWith(AndroidJUnit4::class)
class GroupZHeaderDeviceTest {
    @get:Rule val rule = createComposeRule()

    @Test fun a_manyIconsKeepTheTitleReadable() {
        rule.setContent {
            MTPlayerTheme {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(411.dp)) {
                    SubPageHeader(title = "Extensions", onBack = {}, subtitle = "5 installed · 5 active", handleBack = false) {
                        repeat(7) { i ->
                            IconButton(onClick = {}, modifier = androidx.compose.ui.Modifier.testTag("zh_icon_$i")) { Icon(Icons.Default.Add, "icon $i") }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val title = rule.onNodeWithTag("subpage_title", true).getBoundsInRoot()
        val w = title.right - title.left
        android.util.Log.i("YuZHeader", "ZH_RESULT title width ${w.value} dp")
        assertTrue("title is ${w.value} dp wide, too narrow to read", w >= 100.dp)
        repeat(7) { i ->
            val b = rule.onNodeWithTag("zh_icon_$i", true).getBoundsInRoot()
            assertTrue("icon $i is squeezed or off screen: $b", b.right - b.left >= 40.dp && b.right <= 411.dp)
        }
    }
}
