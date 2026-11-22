/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.profiles

import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.profiles.Profiles

/**
 * OWN-12 (owner 2026-11-22): in the profile's edit dialog, Change PIN, Remove PIN and the security-question
 * actions ran at once, without the current PIN, and a first PIN never led on to the security questions that
 * "Forgot?" uses. Pure, unit-tested; flag [FeatureFlags.Flag.PIN_GUARD].
 */
object ProfilePinGuard {
    /** Changing or removing the lock of [p] asks for its current PIN first. */
    fun needsCurrentPin(p: Profiles.Profile): Boolean = p.hasPin && FeatureFlags.isOn(FeatureFlags.Flag.PIN_GUARD)

    /** After a first PIN is saved, go on to the security questions, unless [p] already has them. */
    fun askQuestionsAfterSet(firstPin: Boolean, p: Profiles.Profile): Boolean =
        firstPin && !p.hasRecovery && FeatureFlags.isOn(FeatureFlags.Flag.PIN_GUARD)
}
