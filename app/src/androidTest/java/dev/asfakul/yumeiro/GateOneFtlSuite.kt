/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * GATE-1-FTL: the gate in one target for Firebase Test Lab (.github/workflows/ftl-gate.yml). The three
 * classes must run in this order, each in its own process (prepare writes the first run and the
 * GATE-1-AB flags; the walk starts the app fresh; the exit check reads the ANR / crash records of the
 * earlier processes). Test Lab runs it with the test orchestrator (one process per test) and
 * clearPackageData=false (data kept between them), like scripts/device-check.sh with REPEAT=1.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    GateOnePrepareDeviceTest::class,
    MainThreadStallRealClockDeviceTest::class,
    GateOneExitCheckDeviceTest::class,
)
class GateOneFtlSuite
