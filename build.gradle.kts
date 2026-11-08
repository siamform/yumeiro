// Top-level build file. Plugins are declared here (apply false) and applied per module.
//
// AGP 9+ ships built-in Kotlin support, so the Kotlin Android plugin is NOT applied to modules.
// It is declared here only to pin the Kotlin Gradle Plugin (KGP) version on the build classpath,
// keeping it in sync with the Compose compiler plugin.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.serialization) apply false
}
