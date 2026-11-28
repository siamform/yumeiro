/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.intel

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SentimentNeutral
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.intel.TasteSignals
import dev.asfakul.yumeiro.ui.components.FilterPill
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The icon of each rating (same everywhere the rating is asked). */
internal fun TasteSignals.Feedback.icon(): ImageVector = when (this) {
    TasteSignals.Feedback.LOVED -> Icons.Default.Favorite
    TasteSignals.Feedback.LIKED -> Icons.Default.ThumbUp
    TasteSignals.Feedback.OKAY -> Icons.Default.SentimentNeutral
    TasteSignals.Feedback.NOT_FOR_ME -> Icons.Default.ThumbDown
}

/**
 * B20: "Loved it / Liked it / It was okay / Not for me" on a title page. Tapping the chosen one again clears it.
 * The rating is this profile's own and only shapes its For You picks (and its AI context when AI is set up).
 * Nothing is saved while incognito is on, and the row says so.
 */
@Composable
fun TasteFeedbackRow(ref: TasteSignals.Ref?, modifier: Modifier = Modifier) {
    if (ref == null || ref.adult) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by TasteSignals.version.collectAsState()
    val incognito by dev.asfakul.yumeiro.security.Privacy.incognito.collectAsState()
    var current by remember(ref.key) { mutableStateOf<TasteSignals.Feedback?>(null) }
    LaunchedEffect(ref.key, version) {
        current = withContext(Dispatchers.IO) { TasteSignals.get(context, ref.key)?.feedback }
    }
    Column(modifier.testTag("taste_feedback")) {
        Text("How was it?", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TasteSignals.Feedback.entries.forEach { f ->
                FilterPill(
                    label = f.label, selected = current == f, icon = f.icon(), testTag = "taste_fb_${f.code}",
                    onClick = {
                        if (incognito) return@FilterPill
                        val next = if (current == f) null else f
                        current = next
                        scope.launch(Dispatchers.IO) { TasteSignals.setFeedback(context, ref, next) }
                    },
                )
            }
        }
        Text(
            if (incognito) "Incognito is on: ratings aren't saved." else "Only this profile's picks use it.",
            color = TextTertiary, fontSize = 11.sp, modifier = Modifier.testTag("taste_feedback_note"),
        )
    }
}
