@file:JvmName("SubtitleHelperPlatform_jvmKt")

package com.lagradost.cloudstream3.utils

import java.util.Locale

/*
 * Compatibility shim. Some extensions were compiled against the JVM build of the CloudStream
 * library, which exposes getCurrentLocale() from SubtitleHelperPlatform_jvmKt. The Android build
 * we ship does not have that class, so those extensions crashed with NoClassDefFoundError.
 * See docs/EXTENSION-COMPAT.md.
 */
fun getCurrentLocale(): String = Locale.getDefault().toLanguageTag()
