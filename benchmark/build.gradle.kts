// G12 X2: measures cold start of the R8 "preview" build (release code, debug key, installs next to
// the store app) and generates the baseline profile. Runs only on CI emulators
// (.github/workflows/macrobenchmark.yml); emulator numbers are for before/after comparison only.
plugins {
  alias(libs.plugins.android.test)
}

android {
  namespace = "dev.asfakul.yumeiro.benchmark"
  compileSdk { version = release(37) }

  defaultConfig {
    minSdk = 28
    targetSdk = 37
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    // The app has phone and TV builds; startup is measured on the phone build.
    missingDimensionStrategy("device", "mobile")
  }

  buildTypes {
    // Matches the app's "preview" build type (R8, not debuggable).
    create("preview") {
      isDebuggable = true
      signingConfig = signingConfigs.getByName("debug")
      matchingFallbacks += listOf("release")
    }
  }

  targetProjectPath = ":app"
  experimentalProperties["android.experimental.self-instrumenting"] = true
}

androidComponents {
  beforeVariants(selector().withBuildType("debug")) { it.enable = false }
}

dependencies {
  implementation(libs.androidx.junit)
  implementation(libs.androidx.uiautomator)
  implementation(libs.androidx.benchmark.macro.junit4)
}
