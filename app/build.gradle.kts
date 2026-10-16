import java.util.Properties
import com.beykus.gradle.DownloadPinnedMihomoTask

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val secretsFile = rootProject.file("secrets.properties")
val secretsProperties = Properties().apply {
    if (secretsFile.isFile) {
        secretsFile.inputStream().use { input -> load(input) }
    }
}

fun secretProperty(name: String, defaultValue: String): String =
    providers.gradleProperty(name)
        .orElse(providers.provider { secretsProperties.getProperty(name, defaultValue) })
        .get()

// Pin the Android bridge/core artifact and verify the release checksum before it can
// enter an APK. The AAR is kept in build/ rather than committed as a large binary.
val mihomoAndroidVersion = "0.3.11"
val mihomoAndroidAar = layout.buildDirectory.file("mihomo/libmihomo-android-v$mihomoAndroidVersion.aar")
val mihomoAndroidSha256 = "01bc41748840c8200bbaf08c044ba8704e35303a3179b75a3cce8517c8944251"
val downloadMihomoAndroid = tasks.register<DownloadPinnedMihomoTask>("downloadMihomoAndroid") {
    version.set(mihomoAndroidVersion)
    sha256.set(mihomoAndroidSha256)
    artifact.set(mihomoAndroidAar)
}

android {
    namespace = "com.beykus.beygram"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.beykus.beygram"
        minSdk = 29
        targetSdk = 36
        ndk { abiFilters += "arm64-v8a" }
        versionCode = 1
        versionName = "1.0"

        val telegramApiId = secretProperty("telegramApiId", "0").ifBlank { "0" }
        val telegramApiHash = secretProperty("telegramApiHash", "")
        buildConfigField("int", "TELEGRAM_API_ID", telegramApiId)
        buildConfigField("String", "TELEGRAM_API_HASH", telegramApiHash.asBuildConfigString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        create("prod") {
            dimension = "environment"
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isDebuggable = false
            optimization {
                enable = false
            }
        }
    }
    // libmihomo-android loads libclash.so from ApplicationInfo.nativeLibraryDir
    // by absolute path, so require PackageManager to extract JNI libs on install.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation(files(mihomoAndroidAar).builtBy(downloadMihomoAndroid))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.snakeyaml)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
