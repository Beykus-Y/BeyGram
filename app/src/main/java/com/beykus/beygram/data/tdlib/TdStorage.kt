package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.CachePolicy
import com.beykus.beygram.domain.model.StorageCategory
import com.beykus.beygram.domain.model.StorageUsage
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/**
 * Занятое место и очистка файлов TDLib. TDLib удаляет только файлы: база сообщений здесь не затрагивается.
 * [sentUploads] — папка копий, из которых отправлены файлы; [mediaFileIds] — файлы, которые показывают загруженные чаты.
 */
internal class TdStorage(
    private val client: () -> Client,
    private val uploadDirectories: List<File>,
    private val sentUploads: File,
    private val mediaFileIds: () -> Set<Int>
) {
    private val requests = TdRequests(client)
    /** Очистка и применение лимита идут по одному, включая фоновую проверку: TDLib не должен удалять файлы параллельно. */
    private val lane = MaintenanceLane()

    suspend fun usage(): StorageUsage {
        val files = statistics(STORAGE_TIMEOUT_MS)
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
        val typesByCategory = statistics(TDLIB_BACKSTOP_MS).fileTypeEntries()
            .map { it.fileType }
            .distinctBy { it.javaClass }
            .groupBy { storageCategoryOf(it) }
            .mapValues { (_, types) -> types.toTypedArray() }
        val targets = StorageCategory.entries.filter { it in categories && it in typesByCategory }
        var freed = 0L
        targets.forEachIndexed { index, category ->
            onProgress(index, targets.size)
            freed += requests(clearFileTypesRequest(typesByCategory.getValue(category)), TDLIB_BACKSTOP_MS).size
        }
        onProgress(targets.size, targets.size)
        freed
    }

    /**
     * Приводит файлы к политике. Если пока она ждала очереди, пришла более новая, эта пропускается:
     * последнюю политику применит её собственный вызов.
     */
    suspend fun applyPolicy(policy: CachePolicy): Long = lane.latestOnly(skipped = 0L) {
        val fileTypes = statistics(TDLIB_BACKSTOP_MS).fileTypeEntries().map { it.fileType }.distinctBy { it.javaClass }.toTypedArray()
        val tdlibFreed = policy.optimizeRequest(fileTypes)?.let { requests(it, TDLIB_BACKSTOP_MS).size } ?: 0L
        tdlibFreed + removeSentUploads(policy)
    }

    /**
     * Копии отправленных файлов живут как кэш: их убирают по сроку и по лимиту. Папку не трогаем, пока её файл показывает
     * загруженный чат, и пока она не старше [IN_USE_GRACE_MILLIS]: так не тронется отправка, которая ещё идёт.
     * Срок отсчитывается от последнего изменения файлов. Лимит считается отдельно от кэша TDLib.
     */
    private suspend fun removeSentUploads(policy: CachePolicy): Long {
        val now = System.currentTimeMillis()
        val referenced = referencedPaths()
        val maxAgeSeconds = policy.maxAgeSeconds
        return withContext(Dispatchers.IO) {
            val folders = sentUploads.listFiles { it.isDirectory }.orEmpty().map { folder ->
                val files = folder.walkTopDown().filter { it.isFile }.toList()
                val lastUsed = files.maxOfOrNull { it.lastModified() } ?: folder.lastModified()
                val inUse = now - lastUsed < IN_USE_GRACE_MILLIS || files.any { it.canonicalPath in referenced }
                SentUpload(folder, bytes = files.sumOf { it.length() }, lastUsedMillis = lastUsed, inUse = inUse)
            }
            val candidates = folders.filter { !it.inUse }.sortedBy { it.lastUsedMillis }
            val expired = candidates.filter { maxAgeSeconds != null && now - it.lastUsedMillis > maxAgeSeconds * 1000L }
            val removed = mutableListOf<SentUpload>().apply { addAll(expired) }
            val limit = policy.maxBytes
            if (limit != null) {
                var total = folders.sumOf { it.bytes } - expired.sumOf { it.bytes }
                for (folder in candidates - expired.toSet()) {
                    if (total <= limit) break
                    removed += folder
                    total -= folder.bytes
                }
            }
            removed.sumOf { if (it.folder.deleteRecursively()) it.bytes else 0L }
        }
    }

    /** Пути, которые TDLib отдаёт для файлов загруженных чатов. */
    private suspend fun referencedPaths(): Set<String> {
        val paths = mediaFileIds().mapNotNull { id ->
            try {
                requests(TdApi.GetFile(id)).local?.path
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
        return withContext(Dispatchers.IO) { paths.filter { it.isNotBlank() }.map { File(it).canonicalPath }.toSet() }
    }

    private suspend fun statistics(timeoutMillis: Long): TdApi.StorageStatistics =
        requests(TdApi.GetStorageStatistics(NO_CHAT_BREAKDOWN), timeoutMillis)

    /** Статистика приходит по чатам; для категорий чаты не нужны, поэтому записи складываем вместе. */
    private fun TdApi.StorageStatistics.fileTypeEntries(): List<TdApi.StorageStatisticsByFileType> =
        byChat.flatMap { it.byFileType.asList() }

    private companion object {
        /** Подсчёт для экрана: если TDLib молчит дольше, экран покажет ошибку. */
        const val STORAGE_TIMEOUT_MS = 120_000L

        /**
         * Очистка и оптимизация держат очередь до ответа TDLib: отказавшись ждать, TDLib ещё удаляет файлы, и следующая
         * операция не должна начаться рядом с ней. Предел нужен только на случай, когда TDLib совсем не отвечает.
         */
        const val TDLIB_BACKSTOP_MS = 10 * 60_000L

        /** Папка моложе этого срока может принадлежать отправке, которая ещё идёт, и не трогается. */
        const val IN_USE_GRACE_MILLIS = 10 * 60_000L
    }
}

/** Папка копий отправленных файлов: [bytes] — её размер, [lastUsedMillis] — последнее изменение файлов. */
private class SentUpload(val folder: File, val bytes: Long, val lastUsedMillis: Long, val inUse: Boolean)

/** Размер всех файлов в папках, включая вложенные. Папки, которых нет, занимают 0 байт. */
internal fun filesBytesIn(directories: List<File>): Long =
    directories.filter { it.isDirectory }.sumOf { directory -> directory.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
