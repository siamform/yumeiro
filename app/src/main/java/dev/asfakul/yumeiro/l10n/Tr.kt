/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.l10n

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import java.util.Locale

/**
 * F19: user-facing text outside composables (view models, resolvers, notifications, error messages, defaults) in
 * the app's language, from the same offline resources as `stringResource` (values-<lang>/strings.xml). No
 * translation service and no network.
 *
 * Each call carries its English text as well: `tr(R.string.torrent_cant_play, "Torrent can't play")`. The English
 * is what plays before the app has started (pure JVM tests, early start-up) and the source the translators work
 * from; `L10nGuardTest` keeps it identical to values/strings.xml, so the two can never drift apart.
 *
 * Some text is made once per process (labels of enums and lists). So a language change restarts the app
 * ([AppLanguage.set], and [MainActivity] when Android's own per-app language page changed it): every line is then
 * in the new language, never a mix. Settings, library, history and positions are saved files and stay; the app
 * opens Settings again. Plurals: [trPlural] (Android quantity rules for every language).
 */
object Tr {
    @Volatile private var res: Resources? = null

    /** The language (a tag, "" = device) this process's text was made in; null before start-up. */
    @Volatile var processTag: String? = null
        private set

    /** Called at start-up with the application in the picked language ([tag]), and with each activity. */
    fun use(context: Context, tag: String? = null) {
        res = context.resources
        if (tag != null) markProcess(tag)
    }

    /** The first language text is made in stays this process's language. */
    internal fun markProcess(tag: String) { if (processTag == null) processTag = tag }

    /** True when the language is now [tag] but this process already made text in another one. Pure. */
    fun staleFor(tag: String): Boolean = processTag != null && processTag != tag

    /** For tests: back to the English source text. */
    fun reset() { res = null; processTag = null }

    internal fun locale(): Locale = res?.configuration?.locales?.get(0) ?: Locale.getDefault()

    fun get(@StringRes id: Int, en: String): String {
        val r = res ?: return en
        return try { r.getString(id) } catch (_: Resources.NotFoundException) { en }
    }

    fun format(@StringRes id: Int, en: String, args: Array<out Any?>): String {
        val pattern = get(id, en)
        return try { String.format(locale(), pattern, *args) } catch (_: java.util.IllegalFormatException) {
            String.format(Locale.ROOT, en, *args)
        }
    }

    fun plural(id: Int, count: Int, enOne: String, enOther: String, args: Array<out Any?>): String {
        val r = res
        val all = if (args.isEmpty()) arrayOf<Any?>(count) else args
        if (r != null) try { return r.getQuantityString(id, count, *all) } catch (_: Exception) { }
        return String.format(Locale.ROOT, if (count == 1) enOne else enOther, *all)
    }
}

/** See [Tr]. */
fun tr(@StringRes id: Int, en: String): String = Tr.get(id, en)

/** See [Tr]; [en] uses `%1$s`, `%2$s`… for [args]. */
fun tr(@StringRes id: Int, en: String, vararg args: Any?): String = Tr.format(id, en, args)

/** A count in the app's language with the right plural form (`%1$d` in both English forms). */
fun trPlural(id: Int, count: Int, enOne: String, enOther: String, vararg args: Any?): String = Tr.plural(id, count, enOne, enOther, args)
