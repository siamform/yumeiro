pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    // CloudStream extension API (and its NiceHttp / NewPipeExtractor deps) is published on JitPack.
    maven("https://jitpack.io") {
      content {
        includeGroup("com.github.recloudstream.cloudstream")
        includeGroup("com.github.Blatzar")
        includeGroup("com.github.mihonapp") // injekt (Aniyomi / Mihon extensions)
        includeGroup("com.github.teamnewpipe")
        includeGroupByRegex("com\\.github\\.TeamNewPipe.*")
      }
    }
  }
}

rootProject.name = "Yumeiro"

include(":app")
