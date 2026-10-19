package com.beykus.beygram.data.tdlib

/**
 * Файлы из общих медиа профиля, которые недавно загрузили. Просмотрщик открывает их из этого списка, поэтому очистка
 * копий не трогает их, пока загрузка свежая. Старше [PROTECTED_MILLIS] запись забывается, и файл подчиняется обычным правилам.
 */
internal class ListedMediaFiles(private val now: () -> Long = { System.currentTimeMillis() }) {
    private val listedAt = LinkedHashMap<Int, Long>()

    @Synchronized
    fun remember(fileIds: Collection<Int>) {
        val at = now()
        fileIds.forEach { fileId ->
            listedAt.remove(fileId)
            listedAt[fileId] = at
        }
        forgetExpired(at)
    }

    @Synchronized
    fun current(): Set<Int> {
        forgetExpired(now())
        return listedAt.keys.toSet()
    }

    // Записи лежат по порядку времени, поэтому устаревшие стоят в начале.
    private fun forgetExpired(at: Long) {
        val iterator = listedAt.entries.iterator()
        while (iterator.hasNext() && at - iterator.next().value > PROTECTED_MILLIS) iterator.remove()
    }

    private companion object {
        const val PROTECTED_MILLIS = 30 * 60_000L
    }
}
