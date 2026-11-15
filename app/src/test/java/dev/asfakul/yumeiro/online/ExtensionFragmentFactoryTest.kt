/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** G12 X8-3: a saved extension fragment must not crash the activity when it is recreated. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExtensionFragmentFactoryTest {
    private val original = ExtensionFragmentFactory.extensionLoaders
    @After fun tearDown() { ExtensionFragmentFactory.extensionLoaders = original }

    /** An app-side class still loads normally. */
    @Test fun appFragmentsStillLoad() {
        val f = ExtensionFragmentFactory.instantiate(javaClass.classLoader!!, KeptDialog::class.java.name)
        assertTrue(f is KeptDialog)
    }

    /** A class only an extension loader knows is found there. */
    @Test fun extensionLoaderIsTried() {
        val hidden = KeptDialog::class.java.name
        val appLoader = object : ClassLoader(null) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> =
                if (name == hidden) throw ClassNotFoundException(name) else javaClass.classLoader!!.loadClass(name)
        }
        ExtensionFragmentFactory.extensionLoaders = { listOf(KeptDialog::class.java.classLoader!!) }
        assertTrue(ExtensionFragmentFactory.instantiate(appLoader, hidden) is KeptDialog)
    }

    /**
     * Recreating an activity with a saved fragment whose class is gone: with the default factory
     * this throws Fragment.InstantiationException (the crash), with ours the fragment is dropped.
     */
    @Test fun missingClassIsDroppedOnRecreate() {
        ExtensionFragmentFactory.extensionLoaders = { emptyList() }
        val state = savedStateWithFragment("com.phisher98.donation.DonationDialogFragment")
        val default = runCatching { Robolectric.buildActivity(Host::class.java).create(state).get() }
        assertTrue("default factory should crash: $default", default.exceptionOrNull() != null)

        Host.useOurs = true
        try {
            val act = Robolectric.buildActivity(Host::class.java).create(state).start().resume().get()
            act.supportFragmentManager.executePendingTransactions()
            assertEquals(0, act.supportFragmentManager.fragments.size)
        } finally { Host.useOurs = false }
    }

    /** Saves an activity whose dialog fragment is renamed in the bundle to a class nobody has. */
    private fun savedStateWithFragment(className: String): Bundle {
        val c = Robolectric.buildActivity(Host::class.java).create().start().resume()
        KeptDialog().show(c.get().supportFragmentManager, "ext")
        c.get().supportFragmentManager.executePendingTransactions()
        val out = Bundle()
        c.saveInstanceState(out)
        return rename(out, KeptDialog::class.java.name, className)
    }

    private fun rename(b: Bundle, from: String, to: String): Bundle {
        for (k in b.keySet().toList()) {
            @Suppress("DEPRECATION") val v = b.get(k)
            when (v) {
                is Bundle -> rename(v, from, to)
                is String -> if (v == from) b.putString(k, to)
                is ArrayList<*> -> v.forEach { item -> if (item is Bundle) rename(item, from, to) }
                is android.os.Parcelable -> renameParcelable(v, from, to)
            }
        }
        return b
    }

    /** FragmentState is a Parcelable with a private `mClassName` field. */
    private fun renameParcelable(p: Any, from: String, to: String) {
        var c: Class<*>? = p.javaClass
        while (c != null && c != Any::class.java) {
            for (f in c.declaredFields) {
                if (java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
                f.isAccessible = true
                val v = runCatching { f.get(p) }.getOrNull()
                when (v) {
                    is String -> if (v == from) f.set(p, to)
                    is Bundle -> rename(v, from, to)
                    is List<*> -> v.forEach { item -> if (item is android.os.Parcelable && item.javaClass.name.startsWith("androidx.fragment")) renameParcelable(item, from, to) else if (item is Bundle) rename(item, from, to) }
                    is android.os.Parcelable -> if (v.javaClass.name.startsWith("androidx.fragment")) renameParcelable(v, from, to)
                }
            }
            c = c.superclass
        }
    }

    class KeptDialog : DialogFragment()

    class Host : AppCompatActivity() {
        companion object { @Volatile var useOurs = false }
        override fun onCreate(savedInstanceState: Bundle?) {
            supportFragmentManager.fragmentFactory = if (useOurs) ExtensionFragmentFactory else FragmentFactory()
            setTheme(androidx.appcompat.R.style.Theme_AppCompat)
            super.onCreate(savedInstanceState)
        }
    }

    @Suppress("unused") private fun unused(f: Fragment) = f
}
