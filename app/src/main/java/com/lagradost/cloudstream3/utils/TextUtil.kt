package com.lagradost.cloudstream3.utils

import android.content.Context
import androidx.annotation.StringRes

/** Text that is either a plain string or a string resource (CloudStream-compatible). */
sealed class UiText {
    companion object { const val TAG = "UiText" }

    data class DynamicString(val value: String) : UiText() {
        override fun toString(): String = value
    }

    class StringResource(@StringRes val resId: Int, val args: List<Any>) : UiText() {
        override fun toString(): String = "resId=$resId,args=$args"
    }

    fun asStringNull(context: Context?): String? = try { asString(context ?: return null) } catch (e: Exception) { null }

    fun asString(context: Context): String = when (this) {
        is DynamicString -> value
        is StringResource -> {
            val str = context.getString(resId)
            if (args.isEmpty()) str else str.format(*args.map { if (it is UiText) it.asString(context) else it }.toTypedArray())
        }
    }
}

fun txt(value: String): UiText = UiText.DynamicString(value)

@JvmName("txtNull")
fun txt(value: String?): UiText? = UiText.DynamicString(value ?: return null)

fun txt(@StringRes resId: Int, vararg args: Any): UiText = UiText.StringResource(resId, args.toList())

@JvmName("txtNull")
fun txt(@StringRes resId: Int?, vararg args: Any?): UiText? {
    if (resId == null || args.any { it == null }) return null
    return UiText.StringResource(resId, args.filterNotNull().toList())
}
