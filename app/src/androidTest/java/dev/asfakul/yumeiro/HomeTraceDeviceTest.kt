/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ST-18: the first-run part of the Home first-draw trace. `scripts/device-check.sh` runs
 * [a_prepare] (welcome guide answered, tab bar on screen, like a returning user), then cold-starts
 * the app itself under Perfetto three times and pulls the traces (stress/trace/home-N.pftrace)
 * with `am start -W` times and `dumpsys gfxinfo` frame stats. The test itself only prepares.
 */
@RunWith(AndroidJUnit4::class)
class HomeTraceDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun a_prepare() {
        FirstRun.toTabs(rule)
        // Let the first Home load (sections, caches) finish so the traced starts read warm caches,
        // as a returning user's start does.
        rule.waitUntil(30_000) { rule.onAllNodesWithTag("home_list", true).fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(8_000)
        Log.i("YuHomeTrace", "prepared: on the tabs, Home composed")
    }
}
