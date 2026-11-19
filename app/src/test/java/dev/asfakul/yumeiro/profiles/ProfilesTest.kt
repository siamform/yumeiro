package dev.asfakul.yumeiro.profiles

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.online.OnlineBookmarks
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.ui.theme.AccentTone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * PR-1 (owner 2026-11-19): profiles with their own lock and their own data (history, favorites, adult
 * setting, private downloads). The default profile keeps the data from before profiles; others live in
 * filesDir/profiles/<id>. PINs are 4–8 digits, stored only as a salted hash, with a lockout after 5
 * wrong tries. Old code: no Profiles.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProfilesTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setUp() { Profiles.resetForTests(ctx); OnlineBookmarks.unloadForTests() }
    @After fun tearDown() { Profiles.resetForTests(ctx); OnlineBookmarks.unloadForTests() }

    @Test fun theDefaultProfileAlwaysExistsAndKeepsTheOldFiles() {
        assertEquals(Profiles.DEFAULT, Profiles.currentId(ctx))
        assertEquals(1, Profiles.all(ctx).size)
        assertFalse("the default profile can not be deleted", Profiles.remove(ctx, Profiles.DEFAULT))
        assertEquals(File(ctx.filesDir, "online_bookmarks.json"), Profiles.file(ctx, "online_bookmarks.json"))
        assertEquals("yt_history", Profiles.prefsName(ctx, "yt_history"))
    }

    @Test fun anotherProfileHasItsOwnFolderAndPrefs() {
        val kid = Profiles.add(ctx, "Kids", AccentTone.AO)
        assertNotEquals(Profiles.DEFAULT, kid.id)
        Profiles.select(ctx, kid.id)
        assertEquals(kid.id, Profiles.currentId(ctx))
        assertEquals(File(File(File(ctx.filesDir, "profiles"), kid.id), "online_bookmarks.json"), Profiles.file(ctx, "online_bookmarks.json"))
        assertEquals("yt_history@p${kid.id}", Profiles.prefsName(ctx, "yt_history"))
        assertEquals("Kids", Profiles.current(ctx).name)
    }

    @Test fun favoritesOfOneProfileAreNotSeenByAnother() {
        val item = OnlineItem("Frieren", "https://example.org/frieren", "Test Source", null, type = "Anime")
        OnlineBookmarks.toggleFavorite(ctx, item)
        assertEquals(1, OnlineBookmarks.items.value.size)
        val other = Profiles.add(ctx, "Guest", AccentTone.SAKURA)
        Profiles.select(ctx, other.id)
        OnlineBookmarks.unloadForTests(); OnlineBookmarks.ensureLoaded(ctx)
        assertTrue("guest sees the default profile's favorites: ${OnlineBookmarks.items.value}", OnlineBookmarks.items.value.isEmpty())
        Profiles.select(ctx, Profiles.DEFAULT)
        OnlineBookmarks.unloadForTests(); OnlineBookmarks.ensureLoaded(ctx)
        assertEquals(1, OnlineBookmarks.items.value.size)
    }

    @Test fun pinsAreDigitsHashedAndCheckedWithALockout() {
        val p = Profiles.add(ctx, "Me", AccentTone.FUJI)
        assertFalse(Profiles.setPin(ctx, p.id, "12a4"))
        assertFalse(Profiles.setPin(ctx, p.id, "123"))
        assertFalse(Profiles.setPin(ctx, p.id, "123456789"))
        assertTrue(Profiles.setPin(ctx, p.id, "0420"))
        assertTrue(Profiles.find(ctx, p.id)!!.locked)
        val stored = ctx.getSharedPreferences(Profiles.PREFS, Context.MODE_PRIVATE).all.values.joinToString()
        assertFalse("the PIN itself is stored: $stored", stored.contains("0420"))
        val t = 1_000_000L
        assertEquals(Profiles.PinCheck.OK, Profiles.checkPin(ctx, p.id, "0420", t))
        repeat(5) { assertEquals(Profiles.PinCheck.WRONG, Profiles.checkPin(ctx, p.id, "1111", t)) }
        assertEquals("right PIN refused during the lockout", Profiles.PinCheck.LOCKED_OUT, Profiles.checkPin(ctx, p.id, "0420", t + 1_000))
        assertEquals(Profiles.PinCheck.OK, Profiles.checkPin(ctx, p.id, "0420", t + Profiles.LOCKOUT_MS + 1))
        assertTrue(Profiles.setPin(ctx, p.id, null))
        assertFalse(Profiles.find(ctx, p.id)!!.locked)
        assertEquals(Profiles.PinCheck.NO_PIN, Profiles.checkPin(ctx, p.id, "", t))
    }

    @Test fun removingAProfileDeletesItsDataAndFallsBackToDefault() {
        val p = Profiles.add(ctx, "Old", AccentTone.MATCHA)
        Profiles.select(ctx, p.id)
        val f = Profiles.file(ctx, "online_history.json").apply { parentFile?.mkdirs(); writeText("{}") }
        assertTrue(Profiles.remove(ctx, p.id))
        assertFalse("the removed profile's files are kept", f.exists())
        assertEquals(Profiles.DEFAULT, Profiles.currentId(ctx))
        assertEquals(1, Profiles.all(ctx).size)
    }

    @Test fun profilesSurviveARestart() {
        val p = Profiles.add(ctx, "Living room", AccentTone.AO)
        Profiles.setPin(ctx, p.id, "2468")
        Profiles.select(ctx, p.id)
        Profiles.unloadForTests()
        assertEquals(p.id, Profiles.currentId(ctx))
        assertEquals("Living room", Profiles.find(ctx, p.id)?.name)
        assertEquals(AccentTone.AO, Profiles.find(ctx, p.id)?.tone)
        assertEquals(Profiles.PinCheck.OK, Profiles.checkPin(ctx, p.id, "2468", 5L))
    }
}
