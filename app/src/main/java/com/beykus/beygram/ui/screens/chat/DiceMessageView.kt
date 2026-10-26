package com.beykus.beygram.ui.screens.chat

import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.beykus.beygram.R
import com.beykus.beygram.data.media.readTgsJson
import com.beykus.beygram.domain.model.AutoDownloadRequest
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.DiceStickerAsset
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageDiceInfo
import com.beykus.beygram.domain.model.MessageDiceState
import com.beykus.beygram.domain.model.MessageIdReplacement
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.playback.LocalPlaybackAccountId
import com.beykus.beygram.ui.components.beyGramPalette
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private val DICE_CARD_SIZE = 136.dp
private const val DICE_ANIMATION_TIMEOUT_MILLIS = 15_000L
private const val DICE_FALLBACK_REVEAL_MILLIS = 8_000L
// The throw of a WebM dice: the static preview spins in from a smaller size, overshoots slightly and settles.
private const val DICE_THROW_MILLIS = 600
private const val DICE_THROW_START_SCALE = 0.35f
private const val DICE_THROW_SPIN_DEGREES = -180f

/** Keeps the throw identity across the temporary-to-server message id replacement in one remember block. */
@Composable
internal fun rememberDiceAnimationId(messageId: Long, replacements: List<MessageIdReplacement>): Long =
    remember(messageId, replacements) { resolveOriginalMessageId(messageId, replacements) }

/**
 * A Telegram dice/game message. The server result chooses the final TDLib sticker; there is no local random value.
 * The throw animation plays once for a live send/receive, and the store remembers it so reopening the chat only
 * shows the final frame. Reduced motion, battery saver, a missing asset or an unsupported format fall back to the
 * emoji and the correct server result instead of an empty bubble.
 */
@Composable
internal fun DiceMessageCard(
    dice: MessageDiceInfo,
    message: ChatMessage,
    animate: Boolean,
    diceAnimationId: Long,
    observeFile: ((Int) -> StateFlow<MediaFileState>)?,
    downloadFile: ((Int) -> Unit)?,
    autoDownloadFile: ((AutoDownloadRequest) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Finished throws must survive an app restart. Attaching is idempotent, so every visible throw may ask for it.
    remember(context) { diceAnimationPlayback.attach(SharedPreferencesDicePlaybackPersistence(context)) }
    val accountId = LocalPlaybackAccountId.current
    val motion = systemMotionEnabled() && !rememberPowerSaveMode(context)
    val effectiveId = diceAnimationId.takeIf { it != 0L } ?: message.id
    val key = remember(accountId, message.chatId, effectiveId) {
        DiceAnimationKey(accountId, message.chatId, effectiveId)
    }
    // The effects below outlive the composition that started them, so they read the id the row has now.
    val currentMessageId by rememberUpdatedState(message.id)
    val displayState = dice.displayState
    val nowSeconds = System.currentTimeMillis() / 1000L
    val recentOutgoing = message.isOutgoing && message.sendState != MessageSendState.Failed &&
        isRecentDiceMessage(message.dateSeconds, nowSeconds)
    val eligible = message.scheduledAtSeconds == null && (animate || recentOutgoing ||
        (message.isOutgoing && message.sendState == MessageSendState.Sending))
    val autoDownload = autoDownloadFile ?: LocalAutoDownloadFile.current

    val files = if (observeFile != null && downloadFile != null) {
        rememberDiceFiles(diceFileAssets(displayState), observeFile, downloadFile, autoDownload)
    } else {
        emptyMap()
    }
    val playable = when (val state = displayState) {
        is MessageDiceState.Regular -> when (state.sticker.mimeType) {
            TGS_MIME -> files[state.sticker.fileId]?.localPath != null
            // A WebM sticker is never played; its static preview carries the throw as a Compose transition.
            WEBM_MIME -> state.sticker.staticPreviewFileId()?.let { files[it]?.localPath } != null
            else -> false
        }
        is MessageDiceState.SlotMachine ->
            state.layers.all { it.mimeType == TGS_MIME && files[it.fileId]?.localPath != null }
        null -> false
    }

    var forcedStatic by remember(key, displayState) { mutableStateOf(false) }
    var animationDone by remember(key, displayState) { mutableStateOf(false) }
    var playback by remember(key, displayState) { mutableStateOf(false) }
    var fallbackRevealed by remember(key, displayState) { mutableStateOf(false) }
    val animationFinished = remember(key, displayState) { CompletableDeferred<Unit>() }
    val lifecycleResumed = rememberLifecycleResumed()

    // A stalled sticker download must not hide an already-known result forever: reveal it after a bounded wait.
    LaunchedEffect(key, displayState, dice.hasFinalValue, motion, eligible) {
        if (!motion || !dice.hasFinalValue || displayState?.canAnimateInline() != true) return@LaunchedEffect
        if (!diceAnimationPlayback.canBegin(key, eligible)) return@LaunchedEffect
        delay(DICE_FALLBACK_REVEAL_MILLIS)
        fallbackRevealed = true
    }

    val canPlay = motion && playable && !forcedStatic && !animationDone &&
        diceAnimationPlayback.canBegin(key, eligible)
    val useVisual = message.sendState != MessageSendState.Failed &&
        displayState != null && files.isNotEmpty() && (dice.hasFinalValue || canPlay)

    LaunchedEffect(canPlay, motion, eligible, key, displayState, lifecycleResumed) {
        if (!canPlay) {
            // Reduced motion consumes a live throw immediately: toggling the setting back must not replay it.
            if (!motion && (eligible || diceAnimationPlayback.canBegin(key, eligible = false))) {
                consumeDiceThrow(key, currentMessageId)
            }
            return@LaunchedEffect
        }
        // Backgrounding the chat pauses the animation; do not let the wall-clock timeout consume the throw.
        if (!lifecycleResumed) return@LaunchedEffect
        if (!diceAnimationPlayback.begin(key, eligible)) {
            forcedStatic = true
            return@LaunchedEffect
        }
        diceAnimationLimiter.acquire()
        playback = true
        try {
            withTimeoutOrNull(DICE_ANIMATION_TIMEOUT_MILLIS) { animationFinished.await() }
        } finally {
            playback = false
            diceAnimationLimiter.release()
        }
        animationDone = true
        if (dice.hasFinalValue) consumeDiceThrow(key, currentMessageId)
    }

    // A confirmed send can replace the temporary id after the throw already finished; the restart only knows the new id.
    LaunchedEffect(currentMessageId, key) {
        if (diceAnimationPlayback.isDone(key)) consumeDiceThrow(key, currentMessageId)
    }

    val resultText = when {
        message.sendState == MessageSendState.Failed -> stringResource(R.string.chat_dice_not_sent)
        dice.value > 0 && dice.isJackpot -> stringResource(R.string.chat_dice_jackpot)
        dice.value > 0 -> stringResource(R.string.chat_dice_result, dice.value)
        else -> stringResource(R.string.chat_dice_rolling)
    }
    // Do not reveal the server result in the fallback before a live throw has had a chance to play.
    val animationExpected = motion && message.sendState != MessageSendState.Failed &&
        !animationDone && !forcedStatic && !fallbackRevealed &&
        files.isNotEmpty() && files.values.none { it.error != null } &&
        dice.hasFinalValue && displayState?.canAnimateInline() == true &&
        diceAnimationPlayback.canBegin(key, eligible)
    val visualResultText = if (animationExpected) stringResource(R.string.chat_dice_rolling) else resultText
    val description = stringResource(R.string.chat_dice_a11y, dice.fallbackEmoji, resultText)

    Box(
        modifier = modifier.size(DICE_CARD_SIZE).clipToBounds().clearAndSetSemantics {
            contentDescription = description
            liveRegion = LiveRegionMode.Polite
        },
        contentAlignment = Alignment.Center
    ) {
        when {
            !useVisual -> DiceFallback(dice, visualResultText, Modifier.fillMaxSize())
            displayState != null -> DiceVisual(
                dice = dice,
                state = displayState,
                files = files,
                animating = canPlay,
                playing = playback && lifecycleResumed,
                motion = motion,
                fallback = { DiceFallback(dice, visualResultText, Modifier.fillMaxSize()) },
                onFinished = { animationFinished.complete(Unit) },
                onFailed = { animationFinished.complete(Unit) },
                modifier = Modifier.fillMaxSize()
            )
            else -> DiceFallback(dice, visualResultText, Modifier.fillMaxSize())
        }
    }
}

/** Formats that can show a real dice visual on this device. A WebM sticker counts only with its static preview. */
private fun MessageDiceState.canAnimateInline(): Boolean = when (this) {
    is MessageDiceState.Regular -> sticker.mimeType == TGS_MIME || sticker.mimeType == WEBP_MIME ||
        (sticker.mimeType == WEBM_MIME && sticker.staticPreviewFileId() != null)
    is MessageDiceState.SlotMachine -> layers.all { it.mimeType == TGS_MIME }
}

/**
 * The static thumbnail of a WebM dice sticker: a WebP or JPEG with the final face and its alpha channel. Without one,
 * a WebM dice keeps the emoji and the server result.
 */
private fun DiceStickerAsset.staticPreviewFileId(): Int? = thumbnailFileId

/** The stickers whose files must be downloaded before [state] can be drawn. A WebM video is never played, so only its preview is fetched. */
private fun diceFileAssets(state: MessageDiceState?): List<DiceStickerAsset> = when (state) {
    null -> emptyList()
    is MessageDiceState.Regular -> if (state.sticker.mimeType == WEBM_MIME) {
        // ImageDecoder recognises WebP and JPEG by their content, so the nominal type only has to name an image.
        listOfNotNull(state.sticker.staticPreviewFileId()?.let { DiceStickerAsset(fileId = it, mimeType = WEBP_MIME) })
    } else {
        listOf(state.sticker)
    }
    is MessageDiceState.SlotMachine -> state.layers
}

/**
 * Marks a throw as consumed under its original id and under the id its row has now. A confirmed send replaces the
 * temporary id, and after a restart only the server id is known, so both are remembered.
 */
private fun consumeDiceThrow(key: DiceAnimationKey, currentMessageId: Long) {
    diceAnimationPlayback.finish(key)
    if (currentMessageId != key.messageId) diceAnimationPlayback.finish(key.copy(messageId = currentMessageId))
}

/**
 * The settled face of a WebM dice with a short throw: it spins in, overshoots and lands. [animating] is the playback
 * claim, and [playing] pauses the throw while the chat is in the background, so a resumed throw continues from the same
 * frame. Without a claim the settled face is shown at once, which is what a reopened chat shows.
 */
@Composable
private fun DiceThrowPreview(
    path: String,
    animating: Boolean,
    playing: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier
) {
    val progress = remember(path) { Animatable(if (animating) 0f else 1f) }
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(path, animating, playing) {
        if (!animating) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        if (!playing) return@LaunchedEffect
        if (progress.value < 1f) {
            val remainingMillis = (DICE_THROW_MILLIS * (1f - progress.value)).roundToInt()
            progress.animateTo(1f, tween(durationMillis = remainingMillis, easing = LinearEasing))
        }
        finished()
    }
    LocalMediaImage(
        path = path,
        modifier = modifier.graphicsLayer {
            val eased = easeOutBack(progress.value)
            val scale = DICE_THROW_START_SCALE + (1f - DICE_THROW_START_SCALE) * eased
            scaleX = scale
            scaleY = scale
            rotationZ = (1f - eased) * DICE_THROW_SPIN_DEGREES
        },
        fit = true
    )
}

/** Overshooting ease-out: the value rises past 1 and returns to it, which gives the landing its small bounce. */
private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val u = t - 1f
    return 1f + c3 * u * u * u + c1 * u * u
}

/**
 * Observes every file of the selected dice state and asks the automatic sticker policy to download it. Sticker
 * requests are always allowed by the auto-download policy, and the repository ignores a file that is already local.
 */
@Composable
private fun rememberDiceFiles(
    assets: List<DiceStickerAsset>,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    autoDownloadFile: ((AutoDownloadRequest) -> Unit)?
): Map<Int, MediaFileState> {
    val observed = ArrayList<Pair<Int, MediaFileState>>(assets.size)
    for (asset in assets) {
        val file by remember(asset.fileId) { observeFile(asset.fileId) }.collectAsStateWithLifecycle()
        LaunchedEffect(asset.fileId, file.localPath, file.isDownloading, file.error) {
            if (file.localPath == null && !file.isDownloading && file.error == null) {
                val request = AutoDownloadRequest(
                    fileId = asset.fileId,
                    kind = MediaKind.Sticker,
                    sizeBytes = file.size
                )
                if (autoDownloadFile != null) autoDownloadFile(request) else downloadFile(asset.fileId)
            }
        }
        observed += asset.fileId to file
    }
    return observed.toMap()
}

@Composable
private fun DiceVisual(
    dice: MessageDiceInfo,
    state: MessageDiceState,
    files: Map<Int, MediaFileState>,
    animating: Boolean,
    playing: Boolean,
    motion: Boolean,
    fallback: @Composable () -> Unit,
    onFinished: () -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier
) {
    when (state) {
        is MessageDiceState.Regular -> RegularDiceVisual(
            asset = state.sticker,
            file = files[state.sticker.fileId],
            preview = state.sticker.staticPreviewFileId()?.let { files[it] },
            animating = animating,
            playing = playing,
            motion = motion,
            fallback = fallback,
            onFinished = onFinished,
            onFailed = onFailed,
            modifier = modifier
        )
        is MessageDiceState.SlotMachine -> SlotMachineDiceVisual(
            dice = dice,
            state = state,
            files = files,
            animating = animating,
            playing = playing,
            fallback = fallback,
            onFinished = onFinished,
            onFailed = onFailed,
            modifier = modifier
        )
    }
}

@Composable
private fun RegularDiceVisual(
    asset: DiceStickerAsset,
    file: MediaFileState?,
    preview: MediaFileState?,
    animating: Boolean,
    playing: Boolean,
    motion: Boolean,
    fallback: @Composable () -> Unit,
    onFinished: () -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier
) {
    if (asset.mimeType == WEBM_MIME) {
        // The VP9 video loses its alpha channel on Android, so it is never decoded. The static preview keeps the alpha
        // and the final face. Still artwork is not motion, so it stays visible under reduced motion, which only skips
        // the transition.
        val previewPath = preview?.localPath
        if (previewPath == null) {
            fallback()
        } else {
            DiceThrowPreview(
                path = previewPath,
                animating = animating,
                playing = playing,
                onFinished = onFinished,
                modifier = modifier
            )
        }
        return
    }
    val path = file?.localPath
    if (path == null) {
        fallback()
        return
    }
    when (asset.mimeType) {
        TGS_MIME -> {
            val decoded = rememberTgsJson(path)
            val json = decoded?.getOrNull()
            if (json == null) {
                // A broken TGS archive is a real failure: release the playback permit instead of waiting for the timeout.
                if (decoded?.isFailure == true) {
                    LaunchedEffect(decoded) { onFailed() }
                }
                fallback()
                return
            }
            var ready by remember(path) { mutableStateOf(false) }
            Box(modifier) {
                Box(Modifier.fillMaxSize().alpha(if (ready) 1f else 0f)) {
                    DiceTgsLayer(
                        json = json,
                        mode = if (animating) DiceFrameMode.Play else DiceFrameMode.End,
                        isPlaying = playing,
                        modifier = Modifier.fillMaxSize(),
                        onReady = { ready = true },
                        onFailed = onFailed,
                        onFinished = onFinished
                    )
                }
                if (!ready) fallback()
            }
        }
        WEBP_MIME -> if (motion) {
            LocalMediaImage(path, modifier, fit = true)
        } else {
            fallback()
        }
        else -> fallback()
    }
}

@Composable
private fun SlotMachineDiceVisual(
    dice: MessageDiceInfo,
    state: MessageDiceState.SlotMachine,
    files: Map<Int, MediaFileState>,
    animating: Boolean,
    playing: Boolean,
    fallback: @Composable () -> Unit,
    onFinished: () -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier
) {
    val layers = state.layers
    if (layers.any { it.mimeType != TGS_MIME }) {
        fallback()
        return
    }
    val paths = layers.map { files[it.fileId]?.localPath }
    val jsonResults = ArrayList<Result<String>?>(paths.size)
    for (path in paths) jsonResults += rememberTgsJson(path)
    val failures = jsonResults.any { it?.isFailure == true }
    LaunchedEffect(failures) { if (failures) onFailed() }
    val jsons = jsonResults.map { it?.getOrNull() }
    if (jsons.any { it == null } || failures) {
        fallback()
        return
    }

    val compositionKey = layers.joinToString(":") { it.fileId.toString() }
    var readyCount by remember(compositionKey) { mutableIntStateOf(0) }
    var failed by remember(compositionKey) { mutableStateOf(false) }
    var reelsDone by remember(compositionKey, animating) { mutableIntStateOf(0) }
    var backgroundFinished by remember(compositionKey, animating) { mutableStateOf(false) }
    var reported by remember(compositionKey, animating) { mutableStateOf(false) }
    val ready = readyCount >= layers.size && !failed
    val finalState = dice.hasFinalValue

    val backgroundAnimating = animating && finalState && reelsDone >= 3
    val backgroundMode = when {
        !finalState -> DiceFrameMode.End
        !animating -> DiceFrameMode.End
        reelsDone >= 3 -> DiceFrameMode.Play
        else -> DiceFrameMode.Start
    }
    val leverMode = if (!finalState && animating) DiceFrameMode.Play else DiceFrameMode.End
    val reelMode = if (animating) DiceFrameMode.Play else DiceFrameMode.End
    val onReelFinished = {
        if (reelsDone < 3) reelsDone += 1
    }
    val onBackgroundFinished: (() -> Unit)? = if (finalState) {
        { backgroundFinished = true }
    } else {
        null
    }
    val backgroundNeedsFinish = !finalState || backgroundFinished
    LaunchedEffect(reelsDone, backgroundFinished, finalState) {
        if (reelsDone >= 3 && backgroundNeedsFinish && !reported) {
            reported = true
            onFinished()
        }
    }

    Box(modifier) {
        Box(Modifier.fillMaxSize().alpha(if (ready) 1f else 0f)) {
            // Recreate the layer subtree when the composition changes; a reused file keeps its last readiness
            // report and would otherwise make the new composition wait forever for five ready layers.
            key(compositionKey) {
                DiceTgsLayer(
                    json = jsons[0].orEmpty(),
                    mode = backgroundMode,
                    isPlaying = playing && ready && backgroundAnimating,
                    modifier = Modifier.fillMaxSize(),
                    onReady = { if (!failed) readyCount += 1 },
                    onFailed = { failed = true; onFailed() },
                    onFinished = onBackgroundFinished
                )
                // Reels go above the background, then the frame/handle is painted last, as in Telegram Desktop.
                for (index in 2..4) {
                    DiceTgsLayer(
                        json = jsons[index].orEmpty(),
                        mode = reelMode,
                        isPlaying = playing && ready,
                        modifier = Modifier.fillMaxSize(),
                        onReady = { if (!failed) readyCount += 1 },
                        onFailed = { failed = true; onFailed() },
                        onFinished = onReelFinished
                    )
                }
                DiceTgsLayer(
                    json = jsons[1].orEmpty(),
                    mode = leverMode,
                    isPlaying = playing && ready && !finalState,
                    modifier = Modifier.fillMaxSize(),
                    onReady = { if (!failed) readyCount += 1 },
                    onFailed = { failed = true; onFailed() }
                )
            }
        }
        if (!ready) fallback()
    }
}

private enum class DiceFrameMode { Play, Start, End }

/**
 * One transparent TGS layer. [mode] separates "play once" from a static first/last frame, so a paused lifecycle or
 * an animation that has not received a playback permit never jumps to the final frame.
 */
@Composable
private fun DiceTgsLayer(
    json: String,
    mode: DiceFrameMode,
    isPlaying: Boolean,
    modifier: Modifier,
    onReady: () -> Unit,
    onFailed: () -> Unit,
    onFinished: (() -> Unit)? = null
) {
    val spec = remember(json) { LottieCompositionSpec.JsonString(json) }
    val result = rememberLottieComposition(spec)
    val composition = result.value
    var reportedReady by remember(json) { mutableStateOf(false) }
    var reportedFinished by remember(json) { mutableStateOf(false) }
    LaunchedEffect(composition, result.isFailure) {
        if (result.isFailure) onFailed()
        else if (composition != null && !reportedReady) {
            reportedReady = true
            onReady()
        }
    }
    if (composition == null) return
    val singleFrame = composition.endFrame - composition.startFrame <= 0f
    LaunchedEffect(composition, mode) {
        if (mode == DiceFrameMode.Play && singleFrame && !reportedFinished) {
            reportedFinished = true
            onFinished?.invoke()
        }
    }
    when (mode) {
        DiceFrameMode.Play -> {
            val progress by animateLottieCompositionAsState(
                composition = composition,
                isPlaying = isPlaying,
                restartOnPlay = false,
                iterations = 1
            )
            LaunchedEffect(composition) {
                snapshotFlow { progress }.collect { value ->
                    if (!reportedFinished && value >= 1f) {
                        reportedFinished = true
                        onFinished?.invoke()
                    }
                }
            }
            LottieAnimation(composition = composition, progress = { progress }, modifier = modifier)
        }
        DiceFrameMode.Start -> LottieAnimation(composition = composition, progress = { 0f }, modifier = modifier)
        DiceFrameMode.End -> LottieAnimation(composition = composition, progress = { 1f }, modifier = modifier)
    }
}

@Composable
private fun rememberTgsJson(path: String?): Result<String>? {
    val decoded by produceState<Result<String>?>(null, path) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            runCatching { readTgsJson(File(path)) }
        }
    }
    return decoded
}

@Composable
private fun DiceFallback(dice: MessageDiceInfo, resultText: String, modifier: Modifier) {
    val palette = beyGramPalette()
    Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = dice.fallbackEmoji,
            fontSize = 54.sp,
            lineHeight = 60.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = resultText,
            color = palette.messageTime,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Battery saver is treated like reduced motion: the result is correct, but a decorative throw must not run.
 * It is re-read on every resume instead of installing another long-lived system observer.
 */
@Composable
private fun rememberPowerSaveMode(context: Context): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var powerSave by remember(context) { mutableStateOf(readPowerSaveMode(context)) }
    LaunchedEffect(context, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            powerSave = readPowerSaveMode(context)
        }
    }
    return powerSave
}

/** Pauses a running one-shot animation when the chat leaves the foreground and resumes it from the same frame. */
@Composable
private fun rememberLifecycleResumed(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return resumed
}

private fun readPowerSaveMode(context: Context): Boolean = runCatching {
    context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
}.getOrDefault(false)
