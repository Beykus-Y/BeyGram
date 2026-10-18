package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.CachePolicy
import com.beykus.beygram.domain.model.StorageCategory
import com.beykus.beygram.domain.model.StorageUsage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/** Занятое место и очистка файлов TDLib. TDLib удаляет только файлы: база сообщений здесь не затрагивается. */
internal class TdStorage(private val client: () -> Client, private val uploadDirectories: List<File>) {
    private val requests = TdRequests(client)
    /** Очистка и применение лимита идут по одному, включая фоновую проверку: TDLib не должен удалять файлы параллельно. */
    private val lane = MaintenanceLane()

    suspend fun usage(): StorageUsage {
        val files = statistics()
        val database = requests(TdApi.GetStorageStatisticsFast())
        return StorageUsage(
            bytesByCategory = files.fileTypeEntries().groupingBy { storageCategoryOf(it.fileType) }
                .fold(0L) { total, entry -> total + entry.size },
            // База, языковые пакеты и журнал TDLib: очистка их не трогает, но в «занято приложением» они входят.
            databaseBytes = database.databaseSize + database.languagePackDatabaseSize + database.logSize,
            // Файлы на отправку лежат в папках приложения, поэтому TDLib их не считает: размер берём с диска.
            uploadBytes = withContext(Dispatchers.IO) { filesBytesIn(uploadDirectories) }
        )
    }

    /** Очищает категории по одной, чтобы прогресс отражал реальный ход работы. Возвращает освобождённые байты. */
    suspend fun clear(categories: Set<StorageCategory>, onProgress: (done: Int, total: Int) -> Unit): Long = lane.exclusive {
        val typesByCategory = statistics().fileTypeEntries()
            .map { it.fileType }
            .distinctBy { it.javaClass }
            .groupBy { storageCategoryOf(it) }
            .mapValues { (_, types) -> types.toTypedArray() }
        val targets = StorageCategory.entries.filter { it in categories && it in typesByCategory }
        var freed = 0L
        targets.forEachIndexed { index, category ->
            onProgress(index, targets.size)
            freed += requests(clearFileTypesRequest(typesByCategory.getValue(category)), STORAGE_TIMEOUT_MS).size
        }
        onProgress(targets.size, targets.size)
        freed
    }

    /**
     * Приводит файлы к политике. Если пока она ждала очереди, пришла более новая, эта пропускается:
     * последнюю политику применит её собственный вызов.
     */
    suspend fun applyPolicy(policy: CachePolicy): Long = lane.latestOnly(skipped = 0L) {
        val fileTypes = statistics().fileTypeEntries().map { it.fileType }.distinctBy { it.javaClass }.toTypedArray()
        val request = policy.optimizeRequest(fileTypes) ?: return@latestOnly 0L
        requests(request, STORAGE_TIMEOUT_MS).size
    }

    private suspend fun statistics(): TdApi.StorageStatistics =
        requests(TdApi.GetStorageStatistics(NO_CHAT_BREAKDOWN), STORAGE_TIMEOUT_MS)

    /** Статистика приходит по чатам; для категорий чаты не нужны, поэтому записи складываем вместе. */
    private fun TdApi.StorageStatistics.fileTypeEntries(): List<TdApi.StorageStatisticsByFileType> =
        byChat.flatMap { it.byFileType.asList() }

    private companion object {
        /** Подсчёт и удаление файлов по большому кэшу занимают заметное время. */
        const val STORAGE_TIMEOUT_MS = 120_000L
    }
}

/** Размер всех файлов в папках, включая вложенные. Папки, которых нет, занимают 0 байт. */
internal fun filesBytesIn(directories: List<File>): Long =
    directories.filter { it.isDirectory }.sumOf { directory -> directory.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
