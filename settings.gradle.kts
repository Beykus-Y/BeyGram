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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // CI only: Google's mirror of Maven Central, tried before repo1.maven.org, which answers GitHub runners with
        // HTTP 429. Only the org.robolectric group comes from it; every other artifact still comes from Maven Central.
        System.getenv("BEYGRAM_MAVEN_CENTRAL_MIRROR")?.takeIf { it.isNotBlank() }?.let { mirror ->
            maven {
                setUrl(mirror)
                content { includeGroup("org.robolectric") }
            }
        }
        mavenCentral()
    }
}

rootProject.name = "BeyGram"
include(":app")
// Macrobenchmark and Baseline Profile generation (#1119). Not part of any release or PR build.
include(":macrobenchmark")
