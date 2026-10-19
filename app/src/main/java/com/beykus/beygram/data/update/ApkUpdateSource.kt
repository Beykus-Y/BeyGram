package com.beykus.beygram.data.update

import java.io.File

/** What the update screen needs from a release channel. Stable and DEV each apply their own checks. */
interface ApkUpdateSource {
    fun installedCode(): Long
    suspend fun check(installedCode: Long): StableRelease?
    suspend fun download(release: StableRelease, onProgress: suspend (Int) -> Unit): File
    suspend fun verifyForInstall(apk: File, release: StableRelease)
    fun install(apk: File): Boolean
}
