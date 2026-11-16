/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.video

/**
 * R17 (G3 proposal "one Back on phones"): what one Back press does in the video player.
 *
 * Remote (TV, keyboard): stepped, as on every TV player. Back first hides the controls, then
 * leaves fullscreen, then leaves the player. A remote has no other way to hide the controls.
 *
 * Touch (phones, tablets): one Back leaves the player (to the floating window, PiP or closed, as
 * set in Settings). Before, a fullscreen video needed two presses: the first only left fullscreen,
 * which looked like Back did nothing. One exception is kept on purpose: on a watch page the
 * fullscreen video is the page's own player, so Back returns to that page, as YouTube does.
 *
 * A locked screen never leaves: Back only shows the unlock button. Pure; `PlayerBackRuleTest`.
 */
internal object PlayerBackRule {
    enum class Step { HIDE_CONTROLS, EXIT_FULLSCREEN, SHOW_UNLOCK, LEAVE }

    fun step(
        keyDriven: Boolean,
        controlsVisible: Boolean,
        locked: Boolean,
        ended: Boolean,
        error: Boolean,
        chromeHidden: Boolean,
        fullscreen: Boolean,
        onWatchPage: Boolean,
    ): Step = when {
        locked -> Step.SHOW_UNLOCK
        keyDriven && controlsVisible && !ended && !error && !chromeHidden -> Step.HIDE_CONTROLS
        fullscreen && (keyDriven || onWatchPage) -> Step.EXIT_FULLSCREEN
        else -> Step.LEAVE
    }

    enum class Leave { CLOSE, MINIMIZE, PIP }

    /**
     * Where leaving the player goes. BUG-7 7c (device run 43935805199, both phones): with an error
     * card on screen, Back minimized the player and the "error while floating" rule expanded it again
     * at once, so three Back presses did nothing and the app looked frozen. A player in error has
     * nothing to keep playing, so leaving closes it.
     */
    fun leave(error: Boolean, backAction: dev.asfakul.yumeiro.player.PlayerSettings.BackAction, pipReady: Boolean, canMinimize: Boolean): Leave = when {
        error -> Leave.CLOSE
        backAction == dev.asfakul.yumeiro.player.PlayerSettings.BackAction.PIP && pipReady -> Leave.PIP
        backAction != dev.asfakul.yumeiro.player.PlayerSettings.BackAction.CLOSE && canMinimize -> Leave.MINIMIZE
        else -> Leave.CLOSE
    }
}
