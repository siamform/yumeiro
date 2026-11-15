package dev.asfakul.yumeiro.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** G12 X6-2: StrictMode breadcrumbs name the real caller and never blame the guarded main looper. */
class StrictCrumbTest {
    private fun f(c: String, m: String, l: Int) = StackTraceElement(c, m, "${c.substringAfterLast('.')}.kt", l)

    // Shape of the DiskReadViolation in device run 43763558754 (api33-lowram).
    private val trace = arrayOf(
        f("android.os.StrictMode\$AndroidBlockGuardPolicy", "onReadFromDisk", 1658),
        f("android.app.ContextImpl", "getSharedPreferences", 553),
        f("dev.asfakul.yumeiro.player.SubtitleStyleSettings", "init", 62),
        f("dev.asfakul.yumeiro.ui.screens.video.VideoPlayerModalKt", "VideoPlayerModalContent", 280),
        f("android.os.Looper", "loop", 288),
        f("dev.asfakul.yumeiro.online.ExtensionGuard", "install\$lambda\$1", 83),
        f("android.app.ActivityThread", "main", 7924),
    )

    @Test fun crumbNamesTheCallerNotTheLooper() {
        val c = DevStrictMode.crumb("DiskReadViolation", trace)
        assertTrue(c, c.startsWith("strict DiskReadViolation SubtitleStyleSettings.init:62"))
        assertTrue(c, "VideoPlayerModalKt.VideoPlayerModalContent:280" in c)
        assertFalse(c, "ExtensionGuard" in c)
    }

    @Test fun frameworkOnlyStackSaysSo() {
        val c = DevStrictMode.crumb("DiskWriteViolation", arrayOf(trace[0], trace[4], trace[5], trace[6]))
        assertTrue(c, c.endsWith("(framework only)"))
    }
}
