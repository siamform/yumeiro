/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.meta.TitleFacts
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary

/**
 * META-1 (owner list 13): the About rows (release date, IMDb votes, country, language, director, writers, companies,
 * network), Cast and Trailers of a title, each only when it has something. Used by the extension title page
 * (tags `online_detail_*`) and, since META-1b, by the watch page under the player (`online_watch_*`), which is where
 * Yumeiro Catalog titles and search results open.
 */
internal fun LazyListScope.titleFactsItems(
    context: Context,
    d: OnlineDetail,
    facts: TitleFacts.Facts,
    tagPrefix: String,
    header: @Composable (title: String, trailing: String?) -> Unit,
) {
    val aboutRows = TitleFacts.rows(facts, d.kind)
    if (aboutRows.isNotEmpty()) item(key = "about") {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(TextPrimary.copy(alpha = 0.05f)).padding(horizontal = 14.dp, vertical = 10.dp).testTag("${tagPrefix}_about"),
        ) {
            aboutRows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {}) {
                    Text(label, color = TextTertiary, fontSize = 12.sp, modifier = Modifier.width(118.dp).padding(end = 10.dp)) // a gap even when a label fills its column
                    Text(value, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    if (facts.cast.isNotEmpty()) item(key = "cast") {
        Column(Modifier.padding(bottom = 8.dp)) {
            header("Cast", "${facts.cast.size}")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.testTag("${tagPrefix}_cast"),
            ) {
                items(facts.cast, key = { it.name }) { c ->
                    Column(Modifier.width(76.dp).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(TextPrimary.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                            Text(c.name.firstOrNull { it.isLetter() }?.uppercase() ?: "?", color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            c.image?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        }
                        Text(c.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp))
                        c.role?.let { Text(it, color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
                    }
                }
            }
        }
    }
    if (facts.trailers.isNotEmpty()) item(key = "trailers") {
        Column(Modifier.padding(bottom = 8.dp)) {
            header(if (facts.trailers.size == 1) "Trailer" else "Trailers", null)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.testTag("${tagPrefix}_trailers"),
            ) {
                items(facts.trailers, key = { it.url }) { t ->
                    val yt = TitleFacts.youTubeId(t.url)
                    Column(Modifier.width(220.dp)) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(TextPrimary.copy(alpha = 0.08f))
                                .clickable(onClickLabel = "Play ${t.name}") {
                                    // Trailers play in Yumeiro's own player (YouTube links go through the login-free extractor).
                                    dev.asfakul.yumeiro.stream.IncomingMedia.open(context, dev.asfakul.yumeiro.stream.StreamRequest(url = t.url, title = "${d.name} · ${t.name}"))
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            yt?.let { AsyncImage(model = "https://i.ytimg.com/vi/$it/mqdefault.jpg", contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                            Box(Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                            }
                        }
                        Text(t.name, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}
