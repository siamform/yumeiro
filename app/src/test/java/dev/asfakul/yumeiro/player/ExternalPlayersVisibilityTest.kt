/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * F-07-VIS: [ExternalPlayers.installed] asks PackageManager for each player app. From Android 11 (API 30)
 * an app sees another app only if its manifest declares it in `<queries>` (package visibility); without
 * that, VLC / mpv / MX / Just Player are invisible, the "Other player app" picker offers only
 * "Choose each time" and a saved choice is dropped. Every app in the table must be declared.
 */
class ExternalPlayersVisibilityTest {
    @Test fun everyPlayerPackageIsDeclaredInQueries() {
        val f = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.exists() }
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(f)
        val queries = doc.getElementsByTagName("queries")
        val declared = HashSet<String>()
        for (i in 0 until queries.length) {
            val pk = (queries.item(i) as org.w3c.dom.Element).getElementsByTagName("package")
            for (j in 0 until pk.length) declared += (pk.item(j) as org.w3c.dom.Element).getAttributeNS("http://schemas.android.com/apk/res/android", "name")
        }
        val missing = ExternalPlayers.App.entries.map { it.pkg }.filter { it !in declared }
        assertTrue("player apps not in <queries> (invisible on Android 11+): $missing", missing.isEmpty())
    }
}
