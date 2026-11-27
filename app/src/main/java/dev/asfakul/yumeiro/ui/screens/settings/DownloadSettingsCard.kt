/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import dev.asfakul.yumeiro.l10n.tr
import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
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
    val downloader by DownloadSettings.downloader.collectAsState()
    val installed = remember { dev.asfakul.yumeiro.online.download.ExternalDownloader.installed(context) }
    val appPath by dev.asfakul.yumeiro.ui.screens.downloads.rememberStoragePath(context, "")
    SettingsCard {
        SettingsToggleRow(tr(R.string.ask_before_each_download, "Ask before each download"), tr(R.string.shows_where_its_saved_and_lets, "Shows where it's saved and lets you pick Permanent or Offline"), askFirst,
            { DownloadSettings.setAskFirst(context, it) }, "setting_ask_first")
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text(stringResource(R.string.download_with), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            if (installed.isEmpty()) tr(R.string.yumeiros_own_downloader_install_a_free, "Yumeiro's own downloader. Install a free download manager such as 1DM or ADM to hand videos to it instead.")
            else tr(R.string.send_download_this_video_to_another, "Send \"Download this video\" to another app. Whole-season and YouTube downloads always use Yumeiro."),
            color = TextTertiary, fontSize = 12.sp,
        )
        FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val options = listOf(dev.asfakul.yumeiro.online.download.ExternalDownloader.IN_APP) + installed.map { it.pkg } +
                dev.asfakul.yumeiro.online.download.ExternalDownloader.CHOOSER + listOfNotNull(downloader.takeIf { d -> d.isNotEmpty() && d != dev.asfakul.yumeiro.online.download.ExternalDownloader.CHOOSER && installed.none { it.pkg == d } })
            options.forEach { pkg ->
                FilterChip(selected = pkg == downloader, onClick = { DownloadSettings.setDownloader(context, pkg) },
                    label = { Text(dev.asfakul.yumeiro.online.download.ExternalDownloader.label(pkg)) }, modifier = Modifier.testTag("setting_downloader_${pkg.ifEmpty { "app" }}"))
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text(stringResource(R.string.auto_delete_watched_downloads), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.offline_copies_are_removed_this_long), color = TextTertiary, fontSize = 12.sp)
        FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DownloadSettings.AUTO_DELETE_CHOICES.forEach { d ->
                FilterChip(selected = d == days, onClick = { DownloadSettings.setAutoDeleteDays(context, d) }, label = { Text(DownloadSettings.autoDeleteLabel(d)) },
                    modifier = Modifier.testTag("setting_autodelete_$d"))
            }
        }
        SettingsToggleRow(tr(R.string.permanent_by_default, "Permanent by default"), tr(R.string.new_episode_downloads_start_as_permanent, "New episode downloads start as Permanent, so the timer skips them"), keepDefault,
            { DownloadSettings.setKeepByDefault(context, it) }, "setting_keep_default")
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text(stringResource(R.string.episode_quality), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.highest_quality_to_save_lower_saves), color = TextTertiary, fontSize = 12.sp)
        FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DownloadSettings.QUALITY_CHOICES.forEach { q ->
                FilterChip(selected = q == quality, onClick = { DownloadSettings.setQuality(context, q) }, label = { Text(DownloadSettings.qualityLabel(q)) })
            }
        }
        Text(stringResource(R.string.downloads_at_once), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.never_more_than_one_per_extension), color = TextTertiary, fontSize = 12.sp)
        FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..3).forEach { n -> FilterChip(selected = n == parallel, onClick = { DownloadSettings.setParallel(context, n) }, label = { Text("$n") }) }
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        SettingsToggleRow(tr(R.string.save_youtube_and_link_downloads_to, "Save YouTube and link downloads to phone"), tr(R.string.videos_go_to_movies_yumeiro_and, "Videos go to Movies/Yumeiro and music to Music/Yumeiro, visible in the gallery and other apps"), toPhone,
            { DownloadSettings.setSaveYouTubeToPhone(context, it) }, "setting_yt_to_phone")
        SettingsToggleRow(tr(R.string.show_tips_on_the_downloads_page, "Show tips on the Downloads page"), tr(R.string.the_short_how_downloads_work_guide, "The short \"How downloads work\" guide"), tips,
            { DownloadSettings.setShowTips(context, it) }, "setting_download_tips")
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text(stringResource(R.string.where_files_go), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Episodes and movies: ${appPath ?: "app storage"}/<series folder>, private to Yumeiro and removed if you uninstall. " +
                "YouTube: ${if (toPhone) "Movies/Yumeiro and Music/Yumeiro on your phone" else "the same app folder"}.",
            color = TextTertiary, fontSize = 12.sp,
        )
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = {
            val n = OnlineDownloads.cleanup(context)
            AppMessages.success(context, if (n == 0) tr(R.string.nothing_to_clean_up, "Nothing to clean up") else "Removed $n old download${if (n == 1) "" else "s"}")
        }) { Text(stringResource(R.string.clean_up_now)) }
    }
}
