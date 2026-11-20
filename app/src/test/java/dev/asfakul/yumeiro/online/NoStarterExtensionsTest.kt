/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * Owner decision 2026-11-21 (closes B-26): a first run installs no third-party extension. Before,
 * StarterExtensions installed the market's top Movies / Anime / Sports picks, one of which ran a
 * WebView on the main thread and showed a donation dialog.
 */
class NoStarterExtensionsTest {
    @Test fun starterInstallerIsGone() {
        val loaded = runCatching { Class.forName("dev.asfakul.yumeiro.online.StarterExtensions") }.isSuccess
        assertFalse("StarterExtensions must not exist", loaded)
    }

    @Test fun firstRunDoesNotInstallExtensions() {
        val app = listOf("src/main/java/dev/asfakul/yumeiro/ui/screens/MTPlayerApp.kt", "app/src/main/java/dev/asfakul/yumeiro/ui/screens/MTPlayerApp.kt")
            .map(::File).first { it.exists() }.readText()
        assertFalse(app.contains("StarterExtensions"))
        assertFalse(app.contains("Ready to watch:"))
    }
}
