package com.beykus.beygram.data.media

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.util.LruCache
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.airbnb.lottie.LottieDrawable
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val EMOJI_SIZE_BUCKETS = intArrayOf(48, 64, 96, 128, 192, 256)

internal fun bucketEmojiSizePx(rawPx: Int): Int {
    val clamped = rawPx.coerceIn(EMOJI_SIZE_BUCKETS.first(), EMOJI_SIZE_BUCKETS.last())
    return EMOJI_SIZE_BUCKETS.firstOrNull { it >= clamped } ?: EMOJI_SIZE_BUCKETS.last()
}

internal enum class MediaPerformanceTier { Low, Mid, High }

internal fun detectMediaPerformanceTier(
    isLowRam: Boolean,
    memoryClassMb: Int,
    cpuCores: Int,
    powerSaveMode: Boolean
): MediaPerformanceTier = when {
    isLowRam || powerSaveMode || (memoryClassMb in 1..192) || (cpuCores in 1..4) -> MediaPerformanceTier.Low
    memoryClassMb in 193..384 -> MediaPerformanceTier.Mid
    else -> MediaPerformanceTier.High
}

internal fun animationCostUnits(sizePx: Int): Int = when {
    sizePx <= 64 -> 1
    sizePx <= 96 -> 2
    sizePx <= 192 -> 3
    else -> 4
}

internal fun maxAnimationBudgetUnits(tier: MediaPerformanceTier): Int = when (tier) {
    MediaPerformanceTier.Low -> 10
    MediaPerformanceTier.Mid -> 12
    MediaPerformanceTier.High -> 16
}

internal fun adaptiveEmojiFps(activeWeightUnits: Int, tier: MediaPerformanceTier): Int = when (tier) {
    MediaPerformanceTier.Low -> when {
        activeWeightUnits <= 8 -> 24
        activeWeightUnits <= 16 -> 18
        else -> 12
    }
    MediaPerformanceTier.Mid -> when {
        activeWeightUnits <= 8 -> 24
        activeWeightUnits <= 16 -> 18
        else -> 12
    }
    MediaPerformanceTier.High -> when {
        activeWeightUnits <= 12 -> 30
        activeWeightUnits <= 24 -> 24
        else -> 15
    }
}

internal fun adaptiveEmojiFps(activePlayingContexts: Int, lowRam: Boolean): Int =
    adaptiveEmojiFps(activePlayingContexts, if (lowRam) MediaPerformanceTier.Low else MediaPerformanceTier.High)

internal fun maxConcurrentEmojiAnimations(lowRam: Boolean): Int =
    maxAnimationBudgetUnits(if (lowRam) MediaPerformanceTier.Low else MediaPerformanceTier.High)

internal data class EmojiContextKey(
    val accountId: String,
    val path: String,
    val sizePx: Int
)

internal data class EmojiRenderKey(
    val accountId: String,
    val path: String,
    val fileLength: Long,
    val fileMtime: Long,
    val sizePx: Int
)

internal data class EmojiFrameKey(
    val renderKey: EmojiRenderKey,
    val frameIndex: Int
)

internal class CachedFrame(
    val bitmap: ImageBitmap,
    val byteCount: Int
)

internal class EmojiFrameCache(
    firstFrameBudgetBytes: Int = DEFAULT_FIRST_FRAME_BUDGET_BYTES,
    frameBudgetBytes: Int = DEFAULT_FRAME_BUDGET_BYTES
) {
    private val firstFrames = object : LruCache<EmojiRenderKey, CachedFrame>(firstFrameBudgetBytes.coerceAtLeast(1)) {
        override fun sizeOf(key: EmojiRenderKey, value: CachedFrame): Int = value.byteCount.coerceAtLeast(1)
    }

    private val animationFrames = object : LruCache<EmojiFrameKey, CachedFrame>(frameBudgetBytes.coerceAtLeast(1)) {
        override fun sizeOf(key: EmojiFrameKey, value: CachedFrame): Int = value.byteCount.coerceAtLeast(1)
    }

    fun getFirstFrame(key: EmojiRenderKey): CachedFrame? = firstFrames.get(key)

    fun putFirstFrame(key: EmojiRenderKey, frame: CachedFrame) {
        firstFrames.put(key, frame)
    }

    fun getFrame(key: EmojiRenderKey, frameIndex: Int): CachedFrame? =
        if (frameIndex == 0) getFirstFrame(key) else animationFrames.get(EmojiFrameKey(key, frameIndex))

    fun putFrame(key: EmojiRenderKey, frameIndex: Int, frame: CachedFrame) {
        if (frameIndex == 0) {
            putFirstFrame(key, frame)
        } else {
            animationFrames.put(EmojiFrameKey(key, frameIndex), frame)
        }
    }

    fun firstFrameCount(): Int = firstFrames.snapshot().size

    fun animationFrameCount(): Int = animationFrames.snapshot().size

    fun clearAccount(accountId: String) {
        firstFrames.snapshot().keys.forEach { key ->
            if (key.accountId == accountId) firstFrames.remove(key)
        }
        animationFrames.snapshot().keys.forEach { key ->
            if (key.renderKey.accountId == accountId) animationFrames.remove(key)
        }
    }

    fun clearAll() {
        firstFrames.evictAll()
        animationFrames.evictAll()
    }

    companion object {
        const val DEFAULT_FIRST_FRAME_BUDGET_BYTES = 2 * 1024 * 1024
        const val DEFAULT_FRAME_BUDGET_BYTES = 6 * 1024 * 1024
    }
}

internal data class PreparedEmojiAnimation(
    val frameCount: Int,
    val durationMs: Long,
    val renderFrame: (progress: Float) -> CachedFrame?
)

internal interface EmojiFrameRasterizer {
    suspend fun prepare(path: String, sizePx: Int): Result<PreparedEmojiAnimation>
}

internal class LottieEmojiFrameRasterizer : EmojiFrameRasterizer {
    override suspend fun prepare(path: String, sizePx: Int): Result<PreparedEmojiAnimation> {
        val loaded = TgsCompositionCache.load(path)
        val composition = loaded.getOrElse { return Result.failure(it) }
        val durationMs = composition.duration.toLong().coerceAtLeast(100L)
        val frameCount = composition.durationFrames.toInt().coerceIn(1, 90)
        val drawable = LottieDrawable().apply {
            this.composition = composition
            setBounds(0, 0, sizePx, sizePx)
        }
        return Result.success(
            PreparedEmojiAnimation(
                frameCount = frameCount,
                durationMs = durationMs,
                renderFrame = { progress ->
                    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    drawable.progress = progress.coerceIn(0f, 1f)
                    drawable.draw(canvas)
                    CachedFrame(bitmap.asImageBitmap(), bitmap.width * bitmap.height * 4)
                }
            )
        )
    }
}

internal class EmojiRenderTarget internal constructor(
    internal val contextKey: EmojiContextKey,
    internal val handleId: Long,
    val frameState: State<ImageBitmap?>,
    val failedState: State<Boolean>
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class SharedTgsEmojiRenderer(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val schedulerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(2),
    internal val frameCache: EmojiFrameCache = EmojiFrameCache(),
    private val rasterizer: EmojiFrameRasterizer = LottieEmojiFrameRasterizer(),
    private val lowRam: Boolean = false,
    performanceTier: MediaPerformanceTier = if (lowRam) MediaPerformanceTier.Low else MediaPerformanceTier.High,
    private val clockMs: () -> Long = SystemClock::elapsedRealtime
) {
    /**
     * The tier the scheduler uses now. It is state rather than a constructor constant because the process can enter or
     * leave battery saver while the renderer lives, and both the weighted budget and the frame rate must follow
     * without a restart.
     */
    @Volatile
    private var tier: MediaPerformanceTier = performanceTier

    /** Reads the tier the scheduler currently applies. */
    internal fun currentPerformanceTier(): MediaPerformanceTier = tier

    /** Applies a new tier; the running scheduler loop picks it up on its next tick. */
    internal fun updatePerformanceTier(next: MediaPerformanceTier) {
        tier = next
    }

    private data class TargetEntry(
        val contextKey: EmojiContextKey,
        var playing: Boolean,
        var priority: Int
    )

    private class SharedContext(
        val contextKey: EmojiContextKey,
        val attachSequence: Long,
        val frameState: MutableState<ImageBitmap?> = mutableStateOf(null),
        val failedState: MutableState<Boolean> = mutableStateOf(false),
        val targets: MutableMap<Long, TargetEntry> = HashMap(),
        var renderKey: EmojiRenderKey? = null,
        var prepared: PreparedEmojiAnimation? = null,
        var prepJob: Job? = null,
        var rendering: Boolean = false,
        var startTimeMs: Long = 0L
    ) {
        fun isPlaying(): Boolean = targets.values.any { it.playing }
        fun maxPriority(): Int = targets.values.maxOfOrNull { it.priority } ?: Int.MIN_VALUE
    }

    private val lock = Any()
    private val contexts = HashMap<EmojiContextKey, SharedContext>()
    private val targets = HashMap<Long, TargetEntry>()
    private val lastVersionByPath = ConcurrentHashMap<String, Pair<Long, Long>>()
    private var nextHandleId = 1L
    private var nextSequence = 1L
    private var schedulerJob: Job? = null

    /**
     * Memory-only first-frame lookup; safe to call during Compose composition without touching disk.
     */
    fun peekFirstFrame(accountId: String, path: String, sizePx: Int): ImageBitmap? {
        val normalizedSize = bucketEmojiSizePx(sizePx)
        val version = lastVersionByPath[path] ?: return null
        val renderKey = EmojiRenderKey(accountId, path, version.first, version.second, normalizedSize)
        return frameCache.getFirstFrame(renderKey)?.bitmap
    }

    /**
     * Attaches a consumer target to a shared context. Never performs disk I/O synchronously on the calling thread.
     */
    fun attach(
        accountId: String,
        path: String,
        sizePx: Int,
        playing: Boolean,
        priority: Int = 0
    ): EmojiRenderTarget {
        val normalizedSize = bucketEmojiSizePx(sizePx)
        val contextKey = EmojiContextKey(accountId = accountId, path = path, sizePx = normalizedSize)
        val handleId: Long
        val ctx: SharedContext
        synchronized(lock) {
            handleId = nextHandleId++
            ctx = contexts.getOrPut(contextKey) {
                SharedContext(contextKey = contextKey, attachSequence = nextSequence++, startTimeMs = clockMs())
            }
            val entry = TargetEntry(contextKey = contextKey, playing = playing, priority = priority)
            targets[handleId] = entry
            ctx.targets[handleId] = entry
            if (ctx.frameState.value == null) {
                peekFirstFrame(accountId, path, normalizedSize)?.let { cached ->
                    ctx.frameState.value = cached
                }
            }
            if (ctx.prepared == null && ctx.prepJob == null && !ctx.failedState.value) {
                ctx.prepJob = scope.launch(workerDispatcher) { prepareContext(ctx) }
            }
            ensureSchedulerLocked()
        }
        return EmojiRenderTarget(contextKey, handleId, ctx.frameState, ctx.failedState)
    }

    fun updateTarget(target: EmojiRenderTarget, playing: Boolean, priority: Int = 0) {
        synchronized(lock) {
            val entry = targets[target.handleId] ?: return
            if (entry.playing == playing && entry.priority == priority) return
            entry.playing = playing
            entry.priority = priority
            if (!playing) {
                val ctx = contexts[entry.contextKey]
                val rKey = ctx?.renderKey
                if (ctx != null && rKey != null && !ctx.isPlaying()) {
                    frameCache.getFirstFrame(rKey)?.let { ctx.frameState.value = it.bitmap }
                }
            }
            ensureSchedulerLocked()
        }
    }

    fun detach(target: EmojiRenderTarget) {
        synchronized(lock) {
            val entry = targets.remove(target.handleId) ?: return
            val ctx = contexts[entry.contextKey] ?: return
            ctx.targets.remove(target.handleId)
            if (ctx.targets.isEmpty()) {
                ctx.prepJob?.cancel()
                ctx.prepJob = null
                contexts.remove(entry.contextKey)
            }
            ensureSchedulerLocked()
        }
    }

    fun clearAccount(accountId: String) {
        frameCache.clearAccount(accountId)
    }

    internal fun activeContextCount(): Int = synchronized(lock) { contexts.size }

    internal fun totalTargetCount(): Int = synchronized(lock) { targets.size }

    internal fun isSchedulerRunning(): Boolean = synchronized(lock) { schedulerJob?.isActive == true }

    private suspend fun prepareContext(ctx: SharedContext) {
        try {
            val file = File(ctx.contextKey.path)
            val length = if (file.exists()) file.length() else 0L
            val mtime = if (file.exists()) file.lastModified() else 0L
            lastVersionByPath[ctx.contextKey.path] = length to mtime
            val renderKey = EmojiRenderKey(
                accountId = ctx.contextKey.accountId,
                path = ctx.contextKey.path,
                fileLength = length,
                fileMtime = mtime,
                sizePx = ctx.contextKey.sizePx
            )
            val result = rasterizer.prepare(ctx.contextKey.path, ctx.contextKey.sizePx)
            val prepared = result.getOrNull()
            if (prepared == null) {
                withContext(mainDispatcher) {
                    synchronized(lock) {
                        if (contexts[ctx.contextKey] === ctx) {
                            ctx.failedState.value = true
                            ctx.prepJob = null
                        }
                    }
                }
                return
            }
            val firstFrame = frameCache.getFirstFrame(renderKey)
                ?: prepared.renderFrame(0f)?.also { frameCache.putFirstFrame(renderKey, it) }
            withContext(mainDispatcher) {
                synchronized(lock) {
                    if (contexts[ctx.contextKey] !== ctx) return@withContext
                    ctx.renderKey = renderKey
                    ctx.prepared = prepared
                    ctx.startTimeMs = clockMs()
                    ctx.prepJob = null
                    if (firstFrame != null) {
                        ctx.frameState.value = firstFrame.bitmap
                    }
                    ensureSchedulerLocked()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            withContext(mainDispatcher) {
                synchronized(lock) {
                    if (contexts[ctx.contextKey] === ctx) {
                        ctx.failedState.value = true
                        ctx.prepJob = null
                    }
                }
            }
        }
    }

    private fun ensureSchedulerLocked() {
        val anyPlaying = contexts.values.any { it.isPlaying() }
        if (anyPlaying && schedulerJob?.isActive != true) {
            schedulerJob = scope.launch(schedulerDispatcher) { runSchedulerLoop() }
        } else if (!anyPlaying && schedulerJob?.isActive == true) {
            schedulerJob?.cancel()
            schedulerJob = null
        }
    }

    private suspend fun runSchedulerLoop() {
        while (scope.isActive) {
            val activeWeightUnits: Int
            val candidates: List<SharedContext>
            synchronized(lock) {
                val playing = contexts.values.filter { it.isPlaying() }
                if (playing.isEmpty()) {
                    schedulerJob = null
                    return
                }
                activeWeightUnits = playing.sumOf { animationCostUnits(it.contextKey.sizePx) }
                val budgetUnits = maxAnimationBudgetUnits(tier)
                val sorted = playing
                    .filter { it.prepared != null && it.renderKey != null && (it.prepared?.frameCount ?: 0) > 1 && !it.rendering }
                    .sortedWith(compareByDescending<SharedContext> { it.maxPriority() }.thenBy { it.attachSequence })
                val selected = ArrayList<SharedContext>(sorted.size)
                var usedUnits = 0
                for (ctx in sorted) {
                    val cost = animationCostUnits(ctx.contextKey.sizePx)
                    if (selected.isEmpty() || usedUnits + cost <= budgetUnits) {
                        selected += ctx
                        usedUnits += cost
                    }
                }
                candidates = selected
            }
            val now = clockMs()
            val pendingCommits = ArrayList<Pair<SharedContext, ImageBitmap>>()
            for (ctx in candidates) {
                val prepared = ctx.prepared ?: continue
                val renderKey = ctx.renderKey ?: continue
                val frameIndex = (((now - ctx.startTimeMs).coerceAtLeast(0L) % prepared.durationMs) *
                    prepared.frameCount / prepared.durationMs).toInt().coerceIn(0, prepared.frameCount - 1)
                val cached = frameCache.getFrame(renderKey, frameIndex)
                if (cached != null) {
                    pendingCommits += ctx to cached.bitmap
                    continue
                }
                val shouldRender = synchronized(lock) {
                    if (contexts[ctx.contextKey] === ctx && !ctx.rendering && ctx.isPlaying()) {
                        ctx.rendering = true
                        true
                    } else false
                }
                if (shouldRender) {
                    scope.launch(workerDispatcher) {
                        try {
                            val rendered = prepared.renderFrame(frameIndex.toFloat() / prepared.frameCount.toFloat())
                            if (rendered != null) {
                                frameCache.putFrame(renderKey, frameIndex, rendered)
                                withContext(mainDispatcher) {
                                    synchronized(lock) {
                                        if (contexts[ctx.contextKey] === ctx && ctx.isPlaying()) {
                                            ctx.frameState.value = rendered.bitmap
                                        }
                                    }
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                        } finally {
                            synchronized(lock) { ctx.rendering = false }
                        }
                    }
                }
            }
            if (pendingCommits.isNotEmpty()) {
                withContext(mainDispatcher) {
                    synchronized(lock) {
                        for ((ctx, bitmap) in pendingCommits) {
                            if (contexts[ctx.contextKey] === ctx && ctx.isPlaying()) {
                                ctx.frameState.value = bitmap
                            }
                        }
                    }
                }
            }
            val fps = adaptiveEmojiFps(activeWeightUnits, tier)
            delay((1000L / fps).coerceAtLeast(16L))
        }
    }

    companion object {
        @Volatile
        private var instance: SharedTgsEmojiRenderer? = null

        fun default(context: Context): SharedTgsEmojiRenderer {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: run {
                    val appContext = context.applicationContext ?: context
                    val lowRam = (appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
                        ?.isLowRamDevice == true
                    // Battery saver can be turned on or off while the app runs; the tier follows it instead of being
                    // frozen at process start (the previous behaviour kept a high budget on saver and a low one after).
                    val tiers = MediaPerformanceTierSource.of(appContext) { tier ->
                        instance?.updatePerformanceTier(tier)
                    }
                    SharedTgsEmojiRenderer(
                        lowRam = lowRam,
                        performanceTier = tiers.tier
                    ).also { instance = it }
                }
            }
        }

        fun clearAccount(accountId: String) {
            instance?.clearAccount(accountId)
        }
    }
}
