package com.beykus.beygram.media.bgbeat

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

interface BgBeatPreferenceStorage {
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun registerListener(listener: (key: String) -> Unit)
}

private class SharedPreferencesBgBeatStorage(
    private val prefs: SharedPreferences
) : BgBeatPreferenceStorage {
    private val listeners = mutableListOf<(key: String) -> Unit>()
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null) {
            listeners.forEach { it(key) }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        prefs.getBoolean(key, defaultValue)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun registerListener(listener: (key: String) -> Unit) {
        listeners.add(listener)
    }
}

/**
 * App-private preferences and cache management for BG Beat.
 * Acts as a single source of truth for the enabled state across UI and background workers.
 */
class BgBeatSettingsStore internal constructor(
    private val storage: BgBeatPreferenceStorage,
    private val baseCacheDirProvider: () -> File
) {
    companion object {
        const val PREFS_NAME = "bgbeat_settings"
        const val KEY_ENABLED = "bgbeat_enabled"
        const val MAX_CACHE_BYTES = 50L * 1024L * 1024L // 50 MiB limit

        @Volatile
        private var instance: BgBeatSettingsStore? = null

        fun getInstance(context: Context): BgBeatSettingsStore {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val appContext = context.applicationContext
                    val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val storage = SharedPreferencesBgBeatStorage(prefs)
                    BgBeatSettingsStore(storage) {
                        File(appContext.filesDir, "bgbeat-cache").apply { mkdirs() }
                    }.also { instance = it }
                }
            }
        }

        fun getBaseCacheDir(context: Context): File {
            return File(context.filesDir, "bgbeat-cache").apply { mkdirs() }
        }

        fun getAccountCacheDir(context: Context, accountId: String): File {
            val safeId = accountId.ifBlank { "legacy" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            return File(getBaseCacheDir(context), "account_$safeId").apply { mkdirs() }
        }

        fun getAccountCacheDir(baseDir: File, accountId: String): File {
            val safeId = accountId.ifBlank { "legacy" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            return File(baseDir, "account_$safeId").apply { mkdirs() }
        }

        fun getCacheSizeBytes(context: Context): Long = getCacheSizeBytes(getBaseCacheDir(context))

        fun getCacheSizeBytes(baseDir: File): Long {
            if (!baseDir.exists()) return 0L
            return baseDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }

        fun clearAllCache(context: Context): Long = clearAllCache(getBaseCacheDir(context))

        fun clearAllCache(baseDir: File): Long {
            if (!baseDir.exists()) return 0L
            var freed = 0L
            baseDir.walkTopDown().filter { it.isFile }.forEach { file ->
                freed += file.length()
                file.delete()
            }
            return freed
        }

        fun clearAccountCache(context: Context, accountId: String): Long =
            clearAccountCache(getBaseCacheDir(context), accountId)

        fun clearAccountCache(baseDir: File, accountId: String): Long {
            val accountDir = getAccountCacheDir(baseDir, accountId)
            if (!accountDir.exists()) return 0L
            var freed = 0L
            accountDir.walkTopDown().filter { it.isFile }.forEach { file ->
                freed += file.length()
                file.delete()
            }
            return freed
        }

        fun enforceCacheQuota(context: Context, maxBytes: Long = MAX_CACHE_BYTES) =
            enforceCacheQuota(getBaseCacheDir(context), maxBytes)

        fun enforceCacheQuota(baseDir: File, maxBytes: Long = MAX_CACHE_BYTES) {
            if (!baseDir.exists()) return
            val files = baseDir.walkTopDown().filter { it.isFile }.sortedBy { it.lastModified() }.toList()
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

    private val _enabled = MutableStateFlow(storage.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    init {
        storage.registerListener { key ->
            if (key == KEY_ENABLED) {
                _enabled.value = storage.getBoolean(KEY_ENABLED, false)
            }
        }
    }

    @Synchronized
    fun setEnabled(value: Boolean) {
        storage.putBoolean(KEY_ENABLED, value)
        _enabled.value = value
    }

    fun getAccountCacheDir(accountId: String): File {
        return getAccountCacheDir(baseCacheDirProvider(), accountId)
    }
}
