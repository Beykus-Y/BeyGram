package com.beykus.beygram.data.update

import java.io.File

/** What the update coordinator needs from a release channel. Stable and DEV each apply their own checks. */
interface ApkUpdateSource {
    /** The folder of this channel's APKs and partial downloads. Nothing outside it is ever deleted. */
    val updateDir: File
    /** The start of this channel's APK file names. */
    val filePrefix: String

    fun installedCode(): Long
    suspend fun check(installedCode: Long): StableRelease?

    /** Downloads [release], resuming [resume] when it binds to the release, and returns a verified new APK file. */
    suspend fun download(release: StableRelease, resume: PartialDownload?, listener: DownloadListener): File

    /** Re-hashes and re-checks the file right before it goes to the installer. */
    suspend fun verifyForInstall(apk: File, release: StableRelease)

    fun canRequestInstalls(): Boolean
    fun openInstallPermissionSettings()
    fun launchInstaller(apk: File)
}
