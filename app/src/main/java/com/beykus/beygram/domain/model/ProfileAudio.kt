package com.beykus.beygram.domain.model

/**
 * Трек из профиля пользователя (TDLib `audio` из `getUserProfileAudios`). Файл живёт в TDLib: путь появляется
 * только после загрузки, поэтому плеер получает его через наблюдение за [fileId], как у остальной музыки.
 */
data class ProfileAudio(
    val fileId: Int,
    val title: String,
    val performer: String,
    val fileName: String,
    val durationSeconds: Int,
    val mimeType: String,
    val sizeBytes: Long
) {
    /** Название без исполнителя; без тега остаётся имя файла. */
    val trackTitle: String get() = title.ifBlank { fileName }

    /** Подпись как в общем плеере: «Исполнитель — Название». Без тегов остаётся имя файла. */
    val displayTitle: String
        get() = if (performer.isBlank() && title.isBlank()) fileName
        else listOf(performer, trackTitle).filter { it.isNotBlank() }.joinToString(" — ")
}
