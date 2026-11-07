package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.readValue

const val PREFERENCES_NAME = "rebuild_preference"
const val USER_SELECTED_HOMEPAGE_API = "home_api_used"
const val USER_PROVIDER_API = "user_custom_sites"

/**
 * Key/value storage for extensions (CloudStream-compatible). Values are stored as JSON in a private
 * SharedPreferences file, so an extension's settings survive updates of the extension.
 */
object DataStore {
    val mapper: JsonMapper = com.lagradost.cloudstream3.mapper

    private fun getPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun Context.getSharedPrefs(): SharedPreferences = getPreferences(this)

    fun getFolderName(folder: String, path: String): String = "$folder/$path"

    fun Context.getDefaultSharedPrefs(): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

    fun Context.getKeys(folder: String): List<String> {
        val fixed = folder.trimEnd('/') + "/"
        return getSharedPrefs().all.keys.filter { it.startsWith(fixed) }
    }

    fun Context.removeKey(folder: String, path: String) = removeKey(getFolderName(folder, path))

    fun Context.containsKey(folder: String, path: String): Boolean = containsKey(getFolderName(folder, path))

    fun Context.containsKey(path: String): Boolean = getSharedPrefs().contains(path)

    fun Context.removeKey(path: String) {
        runCatching {
            val prefs = getSharedPrefs()
            if (prefs.contains(path)) prefs.edit().remove(path).apply()
        }
    }

    fun Context.removeKeys(folder: String): Int {
        val keys = getKeys("$folder/")
        return runCatching {
            getSharedPrefs().edit().apply { keys.forEach { remove(it) } }.apply()
            keys.size
        }.getOrDefault(0)
    }

    fun <T> Context.setKey(path: String, value: T) {
        runCatching { getSharedPrefs().edit().putString(path, mapper.writeValueAsString(value)).apply() }
    }

    fun <T : Any> Context.getKey(path: String, valueType: Class<T>): T? = runCatching {
        val json = getSharedPrefs().getString(path, null) ?: return null
        json.toKotlinObject(valueType)
    }.getOrNull()

    fun <T> Context.setKey(folder: String, path: String, value: T) = setKey(getFolderName(folder, path), value)

    inline fun <reified T : Any> String.toKotlinObject(): T = mapper.readValue(this)

    fun <T : Any> String.toKotlinObject(valueType: Class<T>): T = mapper.readValue(this, valueType)

    inline fun <reified T : Any> Context.getKey(path: String, defVal: T?): T? = try {
        val json: String = getSharedPrefs().getString(path, null) ?: return defVal
        json.toKotlinObject<T>()
    } catch (e: Exception) { null }

    inline fun <reified T : Any> Context.getKey(path: String): T? = getKey(path, null)

    inline fun <reified T : Any> Context.getKey(folder: String, path: String): T? = getKey(getFolderName(folder, path), null)

    inline fun <reified T : Any> Context.getKey(folder: String, path: String, defVal: T?): T? =
        getKey(getFolderName(folder, path), defVal) ?: defVal
}
