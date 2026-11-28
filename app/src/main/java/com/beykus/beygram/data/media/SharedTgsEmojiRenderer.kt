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
import java.util.concurrent.atomic.AtomicInteger
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

internal fun adaptiveEmojiFps(activePlayingContexts: Int, lowRam: Boolean): Int = if (lowRam) {
    when {
        activePlayingContexts <= 8 -> 24
        activePlayingContexts <= 16 -> 18
        else -> 12
    }
} else {
    when {
        activePlayingContexts <= 12 -> 30
        activePlayingContexts <= 24 -> 24
        else -> 15
    }
}

internal fun maxConcurrentEmojiAnimations(lowRam: Boolean): Int = if (lowRam) 10 else 16

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
    internal val key: EmojiRenderKey,
    internal val handleId: Long,
    val frameState: State<ImageBitmap?>,
    val failedState: State<Boolean>
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class SharedTgsEmojiRenderer(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(2),
    internal val frameCache: EmojiFrameCache = EmojiFrameCache(),
    private val rasterizer: EmojiFrameRasterizer = LottieEmojiFrameRasterizer(),
    private val lowRam: Boolean = false,
    private val clockMs: () -> Long = SystemClock::elapsedRealtime
) {
    private data class TargetEntry(
        val key: EmojiRenderKey,
        var playing: Boolean,
        var priority: Int
    )

    private class SharedContext(
        val key: EmojiRenderKey,
        val attachSequence: Long,
        val frameState: MutableState<ImageBitmap?> = mutableStateOf(null),
        val failedState: MutableState<Boolean> = mutableStateOf(false),
        val targets: MutableMap<Long, TargetEntry> = HashMap(),
        var prepared: PreparedEmojiAnimation? = null,
        var prepJob: Job? = null,
        var rendering: Boolean = false,
        var startTimeMs: Long = 0L
    ) {
        fun isPlaying(): Boolean = targets.values.any { it.playing }
        fun maxPriority(): Int = targets.values.maxOfOrNull { it.priority } ?: Int.MIN_VALUE
    }

    private val lock = Any()
    private val contexts = HashMap<EmojiRenderKey, SharedContext>()
    private val targets = HashMap<Long, TargetEntry>()
    private var nextHandleId = 1L
    private var nextSequence = 1L
    private var schedulerJob: Job? = null

    fun attach(
        accountId: String,
        path: String,
        sizePx: Int,
        playing: Boolean,
        priority: Int = 0
    ): EmojiRenderTarget {
        val normalizedSize = bucketEmojiSizePx(sizePx)
        val file = File(path)
        val key = EmojiRenderKey(
            accountId = accountId,
            path = path,
            fileLength = if (file.exists()) file.length() else 0L,
            fileMtime = if (file.exists()) file.lastModified() else 0L,
            sizePx = normalizedSize
        )
        val handleId: Long
        val ctx: SharedContext
        var needsPrepare = false
        synchronized(lock) {
            handleId = nextHandleId++
            ctx = contexts.getOrPut(key) {
                SharedContext(key = key, attachSequence = nextSequence++, startTimeMs = clockMs())
            }
            val entry = TargetEntry(key = key, playing = playing, priority = priority)
            targets[handleId] = entry
            ctx.targets[handleId] = entry
            val cachedFirst = frameCache.getFirstFrame(key)
            if (cachedFirst != null && ctx.frameState.value == null) {
                ctx.frameState.value = cachedFirst.bitmap
            }
            if (ctx.prepared == null && ctx.prepJob == null && !ctx.failedState.value) {
                needsPrepare = true
                ctx.prepJob = scope.launch { prepareContext(ctx) }
            }
            ensureSchedulerLocked()
        }
        if (!needsPrepare && playing) {
            synchronized(lock) { ensureSchedulerLocked() }
        }
        return EmojiRenderTarget(key, handleId, ctx.frameState, ctx.failedState)
    }

    fun updateTarget(target: EmojiRenderTarget, playing: Boolean, priority: Int = 0) {
        synchronized(lock) {
            val entry = targets[target.handleId] ?: return
            if (entry.playing == playing && entry.priority == priority) return
            entry.playing = playing
            entry.priority = priority
            if (!playing) {
                val ctx = contexts[entry.key]
                if (ctx != null && !ctx.isPlaying()) {
                    frameCache.getFirstFrame(ctx.key)?.let { ctx.frameState.value = it.bitmap }
                }
            }
            ensureSchedulerLocked()
        }
    }

    fun detach(target: EmojiRenderTarget) {
        synchronized(lock) {
            val entry = targets.remove(target.handleId) ?: return
            val ctx = contexts[entry.key] ?: return
            ctx.targets.remove(target.handleId)
            if (ctx.targets.isEmpty()) {
                ctx.prepJob?.cancel()
                ctx.prepJob = null
                contexts.remove(entry.key)
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
            val result = withContext(workerDispatcher) {
                rasterizer.prepare(ctx.key.path, ctx.key.sizePx)
            }
            val prepared = result.getOrNull()
            if (prepared == null) {
                synchronized(lock) {
                    if (contexts[ctx.key] === ctx) {
                        ctx.failedState.value = true
                        ctx.prepJob = null
                    }
                }
                return
            }
            val firstFrame = frameCache.getFirstFrame(ctx.key) ?: withContext(workerDispatcher) {
                prepared.renderFrame(0f)?.also { frameCache.putFirstFrame(ctx.key, it) }
            }
            synchronized(lock) {
                if (contexts[ctx.key] !== ctx) return
                ctx.prepared = prepared
                ctx.startTimeMs = clockMs()
                ctx.prepJob = null
                if (firstFrame != null) {
                    ctx.frameState.value = firstFrame.bitmap
                }
                ensureSchedulerLocked()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            synchronized(lock) {
                if (contexts[ctx.key] === ctx) {
                    ctx.failedState.value = true
                    ctx.prepJob = null
                }
            }
        }
    }

    private fun ensureSchedulerLocked() {
        val anyPlaying = contexts.values.any { it.isPlaying() }
        if (anyPlaying && schedulerJob?.isActive != true) {
            schedulerJob = scope.launch { runSchedulerLoop() }
        } else if (!anyPlaying && schedulerJob?.isActive == true) {
            schedulerJob?.cancel()
            schedulerJob = null
        }
    }

    private suspend fun runSchedulerLoop() {
        while (scope.isActive) {
            val activeCount: Int
            val candidates: List<SharedContext>
            synchronized(lock) {
                val playing = contexts.values.filter { it.isPlaying() }
                if (playing.isEmpty()) {
                    schedulerJob = null
                    return
                }
                activeCount = playing.size
                val budget = maxConcurrentEmojiAnimations(lowRam)
                candidates = playing
                    .filter { it.prepared != null && (it.prepared?.frameCount ?: 0) > 1 && !it.rendering }
                    .sortedWith(compareByDescending<SharedContext> { it.maxPriority() }.thenBy { it.attachSequence })
                    .take(budget)
            }
            val now = clockMs()
            for (ctx in candidates) {
                val prepared = ctx.prepared ?: continue
                val frameIndex = (((now - ctx.startTimeMs).coerceAtLeast(0L) % prepared.durationMs) *
                    prepared.frameCount / prepared.durationMs).toInt().coerceIn(0, prepared.frameCount - 1)
                val cached = frameCache.getFrame(ctx.key, frameIndex)
                if (cached != null) {
                    ctx.frameState.value = cached.bitmap
                    continue
                }
                val shouldRender = synchronized(lock) {
                    if (contexts[ctx.key] === ctx && !ctx.rendering && ctx.isPlaying()) {
                        ctx.rendering = true
                        true
                    } else false
                }
                if (shouldRender) {
                    scope.launch {
                        try {
                            val rendered = withContext(workerDispatcher) {
                                prepared.renderFrame(frameIndex.toFloat() / prepared.frameCount.toFloat())
                            }
                            if (rendered != null) {
                                frameCache.putFrame(ctx.key, frameIndex, rendered)
                                synchronized(lock) {
                                    if (contexts[ctx.key] === ctx && ctx.isPlaying()) {
                                        ctx.frameState.value = rendered.bitmap
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
            val fps = adaptiveEmojiFps(activeCount, lowRam)
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
                    val am = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    val isLowRam = am?.isLowRamDevice == true
                    SharedTgsEmojiRenderer(lowRam = isLowRam).also { instance = it }
                }
            }
        }

        fun clearAccount(accountId: String) {
            instance?.clearAccount(accountId)
        }
    }
}
