package dev.asfakul.yumeiro.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** G12 X8: clickable cards carry their own spoken name (device proof: AccessibilityDeviceTest). */
class A11yLabelTest {
    @Test fun joinsNonBlankParts() {
        assertEquals("Music · last played, Nothing yet, Play a song to see it here",
            a11yLabelText("Music · last played", "Nothing yet", "Play a song to see it here"))
        assertEquals("Title", a11yLabelText("Title", null, "  "))
        assertEquals("", a11yLabelText(null, ""))
    }

    @Test fun homeContinueCardIsLabelled() {
        val src = File("src/main/java/dev/asfakul/yumeiro/ui/screens/home/HomeScreen.kt").readText()
        val card = src.substringAfter("private fun ContinueCard(").substringBefore("private fun EmptyArt")
        assertTrue("ContinueCard's clickable must carry a11yLabel", Regex("""clickable\(onClick = onClick\)\.a11yLabel\(""").containsMatchIn(card))
    }
}
