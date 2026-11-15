/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Puts the test clip (an androidTest asset) into Movies/YumeiroTest the way a user's video gets
 * there, and returns its MediaStore uri.
 *
 * API 29+: a MediaStore insert with RELATIVE_PATH and IS_PENDING.
 * API 28 and older (minSdk run 43768670627, api28): an insert without a file path makes the old
 * media provider invent one, `/sdcard/video/<millis>` with no extension, so the clip was listed
 * as "1794781938560" (TITLE), 00:00, and no test could find it. There a real file is copied in
 * with the shell and indexed by the media scanner, which fills TITLE and DURATION.
 */
object TestClip {
    private const val DIR = "Movies/YumeiroTest"

    fun put(ctx: Context, name: String): Uri {
        val inst = InstrumentationRegistry.getInstrumentation()
        val cr = ctx.contentResolver
        if (Build.VERSION.SDK_INT >= 29) {
            val coll = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            runCatching { cr.delete(coll, "${MediaStore.Video.Media.DISPLAY_NAME}=?", arrayOf(name)) }
            val v = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, DIR)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = checkNotNull(cr.insert(coll, v)) { "MediaStore insert failed" }
            cr.openOutputStream(uri)!!.use { out -> inst.context.assets.open(name).use { it.copyTo(out) } }
            cr.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            return uri
        }
        // The app's own external files folder needs no permission and the shell can read it.
        val staged = File(checkNotNull(ctx.getExternalFilesDir(null)) { "no external files dir" }, name)
        inst.context.assets.open(name).use { input -> staged.outputStream().use { input.copyTo(it) } }
        val target = "/sdcard/$DIR/$name"
        FirstRun.shell("mkdir -p /sdcard/$DIR")
        FirstRun.shell("cp ${staged.absolutePath} $target")
        staged.delete()
        val real = File(android.os.Environment.getExternalStorageDirectory(), "$DIR/$name").absolutePath
        val done = CountDownLatch(1)
        var scanned: Uri? = null
        MediaScannerConnection.scanFile(ctx, arrayOf(real), arrayOf("video/mp4")) { _, u -> scanned = u; done.countDown() }
        check(done.await(30, TimeUnit.SECONDS)) { "media scanner did not answer for $real" }
        return checkNotNull(scanned) { "media scanner did not index $real" }
    }

    /** Removes the MediaStore row and, on API 28 and older, the copied file too. */
    fun remove(ctx: Context, uri: Uri, name: String) {
        runCatching { ctx.contentResolver.delete(uri, null, null) }
        if (Build.VERSION.SDK_INT < 29) runCatching { FirstRun.shell("rm -f /sdcard/$DIR/$name") }
    }
}
