/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.asfakul.yumeiro.ui.theme.ThemeSettings
import java.util.Locale

/**
 * The Yumeiro (夢色, "dream colour") accent vocabulary. One short Japanese word per page so the
 * identity shows up in a fixed, quiet place (the overline above a title) instead of everywhere.
 * Kana are decorative: screen readers skip them and every label keeps its English title.
 */
object Kana {
    /** Longest keys first so "watch together" wins over "watch". */
    private val words: List<Pair<String, String>> = listOf(
        "watch together" to "一緒に",
        "now playing" to "再生中",
        "source status" to "状態",
        "live tv" to "生放送",
        "equalizer" to "音響",
        "downloads" to "保存",
        "download" to "保存",
        "settings" to "設定",
        "library" to "書庫",
        "playlist" to "曲集",
        "favorites" to "好き",
        "favourites" to "好き",
        "history" to "履歴",
        "search" to "検索",
        "inbox" to "便り",
        "notifications" to "便り",
        "tracking" to "記録",
        "backup" to "控え",
        "extensions" to "拡張",
        "sources" to "源",
        "source" to "源",
        "manga" to "漫画",
        "anime" to "アニメ",
        "music" to "音楽",
        "songs" to "音楽",
        "videos" to "映像",
        "video" to "映像",
        "youtube" to "映像",
        "stream" to "配信",
        "market" to "市場",
        "updates" to "更新",
        "update" to "更新",
        "privacy" to "守り",
        "network" to "通信",
        "player" to "再生",
        "audio" to "音",
        "look & feel" to "彩り",
        "appearance" to "彩り",
        "about" to "夢色",
        "help" to "案内",
        "guide" to "案内",
        "setup" to "案内",
        "queue" to "順番",
        "home" to "夢色",
        "battery" to "電池",
        "background" to "電池",
        "reading" to "読書",
        "lyrics" to "歌詞",
        "discover" to "発見",
        "account" to "アカウント",
    ).sortedByDescending { it.first.length }

    /** The accent for a page title, or null when nothing fits (no accent beats a wrong one). */
    fun forTitle(title: String): String? {
        if (title.isEmpty()) return null
        synchronized(cache) { cache[title]?.let { return it.ifEmpty { null } } }
        val t = title.lowercase(Locale.ROOT)
        val hit = words.firstOrNull { (k, _) -> containsWord(t, k) }?.second
        synchronized(cache) { cache[title] = hit.orEmpty() }
        return hit
    }

    /**
     * R15 ANR root cause: this used to compile one Regex per vocabulary word (~60) on every call,
     * and it is called from composition for every home section and page title, including pager
     * prefetch. On a 2022 Xiaomi (Android 13) that blocked the main thread for 8 s during focus
     * changes. Now a plain word-boundary scan (same semantics: the key must not touch an a–z letter)
     * plus a small LRU of results.
     */
    internal fun containsWord(text: String, key: String): Boolean {
        var from = 0
        while (true) {
            val i = text.indexOf(key, from)
            if (i < 0) return false
            val before = if (i == 0) ' ' else text[i - 1]
            val end = i + key.length
            val after = if (end >= text.length) ' ' else text[end]
            if (before !in 'a'..'z' && after !in 'a'..'z') return true
            from = i + 1
        }
    }

    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 256
    }

    /** "設定 · SETTINGS"-style overline, or the English text alone when accents are off. */
    fun overline(kana: String?, english: String?, enabled: Boolean): String? = when {
        !enabled || kana == null -> english
        english.isNullOrBlank() -> kana
        else -> "$kana · $english"
    }
}

/** Whether the user keeps the Japanese accents on (Settings › Look & feel). On by default. */
@Composable
fun kanaEnabled(): Boolean {
    val context = LocalContext.current
    remember(context) { ThemeSettings.init(context); ThemeSettings }
    val on by ThemeSettings.kana.collectAsState()
    return on
}
