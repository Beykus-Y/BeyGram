package com.beykus.beygram.data.update

import java.io.File

/**
 * Bounded cleanup of one channel's update folder. Only `.apk` and `.part` files directly inside that folder are
 * candidates. Protected files (the ready APK, the APK handed to the installer, the resumable partial) are never removed.
 */
internal object ApkRetentionPolicy {
    const val KEEP_UNPROTECTED_APKS = 2
    const val APK_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    const val PARTIAL_MAX_AGE_MS = 24L * 60 * 60 * 1000

    data class Entry(val name: String, val size: Long, val lastModifiedMs: Long, val isProtected: Boolean)

    fun staleNames(entries: List<Entry>, nowMs: Long): List<String> {
        val stale = LinkedHashSet<String>()
        entries.filter { !it.isProtected && it.name.endsWith(".apk") }
            .sortedByDescending { it.lastModifiedMs }
            .forEachIndexed { index, entry ->
                if (index >= KEEP_UNPROTECTED_APKS || nowMs - entry.lastModifiedMs > APK_MAX_AGE_MS) {
                    stale += entry.name
                }
            }
        entries.filter { !it.isProtected && it.name.endsWith(".part") && nowMs - it.lastModifiedMs > PARTIAL_MAX_AGE_MS }
            .forEach { stale += it.name }
        return stale.toList()
    }

    fun cleanUp(dir: File, protectedNames: Set<String>, nowMs: Long) {
        val entries = dir.listFiles().orEmpty()
            .filter { it.isFile }
            .map { Entry(it.name, it.length(), it.lastModified(), it.name in protectedNames) }
        staleNames(entries, nowMs).forEach { File(dir, it).delete() }
    }
}
