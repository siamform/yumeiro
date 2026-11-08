/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.sources

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.MainAPI
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.OnlineErrors
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

/** Result of one source check. */
data class Health(val ok: Boolean, val ms: Long, val detail: String)

/**
 * "Which sources work right now?" Loads every installed source's home page (or a search for
 * sources without one) on this phone, and lets the user share the report — handy when filing an
 * issue. The public status page on the website shows the repositories' state from the market check.
 */
@Composable
fun SourceHealthScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { ExtensionManager.start(context) }
    val apis = remember { ExtensionManager.providers().sortedBy { it.name.lowercase() } }
    val results = remember { mutableStateMapOf<String, Health>() }
    var running by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(0) }

    suspend fun check(api: MainAPI): Health {
        val t0 = System.currentTimeMillis()
        return runCatching {
            withTimeout(25_000) {
                if (api.hasMainPage) {
                    val rows = OnlineRepository.home(api, force = true)
                    val n = rows.sumOf { it.items.size }
                    if (n > 0) Health(true, System.currentTimeMillis() - t0, "$n titles on home") else Health(false, System.currentTimeMillis() - t0, "Home page is empty")
                } else {
                    val r = api.search("the", 1)?.items.orEmpty()
                    if (r.isNotEmpty()) Health(true, System.currentTimeMillis() - t0, "${r.size} search results") else Health(false, System.currentTimeMillis() - t0, "Search returned nothing")
                }
            }
        }.getOrElse { Health(false, System.currentTimeMillis() - t0, OnlineErrors.friendly(it)) }
    }

    fun runAll() = scope.launch {
        running = true; done = 0; results.clear()
        val gate = Semaphore(4)
        apis.map { api -> async { gate.withPermit { results[api.name] = check(api); done++ } } }.awaitAll()
        running = false
    }

    fun share() {
        val text = buildString {
            appendLine("Yumeiro source status — " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date()))
            appendLine()
            appendLine("| Source | Type | Status | Time | Details |")
            appendLine("|---|---|---|---|---|")
            apis.forEach { a ->
                val h = results[a.name]
                appendLine("| ${a.name} | ${SourceHub.ecosystemOf(a).label} | ${when { h == null -> "not checked"; h.ok -> "working"; else -> "failing" }} | ${h?.ms?.let { "${it} ms" } ?: ""} | ${h?.detail.orEmpty().replace("|", "/")} |")
            }
        }
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share status").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Column(Modifier.fillMaxSize()) {
        SubPageHeader(title = "Source status", onBack = onBack, subtitle = if (results.isEmpty()) "${apis.size} sources installed" else "${results.count { it.value.ok }} of ${results.size} working") {
            if (results.isNotEmpty()) IconButton(onClick = ::share) { Icon(Icons.Default.Share, "Share report", tint = TextSecondary) }
        }
        if (running) LinearProgressIndicator(progress = { if (apis.isEmpty()) 0f else done / apis.size.toFloat() }, color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth())
        LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item("run") {
                Button(onClick = { runAll() }, enabled = !running && apis.isNotEmpty(), shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent)) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(if (results.isEmpty()) "Check all sources" else "Check again")
                }
                Text("Checks run on your phone and network. A source can work for someone else and fail here if a site is blocked where you are.", color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                val uri = androidx.compose.ui.platform.LocalUriHandler.current
                androidx.compose.material3.TextButton(onClick = { runCatching { uri.openUri("https://yumeiro-phi.vercel.app/status") } }, contentPadding = PaddingValues(0.dp)) {
                    Text("See the public status of every market repository", color = SpotifyGreen, fontSize = 13.sp)
                }
            }
            items(apis, key = { it.name }) { a ->
                val h = results[a.name]
                HubCard(a.name, h?.let { "${it.detail} · ${it.ms} ms" } ?: "Not checked", badge = SourceHub.ecosystemOf(a).label) {
                    Box(Modifier.size(12.dp).background(when { h == null -> TextTertiary.copy(alpha = 0.4f); h.ok -> Color(0xFF4CAF50); else -> Color(0xFFE57373) }, CircleShape))
                }
            }
        }
    }
}
