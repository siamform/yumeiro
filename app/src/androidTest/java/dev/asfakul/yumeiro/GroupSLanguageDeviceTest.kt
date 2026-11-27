/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.os.Build
import android.os.LocaleList
import android.util.Log
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.ui.screens.settings.AppearanceSettingsCard
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * F2 (beta chapter, Group S): the app language, picked in the real Settings › Appearance › Language dialog, then
 * checked in the real app window (MainActivity): translated text, plural forms, right-to-left layout for Arabic,
 * the pick surviving a recreate, and going back to the device language. Uses only UI tags, the preference file and
 * resource names, so it also runs (and fails) on the code before F2. Log tag YuGroupS.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GroupSLanguageDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuGroupS", s)

    // Android 13+ asks for the notification (and media) permission when MainActivity opens; that system dialog
    // pauses the app and the recreate below needs it resumed. Granted up front like the other MainActivity tests.
    // (run 45940770266: READ_MEDIA_AUDIO was still asked, so the shared list FirstRun.grant() is used.)
    @Before fun grant() = FirstRun.grant()
    private fun savedTag() = ctx.getSharedPreferences("mt_language", Context.MODE_PRIVATE).getString("tag", null)

    private fun pick(optionTag: String, wantSaved: String) {
        rule.setContent {
            CompositionLocalProvider(LocalTvMode provides Edition.isTv) {
                MTPlayerTheme { FirstRun.AppBackground { Column(Modifier.verticalScroll(rememberScrollState())) { AppearanceSettingsCard() } } }
            }
        }
        val found = runCatching {
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("settings_language", true).fetchSemanticsNodes().isNotEmpty() }
        }.isSuccess
        log("GROUPS pick $optionTag row=$found")
        assertTrue("F2: Settings › Appearance has no Language row", found)
        rule.onNodeWithTag("settings_language", true).performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag(optionTag, true).fetchSemanticsNodes().isNotEmpty() }
        FirstRun.shot("groups_f2_dialog_${optionTag.removePrefix("language_")}")
        rule.onNodeWithTag(optionTag, true).performClick()
        // The pick is stored before the window redraws in the new language.
        val t0 = System.currentTimeMillis()
        while (savedTag() != wantSaved && System.currentTimeMillis() - t0 < 5_000) Thread.sleep(50)
        log("GROUPS pick $optionTag saved=${savedTag()}")
        assertEquals("F2: the pick is kept", wantSaved, savedTag())
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private class Seen(val lang: String, val close: String, val rtl: Boolean, val plural2: String)

    private fun inApp(): Seen {
        var seen: Seen? = null
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            val read = {
                sc.onActivity { a ->
                    val id = a.resources.getIdentifier("close", "string", a.packageName)
                    val pl = a.resources.getIdentifier("vault_sources", "plurals", a.packageName)
                    seen = Seen(
                        a.resources.configuration.locales[0].language,
                        if (id == 0) "<no string resource>" else a.getString(id),
                        a.window.decorView.layoutDirection == View.LAYOUT_DIRECTION_RTL,
                        if (pl == 0) "<no plural>" else a.resources.getQuantityString(pl, 2, 2),
                    )
                }
            }
            read()
            val first = seen!!
            sc.recreate()
            read()
            val again = seen!!
            log("GROUPS app lang=${first.lang}/${again.lang} close=${first.close}/${again.close} rtl=${first.rtl}/${again.rtl} plural2=${first.plural2}")
            assertEquals("F2: the language survives a recreate", first.close, again.close)
            assertEquals("F2: layout direction survives a recreate", first.rtl, again.rtl)
        }
        FirstRun.shot("groups_f2_app_${seen!!.lang}")
        return seen!!
    }

    @Test fun a_bangla() {
        pick("language_bn", "bn")
        val s = inApp()
        val ok = s.lang == "bn" && s.close == "বন্ধ করুন" && !s.rtl && s.plural2.endsWith("টি সোর্স")
        log("GROUPS_RESULT a ${if (ok) "PASS" else "FAIL"}")
        assertEquals("F2: Bangla locale", "bn", s.lang)
        assertEquals("F2: Bangla text", "বন্ধ করুন", s.close)
        assertTrue("F2: Bangla is left-to-right", !s.rtl)
        // Bangla digits are allowed (২ or 2): only the counted form is checked.
        assertTrue("F2: Bangla plural (${s.plural2})", s.plural2.endsWith("টি সোর্স"))
    }

    @Test fun b_arabicRightToLeft() {
        pick("language_ar", "ar")
        val s = inApp()
        val ok = s.lang == "ar" && s.close == "إغلاق" && s.rtl && s.plural2 == "مصدران"
        log("GROUPS_RESULT b ${if (ok) "PASS" else "FAIL"}")
        assertEquals("F2: Arabic locale", "ar", s.lang)
        assertEquals("F2: Arabic text", "إغلاق", s.close)
        assertTrue("F2: Arabic lays out right-to-left", s.rtl)
        assertEquals("F2: Arabic dual plural", "مصدران", s.plural2)
    }

    @Test fun c_backToDeviceLanguage() {
        pick("language_device", "")
        val s = inApp()
        val dev = android.content.res.Resources.getSystem().configuration.locales[0].language
        log("GROUPS c device=$dev app=${s.lang} close=${s.close}")
        val ok = s.lang == dev && (dev != "en" || s.close == "Close") && !s.rtl
        log("GROUPS_RESULT c ${if (ok) "PASS" else "FAIL"}")
        assertEquals("F2: follows the device language again", dev, s.lang)
        if (dev == "en") assertEquals("F2: English text again", "Close", s.close)
        assertTrue("F2: left-to-right again", !s.rtl)
        if (Build.VERSION.SDK_INT >= 33) {
            val sys = ctx.getSystemService(android.app.LocaleManager::class.java).applicationLocales
            assertEquals("F2: Android's per-app language is cleared too", LocaleList.getEmptyLocaleList(), sys)
        }
    }
}
