# Yumeiro — R8 rules
# AndroidX (Compose, Media3, Lifecycle, Navigation) ship their own consumer rules.

# Keep readable stack traces for crash reports (mapping.txt is attached to CI artifacts).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Manifest-declared components are kept automatically; keep updater receivers explicit for clarity.
-keep class dev.asfakul.yumeiro.update.DownloadCompleteReceiver { <init>(); }
-keep class dev.asfakul.yumeiro.update.InstallResultReceiver { <init>(); }

# ---- Online extensions (CloudStream .cs3 plugins) ----
# Extensions are separate dex files compiled against the CloudStream library and its dependencies.
# They reference those classes/members by name at runtime, so none of them may be renamed or removed.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,KotlinMetadata,Exceptions
-keep class com.lagradost.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }
-keep class kotlinx.datetime.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class org.jsoup.** { *; }
-keep class com.fleeksoft.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class org.mozilla.** { *; }
-keep class me.xdrop.** { *; }
-keep class dev.whyoleg.** { *; }
-keep class io.ktor.** { *; }
-keep class org.schabi.newpipe.** { *; }
-keep class org.json.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn com.lagradost.**
-dontwarn org.mozilla.**
-dontwarn org.schabi.newpipe.**
-dontwarn com.fasterxml.jackson.**
-dontwarn io.ktor.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn javax.lang.model.**
-dontwarn org.slf4j.**
-dontwarn org.jspecify.**
-dontwarn com.google.re2j.**
-dontwarn java.awt.**
-dontwarn java.lang.management.**
-dontwarn jdk.dynalink.**

# ---- CloudStream app compat (extensions call these Android UI/support classes by name) ----
-keep class androidx.appcompat.** { *; }
-keep class androidx.fragment.** { *; }
-keep class androidx.preference.** { *; }
-keep class androidx.navigation.** { *; }
-keep class androidx.recyclerview.** { *; }
-keep class androidx.core.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.webkit.** { *; }
-keep class androidx.activity.** { *; }
-keep class com.google.android.gms.cast.** { *; }
-keep class com.google.android.gms.common.api.** { *; }
-keep class androidx.viewpager2.** { *; }
-keep class androidx.constraintlayout.** { *; }
-keep class com.google.android.material.** { *; }
-keep class dev.asfakul.yumeiro.online.web.** { *; }
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
-keep class dev.asfakul.yumeiro.cast.CastOptionsProvider { <init>(); }
-dontwarn androidx.**
-dontwarn com.google.android.material.**

# Libraries some extensions build their settings screens / players with (WatchParty, SleepGuard,
# AutoExtensions, M6Plus...). Found by auditing every market extension against a release build.
-keep class androidx.compose.foundation.interaction.MutableInteractionSource { *; }
-keep class androidx.compose.material3.ColorScheme { *; }
-keep class androidx.compose.material3.MaterialThemeKt { *; }
-keep class androidx.compose.material3.Shapes { *; }
-keep class androidx.compose.material3.SliderColors { *; }
-keep class androidx.compose.material3.SliderDefaults { *; }
-keep class androidx.compose.material3.SliderKt { *; }
-keep class androidx.compose.material3.SwitchColors { *; }
-keep class androidx.compose.material3.SwitchDefaults { *; }
-keep class androidx.compose.material3.SwitchKt { *; }
-keep class androidx.compose.material3.Typography { *; }
-keep class androidx.compose.runtime.Composable { *; }
-keep class androidx.compose.runtime.ComposableTarget { *; }
-keep class androidx.compose.runtime.Composer { *; }
-keep class androidx.compose.runtime.Composer$Companion { *; }
-keep class androidx.compose.runtime.ComposerKt { *; }
-keep class androidx.compose.runtime.MutableState { *; }
-keep class androidx.compose.runtime.SnapshotMutationPolicy { *; }
-keep class androidx.compose.runtime.SnapshotStateKt { *; }
-keep class androidx.compose.runtime.internal.ComposableLambda { *; }
-keep class androidx.compose.runtime.internal.ComposableLambdaKt { *; }
-keep class androidx.compose.runtime.internal.StabilityInferred { *; }
-keep class androidx.compose.ui.Modifier { *; }
-keep class androidx.compose.ui.graphics.ColorKt { *; }
-keep class androidx.compose.ui.platform.ComposeView { *; }
-keep class androidx.media3.common.Player { *; }
-keep class androidx.media3.ui.PlayerView { *; }
-keep class androidx.mediarouter.app.MediaRouteChooserDialog { *; }
-keep class androidx.mediarouter.media.MediaRouteSelector { *; }
-keep class androidx.mediarouter.media.MediaRouteSelector$Builder { *; }
-keep class androidx.mediarouter.media.MediaRouter { *; }
-keep class androidx.mediarouter.media.MediaRouter$RouteInfo { *; }-keep class androidx.compose.runtime.internal.ComposableLambda* { *; }
-keep class androidx.compose.runtime.Composer* { *; }

# NewPipeExtractor (YouTube / YouTube Music) — JSON parser + JavaScript engine used for deobfuscation.
-keep class com.grack.nanojson.** { *; }
-dontwarn com.grack.nanojson.**
-dontwarn javax.script.**
-dontwarn java.beans.**

# ---- Hardening ----
# Strip verbose/debug/info logging from release builds (no internal state in logcat).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ---- Universal sources (Aniyomi / Mihon / JavaScript / home servers)
# Aniyomi/Mihon extension APKs are loaded at runtime and link against these API classes by name.
-keep class eu.kanade.tachiyomi.** { *; }
-keep class tachiyomi.** { *; }
-keep class mihon.** { *; }
-keep class uy.kohesive.injekt.** { *; }
-keep class rx.** { *; }
-dontwarn rx.**
-keep class fi.iki.elonen.** { *; }
# QuickJS (JavaScript sources) binds Kotlin functions by reflection-free JNI names.
-keep class com.dokar.quickjs.** { *; }
# SMB client and its crypto dependencies.
-keep class com.hierynomus.** { *; }
-keep class net.engio.** { *; }
-dontwarn com.hierynomus.**
-dontwarn net.engio.**
-dontwarn org.bouncycastle.**
-dontwarn javax.el.**
-dontwarn org.slf4j.**
# Our @Serializable models used by backup and the source bridges.
-keepclassmembers class dev.asfakul.yumeiro.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class dev.asfakul.yumeiro.widget.ContinueWidget { <init>(); }
