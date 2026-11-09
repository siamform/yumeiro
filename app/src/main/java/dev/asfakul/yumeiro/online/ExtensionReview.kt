/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The "bridge" between download and install: the safety report ([ExtensionScanner]) is shown to the
 * user, who decides. The sheet is drawn by `ExtensionReviewHost` at the app root.
 */
object ExtensionReview {

    /** How much to ask before installing. */
    enum class Mode {
        /** Always show the report (single install / update the user tapped). */
        ALWAYS,
        /** Only ask when something was flagged (bulk installs). */
        IF_RISKY,
        /** Never ask (background auto-update): install unless it's flagged as dangerous. */
        AUTO,
    }

    data class Request(
        val name: String, val version: Int?, val source: String, val isUpdate: Boolean,
        val report: ExtensionScanner.Report, internal val answer: CompletableDeferred<Boolean>,
    )

    private val _pending = MutableStateFlow<Request?>(null)
    val pending: StateFlow<Request?> = _pending.asStateFlow()
    private val queue = Mutex()

    /** Set by the UI host while it's on screen; without it, [Mode.AUTO] rules apply. */
    @Volatile var hostAttached = false

    /** Decision without asking: dangerous files are never installed silently. Pure → unit tested. */
    fun autoDecision(report: ExtensionScanner.Report) = report.readable && report.level != ExtensionScanner.Level.DANGER

    fun needsAsking(mode: Mode, report: ExtensionScanner.Report): Boolean = when (mode) {
        Mode.ALWAYS -> true
        Mode.IF_RISKY -> report.level != ExtensionScanner.Level.SAFE || !report.readable
        Mode.AUTO -> false
    }

    /** Returns true to go ahead with the install. */
    suspend fun decide(mode: Mode, name: String, version: Int?, source: String, isUpdate: Boolean, report: ExtensionScanner.Report): Boolean {
        if (!needsAsking(mode, report) || !hostAttached) return autoDecision(report)
        return queue.withLock {
            val answer = CompletableDeferred<Boolean>()
            _pending.value = Request(name, version, source, isUpdate, report, answer)
            try { answer.await() } finally { if (_pending.value?.answer === answer) _pending.value = null }
        }
    }

    fun answer(install: Boolean) { _pending.value?.answer?.complete(install) }

    /** Thrown when the user chose not to install. The UI shows no error for it. */
    class Declined(name: String) : ExtensionManager.ExtensionException("$name wasn't installed")
}
