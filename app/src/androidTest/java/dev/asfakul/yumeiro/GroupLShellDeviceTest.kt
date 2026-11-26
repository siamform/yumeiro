/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Group L (Beta chapter) in the real app on phones and TV.
 * B5: no support / star / follow card at launch, even for a long-time user who is due one
 * (the old rules showed one 20 s after every first open of the day).
 * B2: YouTube and YouTube Music chip bars slide away while the feed scrolls down and come back on
 * the first scroll up (touch screens; TV keeps them while focus moves, so B2 is phone only).
 */
@RunWith(AndroidJUnit4::class)
class GroupLShellDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context get() = inst.targetContext
    private val device = UiDevice.getInstance(inst)
    private fun log(s: String) = Log.i("YuGroupL", s)
    private val tv get() = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    private val reminderCard = SemanticsMatcher("a reminder card") { n ->
        val t = n.config.getOrNull(SemanticsProperties.TestTag) ?: return@SemanticsMatcher false
        t.startsWith("reminder_")
    }

    @Test fun b5_noReminderCardAtLaunchEvenWhenDue() {
        dev.asfakul.yumeiro.notify.NotifySettings.init(ctx)
        dev.asfakul.yumeiro.notify.NotifySettings.set(ctx, dev.asfakul.yumeiro.notify.NotifySettings.Key.REMINDERS, true)
        // A long-time user with nothing shown for a year: every kind is due by its interval.
        ctx.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit().clear()
            .putLong("installed_at", System.currentTimeMillis() - 400L * 24 * 3_600_000).apply()
        FirstRun.toTabs(rule)
        val until = SystemClock.uptimeMillis() + 45_000
        var seen = false
        while (SystemClock.uptimeMillis() < until && !seen) {
            seen = rule.onAllNodes(reminderCard, true).fetchSemanticsNodes().isNotEmpty()
            SystemClock.sleep(1_000)
        }
        FirstRun.shot("groupl_b5_after_45s")
        log("B5 reminder card in the first 45 s: $seen")
        assertTrue("B5: a reminder card opened in the first 45 s of a visit", !seen)
    }

    private fun top(tag: String): Float? = runCatching {
        rule.onNodeWithTag(tag, true).getUnclippedBoundsInRoot().top.value
    }.getOrNull()

    private fun bottom(tag: String): Float? = runCatching {
        rule.onNodeWithTag(tag, true).getUnclippedBoundsInRoot().bottom.value
    }.getOrNull()

    private fun swipe(up: Boolean) {
        val w = device.displayWidth; val h = device.displayHeight
        if (up) device.swipe(w / 2, (h * 0.72).toInt(), w / 2, (h * 0.38).toInt(), 24)
        else device.swipe(w / 2, (h * 0.40).toInt(), w / 2, (h * 0.58).toInt(), 18)
        SystemClock.sleep(900); rule.waitForIdle()
    }

    private fun chipBar(card: String, chips: String) {
        assumeFalse("B2 is a touch-scroll behaviour; TV keeps the bar while focus moves", tv)
        FirstRun.toTabs(rule)
        rule.onNodeWithTag("nav_tab_home", true).performClick(); rule.waitForIdle()
        rule.waitUntil(30_000) { rule.onAllNodesWithTag(card, true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithTag(card, true)[0].performClick()
        rule.waitUntil(120_000) { rule.onAllNodesWithTag(chips, true).fetchSemanticsNodes().isNotEmpty() }
        SystemClock.sleep(4_000); rule.waitForIdle()
        val t0 = top(chips)!!
        val b0 = bottom(chips)!!
        FirstRun.shot("groupl_b2_${chips}_0_start")
        swipe(up = true); swipe(up = true)
        FirstRun.shot("groupl_b2_${chips}_1_scrolled")
        val b1 = bottom(chips)
        log("B2 $chips start top=$t0 bottom=$b0 after scroll bottom=$b1")
        assertTrue("B2 $chips: the bar stayed on screen while the feed scrolled down (bottom $b0 -> $b1)", b1 == null || b1 <= t0 + 1f)
        swipe(up = false)
        FirstRun.shot("groupl_b2_${chips}_2_back")
        val t2 = top(chips)
        log("B2 $chips after scroll up top=$t2")
        assertTrue("B2 $chips: the bar did not come back on scroll up (top $t2, was $t0)", t2 != null && kotlin.math.abs(t2 - t0) <= 2f)
    }

    @Test fun b2_youTubeChipsHideOnScroll() = chipBar("quick_card_youtube", "youtube_chips")
    @Test fun b2_youTubeMusicChipsHideOnScroll() = chipBar("quick_card_ytmusic", "ytmusic_chips")
}

/**
 * B1 (Beta chapter): the splash logo showed again after the splash. Probe run 45842083286 (api36):
 * reopening from the launcher while the process was alive replayed the whole in-app splash 2.1 s
 * after the system splash had gone. Opened the way a launcher does, with animations on.
 */
@RunWith(AndroidJUnit4::class)
class GroupLSplashDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(inst)
    private val pkg = inst.targetContext.packageName
    private fun log(s: String) = Log.i("YuGroupL", s)

    private fun save(name: String, b: Bitmap) = runCatching {
        val dir = File(inst.targetContext.getExternalFilesDir(null), "shots").apply { mkdirs() }
        File(dir, "groupl_$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 70, it) }
    }

    private fun launch(clear: Boolean) = inst.targetContext.startActivity(
        inst.targetContext.packageManager.getLaunchIntentForPackage(pkg)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or (if (clear) Intent.FLAG_ACTIVITY_CLEAR_TASK else 0)),
    )

    private fun toHome() {
        FirstRun.grant()
        runCatching { dev.asfakul.yumeiro.notify.ReliabilityGuide.markPrompted(inst.targetContext) }
        launch(clear = true)
        device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 60_000)
        val end = SystemClock.uptimeMillis() + 120_000
        while (SystemClock.uptimeMillis() < end && device.findObject(By.desc("Open Settings")) == null) {
            val o = listOf("Skip", "Not now", "Later", "Close", "Allow", "OK").firstNotNullOfOrNull { device.findObject(By.text(it)) }
            if (o != null) runCatching { o.click() } else device.pressBack().also { SystemClock.sleep(400) }
            SystemClock.sleep(1_500)
            if (device.currentPackageName != pkg) launch(clear = true)
        }
    }

    /** True when the in-app splash ("Yumeiro is opening") shows at any point in 5 s. */
    private fun splashSeen(label: String): Boolean {
        val t0 = SystemClock.uptimeMillis()
        var i = 0
        var seen = false
        while (SystemClock.uptimeMillis() - t0 < 5_000) {
            val at = SystemClock.uptimeMillis() - t0
            val s = device.findObject(By.desc("Yumeiro is opening")) != null
            if (s || i % 6 == 0) inst.uiAutomation.takeScreenshot()?.let { save("b1_${label}_${"%02d".format(i)}_${at}ms", it) }
            if (s) log("B1 $label in-app splash at ${at}ms")
            seen = seen || s
            i++
            SystemClock.sleep(150)
        }
        return seen
    }

    @Test fun b1_reopeningDoesNotReplayTheSplash() {
        val scales = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
        scales.forEach { FirstRun.shell("settings put global $it 1") }
        try {
            toHome()
            device.pressHome(); SystemClock.sleep(1_500)
            launch(clear = true)
            val relaunch = splashSeen("relaunch")
            device.pressHome(); SystemClock.sleep(1_500)
            launch(clear = false)
            val resume = splashSeen("resume")
            log("B1 splash relaunch=$relaunch resume=$resume")
            assertTrue("B1: the logo splash played again on reopening (relaunch=$relaunch resume=$resume)", !relaunch && !resume)
        } finally {
            scales.forEach { FirstRun.shell("settings put global $it 0") }
        }
    }
}

/**
 * B6 / F11 (Beta chapter): the selected tab's icon and label sit together inside one capsule, and
 * the bar is shorter (68 dp before). Measured on the device's own density and font scale.
 */
@RunWith(AndroidJUnit4::class)
class GroupLNavDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun b6_capsuleHoldsIconAndLabel() {
        val items = dev.asfakul.yumeiro.ui.navigation.bottomNavItems
        rule.setContent {
            dev.asfakul.yumeiro.ui.theme.MTPlayerTheme(dev.asfakul.yumeiro.ui.theme.Palettes.Dark) {
                dev.asfakul.yumeiro.ui.components.MTBottomBar(currentRoute = items[1].route, onNavigateToRoute = {})
            }
        }
        rule.waitForIdle(); SystemClock.sleep(800); rule.waitForIdle()
        val cap = rule.onNodeWithTag("nav_capsule", true).getUnclippedBoundsInRoot()
        val icon = rule.onNodeWithTag("nav_icon_filled", true).getUnclippedBoundsInRoot()
        val label = rule.onNodeWithTag("nav_label_${items[1].testTag}", true).getUnclippedBoundsInRoot()
        val tab = rule.onNodeWithTag(items[1].testTag, true).getUnclippedBoundsInRoot()
        Log.i("YuGroupL", "B6 capsule=$cap icon=$icon label=$label tab=$tab")
        FirstRun.shot("groupl_b6_nav")
        fun inside(o: androidx.compose.ui.unit.DpRect) =
            o.top >= cap.top - 0.5.dp && o.bottom <= cap.bottom + 0.5.dp && o.left >= cap.left - 0.5.dp && o.right <= cap.right + 0.5.dp
        assertTrue("B6: icon outside the capsule (capsule $cap, icon $icon)", inside(icon))
        assertTrue("B6: label outside the capsule (capsule $cap, label $label)", inside(label))
        assertTrue("F11: tab row ${tab.bottom - tab.top} tall, want at most 62 dp", tab.bottom - tab.top <= 62.dp)
    }
}
