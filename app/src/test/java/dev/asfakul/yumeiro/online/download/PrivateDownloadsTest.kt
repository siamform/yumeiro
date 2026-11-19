package dev.asfakul.yumeiro.online.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.online.download.OnlineDownloads.Item
import dev.asfakul.yumeiro.online.download.OnlineDownloads.Status
import dev.asfakul.yumeiro.profiles.Profiles
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

/**
 * DL-H (owner 2026-11-19): a download can be hidden in a private vault. It leaves the normal download
 * list and the shared folder (moved into app-private storage that galleries and other apps can not
 * read), belongs to the profile that hid it, survives a restart, and comes back on "Unhide".
 * Old code: no PrivateDownloads and no hidden flag.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrivateDownloadsTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private fun item(id: String) = Item(
        id, "Clip $id", "Network", "u", null, null, emptyMap(), "", false, emptyMap(), "clip$id.mp4",
        status = Status.DONE, bytes = 4, total = 4,
    )

    @Before fun setUp() { Profiles.resetForTests(ctx) }
    @After fun tearDown() { Profiles.resetForTests(ctx); OnlineDownloads.unloadForTests() }

    @Test fun hidingMovesTheFileIntoThePrivateVaultAndOutOfTheList() {
        OnlineDownloads.setItemsForTest(ctx, listOf(item("a"), item("b")))
        val shared = OnlineDownloads.fileOf(ctx, item("a")).apply { writeText("data") }
        assertTrue(PrivateDownloads.hide(ctx, "a"))
        val hidden = OnlineDownloads.get("a")!!
        assertTrue(hidden.hidden)
        assertFalse("the file is still in the shared Downloads folder", shared.exists())
        val vaultFile = OnlineDownloads.fileOf(ctx, hidden)
        assertTrue("the file is not in the vault: $vaultFile", vaultFile.exists() && vaultFile.readText() == "data")
        assertTrue("vault is not app-private", vaultFile.absolutePath.startsWith(ctx.filesDir.absolutePath))
        assertTrue(OnlineDownloads.isSaved(ctx, hidden))
        assertEquals(listOf("b"), PrivateDownloads.visible(OnlineDownloads.items.value).map { it.id })
        assertEquals(listOf("a"), PrivateDownloads.vaultItems(ctx).map { it.id })
    }

    @Test fun unhideBringsItBack() {
        OnlineDownloads.setItemsForTest(ctx, listOf(item("a")))
        OnlineDownloads.fileOf(ctx, item("a")).writeText("data")
        PrivateDownloads.hide(ctx, "a")
        assertTrue(PrivateDownloads.unhide(ctx, "a"))
        val back = OnlineDownloads.get("a")!!
        assertFalse(back.hidden)
        assertTrue(OnlineDownloads.fileOf(ctx, back).readText() == "data")
        assertEquals(listOf("a"), PrivateDownloads.visible(OnlineDownloads.items.value).map { it.id })
        assertTrue(PrivateDownloads.vaultItems(ctx).isEmpty())
    }

    @Test fun anotherProfileDoesNotSeeTheVault() {
        OnlineDownloads.setItemsForTest(ctx, listOf(item("a")))
        OnlineDownloads.fileOf(ctx, item("a")).writeText("data")
        PrivateDownloads.hide(ctx, "a")
        val guest = Profiles.add(ctx, "Guest", AccentTone.AO)
        Profiles.select(ctx, guest.id)
        assertTrue(PrivateDownloads.vaultItems(ctx).isEmpty())
        assertTrue("hidden item shows in the guest's list", PrivateDownloads.visible(OnlineDownloads.items.value).isEmpty())
        assertFalse("guest could unhide it", PrivateDownloads.unhide(ctx, "a"))
    }

    @Test fun theHiddenFlagSurvivesARestart() {
        OnlineDownloads.setItemsForTest(ctx, listOf(item("a")))
        OnlineDownloads.fileOf(ctx, item("a")).writeText("data")
        PrivateDownloads.hide(ctx, "a")
        OnlineDownloads.flushForTests()
        OnlineDownloads.unloadForTests(); OnlineDownloads.ensureLoaded(ctx)
        val again = OnlineDownloads.get("a")!!
        assertTrue(again.hidden)
        assertEquals(Profiles.DEFAULT, again.profile)
        assertTrue(OnlineDownloads.isSaved(ctx, again))
    }
}
