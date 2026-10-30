package com.beykus.beygram.media.bgbeat

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * App-private preferences and cache management for BG Beat.
 */
class BgBeatSettingsStore(context: Context) {
    companion object {
        private const val PREFS_NAME = "bgbeat_settings"
        private const val KEY_ENABLED = "bgbeat_enabled"
        const val MAX_CACHE_BYTES = 50L * 1024L * 1024L // 50 MiB limit

        fun getBaseCacheDir(context: Context): File {
            return File(context.filesDir, "bgbeat-cache").apply { mkdirs() }
        }

        fun getAccountCacheDir(context: Context, accountId: String = "legacy"): File {
            val safeId = accountId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            return File(getBaseCacheDir(context), "account_$safeId").apply { mkdirs() }
        }

        fun getCacheSizeBytes(context: Context): Long {
            val base = getBaseCacheDir(context)
            if (!base.exists()) return 0L
            return base.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }

        fun clearAllCache(context: Context): Long {
            val base = getBaseCacheDir(context)
            if (!base.exists()) return 0L
            var freed = 0L
            base.walkTopDown().filter { it.isFile }.forEach { file ->
                freed += file.length()
                file.delete()
            }
            return freed
        }

        fun enforceCacheQuota(context: Context, maxBytes: Long = MAX_CACHE_BYTES) {
            val base = getBaseCacheDir(context)
            if (!base.exists()) return
            val files = base.walkTopDown().filter { it.isFile }.sortedBy { it.lastModified() }.toList()
            var total = files.sumOf { it.length() }
            for (file in files) {
                if (total <= maxBytes) break
                val len = file.length()
                if (file.delete()) {
                    total -= len
                }
            }
        }
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    @Synchronized
    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }
}
