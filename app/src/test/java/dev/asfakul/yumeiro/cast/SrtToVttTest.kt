/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R17 PL-19: SRT → WebVTT for Cast. */
class SrtToVttTest {
    private val srt = "\uFEFF1\r\n00:00:01,000 --> 00:00:02,500\r\n{\\an8}<font color=\"#fff\">Hello</font>\r\n<i>there</i>\r\n\r\n2\r\n0:01:02,5 --> 0:01:04,05\r\nA --> B\r\n\r\n3\r\n00:00:09,000 --> 00:00:10,000\r\n\r\n"

    @Test fun convertsHeaderTimesAndText() {
        val v = SrtToVtt.convert(srt)
        assertTrue(v.startsWith("WEBVTT\n\n"))
        assertTrue(v, v.contains("00:00:01.000 --> 00:00:02.500\nHello\n<i>there</i>\n\n"))
        assertTrue(v, v.contains("00:01:02.500 --> 00:01:04.050\nA -> B\n"))
        assertFalse("cue numbers dropped", v.lines().any { it.trim() == "1" || it.trim() == "2" })
        assertFalse(v.contains("\\an8")); assertFalse(v.contains("\r")); assertFalse(v.contains("font"))
        assertTrue("an empty cue stays valid", v.contains("00:00:09.000 --> 00:00:10.000\n \n"))
    }

    @Test fun numericLineBeforeNextCueIsNotText() {
        val v = SrtToVtt.convert("1\n00:00:01,000 --> 00:00:02,000\nLine\n2\n00:00:03,000 --> 00:00:04,000\nNext\n")
        assertEquals("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nLine\n\n00:00:03.000 --> 00:00:04.000\nNext\n\n", v)
    }

    @Test fun recognisesSrtButNotVtt() {
        assertTrue(SrtToVtt.looksLikeSrt(srt))
        assertFalse(SrtToVtt.looksLikeSrt("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHi\n"))
        assertFalse(SrtToVtt.looksLikeSrt("<html>blocked</html>"))
    }
}
