/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory

/**
 * G12 X8-3. Extensions show their own fragments (for example a donation dialog) on the app's
 * activity. When the activity is recreated (font size, rotation, dark mode, process restore)
 * the FragmentManager rebuilds every saved fragment by class name with the app's class loader,
 * which cannot see extension classes: `ClassNotFoundException` and the app crashed at start
 * (device run 43762090588, `com.phisher98.donation.DonationDialogFragment`).
 *
 * Order: the app's loader, then each loaded extension's loader, then a [DroppedFragment] that
 * removes itself, so a fragment that cannot be rebuilt is lost instead of crashing the app.
 */
object ExtensionFragmentFactory : FragmentFactory() {
    private const val TAG = "YuFragments"

    /** Test hook: where extension loaders come from. */
    @Volatile internal var extensionLoaders: () -> List<ClassLoader> = { ExtensionManager.pluginClassLoaders() }

    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        runCatching { return super.instantiate(classLoader, className) }
        for (loader in runCatching { extensionLoaders() }.getOrDefault(emptyList())) {
            runCatching { return super.instantiate(loader, className) }
        }
        Log.w(TAG, "dropped saved fragment $className: its extension is not loaded")
        return DroppedFragment()
    }
}

/** Stands in for a saved fragment whose class is gone; removes itself as soon as it is created. */
class DroppedFragment : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { parentFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss() }
    }
}
