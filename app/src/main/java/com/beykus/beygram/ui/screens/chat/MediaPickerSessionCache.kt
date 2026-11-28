package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.AnimationItem
import com.beykus.beygram.domain.model.AnimationPage
import com.beykus.beygram.domain.model.CustomEmojiSetBrief
import com.beykus.beygram.domain.model.StickerItem
import com.beykus.beygram.domain.model.StickerSetContent
import com.beykus.beygram.domain.model.StickerSetSummary
import com.beykus.beygram.domain.repository.ConversationRepository
import java.lang.ref.WeakReference

/**
 * Account-scoped in-memory cache for the unified media picker (Emoji / Stickers / GIFs).
 *
 * Separates loaded TDLib metadata from the Compose UI lifecycle so closing and reopening the picker
 * restores the last known shelves, packs, and GIFs immediately without a loading flash or redundant
 * TDLib bursts when [com.beykus.beygram.domain.model.StickerShelfVersions] has not changed.
 *
 * Entries are bound by identity (`===`) via [WeakReference] to the [ConversationRepository] that
 * produced them so repository swaps or test fakes never inherit stale data, and [clearAccount] wipes
 * all entries on logout.
 */
internal object MediaPickerSessionCache {

    private const val MAX_STICKER_PACKS = 24
    private const val MAX_CUSTOM_EMOJI_SETS = 64

    internal data class CachedShelf<T>(val value: T, val version: Long)

    private class VersionedEntry<T>(
        val value: T,
        val version: Long,
        val repoRef: WeakReference<ConversationRepository>
    ) {
        fun matches(repo: ConversationRepository): Boolean = repoRef.get() === repo
    }

    private class RepoEntry<T>(
        val value: T,
        val repoRef: WeakReference<ConversationRepository>
    ) {
        fun matches(repo: ConversationRepository): Boolean = repoRef.get() === repo
    }

    private class AccountBucket {
        var recentStickers: VersionedEntry<List<StickerItem>>? = null
        var favoriteStickers: VersionedEntry<List<StickerItem>>? = null
        var installedStickerSets: VersionedEntry<List<StickerSetSummary>>? = null
        val stickerPacks = lruMap<Long, RepoEntry<StickerSetContent>>(MAX_STICKER_PACKS)
        var gifRecent: RepoEntry<AnimationPage>? = null
        var gifSaved: VersionedEntry<List<AnimationItem>>? = null
        var customEmojiInstalled: VersionedEntry<List<CustomEmojiSetBrief>>? = null
        var customEmojiRecommended: RepoEntry<List<CustomEmojiSetBrief>>? = null
        val customEmojiSetIds = lruMap<Long, RepoEntry<List<Long>>>(MAX_CUSTOM_EMOJI_SETS)
        var isPremium: RepoEntry<Boolean>? = null
    }

    private val lock = Any()
    private val buckets = HashMap<String, AccountBucket>()

    private fun <K, V> lruMap(maxEntries: Int): LinkedHashMap<K, V> =
        object : LinkedHashMap<K, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxEntries
        }

    private fun bucketLocked(accountId: String): AccountBucket =
        buckets.getOrPut(accountId) { AccountBucket() }

    private fun <T> VersionedEntry<T>?.ifMatches(repo: ConversationRepository): CachedShelf<T>? =
        if (this != null && matches(repo)) CachedShelf(value, version) else null

    private fun <T> RepoEntry<T>?.ifMatches(repo: ConversationRepository): T? =
        if (this != null && matches(repo)) value else null

    fun getRecentStickers(accountId: String, repo: ConversationRepository): CachedShelf<List<StickerItem>>? =
        synchronized(lock) { buckets[accountId]?.recentStickers.ifMatches(repo) }

    fun putRecentStickers(
        accountId: String,
        repo: ConversationRepository,
        value: List<StickerItem>,
        version: Long
    ) {
        synchronized(lock) {
            bucketLocked(accountId).recentStickers = VersionedEntry(value, version, WeakReference(repo))
        }
    }

    fun getFavoriteStickers(accountId: String, repo: ConversationRepository): CachedShelf<List<StickerItem>>? =
        synchronized(lock) { buckets[accountId]?.favoriteStickers.ifMatches(repo) }

    fun putFavoriteStickers(
        accountId: String,
        repo: ConversationRepository,
        value: List<StickerItem>,
        version: Long
    ) {
        synchronized(lock) {
            bucketLocked(accountId).favoriteStickers = VersionedEntry(value, version, WeakReference(repo))
        }
    }

    fun getInstalledStickerSets(
        accountId: String,
        repo: ConversationRepository
    ): CachedShelf<List<StickerSetSummary>>? =
        synchronized(lock) { buckets[accountId]?.installedStickerSets.ifMatches(repo) }

    fun putInstalledStickerSets(
        accountId: String,
        repo: ConversationRepository,
        value: List<StickerSetSummary>,
        version: Long
    ) {
        synchronized(lock) {
            bucketLocked(accountId).installedStickerSets = VersionedEntry(value, version, WeakReference(repo))
        }
    }

    fun getStickerPack(accountId: String, repo: ConversationRepository, setId: Long): StickerSetContent? =
        synchronized(lock) {
            val bucket = buckets[accountId] ?: return null
            val entry = bucket.stickerPacks[setId] ?: return null
            if (entry.matches(repo)) entry.value else {
                bucket.stickerPacks.remove(setId)
                null
            }
        }

    fun putStickerPack(accountId: String, repo: ConversationRepository, pack: StickerSetContent) {
        synchronized(lock) {
            bucketLocked(accountId).stickerPacks[pack.id] = RepoEntry(pack, WeakReference(repo))
        }
    }

    fun snapshotStickerPacks(accountId: String, repo: ConversationRepository): Map<Long, StickerSetContent> =
        synchronized(lock) {
            val bucket = buckets[accountId] ?: return emptyMap()
            val out = LinkedHashMap<Long, StickerSetContent>()
            val it = bucket.stickerPacks.entries.iterator()
            while (it.hasNext()) {
                val (id, entry) = it.next()
                if (entry.matches(repo)) out[id] = entry.value else it.remove()
            }
            out
        }

    fun getGifRecent(accountId: String, repo: ConversationRepository): AnimationPage? =
        synchronized(lock) { buckets[accountId]?.gifRecent.ifMatches(repo) }

    fun putGifRecent(accountId: String, repo: ConversationRepository, value: AnimationPage) {
        synchronized(lock) {
            bucketLocked(accountId).gifRecent = RepoEntry(value, WeakReference(repo))
        }
    }

    fun getGifSaved(accountId: String, repo: ConversationRepository): CachedShelf<List<AnimationItem>>? =
        synchronized(lock) { buckets[accountId]?.gifSaved.ifMatches(repo) }

    fun putGifSaved(
        accountId: String,
        repo: ConversationRepository,
        value: List<AnimationItem>,
        version: Long
    ) {
        synchronized(lock) {
            bucketLocked(accountId).gifSaved = VersionedEntry(value, version, WeakReference(repo))
        }
    }

    fun getCustomEmojiInstalled(
        accountId: String,
        repo: ConversationRepository
    ): CachedShelf<List<CustomEmojiSetBrief>>? =
        synchronized(lock) { buckets[accountId]?.customEmojiInstalled.ifMatches(repo) }

    fun putCustomEmojiInstalled(
        accountId: String,
        repo: ConversationRepository,
        value: List<CustomEmojiSetBrief>,
        version: Long
    ) {
        synchronized(lock) {
            bucketLocked(accountId).customEmojiInstalled = VersionedEntry(value, version, WeakReference(repo))
        }
    }

    fun getCustomEmojiRecommended(
        accountId: String,
        repo: ConversationRepository
    ): List<CustomEmojiSetBrief>? =
        synchronized(lock) { buckets[accountId]?.customEmojiRecommended.ifMatches(repo) }

    fun putCustomEmojiRecommended(
        accountId: String,
        repo: ConversationRepository,
        value: List<CustomEmojiSetBrief>
    ) {
        synchronized(lock) {
            bucketLocked(accountId).customEmojiRecommended = RepoEntry(value, WeakReference(repo))
        }
    }

    fun snapshotCustomEmojiSetIds(accountId: String, repo: ConversationRepository): Map<Long, List<Long>> =
        synchronized(lock) {
            val bucket = buckets[accountId] ?: return emptyMap()
            val out = LinkedHashMap<Long, List<Long>>()
            val it = bucket.customEmojiSetIds.entries.iterator()
            while (it.hasNext()) {
                val (id, entry) = it.next()
                if (entry.matches(repo)) out[id] = entry.value else it.remove()
            }
            out
        }

    fun putCustomEmojiSetIds(
        accountId: String,
        repo: ConversationRepository,
        setId: Long,
        ids: List<Long>
    ) {
        synchronized(lock) {
            bucketLocked(accountId).customEmojiSetIds[setId] = RepoEntry(ids, WeakReference(repo))
        }
    }

    fun getIsPremium(accountId: String, repo: ConversationRepository): Boolean? =
        synchronized(lock) { buckets[accountId]?.isPremium.ifMatches(repo) }

    fun putIsPremium(accountId: String, repo: ConversationRepository, value: Boolean) {
        synchronized(lock) {
            bucketLocked(accountId).isPremium = RepoEntry(value, WeakReference(repo))
        }
    }

    fun clearAccount(accountId: String) {
        synchronized(lock) {
            buckets.remove(accountId)
        }
    }

    fun clearAll() {
        synchronized(lock) {
            buckets.clear()
        }
    }
}
