/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.novel

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * NOVEL-1: a chapter's HTML as the paragraphs the reader shows. Scripts, styles, forms, iframes and ad blocks are
 * dropped; `<p>`, headings, list items, `<br><br>` and block breaks become paragraph ends; whitespace is folded.
 * Pure (jsoup), so it is unit-tested on the JVM.
 */
object NovelText {
    data class Para(val text: String, val heading: Boolean = false)

    private val DROP = "script, style, noscript, iframe, form, button, ins, svg, .ads, .ad, [class*=advert], [id*=advert], .adsbygoogle"
    private val BLOCK = setOf("p", "div", "section", "article", "li", "blockquote", "pre", "h1", "h2", "h3", "h4", "h5", "h6", "tr", "hr", "center")
    private val HEAD = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    private val SPACE = Regex("[\\s\u00a0]+")
    /** Two or more line breaks (with only spaces between): a paragraph break. */
    private val PARA_BREAK = Regex("\u0001[\\s\u00a0]*(?:\u0001[\\s\u00a0]*)+")
    private val LINE = Regex("[ ]*\u0001[ ]*")
    private const val BR = '\u0001'

    fun paragraphs(html: String): List<Para> {
        val body = Jsoup.parse(html).body()
        body.select(DROP).remove()
        val out = ArrayList<Para>()
        val sb = StringBuilder()
        var heading = false
        fun flush() {
            // <br><br> splits paragraphs; a single <br> stays a line break inside the paragraph.
            for (part in sb.toString().split(PARA_BREAK)) {
                val t = part.replace(SPACE, " ").replace(LINE, "\n").trim('\n', ' ')
                if (t.isNotEmpty()) out += Para(t, heading)
            }
            sb.setLength(0); heading = false
        }
        fun walk(n: Node) {
            when (n) {
                is TextNode -> sb.append(n.wholeText)
                is Element -> {
                    val tag = n.normalName()
                    if (tag == "br") { sb.append(BR); return }
                    val block = tag in BLOCK
                    if (block) flush()
                    if (tag in HEAD) heading = true
                    n.childNodes().forEach(::walk)
                    if (block) flush()
                }
            }
        }
        body.childNodes().forEach(::walk)
        flush()
        return out
    }
}
