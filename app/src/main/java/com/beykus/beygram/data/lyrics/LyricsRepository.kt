package com.beykus.beygram.data.lyrics

import android.content.Context
import com.beykus.beygram.data.music.provider.MusicProviders
import com.beykus.beygram.domain.lyrics.LyricsDocument
import com.beykus.beygram.domain.lyrics.LyricsFetchResult
import com.beykus.beygram.domain.lyrics.LyricsLoadState
import com.beykus.beygram.domain.lyrics.LyricsSource
import com.beykus.beygram.domain.lyrics.LyricsSourceId
import com.beykus.beygram.domain.lyrics.LyricsSyncMode
import com.beykus.beygram.domain.lyrics.LyricsUnavailableReasonV2
import com.beykus.beygram.domain.music.TrackAudioSource
import com.beykus.beygram.domain.music.TrackIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Coordinates multi-source lyrics lookup with bounded concurrency, early usable result emission,
 * ranking, cancellation, stale-callback protection, and bounded memory/disk caching with TTL.
 */
class LyricsRepository(
    private val sources: List<LyricsSource>,
    private val diskCache: LyricsDiskCache? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val maxConcurrency: Int = 2,
) {
    private sealed interface MemoryEntry {
        val storedAtMs: Long
        val ttlMs: Long

        data class Hit(
            val document: LyricsDocument,
            val score: Float,
            override val storedAtMs: Long,
            override val ttlMs: Long,
        ) : MemoryEntry

        data class Miss(
            override val storedAtMs: Long,
            override val ttlMs: Long,
        ) : MemoryEntry
    }

    private val cacheLock = Any()

    @Volatile
    private var cacheGeneration = 0L

    private val memoryCache = object : LinkedHashMap<String, MemoryEntry>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MemoryEntry>?): Boolean =
            size > MEMORY_CACHE_LIMIT
    }

    fun observeLyrics(
        identity: TrackIdentity,
        settings: LyricsSourceSettings,
        forceRefresh: Boolean = false,
    ): Flow<LyricsLoadState> = channelFlow {
        send(LyricsLoadState.Loading)
        val key = cacheKey(identity)
        val negKey = negativeCacheKey(identity, settings)
        if (forceRefresh) {
            invalidateOffMain(identity)
        }

        val startedMemGen = synchronized(cacheLock) { cacheGeneration }
        val startedDiskGen = diskCache?.currentGeneration() ?: 0

        val currentMs = now()
        val cachedHit = readMemoryHit(key, currentMs)?.takeIf { isAllowedBySettingsAndPrivacy(it.document, identity, settings) }
        if (cachedHit != null) {
            send(LyricsLoadState.Ready(cachedHit.document, isUpgrading = false))
            return@channelFlow
        }

        val diskHit = if (diskCache != null) {
            withContext(Dispatchers.IO) { diskCache.get(key, currentMs) }
        } else {
            null
        }?.takeIf { isAllowedBySettingsAndPrivacy(it.document, identity, settings) }
        if (diskHit != null) {
            writeMemory(
                key = key,
                entry = MemoryEntry.Hit(
                    document = diskHit.document,
                    score = 1f,
                    storedAtMs = diskHit.storedAtMs,
                    ttlMs = diskHit.ttlMs,
                ),
                expectedGen = startedMemGen,
            )
            send(LyricsLoadState.Ready(diskHit.document, isUpgrading = false))
            return@channelFlow
        }

        if (!forceRefresh && hasValidNegativeCache(negKey, currentMs)) {
            send(LyricsLoadState.Unavailable(fallbackUnavailableReason(identity, settings, sawAuth = false, sawRetryable = false)))
            return@channelFlow
        }

        val eligibleSources = sources.filter { source ->
            isSourceEnabled(source.id, settings) &&
                (!source.isExternalNetwork || (settings.externalLookupEnabled && identity.privacy.allowsExternalMetadataLookup)) &&
                source.supports(identity)
        }

        if (eligibleSources.isEmpty()) {
            send(LyricsLoadState.Unavailable(fallbackUnavailableReason(identity, settings, sawAuth = false, sawRetryable = false)))
            return@channelFlow
        }

        val semaphore = Semaphore(maxConcurrency.coerceAtLeast(1))
        val stateLock = Any()
        var best: RankedDoc? = null
        var sawAuth = false
        var sawRetryable = false
        var retryBackoffMs = 0L
        var sawNonRetryableFailure = false
        var remaining = eligibleSources.size

        coroutineScope {
            eligibleSources.map { source ->
                async {
                    val outcome = semaphore.withPermit { source.fetch(identity) }
                    val emitDoc: LyricsDocument?
                    val emitUpgrading: Boolean
                    synchronized(stateLock) {
                        remaining -= 1
                        when (outcome) {
                            is LyricsFetchResult.Found -> {
                                val candidate = RankedDoc(outcome.document, outcome.matchScore)
                                if (best == null || isBetter(candidate, best!!)) {
                                    best = candidate
                                    emitDoc = candidate.document
                                    emitUpgrading = remaining > 0
                                } else {
                                    emitDoc = null
                                    emitUpgrading = false
                                }
                            }
                            LyricsFetchResult.AuthRequired -> {
                                sawAuth = true
                                emitDoc = null
                                emitUpgrading = false
                            }
                            is LyricsFetchResult.Unavailable -> {
                                if (outcome.retryable) {
                                    sawRetryable = true
                                    val hint = outcome.retryAfterMs ?: RETRYABLE_BACKOFF_DEFAULT_MS
                                    if (hint > retryBackoffMs) retryBackoffMs = hint
                                } else {
                                    sawNonRetryableFailure = true
                                }
                                emitDoc = null
                                emitUpgrading = false
                            }
                            LyricsFetchResult.NotFound -> {
                                emitDoc = null
                                emitUpgrading = false
                            }
                        }
                    }
                    if (emitDoc != null && emitUpgrading) {
                        send(LyricsLoadState.Ready(emitDoc, isUpgrading = true))
                    }
                }
            }.awaitAll()
        }

        val finalBest = best
        if (finalBest != null) {
            val storedAt = now()
            val ttl = positiveTtl(finalBest.document.attribution.sourceId)
            writeMemory(key, MemoryEntry.Hit(finalBest.document, finalBest.score, storedAt, ttl), startedMemGen)
            if (diskCache != null) {
                withContext(Dispatchers.IO) {
                    diskCache.put(key, finalBest.document, ttl, storedAt, expectedGeneration = startedDiskGen)
                }
            }
            send(LyricsLoadState.Ready(finalBest.document, isUpgrading = false))
        } else {
            val reason = fallbackUnavailableReason(identity, settings, sawAuth, sawRetryable)
            if (!sawAuth && !sawNonRetryableFailure) {
                if (sawRetryable) {
                    val backoffTtl = retryBackoffMs.coerceIn(RETRYABLE_BACKOFF_MIN_MS, RETRYABLE_BACKOFF_MAX_MS)
                    writeMemory(negKey, MemoryEntry.Miss(now(), backoffTtl), startedMemGen)
                } else {
                    val negTtl = if (identity.audioSource == TrackAudioSource.YandexMusic) YANDEX_NEGATIVE_TTL_MS else EXTERNAL_NEGATIVE_TTL_MS
                    writeMemory(negKey, MemoryEntry.Miss(now(), negTtl), startedMemGen)
                }
            }
            send(LyricsLoadState.Unavailable(reason))
        }
    }

    suspend fun invalidateOffMain(identity: TrackIdentity) {
        invalidateMemory(identity)
        if (diskCache != null) {
            val key = cacheKey(identity)
            withContext(Dispatchers.IO) { diskCache.remove(key) }
        }
    }

    suspend fun clearCacheOffMain() {
        clearMemoryCache()
        if (diskCache != null) {
            withContext(Dispatchers.IO) { diskCache.clear() }
        }
    }

    /** Clears both in-memory and disk-cached lyrics belonging to [accountId] on sign-out or account removal. */
    suspend fun clearAccountCacheOffMain(accountId: String) {
        if (accountId.isBlank()) return
        val prefix = "$accountId|"
        synchronized(cacheLock) {
            cacheGeneration += 1
            val iter = memoryCache.keys.iterator()
            while (iter.hasNext()) {
                if (iter.next().startsWith(prefix)) iter.remove()
            }
        }
        if (diskCache != null) {
            withContext(Dispatchers.IO) { diskCache.removeByAccount(accountId) }
        }
    }

    fun invalidate(identity: TrackIdentity) {
        invalidateMemory(identity)
        diskCache?.remove(cacheKey(identity))
    }

    fun clearCache() {
        clearMemoryCache()
        diskCache?.clear()
    }

    private fun invalidateMemory(identity: TrackIdentity) {
        val key = cacheKey(identity)
        val negPrefix = "$key|neg|"
        synchronized(cacheLock) {
            memoryCache.remove(key)
            val iter = memoryCache.keys.iterator()
            while (iter.hasNext()) {
                if (iter.next().startsWith(negPrefix)) iter.remove()
            }
        }
    }

    private fun clearMemoryCache() {
        synchronized(cacheLock) {
            cacheGeneration += 1
            memoryCache.clear()
        }
    }

    private fun readMemoryHit(key: String, atMs: Long): MemoryEntry.Hit? = synchronized(cacheLock) {
        val entry = memoryCache[key] ?: return null
        if (atMs - entry.storedAtMs >= entry.ttlMs) {
            memoryCache.remove(key)
            return null
        }
        entry as? MemoryEntry.Hit
    }

    private fun hasValidNegativeCache(negKey: String, atMs: Long): Boolean = synchronized(cacheLock) {
        val entry = memoryCache[negKey] ?: return false
        if (atMs - entry.storedAtMs >= entry.ttlMs) {
            memoryCache.remove(negKey)
            return false
        }
        entry is MemoryEntry.Miss
    }

    private fun writeMemory(key: String, entry: MemoryEntry, expectedGen: Long) = synchronized(cacheLock) {
        if (cacheGeneration == expectedGen) {
            memoryCache[key] = entry
        }
    }

    private fun isSourceEnabled(id: LyricsSourceId, settings: LyricsSourceSettings): Boolean = when (id) {
        LyricsSourceId.YandexMusic -> settings.yandexEnabled
        LyricsSourceId.EmbeddedTags -> settings.embeddedTagsEnabled
        LyricsSourceId.LrcLib -> settings.lrcLibEnabled
    }

    private fun isAllowedBySettingsAndPrivacy(
        doc: LyricsDocument,
        identity: TrackIdentity,
        settings: LyricsSourceSettings,
    ): Boolean {
        if (!isSourceEnabled(doc.attribution.sourceId, settings)) return false
        if (doc.attribution.isExternalLookup) {
            return settings.externalLookupEnabled && identity.privacy.allowsExternalMetadataLookup
        }
        return true
    }

    private fun fallbackUnavailableReason(
        identity: TrackIdentity,
        settings: LyricsSourceSettings,
        sawAuth: Boolean,
        sawRetryable: Boolean,
    ): LyricsUnavailableReasonV2 = when {
        sawAuth -> LyricsUnavailableReasonV2.NotSignedIn
        sawRetryable -> LyricsUnavailableReasonV2.Failed
        identity.audioSource != TrackAudioSource.YandexMusic && !identity.privacy.allowsExternalMetadataLookup ->
            LyricsUnavailableReasonV2.RestrictedPrivacy
        identity.audioSource != TrackAudioSource.YandexMusic && !settings.externalLookupEnabled ->
            LyricsUnavailableReasonV2.ExternalLookupDisabled
        else -> LyricsUnavailableReasonV2.NoText
    }

    private fun positiveTtl(sourceId: LyricsSourceId): Long = when (sourceId) {
        LyricsSourceId.YandexMusic -> YANDEX_POSITIVE_TTL_MS
        LyricsSourceId.EmbeddedTags,
        LyricsSourceId.LrcLib -> EXTERNAL_POSITIVE_TTL_MS
    }

    private fun isBetter(candidate: RankedDoc, current: RankedDoc): Boolean {
        val cRank = syncRank(candidate.document.syncMode)
        val curRank = syncRank(current.document.syncMode)
        if (cRank != curRank) return cRank > curRank
        return candidate.score > current.score
    }

    private fun syncRank(mode: LyricsSyncMode): Int = when (mode) {
        LyricsSyncMode.WordSynced -> 3
        LyricsSyncMode.LineSynced -> 2
        LyricsSyncMode.Plain -> 1
    }

    private fun cacheKey(identity: TrackIdentity): String {
        val trackPart = if (identity.providerTrackId != null) {
            "ym:${identity.providerTrackId}"
        } else {
            identity.playbackKey
        }
        return "${identity.accountId}|$trackPart|v${LyricsDocument.CURRENT_FORMAT_VERSION}"
    }

    private fun negativeCacheKey(identity: TrackIdentity, settings: LyricsSourceSettings): String =
        "${cacheKey(identity)}|neg|ext=${settings.externalLookupEnabled}" +
            "|emb=${settings.embeddedTagsEnabled}|ym=${settings.yandexEnabled}|lrc=${settings.lrcLibEnabled}" +
            "|file=${!identity.localFilePath.isNullOrBlank()}|priv=${identity.privacy.allowsExternalMetadataLookup}"

    private data class RankedDoc(val document: LyricsDocument, val score: Float)

    companion object {
        private const val MEMORY_CACHE_LIMIT = 64
        private const val YANDEX_POSITIVE_TTL_MS = 10 * 60_000L
        private const val EXTERNAL_POSITIVE_TTL_MS = 7L * 24 * 60 * 60_000L
        private const val YANDEX_NEGATIVE_TTL_MS = 60_000L
        private const val EXTERNAL_NEGATIVE_TTL_MS = 15 * 60_000L
        private const val RETRYABLE_BACKOFF_MIN_MS = 15_000L
        private const val RETRYABLE_BACKOFF_DEFAULT_MS = 60_000L
        private const val RETRYABLE_BACKOFF_MAX_MS = 10 * 60_000L
    }
}

object AppLyricsRepository {
    @Volatile
    private var instance: LyricsRepository? = null

    fun get(context: Context): LyricsRepository {
        instance?.let { return it }
        return synchronized(this) {
            instance?.let { return it }
            val app = context.applicationContext
            val created = LyricsRepository(
                sources = listOf(
                    EmbeddedTagsLyricsSource(),
                    YandexLyricsSource { MusicProviders.yandex(app) },
                    LrcLibLyricsSource(isExternalLookupEnabled = { LyricsSettingsStore.current(app).externalLookupEnabled }),
                ),
                diskCache = LyricsDiskCache(File(app.cacheDir, "lyrics-v1")),
            )
            instance = created
            created
        }
    }
}
