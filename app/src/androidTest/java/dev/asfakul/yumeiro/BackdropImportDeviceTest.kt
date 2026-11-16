/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.ui.theme.BackdropSettings
import dev.asfakul.yumeiro.ui.theme.BackdropStyle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * G9 S26: the Look & feel photo backdrop with real pictures, decoded by the phone's own decoders
 * (Robolectric's BackdropImportTest cannot decode HEIC, AVIF or a broken JPEG). Each picture is
 * put in MediaStore and handed over as a content:// URI, like the photo picker does.
 * Pictures (androidTest/assets/backdrop, made with ImageMagick 7): 1200 × 900 JPEG, PNG, WebP,
 * HEIC (8-bit), AVIF (8-bit), GIF, BMP; an 8000 × 6000 JPEG; 40 × 40 PNG; a 1000 × 100 strip;
 * a JPEG cut at 9,000 bytes. Made here: a text file named .jpg and a 61 MB file.
 */
@RunWith(AndroidJUnit4::class)
class BackdropImportDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inserted = ArrayList<Uri>()
    private fun log(s: String) = Log.i("YuBackdropTest", s)

    /** A content:// URI from MediaStore, or a file:// one when MediaStore refuses the type. */
    private fun media(asset: String, mime: String): Uri = runCatching { mediaStore(asset, mime) }.getOrElse { e ->
        log("BD_NOTE MediaStore refused $asset ($mime): ${e.message}; using a file URI")
        val f = File(ctx.cacheDir, asset)
        InstrumentationRegistry.getInstrumentation().context.assets.open("backdrop/$asset").use { i -> f.outputStream().use { i.copyTo(it) } }
        Uri.fromFile(f)
    }

    private fun mediaStore(asset: String, mime: String): Uri {
        val v = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "yu_backdrop_test_$asset")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/YuTest")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)!!
        inserted += uri
        val src = InstrumentationRegistry.getInstrumentation().context.assets.open("backdrop/$asset")
        ctx.contentResolver.openOutputStream(uri)!!.use { out -> src.use { it.copyTo(out) } }
        return uri
    }

    @After fun tidy() {
        inserted.forEach { runCatching { ctx.contentResolver.delete(it, null, null) } }
        runCatching { BackdropSettings.removePhoto(ctx) }
    }

    private class Case(val asset: String, val mime: String, val ok: Boolean, val minSdk: Int = 0, val color: Boolean = true, val why: String = "")

    @Test fun everyAcceptedTypeImportsAndBadOnesSayWhy() = runBlocking {
        FirstRun.toTabs(rule)
        BackdropSettings.init(ctx)
        val cases = listOf(
            Case("a.jpg", "image/jpeg", true), Case("b.png", "image/png", true), Case("c.webp", "image/webp", true),
            Case("d.heic", "image/heic", true, minSdk = 28), Case("e.avif", "image/avif", true, minSdk = 31),
            Case("f.gif", "image/gif", true), Case("g.bmp", "image/bmp", true),
            Case("huge.jpg", "image/jpeg", true, color = false),
            Case("tiny.png", "image/png", false, why = "too small"),
            Case("strip.png", "image/png", false, why = "too small"),
            Case("corrupt.jpg", "image/jpeg", false, why = "couldn't be opened|isn't supported"),
        )
        val problems = ArrayList<String>()
        for (c in cases) {
            if (Build.VERSION.SDK_INT < c.minSdk) { log("BD_CASE ${c.asset} skipped below API ${c.minSdk}"); continue }
            BackdropSettings.removePhoto(ctx)
            val t0 = System.nanoTime()
            val err = BackdropSettings.importPhoto(ctx, media(c.asset, c.mime))
            val ms = (System.nanoTime() - t0) / 1_000_000
            val saved = File(ctx.filesDir, "backdrop_photo.jpg")
            var detail = ""
            if (err == null && saved.isFile) {
                val b = BitmapFactory.decodeFile(saved.path)
                val px = b.getPixel(2, 2)
                detail = "saved ${b.width}x${b.height} corner=(${Color.red(px)},${Color.green(px)},${Color.blue(px)})"
                if (minOf(b.width, b.height) !in 200..1080) problems += "${c.asset}: saved size $detail"
                // The source corner is (199, 80, 144); the saved picture must be that photo, not a blank.
                if (c.color && (abs(Color.red(px) - 199) > 40 || abs(Color.green(px) - 80) > 40 || abs(Color.blue(px) - 144) > 40)) problems += "${c.asset}: wrong picture $detail"
                if (BackdropSettings.style.value != BackdropStyle.PHOTO) problems += "${c.asset}: backdrop not switched to the photo"
                b.recycle()
            }
            log("BD_CASE ${c.asset} ${c.mime} -> ${err ?: "OK"} $detail ${ms}ms")
            when {
                c.ok && err != null -> problems += "${c.asset}: refused: $err"
                !c.ok && err == null -> problems += "${c.asset}: accepted, expected '${c.why}'"
                !c.ok && c.why.split('|').none { it in err!! } -> problems += "${c.asset}: message '$err', expected '${c.why}'"
            }
        }
        // Not pictures: a text file with a .jpg name, and a file over the 60 MB limit.
        val text = File(ctx.cacheDir, "not-a-picture.jpg").apply { writeText("hello") }
        val e1 = BackdropSettings.importPhoto(ctx, Uri.fromFile(text))
        log("BD_CASE text.jpg -> $e1")
        if (e1 == null || ("format" !in e1 && "couldn't be opened" !in e1)) problems += "text.jpg: $e1"
        val big = File(ctx.cacheDir, "big.jpg")
        big.outputStream().use { o ->
            InstrumentationRegistry.getInstrumentation().context.assets.open("backdrop/a.jpg").use { it.copyTo(o) }
            val mb = ByteArray(1 shl 20)
            repeat(61) { o.write(mb) }
        }
        val e2 = BackdropSettings.importPhoto(ctx, Uri.fromFile(big))
        log("BD_CASE big.jpg ${big.length()} bytes -> $e2")
        if (e2 == null || "over 60 MB" !in e2) problems += "61 MB: $e2"
        big.delete()

        // The last good picture, on screen: Home behind the photo, and the Look & feel tile.
        BackdropSettings.importPhoto(ctx, media("a.jpg", "image/jpeg"))
        rule.waitForIdle(); Thread.sleep(2_000)
        FirstRun.shot("backdrop_photo_home")
        FirstRun.tapVisible(rule, "topbar_settings_action"); Thread.sleep(1_200)
        FirstRun.tapVisible(rule, "settings_cat_appearance"); Thread.sleep(1_500)
        FirstRun.shot("backdrop_photo_settings")
        log("BD_RESULT problems=${problems.size} $problems")
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
