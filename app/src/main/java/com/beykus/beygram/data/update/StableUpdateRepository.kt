package com.beykus.beygram.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.coroutineContext

/** The updater is deliberately independent of TDLib and never runs without a user action. */
data class StableRelease(
    val tag: String,
    val versionName: String,
    val versionCode: Long,
    val apkName: String,
    val size: Long,
    val sha256: String,
    val notes: String,
    val publishedAt: String,
)

object StableUpdatePolicy {
    const val PACKAGE = "com.beykus.beygram"
    const val MANIFEST = "BeyGram-stable-update.json"
    const val MAX_APK_BYTES = 400_000_000L
    private val versionPattern = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+$")
    private val digestPattern = Regex("^[0-9a-f]{64}$")

    fun versionCode(name: String): Long? {
        if (!versionPattern.matches(name)) return null
        val numbers = name.split('.').map { it.toLongOrNull() ?: return null }
        if (numbers[0] !in 0..20 || numbers[1] !in 0..99 ||
            numbers[2] !in 0..99 || numbers[3] !in 0..9999) return null
        val code = numbers[0] * 100_000_000L + numbers[1] * 1_000_000L +
            numbers[2] * 10_000L + numbers[3]
        return code.takeIf { it in 1..2_100_000_000L }
    }

    fun valid(name: String, code: Long, size: Long, checksum: String, apkName: String): Boolean =
        versionCode(name) == code &&
            size in 1..MAX_APK_BYTES && digestPattern.matches(checksum) &&
            apkName == "BeyGram-$name-prod-release.apk"

    fun isNewer(installed: Long, candidate: Long): Boolean = candidate > installed
}

class StableUpdateRepository(private val context: Context) {
    private val repoApi = "https://api.github.com/repos/Beykus-Y/BeyGram/releases/latest"
    private val downloadPrefix = "https://github.com/Beykus-Y/BeyGram/releases/download/"
    private val updateDir: File get() = File(context.cacheDir, "stable-updates")

    /** Fetch only the latest published non-prerelease release and its matched manifest. */
    suspend fun check(installedCode: Long): StableRelease? = withContext(Dispatchers.IO) {
        require(context.packageName == StableUpdatePolicy.PACKAGE) { "Обновления доступны только в Stable." }
        val release = JSONObject(readText(repoApi, 2_000_000))
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
            throw IOException("Последний релиз не является Stable.")
        }
        val tag = release.getString("tag_name")
        if (!tag.matches(Regex("^v[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+$"))) {
            throw IOException("Неверный формат версии на GitHub.")
        }
        val version = tag.removePrefix("v")
        val apkName = "BeyGram-$version-prod-release.apk"
        val assets = release.getJSONArray("assets")
        var hasManifest = false
        var hasApk = false
        for (i in 0 until assets.length()) {
            val item = assets.getJSONObject(i)
            when (item.getString("name")) {
                StableUpdatePolicy.MANIFEST -> hasManifest = true
                apkName -> hasApk = true
            }
        }
        if (!hasManifest || !hasApk) throw IOException(
            "У релиза нет проверенных метаданных или Stable APK. Возможно, он выпущен до появления обновлений."
        )
        val base = "$downloadPrefix$tag/"
        val manifest = JSONObject(readText(base + StableUpdatePolicy.MANIFEST, 65_536))
        val name = manifest.getString("versionName")
        val code = manifest.getLong("versionCode")
        val size = manifest.getLong("size")
        val hash = manifest.getString("sha256").lowercase(Locale.ROOT)
        val asset = manifest.getString("apkName")
        if (manifest.getInt("schema") != 1 || name != version ||
            !StableUpdatePolicy.valid(name, code, size, hash, asset)) {
            throw IOException("Некорректные метаданные версии: установка запрещена.")
        }
        // The asset name must match the signed manifest and GitHub's public release listing.
        if (!StableUpdatePolicy.isNewer(installedCode, code)) return@withContext null
        StableRelease(
            tag, name, code, asset, size, hash,
            release.optString("body", "").take(15_000),
            release.optString("published_at", "")
        )
    }

    /** Download into cache, hash while streaming, verify Android package identity and signature. */
    suspend fun download(release: StableRelease, onProgress: suspend (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            require(context.packageName == StableUpdatePolicy.PACKAGE)
            require(StableUpdatePolicy.valid(
                release.versionName, release.versionCode, release.size, release.sha256, release.apkName
            ) && release.tag == "v${release.versionName}")
            if (!StableUpdatePolicy.isNewer(installedCode(), release.versionCode)) {
                throw IOException("Эта версия уже установлена или устарела.")
            }
            if (!updateDir.exists() && !updateDir.mkdirs()) throw IOException("Нет места для загрузки.")
            val temp = File(updateDir, "stable.apk.part")
            val target = File(updateDir, "stable.apk")
            temp.delete()
            target.delete()
            if (updateDir.usableSpace < release.size + 20_000_000L) {
                throw IOException("Недостаточно свободного места для APK.")
            }
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                val url = "$downloadPrefix${release.tag}/${release.apkName}"
                val connection = connect(url)
                try {
                    val reportedSize = connection.contentLengthLong
                    if (reportedSize > 0 && reportedSize != release.size) {
                        throw IOException("Размер файла на сервере изменился.")
                    }
                    var downloaded = 0L
                    var lastPercent = -1
                    connection.inputStream.use { input ->
                        temp.outputStream().buffered().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                coroutineContext.ensureActive()
                                val count = input.read(buffer)
                                if (count == -1) break
                                downloaded += count
                                if (downloaded > release.size) throw IOException("Размер APK превышен.")
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                                val percent = ((downloaded * 100) / release.size).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent)
                                }
                            }
                        }
                    }
                    if (downloaded != release.size ||
                        digest.digest().joinToString("") { "%02x".format(it) } != release.sha256) {
                        throw IOException("Контрольная сумма APK не совпадает.")
                    }
                } finally {
                    connection.disconnect()
                }
                if (!temp.renameTo(target)) throw IOException("Не удалось сохранить APK.")
                verifyPackage(target, release)
                target
            } catch (error: Throwable) {
                temp.delete()
                target.delete()
                throw error
            }
        }

    fun installedCode(): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    private fun verifyPackage(apk: File, release: StableRelease) {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw IOException("Android не распознал скачанный APK.")
        if (archive.packageName != StableUpdatePolicy.PACKAGE ||
            archive.longVersionCode != release.versionCode ||
            archive.longVersionCode <= installedCode()) {
            throw IOException("APK не соответствует ожидаемому пакету или версии.")
        }
        if ((archive.applicationInfo?.minSdkVersion ?: 0) > Build.VERSION.SDK_INT) {
            throw IOException("Версия Android не поддерживается этим APK.")
        }
        @Suppress("DEPRECATION")
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val currentSigners = installed.signingInfo?.apkContentsSigners
            ?.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toList() }?.toSet()
        val downloadedSigners = archive.signingInfo?.apkContentsSigners
            ?.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toList() }?.toSet()
        val signingHistory = archive.signingInfo?.signingCertificateHistory
            ?.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toList() }?.toSet()
        if (currentSigners.isNullOrEmpty() || downloadedSigners.isNullOrEmpty() ||
            (currentSigners != downloadedSigners &&
                (currentSigners.size != 1 || downloadedSigners.size != 1 ||
                    signingHistory?.containsAll(currentSigners) != true))) {
            throw IOException("Подпись обновления не совпадает с подписью установленного BeyGram.")
        }
        // Android's Package Installer remains the final authority for signing lineage and ABI compatibility.
    }

    /** On Android O+ installing from this app requires explicit user permission. */
    fun install(apk: File): Boolean {
        if (!apk.isFile || apk.parentFile?.canonicalFile != updateDir.canonicalFile) {
            throw IOException("Файл обновления не найден.")
        }
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(permissionIntent)
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    /** Explicit redirects let us refuse non-GitHub CDNs and insecure protocol changes. */
    private fun connect(address: String): HttpURLConnection {
        var url = URL(address)
        repeat(6) {
            if (url.protocol != "https" || url.host.lowercase(Locale.ROOT) !in setOf(
                    "github.com", "api.github.com", "release-assets.githubusercontent.com",
                    "objects.githubusercontent.com"
                )) throw IOException("Сервер перенаправил загрузку на недоверенный адрес.")
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            connection.setRequestProperty("User-Agent", "BeyGram-Android-Updater")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            val status = connection.responseCode
            if (status in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: throw IOException("Сервер вернул пустое перенаправление.")
                url = URL(url, location)
                connection.disconnect()
            } else {
                if (status !in 200..299) {
                    connection.disconnect()
                    throw IOException(when(status) {
                        403, 429 -> "GitHub ограничил частоту запросов. Повторите проверку позже."
                        404 -> "Релиз не найден на GitHub."
                        else -> "Ошибка GitHub: HTTP $status."
                    })
                }
                return connection
            }
        }
        throw IOException("Слишком много перенаправлений при скачивании.")
    }

    private fun readText(url: String, limit: Int): String {
        val connection = connect(url)
        try {
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    coroutineContextPlaceholder()
                    val count = input.read(buffer)
                    if (count == -1) break
                    if (output.size() + count > limit) throw IOException("Ответ GitHub слишком большой.")
                    output.write(buffer, 0, count)
                }
                return output.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun coroutineContextPlaceholder() {
        if (Thread.currentThread().isInterrupted) throw IOException("Операция прервана.")
    }
}
