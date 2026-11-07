@file:Suppress("unused", "UNUSED_PARAMETER")

package com.lagradost.cloudstream3.ui.settings

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.BasePreferenceFragmentCompat

class SettingsFragment : BaseFragment() {
    companion object {
        /** CloudStream draws its own toolbar; MT's extension settings host already has one. */
        fun Fragment.setUpToolbar(title: String) {}
        fun Fragment.setUpToolbar(title: Int) {}
    }
}

/** CloudStream's "General" settings page; extensions subclass it to add preferences. */
open class SettingsGeneral : BasePreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        if (preferenceScreen == null) preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
    }
}
