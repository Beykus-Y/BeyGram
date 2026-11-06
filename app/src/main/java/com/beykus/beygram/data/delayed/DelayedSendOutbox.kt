package com.beykus.beygram.data.delayed

import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.TextStyleKind
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

/**
 * Delayed messages that have not been sent yet. Each message is one file in [directory], so a write that is cut short
 * never damages the others. A write goes through a temporary file with fsync and a rename. The directory is private and
 * excluded from backups, as for [com.beykus.beygram.notifications.ReplyOutbox]. All operations are synchronized.
 * The text is stored as typed and is never logged.
 */
internal class DelayedSendOutbox(private val directory: File, private val accountId: String) {
    init {
        // A temporary file is left only by an interrupted write, which was never reported as stored.
        directory.listFiles { file -> file.name.endsWith(TEMP_SUFFIX) }?.forEach { it.delete() }
    }

    /** Stores [entry] and returns only after it is on disk. Throws [IOException] when it cannot be written. */
    @Synchronized
    fun save(entry: DelayedSendEntry) {
        require(entry.accountId == accountId) { "Delayed message belongs to another account" }
        write(entry)
    }

    /** The stored messages, oldest first. Files that cannot be read as a message of this account are deleted. */
    @Synchronized
    fun entries(): List<DelayedSendEntry> = directory.listFiles { file -> file.name.endsWith(SUFFIX) }.orEmpty()
        .mapNotNull { file -> readOrDiscard(file) }
        .sortedBy { it.createdAtMillis }

    /** Deletes a message. Returns false if its file is still on disk. */
    @Synchronized
    fun remove(id: String): Boolean = File(directory, id + SUFFIX).let { !it.exists() || it.delete() }

    /** Deletes the messages older than [ttlMillis] and returns how many were removed. */
    @Synchronized
    fun discardExpired(nowMillis: Long, ttlMillis: Long): Int = entries()
        .filter { nowMillis - it.createdAtMillis > ttlMillis }
        .count { remove(it.id) }

    /** Deletes every stored message and returns how many files could not be deleted. */
    @Synchronized
    fun clear(): Int = directory.listFiles().orEmpty().count { file -> !file.delete() && file.exists() }

    private fun write(entry: DelayedSendEntry) {
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

    private fun encode(entry: DelayedSendEntry, output: DataOutputStream) {
        if (entry.entities.size > MAX_ENTITIES) throw IOException("Delayed message has too many formatting entities")
        output.writeInt(FORMAT_VERSION)
        output.writeUTF(entry.id)
        output.writeUTF(entry.accountId)
        output.writeLong(entry.chatId)
        output.writeLong(entry.topicId)
        output.writeLong(entry.replyToMessageId)
        output.writeBoolean(entry.disableNotification)
        output.writeLong(entry.createdAtMillis)
        output.writeLong(entry.notBeforeMillis)
        output.writeUTF(entry.status.name)
        writeBlock(output, entry.text.toByteArray(Charsets.UTF_8))
        output.writeInt(entry.entities.size)
        for (entity in entry.entities) {
            output.writeInt(entity.offset)
            output.writeInt(entity.length)
            output.writeUTF(entity.kind.name)
            writeBlock(output, entity.argument.toByteArray(Charsets.UTF_8))
        }
    }

    private fun writeBlock(output: DataOutputStream, bytes: ByteArray) {
        if (bytes.size > MAX_BYTES) throw IOException("Delayed message is too large to store")
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun readBlock(input: DataInputStream): ByteArray {
        val size = input.readInt()
        if (size !in 0..MAX_BYTES) throw CorruptDelayedSendException()
        return ByteArray(size).also { input.readFully(it) }
    }

    private fun readOrDiscard(file: File): DelayedSendEntry? = try {
        decode(file)
    } catch (error: IOException) {
        // A file that cannot be parsed will never be read, so it is removed. Another read error may be temporary.
        if (error is EOFException || error is UTFDataFormatException || error is CorruptDelayedSendException) file.delete()
        null
    }

    private fun decode(file: File): DelayedSendEntry = DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
        if (input.readInt() != FORMAT_VERSION) throw CorruptDelayedSendException()
        val id = input.readUTF()
        // The file name is the key that remove() uses, so a file under another name is not a message of this outbox.
        if (id + SUFFIX != file.name) throw CorruptDelayedSendException()
        if (input.readUTF() != accountId) throw CorruptDelayedSendException()
        val chatId = input.readLong()
        val topicId = input.readLong()
        val replyToMessageId = input.readLong()
        val disableNotification = input.readBoolean()
        val createdAtMillis = input.readLong()
        val notBeforeMillis = input.readLong()
        val status = namedEnum<DelayedSendStatus>(input.readUTF())
        val text = String(readBlock(input), Charsets.UTF_8)
        val count = input.readInt()
        if (count !in 0..MAX_ENTITIES) throw CorruptDelayedSendException()
        val entities = List(count) {
            val offset = input.readInt()
            val length = input.readInt()
            val kind = namedEnum<TextStyleKind>(input.readUTF())
            MessageTextEntity(offset, length, kind, String(readBlock(input), Charsets.UTF_8))
        }
        DelayedSendEntry(
            id = id,
            accountId = accountId,
            chatId = chatId,
            topicId = topicId,
            replyToMessageId = replyToMessageId,
            text = text,
            entities = entities,
            disableNotification = disableNotification,
            createdAtMillis = createdAtMillis,
            notBeforeMillis = notBeforeMillis,
            status = status
        )
    }

    private companion object {
        const val SUFFIX = ".delayed"
        const val TEMP_SUFFIX = ".delayed.tmp"
        const val FORMAT_VERSION = 1
        const val MAX_BYTES = 64 * 1024
        const val MAX_ENTITIES = 4096
    }
}

/** The file has the wrong format or sizes, so it is removed rather than retried. */
private class CorruptDelayedSendException : IOException("Stored delayed message is damaged")

private inline fun <reified T : Enum<T>> namedEnum(name: String): T =
    enumValues<T>().firstOrNull { it.name == name } ?: throw CorruptDelayedSendException()
