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
        Journeys.browseTabs(device)
        Journeys.restoreOpenTab(device) // X2-1: the H8 restore path (process death, same tab)
    }
}
