/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram

import android.content.Context
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.stream.StreamRefreshers
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.stream.StreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * F-02 (ADR-042): the file address on Telegram's web preview is temporary. When the player is
 * refused (403 / 410) it asks [StreamRefreshers]; this one reads the post again and returns the
 * new address, so playback continues at the same position. No network work at registration.
 */
object TelegramPlayback {
    @Volatile private var registered = false

    /** Whether [request] is a Telegram post this app opened (cheap; no network). */
    fun owns(request: StreamRequest): Boolean =
        request.group == StreamResolver.TELEGRAM_GROUP && request.pageUrl?.let { TelegramPublic.parse(it) } is TelegramPublic.Post

    fun install(context: Context) {
        if (registered) return
        registered = true
        val app = context.applicationContext
        StreamRefreshers.register(object : StreamRefreshers.Refresher {
            override fun handles(request: StreamRequest) = owns(request) && FeatureFlags.on(app, FeatureFlags.Flag.TELEGRAM_PUBLIC)
            override suspend fun refresh(context: Context, request: StreamRequest): StreamRequest? = withContext(Dispatchers.IO) {
                val post = request.pageUrl?.let { TelegramPublic.parse(it) } as? TelegramPublic.Post ?: return@withContext null
                when (val r = TelegramPublic.fetchPost(NetworkClient.get(context), post.channel, post.id)) {
                    is TelegramPublic.Result.Ok -> request.copy(url = r.value.videoUrl!!)
                    is TelegramPublic.Result.Failed -> null
                }
            }
        })
    }
}
