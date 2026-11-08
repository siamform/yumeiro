/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.RemoteViews
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import dev.asfakul.yumeiro.MainActivity
import dev.asfakul.yumeiro.R
import dev.asfakul.yumeiro.online.OnlineHistory
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.security.Privacy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Home-screen widget: the last title you watched online, its episode and progress, one tap to open
 * it. Plain RemoteViews (no Glance dependency). Hidden while incognito or app lock is on, so the
 * home screen never reveals what you watch.
 */
class ContinueWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch { try { render(context.applicationContext, manager, ids) } finally { pending.finish() } }
    }

    companion object {
        const val ACTION_OPEN_TITLE = "dev.asfakul.yumeiro.OPEN_TITLE"
        const val EXTRA_API = "api"; const val EXTRA_URL = "url"; const val EXTRA_NAME = "name"
        const val EXTRA_POSTER = "poster"; const val EXTRA_TYPE = "type"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile private var lastKey: String? = null
        @Volatile private var lastAt = 0L

        /** Called on every progress save: re-render only when the title changes or every 2 minutes. */
        fun maybeRefresh(context: Context, key: String) {
            val now = System.currentTimeMillis()
            if (key == lastKey && now - lastAt < 120_000) return
            lastKey = key; lastAt = now
            refresh(context)
        }

        /** Re-render every placed widget (cheap no-op when none are placed). */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app) ?: return
            val ids = runCatching { manager.getAppWidgetIds(ComponentName(app, ContinueWidget::class.java)) }.getOrNull() ?: return
            if (ids.isEmpty()) return
            scope.launch { render(app, manager, ids) }
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            OnlineHistory.ensureLoaded(context)
            val hidden = Privacy.incognito.value || Privacy.lockEnabled.value
            val entry = if (hidden) null else OnlineHistory.forFeed(OnlineHistory.entries.value).firstOrNull()
            val views = RemoteViews(context.packageName, R.layout.widget_continue)
            val openApp = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            if (entry == null) {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_empty_title))
                views.setTextViewText(R.id.widget_subtitle, context.getString(if (hidden) R.string.widget_open else R.string.widget_empty_body).let { if (hidden) "Hidden while incognito or app lock is on" else it })
                views.setViewVisibility(R.id.widget_progress, View.GONE)
                views.setTextViewText(R.id.widget_action, context.getString(R.string.widget_open))
                views.setImageViewResource(R.id.widget_poster, R.drawable.ic_app_logo)
                views.setOnClickPendingIntent(R.id.widget_root, openApp)
                views.setOnClickPendingIntent(R.id.widget_action, openApp)
            } else {
                views.setTextViewText(R.id.widget_title, entry.name)
                views.setTextViewText(R.id.widget_subtitle, listOfNotNull(entry.episodeLabel, entry.api).joinToString(" · "))
                val pct = if (entry.durationMs > 0) (entry.positionMs * 1000 / entry.durationMs).toInt().coerceIn(0, 1000) else 0
                views.setViewVisibility(R.id.widget_progress, if (pct > 0) View.VISIBLE else View.GONE)
                views.setProgressBar(R.id.widget_progress, 1000, pct, false)
                views.setTextViewText(R.id.widget_action, context.getString(R.string.widget_resume))
                val open = Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_TITLE)
                    .putExtra(EXTRA_API, entry.api).putExtra(EXTRA_URL, entry.url).putExtra(EXTRA_NAME, entry.name)
                    .putExtra(EXTRA_POSTER, entry.posterUrl).putExtra(EXTRA_TYPE, entry.type)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val pi = PendingIntent.getActivity(context, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                views.setOnClickPendingIntent(R.id.widget_root, pi)
                views.setOnClickPendingIntent(R.id.widget_action, pi)
                val bmp = entry.posterUrl?.let { poster(context, it, entry.posterHeaders) }
                if (bmp != null) views.setImageViewBitmap(R.id.widget_poster, bmp) else views.setImageViewResource(R.id.widget_poster, R.drawable.ic_app_logo)
            }
            runCatching { manager.updateAppWidget(ids, views) }
        }

        private suspend fun poster(context: Context, url: String, headers: Map<String, String>): Bitmap? = withTimeoutOrNull(8_000) {
            runCatching {
                val req = ImageRequest.Builder(context).data(url).size(192, 288).allowHardware(false)
                    .apply { headers.forEach { (k, v) -> addHeader(k, v) } }.build()
                ((ImageLoader(context).execute(req) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
            }.getOrNull()
        }

        /** Parses the widget's open intent back into a title. */
        fun itemFrom(intent: Intent?): OnlineItem? {
            if (intent?.action != ACTION_OPEN_TITLE) return null
            val api = intent.getStringExtra(EXTRA_API) ?: return null
            val url = intent.getStringExtra(EXTRA_URL) ?: return null
            return OnlineItem(intent.getStringExtra(EXTRA_NAME).orEmpty(), url, api, intent.getStringExtra(EXTRA_POSTER), emptyMap(), intent.getStringExtra(EXTRA_TYPE))
        }
    }
}

/** One-shot "open this title" requests from outside Compose (widget, shortcuts). */
object TitleNav {
    private val _pending = MutableStateFlow<OnlineItem?>(null)
    val pending: StateFlow<OnlineItem?> = _pending.asStateFlow()
    fun open(item: OnlineItem) { _pending.value = item }
    fun consumed() { _pending.value = null }
}
