import java.util.Properties
import com.beykus.gradle.DownloadPinnedMihomoTask

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

// A production release is versioned before the main build is signed. The publish job
// only reuses that immutable APK and validates its embedded version against the tag.
val stableVersion = rootProject.file("release-version.properties").inputStream().use { input ->
    Properties().apply { load(input) }.getProperty("versionName")
        ?: error("release-version.properties must define versionName")
}
val stableVersionParts = stableVersion.split('.').map { it.toIntOrNull() ?: error("Invalid release version") }
require(stableVersionParts.size == 4 && stableVersionParts[0] in 0..20 &&
    stableVersionParts[1] in 0..99 && stableVersionParts[2] in 0..99 &&
    stableVersionParts[3] in 0..9999) { "Expected version X.X.X.X, bounded to Android versionCode range" }
val stableVersionCode = stableVersionParts[0] * 100_000_000 +
    stableVersionParts[1] * 1_000_000 + stableVersionParts[2] * 10_000 + stableVersionParts[3]
require(stableVersionCode > 0 && stableVersionCode <= 2_100_000_000) { "Invalid Android versionCode" }

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

val telegramApiId = secretProperty("telegramApiId", "0").ifBlank { "0" }
val telegramApiHash = secretProperty("telegramApiHash", "")
// An APK without these values cannot sign in, so assembling or installing one stops with the reason.
// Pull request builds in CI get no secrets by design (see android-release.yml) and are not checked.
val failWithoutTelegramCredentials =
    (telegramApiId.toIntOrNull() == 0 || telegramApiHash.isBlank()) &&
        providers.environmentVariable("GITHUB_EVENT_NAME").orNull != "pull_request" &&
        providers.gradleProperty("allowMissingTelegramCredentials").orNull != "true"

val checkTelegramCredentials = tasks.register("checkTelegramCredentials") {
    val failBuild = failWithoutTelegramCredentials
    doLast {
        if (failBuild) {
            throw GradleException(
                "Telegram API credentials are not set: add telegramApiId and telegramApiHash to " +
                    "secrets.properties in the project root or to ~/.gradle/gradle.properties. " +
                    "Add -PallowMissingTelegramCredentials=true to build without them."
            )
        }
    }
}

tasks.matching { Regex("(assemble|install)(Dev|Prod)(Debug|Release)").matches(it.name) }.configureEach {
    dependsOn(checkTelegramCredentials)
}

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
        versionCode = stableVersionCode
        versionName = stableVersion

        buildConfigField("int", "TELEGRAM_API_ID", telegramApiId)
        buildConfigField("String", "TELEGRAM_API_HASH", telegramApiHash.asBuildConfigString())

        // Публичная конфигурация диагностики (не секреты): адрес сервера отчётов и
        // служебный бот. Пустой DIAGNOSTICS_API_BASE_URL отключает отправку отчётов.
        // Переопределяются gradle-свойством или secrets.properties без изменения кода.
        val diagnosticsApiBaseUrl = secretProperty("diagnosticsApiBaseUrl", "https://beygram.beykus.fun")
        val reportBotUsername = secretProperty("reportBotUsername", "BeyGram_Bot")
        val reportBotUserId = secretProperty("reportBotUserId", "8841599020").ifBlank { "0" }
        buildConfigField("String", "DIAGNOSTICS_API_BASE_URL", diagnosticsApiBaseUrl.asBuildConfigString())
        buildConfigField("String", "REPORT_BOT_USERNAME", reportBotUsername.asBuildConfigString())
        buildConfigField("long", "REPORT_BOT_USER_ID", "${reportBotUserId.toLong()}L")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            // Dev and Stable are often installed side by side. Loopback ports are shared by every app on the
            // device, so each variant needs its own listener port (issue #75).
            buildConfigField("int", "MIHOMO_PROXY_PORT", "17892")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("int", "MIHOMO_PROXY_PORT", "17891")
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
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.snakeyaml)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
