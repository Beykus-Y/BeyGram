package com.beykus.beygram.notifications

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.UTFDataFormatException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Ответы из уведомлений, которые ещё не отправлены. Каждый ответ лежит отдельным файлом. Запись идёт через временный
 * файл с fsync и переименованием, поэтому оборванная запись не портит уже сохранённые ответы. Каталог приватный и не
 * попадает в резервные копии. Все операции синхронизированы: отправка и очистка не пересекаются.
 */
internal class ReplyOutbox(private val directory: File) {
    data class Entry(
        val id: String,
        val chatId: Long,
        val groupId: Int,
        val maxId: Int,
        val replyToMessageId: Long,
        val text: String,
        val createdAtMillis: Long,
        val attempts: Int
    )

    init {
        // A temporary file is left only by an interrupted write, which was never reported as stored.
        directory.listFiles { file -> file.name.endsWith(TEMP_SUFFIX) }?.forEach { it.delete() }
    }

    /** Сохраняет ответ и возвращает его только после записи на диск. Если записать не удалось, бросает [IOException]. */
    @Synchronized
    fun add(chatId: Long, groupId: Int, maxId: Int, replyToMessageId: Long, text: String, nowMillis: Long): Entry {
        val entry = Entry(UUID.randomUUID().toString(), chatId, groupId, maxId, replyToMessageId, text, nowMillis, attempts = 0)
        write(entry)
        return entry
    }

    /** Ответы, которые ещё не отправлены, от старых к новым. Файлы, которые не читаются, удаляются. */
    @Synchronized
    fun entries(): List<Entry> = directory.listFiles { file -> file.name.endsWith(SUFFIX) }.orEmpty()
        .mapNotNull { file -> readOrDiscard(file) }
        .sortedBy { it.createdAtMillis }

    /** Удаляет отправленный ответ. Возвращает false, если файл остался на диске. */
    @Synchronized
    fun remove(id: String): Boolean = File(directory, id + SUFFIX).let { !it.exists() || it.delete() }

    /** Проверяет, что ответ ещё в очереди. Очистка при выходе и удаление после отправки убирают его из очереди. */
    @Synchronized
    fun isStored(id: String): Boolean = File(directory, id + SUFFIX).exists()

    /**
     * Засчитывает неудачную попытку. Возвращает true, если ответ больше не будет отправляться: попытки исчерпаны или
     * ответ уже удалён очисткой. Удалённый ответ не записывается заново. Если запись не удалась, бросает [IOException].
     */
    @Synchronized
    fun recordFailure(entry: Entry, maxAttempts: Int): Boolean {
        if (!isStored(entry.id)) return true
        val next = entry.copy(attempts = entry.attempts + 1)
        if (next.attempts >= maxAttempts) {
            remove(entry.id)
            return true
        }
        write(next)
        return false
    }

    /** Удаляет ответы старше [ttlMillis] и возвращает, сколько удалено. */
    @Synchronized
    fun discardExpired(nowMillis: Long, ttlMillis: Long): Int = entries()
        .filter { nowMillis - it.createdAtMillis > ttlMillis }
        .count { remove(it.id) }

    /** Удаляет все ответы, например при смене аккаунта: они относятся к прежнему аккаунту. */
    @Synchronized
    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    private fun write(entry: Entry) {
        directory.mkdirs()
        val target = File(directory, entry.id + SUFFIX)
        val temp = File(directory, entry.id + TEMP_SUFFIX)
        try {
            FileOutputStream(temp).use { stream ->
                val output = DataOutputStream(BufferedOutputStream(stream))
                encode(entry, output)
                output.flush()
                stream.fd.sync()
            }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (error: IOException) {
            temp.delete()
            throw error
        }
    }

    private fun readOrDiscard(file: File): Entry? = try {
        decode(file)
    } catch (error: IOException) {
        // A file that cannot be parsed will never be read, so it is removed. Another read error may be temporary.
        if (error is EOFException || error is UTFDataFormatException || error is CorruptReplyException) file.delete()
        null
    }

    private fun encode(entry: Entry, output: DataOutputStream) {
        val text = entry.text.toByteArray(Charsets.UTF_8)
        if (text.size > MAX_TEXT_BYTES) throw IOException("Reply is too large to store")
        output.writeInt(FORMAT_VERSION)
        output.writeUTF(entry.id)
        output.writeLong(entry.chatId)
        output.writeInt(entry.groupId)
        output.writeInt(entry.maxId)
        output.writeLong(entry.replyToMessageId)
        output.writeLong(entry.createdAtMillis)
        output.writeInt(entry.attempts)
        output.writeInt(text.size)
        output.write(text)
    }

    private fun decode(file: File): Entry = DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
        if (input.readInt() != FORMAT_VERSION) throw CorruptReplyException()
        val id = input.readUTF()
        // The file name is the key that remove() uses, so a file under another name is not a reply of this outbox.
        if (id + SUFFIX != file.name) throw CorruptReplyException()
        val chatId = input.readLong()
        val groupId = input.readInt()
        val maxId = input.readInt()
        val replyToMessageId = input.readLong()
        val createdAtMillis = input.readLong()
        val attempts = input.readInt()
        val size = input.readInt()
        if (size !in 0..MAX_TEXT_BYTES) throw CorruptReplyException()
        val text = ByteArray(size).also { input.readFully(it) }
        Entry(id, chatId, groupId, maxId, replyToMessageId, String(text, Charsets.UTF_8), createdAtMillis, attempts)
    }

    private companion object {
        const val SUFFIX = ".reply"
        const val TEMP_SUFFIX = ".reply.tmp"
        const val FORMAT_VERSION = 1
        const val MAX_TEXT_BYTES = 64 * 1024
    }
}

/** The file has the wrong format or sizes, so it is removed rather than retried. */
private class CorruptReplyException : IOException("Stored reply is damaged")
