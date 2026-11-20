package dev.asfakul.yumeiro.profiles

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.ui.theme.AccentTone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F-19: security questions replace the phone's screen lock for "Forgot PIN" (a child who knows the
 * phone's lock could otherwise remove a parent's PIN). Old code: setRecovery / recoverWithAnswers do
 * not exist (compile failure).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProfileRecoveryTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val qs = listOf("First school?", "Mother's city?", "First pet?")
    private val ans = listOf("Ideal School", "Dhaka", "Mishti")

    @Before fun setUp() { Profiles.resetForTests(ctx) }
    @After fun tearDown() { Profiles.resetForTests(ctx) }

    private fun parent() = Profiles.add(ctx, "Parent", AccentTone.AO).also {
        assertTrue(Profiles.setPin(ctx, it.id, "4321"))
        assertTrue(Profiles.setRecovery(ctx, it.id, qs, ans))
    }

    @Test fun rightAnswersRemoveThePinAndKeepTheProfile() {
        val p = parent()
        assertTrue(Profiles.find(ctx, p.id)!!.hasRecovery)
        assertEquals(Profiles.PinCheck.OK, Profiles.recoverWithAnswers(ctx, p.id, listOf("  ideal   SCHOOL ", "dhaka", "MISHTI")))
        val after = Profiles.find(ctx, p.id)!!
        assertFalse(after.hasPin)
        assertEquals(qs, after.recoveryQuestions) // the questions stay for next time
    }

    @Test fun oneWrongAnswerKeepsThePinAndCountsAsATry() {
        val p = parent()
        val t = 5_000_000L
        repeat(5) { assertEquals(Profiles.PinCheck.WRONG, Profiles.recoverWithAnswers(ctx, p.id, listOf("Ideal School", "Dhaka", "Tom"), t)) }
        assertTrue(Profiles.find(ctx, p.id)!!.hasPin)
        // the same lockout as wrong PINs: even the right answers wait
        assertEquals(Profiles.PinCheck.LOCKED_OUT, Profiles.recoverWithAnswers(ctx, p.id, ans, t + 1))
        assertEquals(Profiles.PinCheck.LOCKED_OUT, Profiles.checkPin(ctx, p.id, "4321", t + 1))
    }

    @Test fun answersAreNotStoredInPlainText() {
        parent()
        val raw = ctx.getSharedPreferences(Profiles.PREFS, Context.MODE_PRIVATE).getString("list", "")!!
        ans.forEach { assertFalse(raw.lowercase().contains(it.lowercase())) }
    }

    @Test fun questionsSurviveAReloadAndCanBeRemoved() {
        val p = parent()
        Profiles.unloadForTests()
        assertTrue(Profiles.find(ctx, p.id)!!.hasRecovery)
        assertTrue(Profiles.setRecovery(ctx, p.id, emptyList(), emptyList()))
        assertFalse(Profiles.find(ctx, p.id)!!.hasRecovery)
        assertEquals(Profiles.PinCheck.NO_PIN, Profiles.recoverWithAnswers(ctx, p.id, ans))
    }

    @Test fun badSetsAreRefused() {
        val p = Profiles.add(ctx, "Kid", AccentTone.AO)
        assertFalse(Profiles.setRecovery(ctx, p.id, qs.take(2), ans.take(2)))
        assertFalse(Profiles.setRecovery(ctx, p.id, listOf("Same?", "same?", "Other?"), ans))
        assertFalse(Profiles.setRecovery(ctx, p.id, qs, listOf("a", "Dhaka", "Mishti")))
        assertNull(Profiles.find(ctx, p.id)!!.recoveryHash)
    }

    @Test fun aRestoredBackupCanNotBringOtherAnswers() {
        val p = parent()
        val before = Profiles.lockSnapshot(ctx)
        // the backup's list has answers someone else knows
        assertTrue(Profiles.setRecovery(ctx, p.id, qs, listOf("x1", "x2", "x3")))
        Profiles.unloadForTests()
        Profiles.keepLocksAfterRestore(ctx, before)
        assertEquals(Profiles.PinCheck.WRONG, Profiles.recoverWithAnswers(ctx, p.id, listOf("x1", "x2", "x3")))
        assertEquals(Profiles.PinCheck.OK, Profiles.recoverWithAnswers(ctx, p.id, ans))
    }
}
