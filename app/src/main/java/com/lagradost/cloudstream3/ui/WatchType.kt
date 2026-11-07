@file:Suppress("unused")

package com.lagradost.cloudstream3.ui

import androidx.fragment.app.Fragment
import androidx.preference.PreferenceFragmentCompat

/** Bookmark state, same ids as CloudStream so stored values stay interchangeable. */
enum class WatchType(val internalId: Int, val stringRes: Int, val iconRes: Int) {
    WATCHING(0, 0, 0),
    COMPLETED(1, 0, 0),
    ONHOLD(2, 0, 0),
    DROPPED(3, 0, 0),
    PLANTOWATCH(4, 0, 0),
    NONE(5, 0, 0);

    companion object {
        fun fromInternalId(id: Int?): WatchType = entries.find { it.internalId == id } ?: NONE
    }
}

enum class SyncWatchType(val internalId: Int, val stringRes: Int, val iconRes: Int) {
    NONE(-1, 0, 0),
    WATCHING(0, 0, 0),
    COMPLETED(1, 0, 0),
    ONHOLD(2, 0, 0),
    DROPPED(3, 0, 0),
    PLANTOWATCH(4, 0, 0),
    REWATCHING(5, 0, 0);

    companion object {
        fun fromInternalId(id: Int?): SyncWatchType = entries.find { it.internalId == id } ?: NONE
    }
}

/** Base classes of CloudStream screens some extensions subclass for their own settings pages. */
abstract class BaseFragment : Fragment()

abstract class BasePreferenceFragmentCompat : PreferenceFragmentCompat()
