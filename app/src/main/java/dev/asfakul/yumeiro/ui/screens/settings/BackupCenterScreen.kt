/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.settings

import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.backup.Backup
import dev.asfakul.yumeiro.backup.BackupMeta
import dev.asfakul.yumeiro.backup.BackupStore
import dev.asfakul.yumeiro.backup.AutoBackupWorker
import dev.asfakul.yumeiro.backup.ResetCenter
import dev.asfakul.yumeiro.backup.Restart
import dev.asfakul.yumeiro.cache.DataCache
import dev.asfakul.yumeiro.online.OnlineErrors
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.components.DialogTone
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.components.TsukiDialog
import dev.asfakul.yumeiro.ui.components.formatFileSize
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import java.io.InputStream
import dev.asfakul.yumeiro.ui.theme.yuRound

/**
 * Backup, storage & reset (Round 12, rebuilt). Backups are saved in the phone's own storage
 * (Download/Yumeiro/Backups) and listed here, so restoring is one tap, even after a reinstall.
 * Every restore and reset makes a safety backup first and is applied at the next launch.
 */
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<BackupStore.Item>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var sections by remember { mutableStateOf(Backup.Section.entries.toSet() - Backup.Section.EXTENSION_FILES) }
    var preview by remember { mutableStateOf<Triple<Backup.Info, () -> InputStream?, CharArray?>?>(null) }
    // B20 S7: password lock for new backups, and the password a locked backup asks for.
    var lock by remember { mutableStateOf(false) }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var askPassword by remember { mutableStateOf<(() -> InputStream?)?>(null) }
    var deleting by remember { mutableStateOf<BackupStore.Item?>(null) }
    var resetting by remember { mutableStateOf<ResetCenter.Scope?>(null) }
    var erase by remember { mutableStateOf(false) }
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var location by remember { mutableStateOf(BackupStore.locationLabel(context)) }
    val repair by dev.asfakul.yumeiro.online.ExtensionManager.repair.collectAsState()

    LaunchedEffect(refresh) {
        items = runCatching { BackupStore.list(context) }.getOrDefault(emptyList())
        cacheBytes = runCatching { ResetCenter.cacheBytes(context) }.getOrDefault(0L)
        location = BackupStore.locationLabel(context)
    }

    fun work(label: String, block: suspend () -> Unit) {
        if (busy != null) return
        scope.launch {
            busy = label
            runCatching { block() }.onFailure { AppMessages.error(context, OnlineErrors.friendly(it)) }
            busy = null
            refresh++
        }
    }

    fun openPreview(open: () -> InputStream?) = work("Reading backup") {
        if (kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dev.asfakul.yumeiro.backup.BackupCrypt.isLocked(open) }) { askPassword = open; return@work }
        val info = Backup.inspect(open)
        preview = Triple(info, open, null)
    }

    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { BackupStore.rememberFolder(context, uri); AppMessages.success(context, "Backups will be saved there"); refresh++ }
    }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) openPreview { BackupStore.open(context, uri) }
    }
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    Column(Modifier.fillMaxSize()) {
        SubPageHeader(title = "Backup, storage & reset", onBack = onBack)
        if (busy != null) {
            LinearProgressIndicator(color = SpotifyGreen, trackColor = GlassFill, modifier = Modifier.fillMaxWidth().testTag("backup_busy"))
        }
        Column(
            Modifier.verticalScroll(dev.asfakul.yumeiro.ui.components.ScrollMemory.scroll("backup"))
                .padding(horizontal = 16.dp).padding(bottom = 48.dp),
        ) {
            busy?.let { Text("$it…", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            repair?.let { (done, total) ->
                Text(stringResource(R.string.downloading_your_extensions_again_of, done, total), color = TextSecondary, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp).testTag("backup_repair_progress"))
            }

            // Round 13: Restore gets its own block at the top, so it's never hidden behind the list.
            // After a reinstall Android may hide the old backups from the app; "Choose a backup file"
            // always works because the user picks the file in the system file picker.
            Block("Restore", "Bring back your settings, lists, sources and extensions from a backup") {
                val latest = items.firstOrNull()
                if (latest != null) {
                    Button(
                        onClick = { openPreview { BackupStore.open(context, latest) } },
                        enabled = busy == null, shape = yuRound(20),
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                        modifier = Modifier.fillMaxWidth().testTag("restore_latest"),
                    ) { Text(stringResource(R.string.restore_latest_backup, whenText(latest.modified)), maxLines = 1) }
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { file.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                        enabled = busy == null, shape = yuRound(20), modifier = Modifier.weight(1f).testTag("restore_pick_file"),
                    ) { Text(stringResource(R.string.choose_a_backup_file), maxLines = 1) }
                    OutlinedButton(
                        onClick = { runCatching { folder.launch(BackupStore.pickerStart()) } },
                        enabled = busy == null, shape = yuRound(20), modifier = Modifier.weight(1f).testTag("restore_find_folder"),
                    ) { Text(stringResource(R.string.find_my_backups), maxLines = 1) }
                }
                Text(
                    stringResource(R.string.you_see_whats_inside_and_pick),
                    color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }

            // Make a backup
            Block("Back up", "Saved to: $location") {
                Backup.Section.entries.filter { it != Backup.Section.SECRETS || lock }.forEach { s ->
                    CheckRow(s.label, s.hint, s in sections, "backup_section_${s.name.lowercase()}") { on ->
                        sections = if (on) sections + s else sections - s
                    }
                }
                SettingsDivider()
                CheckRow("Lock with a password", "Encrypts the whole backup. Needed to include keys and sign-ins", lock, "backup_lock") { on ->
                    lock = on; if (!on) { pw = ""; pw2 = "" }
                }
                val pwProblem = if (lock) dev.asfakul.yumeiro.backup.BackupCrypt.passwordProblem(pw.toCharArray(), pw2.toCharArray()) else null
                if (lock) {
                    PasswordField(pw, "Password", "backup_password") { pw = it }
                    PasswordField(pw2, "Password again", "backup_password_again") { pw2 = it }
                    Text(
                        if (pw.isNotEmpty() && pwProblem != null) pwProblem
                        else "Yumeiro never saves this password. Without it, nobody (not even you) can open this backup.",
                        color = if (pw.isNotEmpty() && pwProblem != null) dev.asfakul.yumeiro.ui.theme.DangerTone else TextTertiary,
                        fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp).testTag("backup_password_hint"),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            val password = if (lock) pw.toCharArray() else null
                            work(if (lock) "Locking and backing up" else "Backing up") {
                                try {
                                    val saved = BackupStore.save(context, "manual", sections, password)
                                    AppMessages.success(context, (if (password != null) "Locked backup saved · " else "Backup saved · ") + formatFileSize(saved.size))
                                } finally { password?.fill(' ') }
                            }
                        },
                        enabled = busy == null && sections.any { it != Backup.Section.SECRETS || lock } && pwProblem == null,
                        shape = yuRound(20),
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                        modifier = Modifier.testTag("backup_now"),
                    ) { Text(stringResource(R.string.back_up_now)) }
                    OutlinedButton(onClick = { runCatching { folder.launch(BackupStore.pickerStart()) } }, shape = yuRound(20),
                        modifier = Modifier.testTag("backup_choose_folder")) { Text(stringResource(R.string.choose_folder)) }
                }
                if (BackupStore.folderUri(context) != null) {
                    TextButton(onClick = { BackupStore.forgetFolder(context); refresh++ }, modifier = Modifier.testTag("backup_forget_folder")) {
                        Text(stringResource(R.string.use_download_yumeiro_backups_again))
                    }
                }
                if (BackupStore.legacyNeedsPermission(context)) {
                    TextButton(onClick = { storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }, modifier = Modifier.testTag("backup_allow_storage")) {
                        Text(stringResource(R.string.allow_storage_access_so_backups_survive))
                    }
                }
            }

            // Backups on this phone
            Block("Backups on this phone", if (items.isEmpty()) null else "Tap one to see what's inside and restore it") {
                if (items.isEmpty()) {
                    Text(stringResource(R.string.no_backups_found_yet_after_reinstalling),
                        color = TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp).testTag("backup_list_empty"))
                }
                items.forEachIndexed { i, item ->
                    BackupRow(item, i, onRestore = { openPreview { BackupStore.open(context, item) } }, onDelete = { deleting = item })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    OutlinedButton(onClick = { runCatching { folder.launch(BackupStore.pickerStart()) } }, shape = yuRound(20),
                        modifier = Modifier.testTag("backup_find")) { Text(stringResource(R.string.find_my_backups)) }
                    OutlinedButton(onClick = { file.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, shape = yuRound(20),
                        modifier = Modifier.testTag("backup_pick_file")) { Text(stringResource(R.string.open_a_file)) }
                }
            }

            // Automatic backups
            var autoOn by remember { mutableStateOf(BackupMeta.autoEnabled(context)) }
            var autoDays by remember { mutableIntStateOf(BackupMeta.autoEveryDays(context)) }
            var autoKeep by remember { mutableIntStateOf(BackupMeta.autoKeep(context)) }
            Block("Automatic backups", BackupMeta.lastAutoAt(context).takeIf { it > 0 }?.let { "Last one: " + whenText(it) }) {
                SettingsToggleRow("Back up automatically", "Only when something changed, while the phone is idle", autoOn, {
                    autoOn = it; BackupMeta.setAutoEnabled(context, it); AutoBackupWorker.schedule(context, replace = true)
                }, "backup_auto")
                ChoiceRow("How often", listOf(1 to "Daily", 3 to "Every 3 days", 7 to "Weekly"), autoDays, "backup_auto_days", autoOn) {
                    autoDays = it; BackupMeta.setAutoEveryDays(context, it); AutoBackupWorker.schedule(context, replace = true)
                }
                ChoiceRow("Keep", listOf(3 to "3", 5 to "5", 10 to "10"), autoKeep, "backup_auto_keep", autoOn) {
                    autoKeep = it; BackupMeta.setAutoKeep(context, it)
                }
            }

            // Storage and cache
            var cacheOn by remember { mutableStateOf(DataCache.Settings.enabled) }
            var fresh by remember { mutableIntStateOf(DataCache.Settings.freshHours) }
            var keep by remember { mutableIntStateOf(DataCache.Settings.keepDays) }
            var maxMb by remember { mutableIntStateOf((DataCache.Settings.maxBytes / (1024 * 1024)).toInt()) }
            var cookieDays by remember { mutableIntStateOf(DataCache.Settings.cookieDays) }
            Block("Saved pages and cache", "Using ${formatFileSize(cacheBytes).let { if (it == "Unknown") "0 MB" else it }}. Home rows, details and searches open instantly instead of loading every time") {
                SettingsToggleRow("Save loaded pages on the phone", "Opens Yumix pages instantly, refreshes them when they're old", cacheOn, {
                    cacheOn = it; DataCache.Settings.set(enabled = it)
                }, "cache_enabled")
                ChoiceRow("Refresh after", listOf(1 to "1 h", 6 to "6 h", 12 to "12 h", 24 to "1 day"), fresh, "cache_fresh", cacheOn) {
                    fresh = it; DataCache.Settings.set(freshHours = it)
                }
                ChoiceRow("Delete after", listOf(3 to "3 days", 7 to "7 days", 14 to "14 days", 30 to "30 days"), keep, "cache_keep", cacheOn) {
                    keep = it; DataCache.Settings.set(keepDays = it)
                }
                ChoiceRow("Up to", listOf(64 to "64 MB", 128 to "128 MB", 256 to "256 MB", 512 to "512 MB"), maxMb, "cache_max", cacheOn) {
                    maxMb = it; DataCache.Settings.set(maxMb = it)
                }
                ChoiceRow("Clear website cookies every", listOf(7 to "7 days", 30 to "30 days", 90 to "90 days", 0 to "Never"), cookieDays, "cookie_days", true) {
                    cookieDays = it; DataCache.Settings.set(cookieDays = it)
                }
                SettingsDivider()
                SettingsActionRow(ResetCenter.Scope.CACHE.label, ResetCenter.Scope.CACHE.hint, "reset_cache") {
                    work("Clearing cache") {
                        val freed = ResetCenter.clearCache(context)
                        dev.asfakul.yumeiro.online.OnlineRepository.clearCaches()
                        AppMessages.success(context, "Cache cleared · ${formatFileSize(freed).let { if (it == "Unknown") "0 MB" else it }} freed")
                    }
                }
                SettingsActionRow(ResetCenter.Scope.COOKIES.label, ResetCenter.Scope.COOKIES.hint, "reset_cookies") {
                    ResetCenter.clearCookies(); AppMessages.success(context, "Cookies cleared")
                }
            }

            // Reset
            Block("Reset", "A backup is saved first, then Yumeiro restarts") {
                ResetCenter.Scope.entries.filter { it.needsRestart }.forEach { s ->
                    SettingsActionRow(s.label, s.hint, "reset_${s.id}", danger = true) { resetting = s }
                }
                SettingsDivider()
                SettingsActionRow("Erase everything", "Like a fresh install. Backups in Download › Yumeiro stay", "reset_erase", danger = true) { erase = true }
            }

            Text(
                "Never in a backup: downloaded videos and the app lock. Keys and sign-ins only go into a backup locked with a password. " +
                    "Extensions are saved as a list and download again from their own repositories after a restore.",
                color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp),
            )
        }
    }

    askPassword?.let { open ->
        UnlockDialog(onDismiss = { askPassword = null }) { typed ->
            askPassword = null
            work("Unlocking backup") {
                try {
                    val info = Backup.inspect(open, typed)
                    preview = Triple(info, open, typed)
                } catch (e: dev.asfakul.yumeiro.backup.BackupCrypt.WrongPassword) {
                    typed.fill(' '); AppMessages.error(context, e.message ?: "Wrong password"); askPassword = open
                } catch (t: Throwable) { typed.fill(' '); throw t }
            }
        }
    }
    preview?.let { (info, open, password) ->
        RestoreDialog(info, onDismiss = { password?.fill(' '); preview = null }) { chosen ->
            preview = null
            work("Restoring") {
                try {
                    // Safety first: what's on the phone now goes into a backup before it's replaced.
                    runCatching { BackupStore.save(context, "safety", Backup.Section.entries.toSet() - Backup.Section.EXTENSION_FILES) }
                    Backup.stage(context, open, chosen, password)
                } finally { password?.fill(' ') }
                Restart.now(context)
            }
        }
    }
    deleting?.let { item ->
        TsukiDialog(
            tone = DialogTone.DANGER,
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_this_backup)) },
            text = { Text(stringResource(R.string.is_removed_from_the_phone_this, item.name)) },
            confirmButton = { TextButton(onClick = {
                deleting = null
                if (BackupStore.delete(context, item)) AppMessages.success(context, "Backup deleted") else AppMessages.error(context, "Couldn't delete that file")
                refresh++
            }, modifier = Modifier.testTag("backup_delete_confirm")) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    resetting?.let { s ->
        TsukiDialog(
            tone = DialogTone.WARNING,
            onDismissRequest = { resetting = null },
            title = { Text("${s.label}?") },
            text = { Text(stringResource(R.string.a_backup_is_saved_first_so, s.hint)) },
            confirmButton = { TextButton(onClick = {
                resetting = null
                work("Saving a backup first") {
                    BackupStore.save(context, "safety", Backup.Section.entries.toSet() - Backup.Section.EXTENSION_FILES)
                    ResetCenter.stage(context, setOf(s))
                    Restart.now(context)
                }
            }, modifier = Modifier.testTag("reset_confirm")) { Text(stringResource(R.string.back_up_and_reset)) } },
            dismissButton = { TextButton(onClick = { resetting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (erase) {
        TsukiDialog(
            tone = DialogTone.CRITICAL,
            onDismissRequest = { erase = false },
            title = { Text(stringResource(R.string.erase_everything)) },
            text = { Text(stringResource(R.string.every_setting_list_source_and_extension)) },
            confirmButton = { TextButton(onClick = {
                erase = false
                work("Saving a backup first") {
                    BackupStore.save(context, "safety", Backup.Section.entries.toSet() - Backup.Section.EXTENSION_FILES)
                    ResetCenter.eraseEverything(context)
                }
            }, modifier = Modifier.testTag("reset_erase_confirm")) { Text(stringResource(R.string.back_up_and_erase)) } },
            dismissButton = { TextButton(onClick = { erase = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun RestoreDialog(info: Backup.Info, onDismiss: () -> Unit, onRestore: (Set<Backup.Section>) -> Unit) {
    val available = info.sections
    var chosen by remember(info) { mutableStateOf(available - Backup.Section.EXTENSION_FILES) }
    var useFiles by remember(info) { mutableStateOf(false) }
    TsukiDialog(
        tone = DialogTone.WARNING,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_this_backup)) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Text(buildString {
                    append("Made ").append(whenText(info.createdAt))
                    if (info.version.isNotBlank()) append(" with Yumeiro ").append(info.version)
                    if (info.device.isNotBlank()) append(" on ").append(info.device)
                    append('.')
                }, fontSize = 13.sp, color = TextSecondary)
                val facts = listOfNotNull(
                    info.counts.optInt("history").takeIf { it > 0 }?.let { "$it in history" },
                    info.counts.optInt("myList").takeIf { it > 0 }?.let { "$it in My List" },
                    info.extensions.takeIf { it > 0 }?.let { "$it extensions" },
                    info.counts.optInt("repositories").takeIf { it > 0 }?.let { "$it repositories" },
                )
                if (facts.isNotEmpty()) Text(facts.joinToString(" · "), fontSize = 13.sp, color = TextPrimary, modifier = Modifier.padding(top = 6.dp).testTag("restore_counts"))
                Spacer(Modifier.height(8.dp))
                Backup.Section.entries.filter { it in available && it != Backup.Section.EXTENSION_FILES }.forEach { s ->
                    CheckRow(s.label, s.hint, s in chosen, "restore_section_${s.name.lowercase()}") { on -> chosen = if (on) chosen + s else chosen - s }
                }
                if (Backup.Section.SOURCES in chosen && info.extensions > 0) {
                    Text(stringResource(R.string.extensions), fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    RadioRow("Download them again (recommended)", "Fresh copies from their own repositories, checked like an update", !useFiles, "restore_ext_redownload") { useFiles = false }
                    if (info.hasCode) {
                        RadioRow("Use the copies in this backup", "Works offline. Only for a backup you made yourself", useFiles, "restore_ext_files") { useFiles = true }
                    }
                }
                if (Backup.Section.SECRETS in chosen) {
                    Text(stringResource(R.string.restore_secrets_note),
                        fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 8.dp).testTag("restore_secrets_note"))
                }
                Text(stringResource(R.string.whats_on_this_phone_now_is), fontSize = 12.sp, color = TextTertiary, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onRestore(if (useFiles && info.hasCode) chosen + Backup.Section.EXTENSION_FILES else chosen) },
                enabled = chosen.isNotEmpty(),
                modifier = Modifier.testTag("restore_confirm"),
            ) { Text(stringResource(R.string.restore)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** B20 S7: a password field (hidden text, no suggestions, no autofill of other apps' passwords). */
@Composable
private fun PasswordField(value: String, label: String, tag: String, onChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = value, onValueChange = { onChange(it.take(256)) }, label = { Text(label) }, singleLine = true,
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(tag),
    )
}

/** B20 S7: asks for a locked backup's password. */
@Composable
private fun UnlockDialog(onDismiss: () -> Unit, onUnlock: (CharArray) -> Unit) {
    var typed by remember { mutableStateOf("") }
    TsukiDialog(
        tone = DialogTone.WARNING,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_locked_title)) },
        text = {
            Column {
                Text(stringResource(R.string.restore_locked_hint), fontSize = 13.sp, color = TextSecondary)
                PasswordField(typed, "Password", "restore_password") { typed = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onUnlock(typed.toCharArray()); typed = "" }, enabled = typed.isNotEmpty(), modifier = Modifier.testTag("restore_unlock")) { Text(stringResource(R.string.unlock)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Block(title: String, hint: String?, content: @Composable () -> Unit) {
    Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 18.dp))
    if (hint != null) Text(hint, color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp)
    Spacer(Modifier.height(8.dp))
    Card(Modifier.fillMaxWidth(), shape = yuRound(14), colors = CardDefaults.cardColors(containerColor = GlassFill)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) { content() }
    }
}

@Composable
private fun CheckRow(title: String, hint: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onChange(!checked) }.padding(vertical = 4.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange, colors = CheckboxDefaults.colors(checkedColor = SpotifyGreen, checkmarkColor = OnAccent))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun RadioRow(title: String, hint: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(vertical = 4.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = SpotifyGreen))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp)
            Text(hint, color = TextTertiary, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

/** A label with a few chips (the chosen one filled), for small fixed choices. */
@Composable
private fun ChoiceRow(title: String, options: List<Pair<Int, String>>, value: Int, tag: String, enabled: Boolean, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(tag)) {
        Text(title, color = if (enabled) TextPrimary else TextTertiary, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (v, label) ->
                val on = v == value
                if (on) {
                    Button(onClick = { onPick(v) }, enabled = enabled, shape = yuRound(16),
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 36.dp).testTag("${tag}_$v")) { Text(label, fontSize = 12.sp, maxLines = 1) }
                } else {
                    OutlinedButton(onClick = { onPick(v) }, enabled = enabled, shape = yuRound(16),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 36.dp).testTag("${tag}_$v")) { Text(label, fontSize = 12.sp, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun BackupRow(item: BackupStore.Item, index: Int, onRestore: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onRestore).padding(vertical = 6.dp).testTag("backup_item_$index"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Restore, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when (item.kind) { "auto" -> "Automatic backup"; "safety" -> "Saved before a restore or reset"; else -> "Backup" },
                color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(
                listOf(whenText(item.modified), formatFileSize(item.size).takeIf { it != "Unknown" }).filterNotNull().joinToString(" · "),
                color = TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.testTag("backup_item_${index}_delete")) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete_backup), tint = TextTertiary)
        }
    }
}

private fun whenText(at: Long): String =
    if (at <= 0) "at an unknown time"
    else android.text.format.DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()

