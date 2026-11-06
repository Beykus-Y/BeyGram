package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ProfileAudio
import org.drinkless.tdlib.TdApi

/** Максимум треков в одном запросе `getUserProfileAudios`; больший список в интерфейсе не нужен. */
internal const val PROFILE_AUDIO_PAGE_LIMIT = 100

internal fun TdApi.Audio.toProfileAudio(): ProfileAudio = ProfileAudio(
    fileId = audio.id,
    title = title.orEmpty(),
    performer = performer.orEmpty(),
    fileName = fileName.orEmpty(),
    durationSeconds = duration,
    mimeType = mimeType.orEmpty(),
    sizeBytes = audio.size
)

