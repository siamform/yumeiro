package dev.asfakul.yumeiro.online.download

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import dev.asfakul.yumeiro.online.download.OnlineDownloads.Item
import dev.asfakul.yumeiro.online.download.OnlineDownloads.Status
import dev.asfakul.yumeiro.profiles.Profiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * DL-H (ADR-041): the private vault. Hiding a finished download moves its file from the Downloads
 * folder (and deletes its copy in the phone's Movies / Music folder) into `filesDir/private/<profile>/`,
 * which no gallery, file manager or other app can read, and takes it out of every normal list. Only the
 * profile that hid it sees it, in Downloads → Private, after the phone's biometric / screen lock or the
 * profile PIN. "Unhide" moves it back to the Downloads folder.
 *
 * [hide] and [unhide] copy files: call them off the main thread.
 */
object PrivateDownloads {
    private const val PREFS = "private_vault"

    fun vaultDir(context: Context, profile: String): File = File(File(context.applicationContext.filesDir, "private"), profile)

    /** Every item a normal list may show. */
    fun visible(list: List<Item>): List<Item> = list.filter { !it.hidden }

    /** The current profile's hidden downloads. */
    fun vaultItems(context: Context): List<Item> {
        OnlineDownloads.ensureLoaded(context)
        val me = Profiles.currentId(context)
        return OnlineDownloads.items.value.filter { it.hidden && it.profile == me }
    }

    /** Downloads list without hidden items, for screens. */
    @Composable
    fun visibleState(): State<List<Item>> {
        val all = OnlineDownloads.items.collectAsState()
        return remember { derivedStateOf { visible(all.value) } }
    }

    /** Moves a finished download into the vault. False when it is not finished, already hidden or the copy failed. */
    fun hide(context: Context, id: String): Boolean {
        OnlineDownloads.ensureLoaded(context)
        val item = OnlineDownloads.get(id) ?: return false
        if (item.hidden || item.status != Status.DONE) return false
        val me = Profiles.currentId(context)
        val named = if (item.fileName.isBlank()) item.copy(fileName = OnlineDownloads.safeName(item.title) + "_" + id + ".mp4") else item
        val target = named.copy(hidden = true, profile = me)
        val dest = OnlineDownloads.fileOf(context, target)
        val src = OnlineDownloads.fileOf(context, named)
        val moved = when {
            src.isFile -> move(src, dest)
            item.publicUri != null -> copyUri(context, Uri.parse(item.publicUri), dest)
            else -> false
        }
        if (!moved) return false
        item.publicUri?.let { u -> runCatching { context.contentResolver.delete(Uri.parse(u), null, null) } }
        OnlineDownloads.replace(id) { target.copy(publicUri = null) }
        return true
    }

    /** Moves a hidden download back to the Downloads folder. Only the profile that hid it can. */
    fun unhide(context: Context, id: String): Boolean {
        OnlineDownloads.ensureLoaded(context)
        val item = OnlineDownloads.get(id) ?: return false
        if (!item.hidden || item.profile != Profiles.currentId(context)) return false
        val back = item.copy(hidden = false)
        val src = OnlineDownloads.fileOf(context, item)
        if (!src.isFile || !move(src, OnlineDownloads.fileOf(context, back))) return false
        OnlineDownloads.replace(id) { back }
        return true
    }

    /** A profile is removed: its hidden downloads go with it. */
    fun removeProfile(context: Context, profile: String) {
        OnlineDownloads.ensureLoaded(context)
        OnlineDownloads.items.value.filter { it.hidden && it.profile == profile }.forEach { OnlineDownloads.delete(context, it.id) }
        vaultDir(context, profile).deleteRecursively()
    }

    // region stealth: the Private entry is not shown; a long press on the Downloads title opens it

    private val _stealth = MutableStateFlow(false)
    val stealth: StateFlow<Boolean> = _stealth.asStateFlow()
    fun loadStealth(context: Context) {
        _stealth.value = context.getSharedPreferences(Profiles.prefsName(context, PREFS), Context.MODE_PRIVATE).getBoolean("stealth", false)
    }
    fun setStealth(context: Context, on: Boolean) {
        _stealth.value = on
        context.getSharedPreferences(Profiles.prefsName(context, PREFS), Context.MODE_PRIVATE).edit().putBoolean("stealth", on).apply()
    }

    // endregion

    private fun move(src: File, dest: File): Boolean {
        if (src.canonicalPath == dest.canonicalPath) return true
        dest.parentFile?.mkdirs()
        if (src.renameTo(dest)) return true
        // Different storage (Android/data → app files): copy, check, then delete.
        if ((dest.parentFile?.usableSpace ?: 0L) in 1 until src.length()) return false // 0 = unknown
        val tmp = File(dest.path + ".part")
        val ok = runCatching { src.inputStream().use { i -> tmp.outputStream().use { o -> i.copyTo(o, 256 * 1024) } } }.isSuccess &&
            tmp.length() == src.length() && tmp.renameTo(dest)
        if (!ok) { tmp.delete(); return false }
        src.delete()
        return true
    }

    private fun copyUri(context: Context, uri: Uri, dest: File): Boolean {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.path + ".part")
        val ok = runCatching {
            context.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { o -> i.copyTo(o, 256 * 1024) } }
        }.isSuccess && tmp.length() > 0 && tmp.renameTo(dest)
        if (!ok) tmp.delete()
        return ok
    }
}
