package dev.asfakul.yumeiro

import java.io.File

/**
 * FINAL-24 (runs 44659196962, 44660785480, tv-api24): before Android 10 a MediaStore insert needs the
 * file path (DATA); without it MediaProvider throws NullPointerException (String.lastIndexOf). Tests that
 * add media on Android 7–9 put DATA = /sdcard/<top>/<folder>/<name> (folder made first) instead of
 * RELATIVE_PATH / IS_PENDING. Needs WRITE_EXTERNAL_STORAGE there (device-check.sh grants it).
 */
object LegacyMedia {
    fun path(top: String, folder: String, name: String): String {
        val dir = File(File(android.os.Environment.getExternalStorageDirectory(), top), folder)
        dir.mkdirs()
        return File(dir, name).absolutePath
    }
}
