plugins {
    alias(libs.plugins.android.test)
}

/**
 * Macrobenchmark module (#1119).
 *
 * It instruments the `benchmark` build type of `:app`, a release-like variant signed with the debug key. The module
 * never ships and is not part of any pull-request build: nothing here runs unless a benchmark or profile task is
 * invoked explicitly on a physical device.
 */
android {
    namespace = "com.beykus.beygram.macrobenchmark"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    defaultConfig {
        // Same floor as the app, so the benchmarks can only run where BeyGram itself runs.
        minSdk = 29
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // The app is the target: the benchmark APK has no code of its own to install besides the tests.
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
    // The module follows the app's flavour dimension, otherwise no variant could be matched to it.
    flavorDimensions += "environment"
    productFlavors {
        create("dev") { dimension = "environment" }
        create("prod") { dimension = "environment" }
    }
    buildTypes {
        create("benchmark") {
            isDebuggable = false
        }
    }
}

androidComponents {
    beforeVariants(selector().all()) { variant ->
        // Only the benchmark variant exists; a debug/release pair of this module would just double the build time.
        variant.enable = variant.buildType == "benchmark"
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
}