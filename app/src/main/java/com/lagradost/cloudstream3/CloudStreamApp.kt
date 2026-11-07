package com.lagradost.cloudstream3

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.Fragment
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.removeKeys
import com.lagradost.cloudstream3.utils.DataStore.setKey
import java.lang.ref.WeakReference

/**
 * CloudStream-compatible application helpers. Extensions use the companion to store their own
 * settings (`CloudStreamApp.setKey/getKey`) and to get a [Context]. The real application class of
 * this app is separate; [init] wires the context in.
 */
class CloudStreamApp {
    companion object {
        private var _context: WeakReference<Context>? = null

        var context: Context?
            get() = _context?.get()
            private set(value) { _context = WeakReference(value) }

        /** Called once by the host app with the application context. */
        fun init(app: Context) {
            context = app.applicationContext
            runCatching { com.lagradost.api.setContext(WeakReference<Any>(app.applicationContext)) }
        }

        tailrec fun Context.getActivity(): Activity? = when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.getActivity()
            else -> null
        }

        fun <T : Any> getKeyClass(path: String, valueType: Class<T>): T? = context?.getKey(path, valueType)
        fun <T : Any> setKeyClass(path: String, value: T) { context?.setKey(path, value) }
        fun removeKeys(folder: String): Int? = context?.removeKeys(folder)
        fun <T> setKey(path: String, value: T) { context?.setKey(path, value) }
        fun <T> setKey(folder: String, path: String, value: T) { context?.setKey(folder, path, value) }
        inline fun <reified T : Any> getKey(path: String, defVal: T?): T? = context?.getKey(path, defVal)
        inline fun <reified T : Any> getKey(path: String): T? = context?.getKey(path)
        inline fun <reified T : Any> getKey(folder: String, path: String): T? = context?.getKey(folder, path)
        inline fun <reified T : Any> getKey(folder: String, path: String, defVal: T?): T? = context?.getKey(folder, path, defVal)
        fun getKeys(folder: String): List<String>? = context?.getKeys(folder)
        fun removeKey(folder: String, path: String) { context?.removeKey(folder, path) }
        fun removeKey(path: String) { context?.removeKey(path) }

        fun openBrowser(url: String, fallbackWebView: Boolean = false, fragment: Fragment? = null) {
            val ctx = CommonActivity.activity ?: context ?: return
            runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { CommonActivity.showToast("No browser found to open $url") }
        }

        fun openBrowser(url: String, activity: Activity?) = openBrowser(url, false, null)
    }
}

/** Old name of [CloudStreamApp] that many extensions still use. */
class AcraApplication {
    companion object {
        val context: Context? get() = CloudStreamApp.context
        fun removeKeys(folder: String): Int? = CloudStreamApp.removeKeys(folder)
        fun <T> setKey(path: String, value: T) = CloudStreamApp.setKey(path, value)
        fun <T> setKey(folder: String, path: String, value: T) = CloudStreamApp.setKey(folder, path, value)
        inline fun <reified T : Any> getKey(path: String, defVal: T?): T? = CloudStreamApp.getKey(path, defVal)
        inline fun <reified T : Any> getKey(path: String): T? = CloudStreamApp.getKey(path)
        inline fun <reified T : Any> getKey(folder: String, path: String): T? = CloudStreamApp.getKey(folder, path)
        inline fun <reified T : Any> getKey(folder: String, path: String, defVal: T?): T? = CloudStreamApp.getKey(folder, path, defVal)
        fun removeKey(folder: String, path: String) = CloudStreamApp.removeKey(folder, path)
        fun removeKey(path: String) = CloudStreamApp.removeKey(path)
        fun getKeys(folder: String): List<String>? = CloudStreamApp.getKeys(folder)
        fun openBrowser(url: String) = CloudStreamApp.openBrowser(url)
    }
}
