/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram.td

import org.junit.Assert.assertEquals
import org.junit.Test

/** F-02 downloads: the link's file name gives the saved file the right type. */
class TgAccountsNameTest {
    private fun v(name: String, mime: String) = TgVideo(1, 2, 3, name, mime, 10, 0, 0, 0, 0, "", 0)

    @Test fun keepsTheFilesOwnExtension() {
        assertEquals("SubShow-03.mkv", TgAccounts.fileName(v("[Sub] Show - 03.MKV", "application/octet-stream")))
        assertEquals("OST01.flac", TgAccounts.fileName(v("OST 01.flac", "audio/flac")))
    }

    @Test fun fallsBackToTheMimeType() {
        assertEquals("AimerKataomoi.mp3", TgAccounts.fileName(v("Aimer – Kataomoi", "audio/mpeg")))
        assertEquals("Voicemessage.ogg", TgAccounts.fileName(v("Voice message", "audio/ogg")))
        assertEquals("telegram.mp4", TgAccounts.fileName(v("", "video/mp4")))
        assertEquals("mkv", TgAccounts.extFor("video/x-matroska"))
        assertEquals("m4a", TgAccounts.extFor("audio/mp4"))
    }
}
