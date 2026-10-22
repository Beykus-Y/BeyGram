package com.beykus.beygram.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

internal const val DEV_PACKAGE = "com.beykus.beygram.dev"

/** SHA-256 of the signing certificate used by android-dev-channel.yml. The same key signs Stable. */
private const val DEV_SIGNER_SHA256 = "e7b9e482b2c2feacda9edcef17f960576ed94e94ff8cf7991f89a16e33bf436d"
private const val DEV_APK_NAME = "BeyGram-dev-beta.apk"
private const val DEV_MANIFEST_NAME = "manifest.json"
private const val DEV_MAX_APK_BYTES = 400_000_000L
private val devTagPattern = Regex("^dev-[0-9]+$")
private val devDigestPattern = Regex("^[0-9a-f]{64}$")

/**
 * Обновления DEV beta из предрелизов dev-N. Работает только в пакете DEV и принимает только APK,
 * подписанный закреплённым ключом DEV, и только если манифест совпадает с файлом.
 */
class DevUpdateRepository(private val context: Context) : ApkUpdateSource {
    private val releasesApi = "https://api.github.com/repos/Beykus-Y/BeyGram/releases?per_page=100"
    private val downloadPrefix = "https://github.com/Beykus-Y/BeyGram/releases/download/"
    private val updateDir: File get() = File(context.cacheDir, "dev-updates")

    override fun installedCode(): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    /** Newest DEV prerelease with its manifest. Returns null when the installed build is already current. */
    override suspend fun check(installedCode: Long): StableRelease? = withContext(Dispatchers.IO) {
        require(context.packageName == DEV_PACKAGE) { "Обновления DEV доступны только в DEV." }
        val releases = fetchReleases()
        // GitHub does not list releases in tag order (dev-112 can sit below dev-99), so pick the highest dev-N.
        val release = (0 until releases.length()).map { releases.getJSONObject(it) }
            .filter { isDevPrerelease(it) }
            .maxByOrNull { it.getString("tag_name").removePrefix("dev-").toLongOrNull() ?: 0L }
            ?: return@withContext null
        val tag = release.getString("tag_name")
        val assets = release.getJSONArray("assets")
        val names = (0 until assets.length()).map { assets.getJSONObject(it).getString("name") }.toSet()
        if (DEV_APK_NAME !in names || DEV_MANIFEST_NAME !in names) {
            throw IOException("В последней DEV-сборке нет APK или манифеста.")
        }
        val manifest = JSONObject(readText("$downloadPrefix$tag/$DEV_MANIFEST_NAME", 65_536))
        val code = manifest.getLong("versionCode")
        val size = manifest.getLong("size")
        val hash = manifest.getString("sha256").lowercase(Locale.ROOT)
        val signer = manifest.getString("signerSha256").lowercase(Locale.ROOT)
        if (manifest.getString("package") != DEV_PACKAGE || signer != DEV_SIGNER_SHA256 ||
            code !in 1..2_100_000_000L || size !in 1..DEV_MAX_APK_BYTES || !devDigestPattern.matches(hash)) {
            throw IOException("Некорректный манифест DEV-сборки: установка запрещена.")
        }
        if (code <= installedCode) return@withContext null
        StableRelease(
            tag = tag,
            versionName = tag,
            versionCode = code,
            apkName = DEV_APK_NAME,
            size = size,
            sha256 = hash,
            notes = release.optString("body", "").take(15_000),
            publishedAt = release.optString("published_at", ""),
        )
    }

    /** Download into a private cache path, hash while streaming, then verify the package and signer. */
    override suspend fun download(release: StableRelease, onProgress: suspend (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            require(context.packageName == DEV_PACKAGE)
            require(
                release.apkName == DEV_APK_NAME && devTagPattern.matches(release.tag) &&
                    release.tag == release.versionName && release.versionCode > 0 &&
                    release.size in 1..DEV_MAX_APK_BYTES && devDigestPattern.matches(release.sha256)
            )
            if (release.versionCode <= installedCode()) {
                throw IOException("Эта версия уже установлена или устарела.")
            }
            if (!updateDir.exists() && !updateDir.mkdirs()) throw IOException("Нет места для загрузки.")
            // A new download replaces older DEV APKs, so the cache holds at most one of them.
            updateDir.listFiles()?.forEach { if (it.name.startsWith("dev-")) it.delete() }
            // Every user-initiated download gets its own path, so an APK already handed to the installer is never rewritten.
            val downloadId = UUID.randomUUID().toString()
            val temp = File(updateDir, "dev-$downloadId.part")
            val target = File(updateDir, "dev-$downloadId.apk")
            if (updateDir.usableSpace < release.size + 20_000_000L) {
                throw IOException("Недостаточно свободного места для APK.")
            }
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                val connection = connect("$downloadPrefix${release.tag}/${release.apkName}")
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
                                currentCoroutineContext().ensureActive()
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
                // Only files created by this failed attempt may be removed.
                temp.delete()
                target.delete()
                throw error
            }
        }

    /** Recheck the bytes off the UI thread right before the platform installer. */
    override suspend fun verifyForInstall(apk: File, release: StableRelease) = withContext(Dispatchers.IO) {
        if (!apk.isFile || apk.parentFile?.canonicalFile != updateDir.canonicalFile) {
            throw IOException("Файл обновления не найден.")
        }
        val bytes = apk.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        if (apk.length() != release.size || bytes != release.sha256) {
            throw IOException("Контрольная сумма APK изменилась.")
        }
        verifyPackage(apk, release)
    }

    /** Android 8+ asks for consent to install from this source. Call on the main thread. */
    override fun install(apk: File): Boolean {
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

    /** The releases endpoint sometimes answers with an empty list; a repository with DEV builds is never empty. */
    private suspend fun fetchReleases(): JSONArray {
        repeat(3) {
            val releases = JSONArray(readText(releasesApi, 4_000_000))
            if (releases.length() > 0) return releases
            kotlinx.coroutines.delay(1_500)
        }
        throw IOException("GitHub вернул пустой список сборок. Повторите проверку позже.")
    }

    private fun isDevPrerelease(release: JSONObject): Boolean =
        release.optBoolean("prerelease") && !release.optBoolean("draft") &&
            devTagPattern.matches(release.optString("tag_name"))

    private fun verifyPackage(apk: File, release: StableRelease) {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw IOException("Android не распознал скачанный APK.")
        if (archive.packageName != DEV_PACKAGE || archive.longVersionCode != release.versionCode ||
            archive.longVersionCode <= installedCode()) {
            throw IOException("APK не соответствует ожидаемому пакету или версии.")
        }
        if ((archive.applicationInfo?.minSdkVersion ?: 0) > Build.VERSION.SDK_INT) {
            throw IOException("Версия Android не поддерживается этим APK.")
        }
        // The pinned DEV key is the only accepted signer. Android's installer checks the rest of the lineage.
        if (apkSignerDigests(apk.absolutePath) != setOf(DEV_SIGNER_SHA256)) {
            throw IOException("APK подписан другим ключом. Установка запрещена.")
        }
    }

    /** Signer digests of an APK. Android 9 and older may leave signingInfo empty, so the legacy field is the fallback. */
    @Suppress("DEPRECATION")
    private fun apkSignerDigests(path: String): Set<String> {
        val pm = context.packageManager
        val modern = pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?.signingInfo?.apkContentsSigners?.takeIf { it.isNotEmpty() }
        val signatures = modern ?: pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES)?.signatures
            ?: return emptySet()
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    /** Only GitHub hosts may serve the DEV APK; redirects to other hosts are refused. */
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
                    throw IOException(when (status) {
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

    private suspend fun readText(url: String, limit: Int): String {
        val connection = connect(url)
        try {
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
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
}
