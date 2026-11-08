package eu.kanade.tachiyomi

/** Yumeiro's answer to extensions that ask which app version hosts them. */
object AppInfo {
    fun getVersionCode(): Int = dev.asfakul.yumeiro.BuildConfig.VERSION_CODE
    fun getVersionName(): String = dev.asfakul.yumeiro.BuildConfig.VERSION_NAME
}
