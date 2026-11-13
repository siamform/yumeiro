import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
}

// ---------------------------------------------------------------------------------------------
// Version — single source of truth lives in /version.properties (bumped by the Release workflow)
// ---------------------------------------------------------------------------------------------
val versionProps = Properties().apply {
  rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionName: String = versionProps.getProperty("VERSION_NAME")
val appVersionCode: Int = versionProps.getProperty("VERSION_CODE").toInt()

// ---------------------------------------------------------------------------------------------
// Constant release keystore
//
// Every release MUST be signed with the same keystore, otherwise Android refuses to install the
// update over the existing app. The keystore is NEVER generated on the fly:
//   * CI:    decoded from the encrypted GitHub secret MT_KEYSTORE_BASE64 (see release.yml)
//   * Local: referenced from the git-ignored file /keystore.properties (see keystore.properties.example)
// If neither is present, release builds are produced UNSIGNED instead of with a random key.
// ---------------------------------------------------------------------------------------------
val keystoreProps = Properties().apply {
  val f = rootProject.file("keystore.properties")
  if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(envName: String, propName: String): String? =
  System.getenv(envName)?.takeIf { it.isNotBlank() }
    ?: keystoreProps.getProperty(propName)?.takeIf { it.isNotBlank() }

val releaseStoreFile = secret("MT_KEYSTORE_PATH", "storeFile")
val releaseStorePassword = secret("MT_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = secret("MT_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = secret("MT_KEY_PASSWORD", "keyPassword")
val hasReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
  .all { it != null } && rootProject.file(releaseStoreFile!!).exists()

// ---------------------------------------------------------------------------------------------
// In-app updater configuration (GitHub Releases)
// ---------------------------------------------------------------------------------------------
val updateRepoOwner = secret("MT_UPDATE_REPO_OWNER", "updateRepoOwner") ?: "siamform"
val updateRepoName = secret("MT_UPDATE_REPO_NAME", "updateRepoName") ?: "yumeiro"

// Optional read-only token so the updater keeps working once the repository becomes private.
// It is injected at build time only (never committed) and lightly obfuscated in the binary.
val updaterToken = secret("MT_UPDATER_TOKEN", "updaterToken") ?: ""
val obfuscationKey = (appVersionCode * 31 + 0x5A) and 0xFF
// SHA-256 of the official release signing certificate. The app compares its own signature
// against it at runtime (IntegrityGuard) so re-signed / repackaged copies are flagged.
val officialCertSha = rootProject.file(".github/release-signing-cert.sha256")
  .takeIf { it.exists() }?.readText()?.trim()?.lowercase()?.filter { it in "0123456789abcdef" } ?: ""
val obfuscatedCert = officialCertSha.toByteArray(Charsets.US_ASCII)
  .mapIndexed { i, b -> (b.toInt() xor ((obfuscationKey + i * 7) and 0xFF)) }.joinToString(",")
val obfuscatedToken = updaterToken.toByteArray(Charsets.UTF_8)
  .joinToString(",") { ((it.toInt() xor obfuscationKey) and 0xFF).toString() }

/**
 * Keeps only characters that are safe inside a BuildConfig string literal. Client IDs may be
 * base64url (letters, digits, `-`, `_`; Trakt IDs since 2026-10, LRCLIB app IDs) or JWTs (`.`; TMDB
 * read tokens), so only quotes, backslashes, spaces and other punctuation are dropped. Stripping `-`
 * and `_` (the old `isLetterOrDigit` filter) turned a valid Trakt ID into one Trakt rejects with 401.
 */
/**
 * Firebase Cloud Messaging *client* config (never the service account — that lives only in the
 * Worker as a secret). Read from an untracked app/google-services.json (picks the client whose
 * package matches each build type) or from Gradle properties / environment variables. Blank values
 * turn push off; the app still builds and runs. App ids contain ':' so they get their own filter.
 */
fun fcmSafe(value: String): String = value.trim().filter { it.isLetterOrDigit() || it in "-_.:" }
val fcmJson: Map<String, Any?>? = rootProject.file("app/google-services.json").takeIf { it.exists() }
  ?.let { @Suppress("UNCHECKED_CAST") (groovy.json.JsonSlurper().parse(it) as? Map<String, Any?>) }
@Suppress("UNCHECKED_CAST")
fun fcmFromJson(pkg: String): Pair<String, String>? {
  val clients = fcmJson?.get("client") as? List<Map<String, Any?>> ?: return null
  val c = clients.firstOrNull { ((it["client_info"] as? Map<String, Any?>)?.get("android_client_info") as? Map<String, Any?>)?.get("package_name") == pkg } ?: return null
  val appId = (c["client_info"] as? Map<String, Any?>)?.get("mobilesdk_app_id") as? String ?: return null
  val key = ((c["api_key"] as? List<Map<String, Any?>>)?.firstOrNull())?.get("current_key") as? String ?: ""
  return appId to key
}
fun fcmProp(prop: String, env: String): String = providers.gradleProperty(prop).orNull ?: System.getenv(env) ?: ""
val fcmProjectInfo = fcmJson?.get("project_info") as? Map<*, *>
val fcmProjectId: String = fcmSafe(fcmProp("fcmProjectId", "YUMEIRO_FCM_PROJECT_ID").ifBlank { fcmProjectInfo?.get("project_id") as? String ?: "" })
val fcmSenderId: String = fcmSafe(fcmProp("fcmSenderId", "YUMEIRO_FCM_SENDER_ID").ifBlank { fcmProjectInfo?.get("project_number") as? String ?: "" })
/** (appId, apiKey) for a package; props/env override the JSON, so CI needs no file. */
fun fcmClient(pkg: String, suffix: String): Pair<String, String> {
  // A Firebase project often registers only the store package. Preview/debug builds then reuse that
  // client (same project, same key): their topics are channel-prefixed (`preview-…`, `debug-…`), so a
  // test build never receives or sends real-user broadcasts. A dedicated client always wins.
  val json = fcmFromJson(pkg) ?: fcmFromJson("dev.asfakul.yumeiro")
  val appId = fcmProp("fcmAppId$suffix", "YUMEIRO_FCM_APP_ID${suffix.uppercase().let { if (it.isEmpty()) "" else "_$it" }}").ifBlank { json?.first ?: "" }
  val key = fcmProp("fcmApiKey", "YUMEIRO_FCM_API_KEY").ifBlank { json?.second ?: "" }
  return fcmSafe(appId) to fcmSafe(key)
}

fun buildConfigSafe(value: String): String = value.trim().filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }

android {
  namespace = "dev.asfakul.yumeiro"
  compileSdk { version = release(37) }

  defaultConfig {
    applicationId = "dev.asfakul.yumeiro"
    // Android 7.0: most TV boxes and Fire TV sticks (Fire OS 6 = Android 7.1) and older phones.
    // API 26–28 calls are guarded (OsCompat, PipController, Privacy); java.time is desugared.
    minSdk = 24
    targetSdk = 37 // Android 17
    versionCode = appVersionCode
    versionName = appVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    buildConfigField("String", "UPDATE_REPO_OWNER", "\"$updateRepoOwner\"")
    buildConfigField("String", "UPDATE_REPO_NAME", "\"$updateRepoName\"")
    buildConfigField("int[]", "UPDATER_TOKEN_BYTES", "new int[]{$obfuscatedToken}")
    buildConfigField("int", "UPDATER_TOKEN_KEY", "$obfuscationKey")
    buildConfigField("int[]", "INTEGRITY_BYTES", "new int[]{$obfuscatedCert}")
    // OAuth client ids for AniList / MyAnimeList sync (public ids, not secrets). Set them as
    // Gradle properties or CI env vars; users can also enter their own in Settings.
    val anilistClient = (providers.gradleProperty("anilistClientId").orNull ?: System.getenv("ANILIST_CLIENT_ID") ?: "").let(::buildConfigSafe)
    val malClient = (providers.gradleProperty("malClientId").orNull ?: System.getenv("MAL_CLIENT_ID") ?: "").let(::buildConfigSafe)
    buildConfigField("String", "ANILIST_CLIENT_ID", "\"$anilistClient\"")
    buildConfigField("String", "MAL_CLIENT_ID", "\"$malClient\"")
    // Trakt (movies / shows tracking): free API app from trakt.tv/oauth/applications. Optional.
    val traktClient = (providers.gradleProperty("traktClientId").orNull ?: System.getenv("TRAKT_CLIENT_ID") ?: "").let(::buildConfigSafe)
    val traktSecret = (providers.gradleProperty("traktClientSecret").orNull ?: System.getenv("TRAKT_CLIENT_SECRET") ?: "").let(::buildConfigSafe)
    buildConfigField("String", "TRAKT_CLIENT_ID", "\"$traktClient\"")
    buildConfigField("String", "TRAKT_CLIENT_SECRET", "\"$traktSecret\"")
    // Kitsu (anime tracking) OAuth client for the password grant. Optional; never committed.
    val kitsuClient = (providers.gradleProperty("kitsuClientId").orNull ?: System.getenv("KITSU_CLIENT_ID") ?: "").let(::buildConfigSafe)
    val kitsuSecret = (providers.gradleProperty("kitsuClientSecret").orNull ?: System.getenv("KITSU_CLIENT_SECRET") ?: "").let(::buildConfigSafe)
    buildConfigField("String", "KITSU_CLIENT_ID", "\"$kitsuClient\"")
    buildConfigField("String", "KITSU_CLIENT_SECRET", "\"$kitsuSecret\"")
    // Free API keys for Simkl (tracking), Last.fm (scrobbling), LRCLIB (lyrics), OpenSubtitles and
    // TMDB. All optional (features hide or ask for the user's own key when blank); never committed.
    fun optionalKey(prop: String, env: String) = (providers.gradleProperty(prop).orNull ?: System.getenv(env) ?: "").let(::buildConfigSafe)
    buildConfigField("String", "SIMKL_CLIENT_ID", "\"${optionalKey("simklClientId", "SIMKL_CLIENT_ID")}\"")
    buildConfigField("String", "LASTFM_API_KEY", "\"${optionalKey("lastfmApiKey", "LASTFM_API_KEY")}\"")
    buildConfigField("String", "LASTFM_SHARED_SECRET", "\"${optionalKey("lastfmSharedSecret", "LASTFM_SHARED_SECRET")}\"")
    buildConfigField("String", "LRCLIB_APP_ID", "\"${optionalKey("lrclibAppId", "LRCLIB_APP_ID")}\"")
    buildConfigField("String", "OPENSUBTITLES_API_KEY", "\"${optionalKey("opensubtitlesApiKey", "OPENSUBTITLES_API_KEY")}\"")
    // Yumeiro Cloud (server/): host of the tiny wake-up server. Blank turns the feature off.
    buildConfigField("String", "CLOUD_HOST", "\"${(providers.gradleProperty("cloudHost").orNull ?: System.getenv("YUMEIRO_CLOUD_HOST") ?: "api.yumeiro.click").let(::buildConfigSafe)}\"")
    buildConfigField("String", "TMDB_API_KEY", "\"${optionalKey("tmdbApiKey", "TMDB_API_KEY")}\"")

    // Push (FCM). Per-build-type app id/key are set in buildTypes below.
    buildConfigField("String", "FCM_PROJECT_ID", "\"$fcmProjectId\"")
    buildConfigField("String", "FCM_SENDER_ID", "\"$fcmSenderId\"")
  }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = rootProject.file(releaseStoreFile!!)
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
        enableV1Signing = true // harmless on API 28+, keeps tooling compatibility
        enableV2Signing = true
        enableV3Signing = true // enables future key rotation
      }
    }
  }

  buildTypes {
    release {
      // R8 code + resource shrinking: ~85% smaller APK (faster update downloads).
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
      buildConfigField("boolean", "AUTO_UPDATE_CHECK", "true")
      fcmClient("dev.asfakul.yumeiro", "").let { (id, key) ->
        buildConfigField("String", "FCM_APP_ID", "\"$id\"")
        buildConfigField("String", "FCM_API_KEY", "\"$key\"")
      }
      buildConfigField("String", "PUSH_CHANNEL", "\"release\"")
    }
    // Round 8: an R8-optimised build for owner / tester try-outs before a release. Same code as
    // release, but it installs next to the store app (".preview"), is signed with the local debug
    // key and never checks for updates, so it can't replace or update a real install.
    create("preview") {
      initWith(getByName("release"))
      applicationIdSuffix = ".preview"
      versionNameSuffix = "-preview"
      signingConfig = signingConfigs.getByName("debug")
      matchingFallbacks += listOf("release")
      buildConfigField("boolean", "AUTO_UPDATE_CHECK", "false")
      fcmClient("dev.asfakul.yumeiro.preview", "Preview").let { (id, key) ->
        buildConfigField("String", "FCM_APP_ID", "\"$id\"")
        buildConfigField("String", "FCM_API_KEY", "\"$key\"")
      }
      buildConfigField("String", "PUSH_CHANNEL", "\"preview\"")
    }
    debug {
      // Debug builds install side-by-side with the release app and never replace it.
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
      buildConfigField("boolean", "AUTO_UPDATE_CHECK", "false")
      fcmClient("dev.asfakul.yumeiro.debug", "Debug").let { (id, key) ->
        buildConfigField("String", "FCM_APP_ID", "\"$id\"")
        buildConfigField("String", "FCM_API_KEY", "\"$key\"")
      }
      buildConfigField("String", "PUSH_CHANNEL", "\"debug\"")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    // java.time & co. on Android 7 (our code, NewPipe's extractor and CloudStream use them).
    isCoreLibraryDesugaringEnabled = true
  }

  // One APK per processor plus a universal one (all share applicationId, key and versionCode, so
  // they install over each other). The release workflow names the universal APK exactly like the
  // single APK before, so older in-app updaters keep picking a build that runs everywhere.
  splits {
    abi {
      val wanted = providers.gradleProperty("yumeiro.abiSplits").orNull != "false"
      isEnable = wanted
      reset()
      include("arm64-v8a", "armeabi-v7a")
      isUniversalApk = true
    }
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      // Small machines (2 cores / 8 GB) ran the Robolectric JVM out of memory. Opt in with
      // -Pyumeiro.lowMemTests=true to cap the heap and recycle the test JVM.
      if (providers.gradleProperty("yumeiro.lowMemTests").orNull == "true") {
        all { it.maxHeapSize = "1600m"; it.setForkEvery(25) }
      }
    }
  }

  packaging {
    resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    jniLibs {
      // FFmpeg (NextLib) is ~10 MB per ABI. Phones are ARM; x86/x86_64 (emulators, a few
      // Chromebooks) fall back to the platform decoders (FfmpegLibrary.isAvailable() is false there).
      for (abi in listOf("x86", "x86_64")) {
        for (lib in listOf("ass", "avcodec", "avutil", "media3ext", "swresample", "swscale")) excludes += "lib/$abi/lib$lib.so"
      }
      // Compressed in the APK (~9 MB instead of ~21 MB for the two ARM ABIs).
      useLegacyPackaging = true
    }
  }

  dependenciesInfo {
    // Don't embed the (Google-encrypted) dependency block: we ship via GitHub, not Play.
    includeInApk = false
    includeInBundle = false
  }
}

dependencies {
  coreLibraryDesugaring(libs.desugar.jdk.libs)
  // Security floor for vulnerable transitive libraries (OSV scan, 2026-11-07).
  constraints {
    implementation("org.jsoup:jsoup:1.24.1") { because("GHSA-65r4-943x-97jj, GHSA-pmhh-3w7g-xqp8 (via CloudStream library)") }
    implementation("com.google.guava:guava:33.8.0-android") { because("GHSA-xxph-c9ww-hj94 (via Media3)") }
  }
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // CloudStream extensions build their settings screens with AppCompat / Fragment / Material views.
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.google.material)
  implementation(libs.androidx.preference.ktx)
  implementation(libs.androidx.recyclerview)
  implementation(libs.androidx.webkit)
  implementation(libs.androidx.work.runtime.ktx)
  // UnifiedPush (Apache-2.0): optional free push through ntfy or any distributor, no Google services.
  // Tink's full protobuf runtime clashes with protobuf-lite and isn't needed for Web Push.
  implementation(libs.unifiedpush.connector) { exclude(group = "com.google.protobuf", module = "protobuf-java") }
  // Installs the baseline profiles bundled with Compose/Media3 on sideloaded APKs → smoother frames.
  implementation(libs.androidx.profileinstaller)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  // CloudStream extensions look up NavHostFragment; keep the class available to them.
  implementation(libs.androidx.navigation.fragment)
  implementation(libs.coil.compose)

  // Media3
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.ui)
  implementation(libs.media3.session)
  implementation(libs.media3.common)
  implementation(libs.media3.exoplayer.hls)
  implementation(libs.media3.exoplayer.dash)
  implementation(libs.media3.exoplayer.smoothstreaming)
  implementation(libs.media3.exoplayer.rtsp)
  // OkHttp data source: one HTTP stack for probing, playlists and playback, with DNS fallback / DoH.
  implementation(libs.media3.datasource.okhttp)
  // FFmpeg audio fallback: AC-3 / E-AC-3 / DTS / TrueHD / Opus / Vorbis tracks the phone can't decode played silent.
  implementation(libs.nextlib.media3ext)
  implementation(libs.okhttp)
  implementation(libs.okhttp.dnsoverhttps)
  implementation(libs.okhttp.brotli)
  implementation(libs.okhttp.zstd)

  // Online: runs CloudStream-compatible extensions (MainAPI providers / extractors).
  implementation(libs.cloudstream.library)
  // Rhino already ships at runtime with the CloudStream library; compile against it for the
  // restored Vidguard extractor (com/lagradost/cloudstream3/extractors/Vidguard.kt).
  compileOnly(libs.rhino)
  implementation(libs.cast.framework) // Chromecast
  // Push: Firebase Cloud Messaging only. Initialised by hand from BuildConfig (no google-services
  // plugin, no Analytics); a build without FCM config simply runs with push off.
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.messaging)
  implementation(libs.androidx.mediarouter)
  implementation(libs.jackson.module.kotlin) // same Jackson the CloudStream library already ships; extensions use it
  implementation(libs.nicehttp) // CloudStream's `app` HTTP helper, wired to our OkHttp client
  // YouTube / YouTube Music (no login). The CloudStream library already pulls an older NewPipeExtractor
  // under the lowercase "teamnewpipe" group; the substitution below makes both resolve to one copy.
  implementation(libs.newpipe.extractor)

  // Aniyomi (anime) and Mihon (manga) extensions: their source API is vendored under eu.kanade.tachiyomi
  // (Apache-2.0, see NOTICE); these are the libraries those extensions expect the host app to provide.
  implementation(libs.injekt)
  implementation(libs.rxjava)
  implementation(libs.nanohttpd)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.serialization.json.okio)
  implementation(libs.kotlinx.serialization.protobuf)
  // Mangayomi-style JavaScript sources
  implementation(libs.quickjs.kt)
  // SMB shares (home servers)
  implementation(libs.smbj)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(platform(libs.androidx.compose.bom))
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.okhttp.mockwebserver)

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.okhttp.mockwebserver)

  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}

// One NewPipeExtractor on the classpath: CloudStream depends on "com.github.teamnewpipe" (lowercase),
// we use the current "com.github.TeamNewPipe" release. Same artifact, different JitPack group casing.
configurations.configureEach {
  resolutionStrategy.dependencySubstitution {
    substitute(module("com.github.teamnewpipe:NewPipeExtractor"))
      .using(module("com.github.TeamNewPipe:NewPipeExtractor:${libs.versions.newpipeExtractor.get()}"))
  }
}
