package com.beykus.beygram.ui.screens.profile

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.ProfileAudio
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.playback.PlaybackItem

/** Префикс ключей треков профиля. По нему плеер не предлагает добавить в профиль трек, который уже оттуда играет. */
internal const val PROFILE_AUDIO_KEY_PREFIX = "profile|"

/**
 * Ключ трека профиля в общем плеере. Включает аккаунт и владельца профиля: один и тот же файл, показанный
 * в профиле двух людей или двух аккаунтов, остаётся разными треками.
 */
internal fun profileAudioPlaybackKey(accountId: String, ownerId: Long, fileId: Int): String =
    "$PROFILE_AUDIO_KEY_PREFIX$accountId|$ownerId|$fileId"

/**
 * Трек профиля как элемент общего плеера. [path] пустой, пока файл не скачан: плеер сам запросит его у TDLib
 * через [com.beykus.beygram.playback.PlaybackQueueSource], так же как музыка из чата.
 */
internal fun ProfileAudio.playbackItem(accountId: String, ownerId: Long, path: String): PlaybackItem = PlaybackItem(
    key = profileAudioPlaybackKey(accountId, ownerId, fileId),
    kind = MediaKind.Audio,
    path = path,
    title = displayTitle,
    durationSeconds = durationSeconds,
    performer = performer,
    trackTitle = trackTitle,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    accountId = accountId,
    fileId = fileId
)

/**
 * Добавляет трек, который игрок сейчас держит, в профиль текущего аккаунта. Трек без TDLib-файла
 * (локальный файл) отклоняется сразу, без запроса.
 */
internal suspend fun TelegramRepository.addPlaybackItemToProfile(item: PlaybackItem): Result<Unit> {
    val fileId = item.fileId ?: return Result.failure(IllegalArgumentException("Playback item has no Telegram file"))
    return addProfileAudio(fileId, item.trackTitle, item.performer, item.durationSeconds)
}
