/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

/**
 * SPEED-4: a running search grows with the extension engine instead of restarting. [plan] says which extensions
 * to start searching (loaded since the search began) and which to drop (unloaded or disabled meanwhile); the ones
 * already searching keep going.
 */
object SearchJoin {
    data class Step(val start: List<String>, val drop: List<String>)

    fun plan(started: Set<String>, current: List<String>): Step {
        val now = current.toSet()
        return Step(current.filter { it !in started }.distinct(), started.filter { it !in now })
    }
}
