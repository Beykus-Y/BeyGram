package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.CachePolicy
import com.beykus.beygram.domain.model.StorageCategory
import org.drinkless.tdlib.TdApi

/** Статистика без разбивки по чатам: категории считаются по всем чатам сразу. */
internal const val NO_CHAT_BREAKDOWN = 0

/** Максимальное целое, которое без потерь проходит через int53. Больше размера TDLib не упрётся. */
private const val NO_SIZE_LIMIT = (1L shl 53) - 1

/** Срок и количество без ограничения: TDLib принимает их как int32. */
private const val NO_AGE_OR_COUNT_LIMIT = Int.MAX_VALUE

/** Значение TDLib по умолчанию: новые файлы защищены от удаления своим сроком. */
private const val DEFAULT_IMMUNITY_SECONDS = -1

/** Очистка по кнопке удаляет и свежие файлы: пользователь просит убрать всё выбранное. */
private const val NO_IMMUNITY_SECONDS = 0

/** Категория, в которую попадает тип файла TDLib. Всё, что не фото, видео, файлы, голос или музыка, идёт в «Прочее». */
internal fun storageCategoryOf(fileType: TdApi.FileType): StorageCategory = when (fileType) {
    is TdApi.FileTypePhoto, is TdApi.FileTypePhotoStory, is TdApi.FileTypeSelfDestructingPhoto -> StorageCategory.Photos
    is TdApi.FileTypeVideo, is TdApi.FileTypeVideoStory, is TdApi.FileTypeAnimation, is TdApi.FileTypeLivePhotoVideo,
    is TdApi.FileTypeSelfDestructingVideo, is TdApi.FileTypeSelfDestructingLivePhotoVideo -> StorageCategory.Videos
    is TdApi.FileTypeDocument, is TdApi.FileTypeSecure -> StorageCategory.Files
    is TdApi.FileTypeVoiceNote, is TdApi.FileTypeVideoNote,
    is TdApi.FileTypeSelfDestructingVoiceNote, is TdApi.FileTypeSelfDestructingVideoNote -> StorageCategory.VoiceAndVideoNotes
    is TdApi.FileTypeAudio -> StorageCategory.Music
    else -> StorageCategory.Other
}

/**
 * Запрос, который приводит файлы к лимитам. Без лимитов запроса нет, и TDLib ничего не удаляет.
 * Типы файлов не передаются: TDLib удаляет как обычно и не трогает миниатюры, аватарки, стикеры и обои.
 */
internal fun CachePolicy.optimizeRequest(): TdApi.OptimizeStorage? {
    if (maxBytes == null && maxAgeSeconds == null) return null
    return TdApi.OptimizeStorage(
        maxBytes ?: NO_SIZE_LIMIT,
        maxAgeSeconds ?: NO_AGE_OR_COUNT_LIMIT,
        NO_AGE_OR_COUNT_LIMIT,
        DEFAULT_IMMUNITY_SECONDS,
        emptyArray(),
        longArrayOf(),
        longArrayOf(),
        true,
        NO_CHAT_BREAKDOWN
    )
}

/** Запрос, который удаляет все файлы перечисленных типов. Просим статистику удалённого, чтобы показать, сколько освободили. */
internal fun clearFileTypesRequest(fileTypes: Array<TdApi.FileType>): TdApi.OptimizeStorage =
    TdApi.OptimizeStorage(0L, 0, 0, NO_IMMUNITY_SECONDS, fileTypes, longArrayOf(), longArrayOf(), true, NO_CHAT_BREAKDOWN)
