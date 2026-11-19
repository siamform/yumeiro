package dev.asfakul.yumeiro.profiles

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.backup.Backup
import dev.asfakul.yumeiro.ui.theme.AccentTone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * PR-1 security review (ADR-041, "bounty" pass). Each test is a way round a profile lock found while
 * reviewing the first build against Netflix / Plex / Kodi / Files-Safe-folder behaviour:
 * - killing the app reset the 5-try lockout (counter only in memory),
 * - lockouts never grew, so a PIN could be walked in a few hours,
 * - restoring an old backup put back a profile list without the PIN,
 * - the one-time "restart into this profile" pass sat in a backed-up file,
 * - a backup made from another profile carried a locked profile's history,
 * - a child's profile could add profiles / turn on 18+ although the main profile is locked,
 * - a forgotten PIN had no way out.
 * Old code: none of these APIs exist (compile failure) and the lockout was in memory.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProfilesGuardTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setUp() { Profiles.resetForTests(ctx) }
    @After fun tearDown() { Profiles.resetForTests(ctx) }

    private fun locked(name: String = "Parent", pin: String = "4321") =
        Profiles.add(ctx, name, AccentTone.AO).also { assertTrue(Profiles.setPin(ctx, it.id, pin)) }

    @Test fun theLockoutSurvivesKillingTheApp() {
        val p = locked()
        val t = 2_000_000L
        repeat(5) { Profiles.checkPin(ctx, p.id, "0000", t) }
        Profiles.unloadForTests() // process death
        assertEquals(Profiles.PinCheck.LOCKED_OUT, Profiles.checkPin(ctx, p.id, "4321", t + 1_000))
    }

    @Test fun eachNewLockoutIsLonger() {
        val p = locked()
        val t = 3_000_000L
        repeat(5) { Profiles.checkPin(ctx, p.id, "0000", t) }
        val t2 = t + Profiles.LOCKOUT_MS + 1
        repeat(5) { Profiles.checkPin(ctx, p.id, "0000", t2) }
        assertEquals("the second lockout is no longer than the first", Profiles.PinCheck.LOCKED_OUT, Profiles.checkPin(ctx, p.id, "4321", t2 + Profiles.LOCKOUT_MS + 1))
        assertEquals(Profiles.PinCheck.OK, Profiles.checkPin(ctx, p.id, "4321", t2 + 2 * Profiles.LOCKOUT_MS + 1))
    }

    @Test fun aRestoredOldListCanNotRemoveALock() {
        val p = locked()
        val before = Profiles.lockSnapshot(ctx)
        // An old backup: the same profile, no PIN, no phone lock.
        ctx.getSharedPreferences(Profiles.PREFS, Context.MODE_PRIVATE).edit()
            .putString("list", """[{"id":"0","name":"Me","tone":"GOLD"},{"id":"${p.id}","name":"Parent","tone":"AO"}]""").commit()
        Profiles.keepLocksAfterRestore(ctx, before)
        Profiles.unloadForTests()
        assertTrue("restore removed the PIN", Profiles.find(ctx, p.id)!!.hasPin)
        assertEquals(Profiles.PinCheck.OK, Profiles.checkPin(ctx, p.id, "4321", 9L))
    }

    @Test fun theRestartPassWorksOnceAndIsNeverBackedUp() {
        val p = Profiles.add(ctx, "Kid", AccentTone.SAKURA)
        Profiles.select(ctx, p.id)
        Profiles.passRestart(ctx, p.id)
        assertTrue(Profiles.consumePass(ctx))
        assertFalse("the pass works twice", Profiles.consumePass(ctx))
        assertFalse("the pass file goes into backups", Backup.prefIncluded(Profiles.PASS_PREFS))
        assertFalse("the lockout file goes into backups", Backup.prefIncluded(Profiles.GUARD_PREFS))
        ctx.getSharedPreferences(Profiles.PREFS, Context.MODE_PRIVATE).all.keys.forEach { assertFalse(it, it.contains("pass")) }
    }

    @Test fun aBackupFromAnotherProfileLeavesALockedProfilesDataOut() {
        val kid = Profiles.add(ctx, "Kid", AccentTone.MATCHA)
        val parent = locked()
        // The parent's history (a locked non-default profile).
        Profiles.select(ctx, parent.id)
        Profiles.file(ctx, "online_history.json").writeText("[1]")
        ctx.getSharedPreferences(Profiles.prefsName(ctx, "yt_history"), Context.MODE_PRIVATE).edit().putString("h", "secret").commit()
        // The default profile is locked too, and has history.
        Profiles.setPin(ctx, Profiles.DEFAULT, "1111")
        File(ctx.filesDir, "online_history.json").writeText("[2]")
        Profiles.select(ctx, kid.id)
        Profiles.file(ctx, "online_history.json").writeText("[3]")
        val names = Backup.collect(ctx, Backup.Section.entries.toSet()).map { it.first }
        assertTrue("the kid's own history is missing: $names", "files/profiles/${kid.id}/online_history.json" in names)
        assertFalse("a locked profile's history is in the kid's backup", "files/profiles/${parent.id}/online_history.json" in names)
        assertFalse("the locked default profile's history is in the kid's backup", "files/online_history.json" in names)
        val prefs = Backup.prefsSnapshot(ctx, Backup.Section.entries.toSet())
        assertFalse("a locked profile's YouTube history is in the kid's backup", prefs.has("yt_history@p${parent.id}"))
        // From the parent's own profile everything of the parent goes in.
        Profiles.select(ctx, parent.id)
        val own = Backup.collect(ctx, Backup.Section.entries.toSet()).map { it.first }
        assertTrue("files/profiles/${parent.id}/online_history.json" in own)
    }

    @Test fun aChildsProfileNeedsTheMainLockToAddProfilesOrTurnOnAdult() {
        val kid = Profiles.add(ctx, "Kid", AccentTone.FUJI)
        assertFalse("no main lock yet", Profiles.needsMainUnlock(ctx))
        Profiles.setPin(ctx, Profiles.DEFAULT, "2580")
        assertFalse("the main profile itself is asked", Profiles.needsMainUnlock(ctx))
        Profiles.select(ctx, kid.id)
        assertTrue(Profiles.needsMainUnlock(ctx))
    }

    @Test fun aForgottenPinResetsTheProfileAndItsData() {
        val p = locked()
        Profiles.select(ctx, p.id)
        val f = Profiles.file(ctx, "online_history.json").apply { writeText("[]") }
        Profiles.select(ctx, Profiles.DEFAULT)
        assertTrue(Profiles.resetForgotten(ctx, p.id))
        assertFalse(f.exists())
        assertEquals(null, Profiles.find(ctx, p.id))
        // The default profile can not be deleted: its lock goes and its own lists with it.
        Profiles.setPin(ctx, Profiles.DEFAULT, "9999")
        val main = File(ctx.filesDir, "online_bookmarks.json").apply { writeText("{}") }
        assertTrue(Profiles.resetForgotten(ctx, Profiles.DEFAULT))
        assertFalse(Profiles.find(ctx, Profiles.DEFAULT)!!.locked)
        assertFalse("the main profile's lists were kept after a forgotten-PIN reset", main.exists())
    }

    @Test fun theHomeScreenWidgetHidesALockedProfile() {
        assertFalse(Profiles.hideFromWidget(ctx))
        Profiles.setPin(ctx, Profiles.DEFAULT, "1357")
        assertTrue(Profiles.hideFromWidget(ctx))
    }
}
