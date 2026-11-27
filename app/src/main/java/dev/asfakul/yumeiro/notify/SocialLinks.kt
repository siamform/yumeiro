/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.notify

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R

/**
 * OL-13 (owner 2026-11-22): every official account, in one place. Yumeiro is @officialyumeiro on Facebook,
 * Instagram, X, TikTok and Reddit; the Telegram channel / group and the website stay in [Reminders]. Plain https
 * links: Android hands each one to the matching app when it is installed (App Links), else the browser.
 */
object SocialLinks {
    class Link(val id: String, val label: String, val handle: String, val url: String, val icon: Int)

    const val HANDLE = "officialyumeiro"

    /** Yumeiro's own accounts besides Telegram and the website (those are rows of their own). */
    val yumeiro: List<Link> = listOf(
        Link("discord", "Discord", "Yumeiro server", "https://discord.gg/eqkNymUAMJ", R.drawable.ic_brand_discord),
        Link("facebook", "Facebook", HANDLE, "https://www.facebook.com/$HANDLE", R.drawable.ic_brand_facebook),
        Link("instagram", "Instagram", "@$HANDLE", "https://www.instagram.com/$HANDLE", R.drawable.ic_brand_instagram),
        Link("x", tr(R.string.x_twitter, "X (Twitter)"), "@$HANDLE", "https://x.com/$HANDLE", R.drawable.ic_brand_x),
        Link("tiktok", "TikTok", "@$HANDLE", "https://www.tiktok.com/@$HANDLE", R.drawable.ic_brand_tiktok),
        Link("reddit", "Reddit", "u/$HANDLE", "https://www.reddit.com/user/$HANDLE", R.drawable.ic_brand_reddit),
    )

    /** The developer's own accounts (About › Developer). */
    val developer: List<Link> = listOf(
        Link("facebook", "Facebook", "siam1p", "https://www.facebook.com/siam1p", R.drawable.ic_brand_facebook),
        Link("instagram", "Instagram", "@asfakul_siam", "https://www.instagram.com/asfakul_siam", R.drawable.ic_brand_instagram),
        Link("telegram", "Telegram", "@siamorwho", "https://t.me/siamorwho", R.drawable.ic_brand_telegram),
    )
}
