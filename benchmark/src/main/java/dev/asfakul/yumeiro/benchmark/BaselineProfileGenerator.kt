/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G12 X2: records the classes and methods used at start and while browsing the tabs. The
 * workflow uploads `*-baseline-prof.txt`; it is reviewed and committed as
 * `app/src/main/baseline-prof.txt`, which profileinstaller ships with the app.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun startupAndTabs() = rule.collect(packageName = TARGET, includeInStartupProfile = true) {
        Journeys.grantPermissions(device)
        pressHome()
        startActivityAndWait()
        // X2-1 (44128769774): restore last killed the process that had run Home and the tabs, and its
        // unsaved profile went with it (Home rules 684 → 99). Kill first, then browse in the final process.
        Journeys.firstFrameSettles(device)
        Journeys.passWelcome(device)
        Journeys.restoreOpenTab(device) // X2-1: the H8 restore path (process death, same tab)
        Journeys.browseTabs(device)
        // NAV-3: the Settings sub-pages, so their first open is compiled code, not the interpreter.
        Journeys.settingsPages(device)
    }
}
