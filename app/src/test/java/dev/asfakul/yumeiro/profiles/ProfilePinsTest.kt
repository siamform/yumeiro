package dev.asfakul.yumeiro.profiles

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.online.PinnedExtensions
import dev.asfakul.yumeiro.stream.PinnedPlaylists
import dev.asfakul.yumeiro.ui.theme.AccentTone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F-19 isolation: pinned extensions and pinned Live TV playlists belong to the profile that pinned
 * them. Old code: both used one shared prefs file, so the kid's pins showed in the parent's profile.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProfilePinsTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    private fun reset() { PinnedExtensions.resetForTests(); PinnedPlaylists.resetForTests() }

    @Before fun setUp() { Profiles.resetForTests(ctx); reset() }
    @After fun tearDown() { Profiles.resetForTests(ctx); reset() }

    @Test fun pinsStayInTheirProfile() {
        PinnedExtensions.toggle(ctx, "ParentExt")
        PinnedPlaylists.toggle(ctx, "parent-list")
        val kid = Profiles.add(ctx, "Kid", AccentTone.AO)
        assertTrue(Profiles.select(ctx, kid.id))
        reset() // switching profile restarts the app
        PinnedExtensions.init(ctx); PinnedPlaylists.init(ctx)
        assertEquals(emptyList<String>(), PinnedExtensions.pins.value)
        assertEquals(emptyList<String>(), PinnedPlaylists.pins.value)
        PinnedExtensions.toggle(ctx, "KidExt")
        assertTrue(Profiles.select(ctx, Profiles.DEFAULT))
        reset()
        PinnedExtensions.init(ctx); PinnedPlaylists.init(ctx)
        assertEquals(listOf("ParentExt"), PinnedExtensions.pins.value)
        assertEquals(listOf("parent-list"), PinnedPlaylists.pins.value)
    }
}
