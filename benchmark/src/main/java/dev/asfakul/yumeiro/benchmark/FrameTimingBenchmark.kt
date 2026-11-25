/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G12 ST-7: frame timing while scrolling Home and swiping tabs, on the R8 preview build (not
 * debuggable, so ART uses the baseline profile; the device-check stress runs are debug builds and
 * cannot show the profile's effect). Without AOT versus with the baseline profile, same image, same
 * run. Results: `frameDurationCpuMs` and `frameOverrunMs` percentiles in `*-benchmarkData.json`.
 */
@RunWith(AndroidJUnit4::class)
class FrameTimingBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun scrollNoCompilation() = scroll(CompilationMode.None())

    @Test fun scrollBaselineProfile() = scroll(CompilationMode.Partial(BaselineProfileMode.Require))

    /** NAV-3: open and close three Settings sub-pages (the NAV-2 device test's pages), cold page each iteration. */
    @Test fun settingsPagesNoCompilation() = settings(CompilationMode.None())

    @Test fun settingsPagesBaselineProfile() = settings(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun settings(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        // No startupMode: COLD kills the process after setupBlock (run 45337558852 measured the launcher). A fresh
        // process per iteration instead: kill here, start, open Settings home, then measure only the sub-pages.
        startupMode = null,
        iterations = 5,
        setupBlock = { Journeys.grantPermissions(device); killProcess(); startActivityAndWait(); Journeys.firstFrameSettles(device); Journeys.passWelcome(device); Journeys.openSettings(device) },
    ) {
        Journeys.settingsPages(device, listOf("Look & feel", "Video quality & playback", "Profiles"))
        Journeys.snapshot(device, "settings_after")
    }

    /**
     * PERF-A1: frame times on the software-rendered CI emulators move by up to 40 % between runs (v2.2.0 measured
     * 293 ms and 430 ms P50 on the same image), so they cannot hold a budget. The amount of work can: how many
     * times Compose recomposed and laid out during the journey. `.github/scripts/jank_budget.py` fails the run
     * when these counts go over the budget.
     */
    @OptIn(ExperimentalMetricApi::class)
    private fun workMetrics() = listOf(
        TraceSectionMetric("Compose:recompose", TraceSectionMetric.Mode.Count),
        TraceSectionMetric("AndroidOwner:measureAndLayout", TraceSectionMetric.Mode.Count),
    )

    @OptIn(ExperimentalMetricApi::class)
    private fun scroll(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET,
        metrics = listOf(FrameTimingMetric()) + workMetrics(),
        compilationMode = mode,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { Journeys.grantPermissions(device); pressHome() },
    ) {
        startActivityAndWait()
        Journeys.scrollHomeAndSwipeTabs(device)
    }
}
