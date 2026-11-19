package com.beykus.beygram.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

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
    private val releasesApi = "https://api.github.com/repos/Beykus-Y/BeyGram/releases?per_page=100&page="
    private val downloadPrefix = "https://github.com/Beykus-Y/BeyGram/releases/download/"
    override val updateDir: File get() = File(context.cacheDir, "dev-updates")
    override val filePrefix: String = "dev"

    override fun installedCode(): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    /** Newest DEV prerelease with its manifest. Returns null when the installed build is already current. */
    override suspend fun check(installedCode: Long): StableRelease? = withContext(Dispatchers.IO) {
        require(context.packageName == DEV_PACKAGE) { "Обновления DEV доступны только в DEV." }
        val release = pickLatestDevRelease(fetchReleases()) ?: return@withContext null
        val tag = release.getString("tag_name")
        val assets = release.getJSONArray("assets")
        val names = (0 until assets.length()).map { assets.getJSONObject(it).getString("name") }.toSet()
        if (DEV_APK_NAME !in names || DEV_MANIFEST_NAME !in names) {
            throw StableUpdateException(StableUpdateReason.MissingAsset, "В последней DEV-сборке нет APK или манифеста.")
        }
        val manifest = JSONObject(UpdateHttp.readText("$downloadPrefix$tag/$DEV_MANIFEST_NAME", 65_536))
        val code = manifest.getLong("versionCode")
        val size = manifest.getLong("size")
        val hash = manifest.getString("sha256").lowercase(Locale.ROOT)
        val signer = manifest.getString("signerSha256").lowercase(Locale.ROOT)
        if (manifest.getString("package") != DEV_PACKAGE || signer != DEV_SIGNER_SHA256 ||
            code !in 1..2_100_000_000L || size !in 1..DEV_MAX_APK_BYTES || !devDigestPattern.matches(hash)) {
            throw StableUpdateException(StableUpdateReason.InvalidMetadata, "Некорректный манифест DEV-сборки.")
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

    /** Downloads with resume support, hashes the whole file, then verifies the package and the pinned signer. */
    override suspend fun download(release: StableRelease, resume: PartialDownload?, listener: DownloadListener): File =
        withContext(Dispatchers.IO) {
            require(context.packageName == DEV_PACKAGE)
            require(
                release.apkName == DEV_APK_NAME && devTagPattern.matches(release.tag) &&
                    release.tag == release.versionName && release.versionCode > 0 &&
                    release.size in 1..DEV_MAX_APK_BYTES && devDigestPattern.matches(release.sha256)
            )
            val installed = installedCode()
            if (release.versionCode == installed) {
                throw StableUpdateException(StableUpdateReason.AlreadyInstalled, "Эта версия уже установлена.")
            }
            if (release.versionCode < installed) {
                throw StableUpdateException(StableUpdateReason.Outdated, "Версия DEV старше установленной.")
            }
            // Older DEV APKs are not deleted here: the shared retention policy removes stale ones, never a handed-off APK.
            downloadApkFile(
                updateDir = updateDir,
                prefix = filePrefix,
                url = "$downloadPrefix${release.tag}/${release.apkName}",
                release = release,
                resume = resume,
                listener = listener,
                verifyPackage = { apk -> verifyPackage(apk, release) },
            )
        }

    /** Recheck the bytes off the UI thread right before the platform installer. */
    override suspend fun verifyForInstall(apk: File, release: StableRelease) = withContext(Dispatchers.IO) {
        if (!apk.isFile || apk.parentFile?.canonicalFile != updateDir.canonicalFile) {
            throw StableUpdateException(StableUpdateReason.MissingFile, "Файл обновления не найден.")
        }
        if (apk.length() != release.size || sha256Of(apk) != release.sha256) {
            throw StableUpdateException(StableUpdateReason.ChecksumMismatch, "Контрольная сумма APK изменилась.")
        }
        verifyPackage(apk, release)
    }

    override fun canRequestInstalls(): Boolean = ApkInstaller.canRequestInstalls(context)

    override fun openInstallPermissionSettings() = ApkInstaller.openPermissionSettings(context)

    override fun launchInstaller(apk: File) = ApkInstaller.launch(context, apk, updateDir)

    /**
     * Up to three pages of releases. The endpoint sometimes answers page 1 with an empty list, though a
     * repository with DEV builds is never empty, so that case is retried.
     */
    private suspend fun fetchReleases(): List<JSONObject> {
        val all = ArrayList<JSONObject>()
        for (page in 1..3) {
            var batch = JSONArray(UpdateHttp.readText("$releasesApi$page", 4_000_000))
            if (page == 1) {
                repeat(2) {
                    if (batch.length() == 0) {
                        kotlinx.coroutines.delay(1_500)
                        batch = JSONArray(UpdateHttp.readText("$releasesApi$page", 4_000_000))
                    }
                }
                if (batch.length() == 0) {
                    throw StableUpdateException(StableUpdateReason.MissingRelease, "GitHub вернул пустой список сборок.")
                }
            }
            for (i in 0 until batch.length()) all += batch.getJSONObject(i)
            if (batch.length() < 100) break
        }
        return all
    }

    private fun verifyPackage(apk: File, release: StableRelease) {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw StableUpdateException(StableUpdateReason.PackageMismatch, "Android не распознал скачанный APK.")
        if (archive.packageName != DEV_PACKAGE || archive.longVersionCode != release.versionCode) {
            throw StableUpdateException(StableUpdateReason.PackageMismatch, "APK не соответствует ожидаемому пакету или версии.")
        }
        if (archive.longVersionCode <= installedCode()) {
            throw StableUpdateException(StableUpdateReason.Outdated, "APK не новее установленной версии.")
        }
        if ((archive.applicationInfo?.minSdkVersion ?: 0) > Build.VERSION.SDK_INT) {
            throw StableUpdateException(StableUpdateReason.PackageMismatch, "Версия Android не поддерживается этим APK.")
        }
        // The pinned DEV key is the only accepted signer. Android's installer checks the rest of the lineage.
        if (apkSignerDigests(apk.absolutePath) != setOf(DEV_SIGNER_SHA256)) {
            throw StableUpdateException(StableUpdateReason.SignerMismatch, "APK подписан другим ключом.")
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
}

private fun isDevPrerelease(release: JSONObject): Boolean =
    release.optBoolean("prerelease") && !release.optBoolean("draft") &&
        devTagPattern.matches(release.optString("tag_name"))

/** GitHub does not list releases in tag order (dev-112 can sit below dev-99), so the highest dev-N wins. */
internal fun pickLatestDevRelease(releases: List<JSONObject>): JSONObject? =
    releases.filter(::isDevPrerelease)
        .maxByOrNull { it.getString("tag_name").removePrefix("dev-").toLongOrNull() ?: 0L }
