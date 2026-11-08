/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.download.DownloadSettings
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextTertiary

/** Settings → Downloads: auto-delete timer, episode quality, parallel downloads, YouTube copies. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadSettingsCard() {
    val context = LocalContext.current
    remember { DownloadSettings.init(context); true }
    val days by DownloadSettings.autoDeleteDays.collectAsState()
    val quality by DownloadSettings.quality.collectAsState()
    val parallel by DownloadSettings.parallel.collectAsState()
    val keepDefault by DownloadSettings.keepByDefault.collectAsState()
    val toPhone by DownloadSettings.saveYouTubeToPhone.collectAsState()
    val askFirst by DownloadSettings.askFirst.collectAsState()
    val tips by DownloadSettings.showTips.collectAsState()
    SettingsCard {
        SettingsToggleRow("Ask before each download", "Shows where it's saved and lets you pick Permanent or Offline", askFirst,
            { DownloadSettings.setAskFirst(context, it) }, "setting_ask_first")
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("Auto-delete watched downloads", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("Offline copies are removed this long after they finish. Permanent (pinned) ones always stay.", color = TextTertiary, fontSize = 12.sp)
        FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DownloadSettings.AUTO_DELETE_CHOICES.forEach { d ->
                FilterChip(selected = d == days, onClick = { DownloadSettings.setAutoDeleteDays(context, d) }, label = { Text(DownloadSettings.autoDeleteLabel(d)) },
                    modifier = Modifier.testTag("setting_autodelete_$d"))
            }
        }
        SettingsToggleRow("Permanent by default", "New episode downloads start as Permanent, so the timer skips them", keepDefault,
            { DownloadSettings.setKeepByDefault(context, it) }, "setting_keep_default")
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("Episode quality", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("Highest quality to save. Lower saves space and data.", color = TextTertiary, fontSize = 12.sp)
        FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DownloadSettings.QUALITY_CHOICES.forEach { q ->
                FilterChip(selected = q == quality, onClick = { DownloadSettings.setQuality(context, q) }, label = { Text(DownloadSettings.qualityLabel(q)) })
            }
        }
        Text("Downloads at once", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("Never more than one per extension, so sites don't block you.", color = TextTertiary, fontSize = 12.sp)
        FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..3).forEach { n -> FilterChip(selected = n == parallel, onClick = { DownloadSettings.setParallel(context, n) }, label = { Text("$n") }) }
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        SettingsToggleRow("Save YouTube downloads to phone", "Videos go to Movies/Yumeiro and music to Music/Yumeiro, visible in other apps", toPhone,
            { DownloadSettings.setSaveYouTubeToPhone(context, it) }, "setting_yt_to_phone")
        SettingsToggleRow("Show tips on the Downloads page", "The short \"How downloads work\" guide", tips,
            { DownloadSettings.setShowTips(context, it) }, "setting_download_tips")
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("Where files go", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Episodes and movies: ${OnlineDownloads.storagePath(context, "")}/<series folder>, private to Yumeiro and removed if you uninstall. " +
                "YouTube: ${if (toPhone) "Movies/Yumeiro and Music/Yumeiro on your phone" else "the same app folder"}.",
            color = TextTertiary, fontSize = 12.sp,
        )
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = {
            val n = OnlineDownloads.cleanup(context)
            AppMessages.success(context, if (n == 0) "Nothing to clean up" else "Removed $n old download${if (n == 1) "" else "s"}")
        }) { Text("Clean up now") }
    }
}
