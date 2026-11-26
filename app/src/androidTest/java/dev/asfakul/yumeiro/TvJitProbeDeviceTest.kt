/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TV-API24-JIT diagnosis marker: naming this class in the device-check filter makes
 * scripts/device-check.sh turn on ART's compiler log on Android 7.0 (API 24) before the other
 * classes run (MockupRealAppShotsDeviceTest reproduces the crash). It only records the setting.
 */
@RunWith(AndroidJUnit4::class)
class TvJitProbeDeviceTest {
    @Test fun recordsCompilerLogSetting() {
        Log.i("YuJitProbe", "extra-opts=" + FirstRun.shell("getprop dalvik.vm.extra-opts").trim() + " sdk=" + android.os.Build.VERSION.SDK_INT)
    }
}
