package com.beykus.beygram.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

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
    /** DEV only: build number, commit and the cumulative changes. Null for Stable. */
    val dev: DevReleaseInfo? = null,
    /** True when the release body was longer than the 15_000 characters kept in [notes]. */
    val notesTruncated: Boolean = false,
) {
    /*
     * Identity is the published APK artifact (tag/version, file name, size, SHA-256). notes, publishedAt, dev and
     * notesTruncated are display details that may be edited on GitHub without changing the APK, so a re-check with
     * edited release notes must still match an already downloaded and verified APK (UpdateCoordinator.install and
     * UpdateCardModel compare releases with ==).
     */
    override fun equals(other: Any?): Boolean =
        other is StableRelease && tag == other.tag && versionName == other.versionName &&
            versionCode == other.versionCode && apkName == other.apkName && size == other.size &&
            sha256 == other.sha256

    override fun hashCode(): Int =
        listOf(tag, versionName, versionCode, apkName, size, sha256).hashCode()
}

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

/**
 * Never accept unknown certificates. On Android 10, PackageManager may parse an archive
 * without filling signingInfo; GET_SIGNATURES is then queried as a compatibility fallback.
 */
internal object StableApkSignerPolicy {
    fun allowLegacyFallback(sdk: Int, modernSignersAvailable: Boolean): Boolean =
        sdk <= 29 && !modernSignersAvailable

    fun compatible(
        installed: Set<String>?,
        candidate: Set<String>?,
        candidateHistory: Set<String>?,
    ): Boolean {
        if (installed.isNullOrEmpty() || candidate.isNullOrEmpty()) return false
        if (installed == candidate) return true
        // Android's installer still validates cryptographic proof of signing rotation.
        return installed.size == 1 && candidate.size == 1 &&
            candidateHistory?.containsAll(installed) == true &&
            candidateHistory.containsAll(candidate)
    }
}

/** The typed failures of the updater, shared by Stable and DEV. Each one has its own text on the screen. */
enum class StableUpdateReason {
    Network, Timeout, RateLimited, MissingRelease, MissingAsset, ServerError, InvalidMetadata,
    NoFreeSpace, AlreadyInstalled, Outdated, ChecksumMismatch, PackageMismatch, SignerMismatch, InstallerLaunch,
    MissingFile, Storage, UntrustedRedirect,
}

/** A failure with a known [reason]. [message] is for the log only; the screen words the failure by [reason]. */
class StableUpdateException(val reason: StableUpdateReason, message: String) : IOException(message)

class StableUpdateRepository(private val context: Context) : ApkUpdateSource {
    private val repoApi = "https://api.github.com/repos/Beykus-Y/BeyGram/releases/latest"
    private val downloadPrefix = "https://github.com/Beykus-Y/BeyGram/releases/download/"
    override val updateDir: File get() = File(context.cacheDir, "stable-updates")
    override val filePrefix: String = "stable"

    /** Fetch only the latest published non-prerelease release and its matched manifest. */
    override suspend fun check(installedCode: Long): StableRelease? = withContext(Dispatchers.IO) {
        require(context.packageName == StableUpdatePolicy.PACKAGE) { "Обновления доступны только в Stable." }
        val release = JSONObject(UpdateHttp.readText(repoApi, 2_000_000))
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
            throw StableUpdateException(StableUpdateReason.MissingRelease, "Последний релиз не является Stable.")
        }
        val tag = release.getString("tag_name")
        if (!tag.matches(Regex("^v[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+$"))) {
            throw StableUpdateException(StableUpdateReason.InvalidMetadata, "Неверный формат версии на GitHub.")
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
        if (!hasManifest || !hasApk) {
            throw StableUpdateException(
                StableUpdateReason.MissingAsset,
                "У релиза нет проверенных метаданных или Stable APK.",
            )
        }
        val base = "$downloadPrefix$tag/"
        val manifest = JSONObject(UpdateHttp.readText(base + StableUpdatePolicy.MANIFEST, 65_536))
        val name = manifest.getString("versionName")
        val code = manifest.getLong("versionCode")
        val size = manifest.getLong("size")
        val hash = manifest.getString("sha256").lowercase(Locale.ROOT)
        val asset = manifest.getString("apkName")
        if (manifest.getInt("schema") != 1 || name != version ||
            !StableUpdatePolicy.valid(name, code, size, hash, asset)) {
            throw StableUpdateException(StableUpdateReason.InvalidMetadata, "Некорректные метаданные версии.")
        }
        // The asset name must match the signed manifest and GitHub's public release listing.
        if (!StableUpdatePolicy.isNewer(installedCode, code)) return@withContext null
        val body = release.optString("body", "")
        StableRelease(
            tag, name, code, asset, size, hash,
            body.take(15_000),
            release.optString("published_at", ""),
            notesTruncated = body.length > 15_000,
        )
    }

    /** Downloads with resume support, hashes the whole file and verifies Android package identity and signer. */
    override suspend fun download(release: StableRelease, resume: PartialDownload?, listener: DownloadListener): File =
        withContext(Dispatchers.IO) {
            require(context.packageName == StableUpdatePolicy.PACKAGE)
            require(StableUpdatePolicy.valid(
                release.versionName, release.versionCode, release.size, release.sha256, release.apkName
            ) && release.tag == "v${release.versionName}")
            val installed = installedCode()
            if (release.versionCode == installed) {
                throw StableUpdateException(StableUpdateReason.AlreadyInstalled, "Эта версия уже установлена.")
            }
            if (release.versionCode < installed) {
                throw StableUpdateException(StableUpdateReason.Outdated, "Версия на GitHub старше установленной.")
            }
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

    override fun installedCode(): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return info.longVersionCode
    }

    /** Reads signers from an APK archive, including the Android 10 compatibility path. */
    @Suppress("DEPRECATION")
    private fun archiveSigners(pm: PackageManager, path: String, archive: android.content.pm.PackageInfo):
        Pair<Set<String>?, Set<String>?> {
        val modern = archive.signingInfo
        val modernSigners = modern?.apkContentsSigners?.takeIf { it.isNotEmpty() }
        if (StableApkSignerPolicy.allowLegacyFallback(Build.VERSION.SDK_INT, modernSigners != null)) {
            val legacy = pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES)
            return Pair(certificateFingerprints(legacy?.signatures), null)
        }
        return Pair(
            certificateFingerprints(modernSigners),
            certificateFingerprints(modern?.signingCertificateHistory)
        )
    }

    @Suppress("DEPRECATION")
    private fun installedSigners(pm: PackageManager):
        Set<String>? {
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val modern = installed.signingInfo?.apkContentsSigners?.takeIf { it.isNotEmpty() }
        if (StableApkSignerPolicy.allowLegacyFallback(Build.VERSION.SDK_INT, modern != null)) {
            return certificateFingerprints(
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            )
        }
        return certificateFingerprints(modern)
    }

    private fun certificateFingerprints(signatures: Array<android.content.pm.Signature>?): Set<String>? =
        signatures?.takeIf { it.isNotEmpty() }?.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }?.toSet()

    /** Also exercised by an instrumentation test against the APK installed on API 29. */
    @Suppress("DEPRECATION")
    internal fun isArchiveSignerCompatible(apkPath: String): Boolean {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: return false
        val (candidate, history) = archiveSigners(pm, apkPath, archive)
        return StableApkSignerPolicy.compatible(installedSigners(pm), candidate, history)
    }

    private fun verifyPackage(apk: File, release: StableRelease) {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw StableUpdateException(StableUpdateReason.PackageMismatch, "Android не распознал скачанный APK.")
        if (archive.packageName != StableUpdatePolicy.PACKAGE || archive.longVersionCode != release.versionCode) {
            throw StableUpdateException(StableUpdateReason.PackageMismatch, "APK не соответствует ожидаемому пакету или версии.")
        }
        if (archive.longVersionCode <= installedCode()) {
            throw StableUpdateException(StableUpdateReason.Outdated, "APK не новее установленной версии.")
        }
        if ((archive.applicationInfo?.minSdkVersion ?: 0) > Build.VERSION.SDK_INT) {
            throw StableUpdateException(StableUpdateReason.PackageMismatch, "Версия Android не поддерживается этим APK.")
        }
        if (!isArchiveSignerCompatible(apk.absolutePath)) {
            throw StableUpdateException(StableUpdateReason.SignerMismatch, "Не удалось подтвердить совместимую подпись APK.")
        }
        // Android's Package Installer still performs authoritative signing-lineage, ABI and version checks.
    }

    /** Recheck the bytes off the UI thread immediately before the platform installer. */
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
}
