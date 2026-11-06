package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ProfileAudio
import org.drinkless.tdlib.TdApi

/** Максимум треков в одном запросе `getUserProfileAudios`; больший список в интерфейсе не нужен. */
internal const val PROFILE_AUDIO_PAGE_LIMIT = 100

/** TDLib answers 400, 403 or 404 when the list is hidden or not available. Other errors keep the list on screen. */
internal val profileAudioHiddenCodes = setOf(400, 403, 404)

internal fun TdApi.Audio.toProfileAudio(): ProfileAudio = ProfileAudio(
    fileId = audio.id,
    title = title.orEmpty(),
    performer = performer.orEmpty(),
    fileName = fileName.orEmpty(),
    durationSeconds = duration,
    mimeType = mimeType.orEmpty(),
    sizeBytes = audio.size
)

