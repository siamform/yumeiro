package dev.asfakul.yumeiro.profiles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.ui.theme.AccentTone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** PROFILE-PIC-1 (owner list 30): a picture per profile, from a real image file, kept with the profile. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ProfilePhotosTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setUp() { Profiles.resetForTests(ctx) }
    @After fun tearDown() { Profiles.resetForTests(ctx) }

    private fun picture(w: Int, h: Int): Uri {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 40, 90)) }
        val f = File(ctx.cacheDir, "pick_${w}x$h.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(f)
    }

    @Test fun centreSquareCropsTheMiddle() {
        assertEquals(Triple(100, 0, 400), ProfilePhotos.centreSquare(600, 400))
        assertEquals(Triple(0, 50, 300), ProfilePhotos.centreSquare(300, 400))
    }

    @Test fun aPickedPictureIsSavedSquarePersistsAndCanBeRemoved() = runBlocking {
        val kid = Profiles.add(ctx, "Kid", AccentTone.AO)
        assertEquals(0L, kid.photo)
        assertNull(ProfilePhotos.import(ctx, kid.id, picture(1200, 800)))
        val saved = Profiles.find(ctx, kid.id)!!
        assertTrue("version recorded", saved.photo > 0L)
        val f = ProfilePhotos.file(ctx, kid.id)
        assertTrue(f.isFile)
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, opts)
        assertEquals(opts.outWidth, opts.outHeight)
        assertTrue(opts.outWidth in 64..ProfilePhotos.SIZE)
        assertNotNull(ProfilePhotos.load(ctx, saved))
        // Only that profile changed: the default profile keeps the moon.
        assertEquals(0L, Profiles.find(ctx, Profiles.DEFAULT)!!.photo)

        // Survives an app restart (list reloaded from disk).
        Profiles.unloadForTests(); Profiles.init(ctx)
        assertEquals(saved.photo, Profiles.find(ctx, kid.id)!!.photo)

        // Changing it gives a new version, so no stale cached bitmap is shown.
        assertNull(ProfilePhotos.import(ctx, kid.id, picture(500, 900)))
        assertTrue(Profiles.find(ctx, kid.id)!!.photo > saved.photo)

        ProfilePhotos.remove(ctx, kid.id)
        assertEquals(0L, Profiles.find(ctx, kid.id)!!.photo)
        assertFalse(f.exists())
        assertNull(ProfilePhotos.load(ctx, Profiles.find(ctx, kid.id)!!))
    }

    @Test fun deletingAProfileDeletesItsPicture() = runBlocking {
        val kid = Profiles.add(ctx, "Kid", AccentTone.AO)
        assertNull(ProfilePhotos.import(ctx, kid.id, picture(400, 400)))
        val f = ProfilePhotos.file(ctx, kid.id)
        assertTrue(f.isFile)
        assertTrue(Profiles.remove(ctx, kid.id))
        assertFalse(f.exists())
    }

    @Test fun tinyOrMissingPicturesAreRefusedWithAMessage() = runBlocking {
        val kid = Profiles.add(ctx, "Kid", AccentTone.AO)
        assertNotNull(ProfilePhotos.import(ctx, kid.id, picture(20, 20)))
        assertNotNull(ProfilePhotos.import(ctx, kid.id, Uri.fromFile(File(ctx.cacheDir, "missing.png"))))
        assertEquals(0L, Profiles.find(ctx, kid.id)!!.photo)
    }

    @Test fun aLockedProfilesPictureStaysOutOfBackupsMadeFromAnotherProfile() {
        val kid = Profiles.add(ctx, "Kid", AccentTone.AO)
        assertTrue(Profiles.setPin(ctx, kid.id, "4321"))
        assertTrue(Profiles.hiddenFromBackup(ctx, "files/profiles/${kid.id}/avatar.jpg"))
    }
}
