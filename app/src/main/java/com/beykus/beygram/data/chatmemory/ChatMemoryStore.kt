package com.beykus.beygram.data.chatmemory

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.beykus.beygram.data.tdlib.TdAccountStorageLayout
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Chat Memory's database of one account: the latest text of each seen message, the earlier texts of edited messages and
 * the messages the other side deleted. Every call holds the store's lock, so a change that touches several rows is never
 * seen half done. The calls are quick but touch the disk, so the repository runs them off the TDLib update thread.
 */
internal class ChatMemoryStore internal constructor(private val helper: SQLiteOpenHelper) {

    /**
     * Records what TDLib showed or changed. A new message gets a row when [insertMissing] is set. A message whose text
     * changed keeps its earlier text as a version. A deleted message is not changed any more.
     * Returns the new version count of each message whose earlier text was kept by this call, keyed by chat and message id.
     */
    @Synchronized
    fun observe(
        observations: List<MemoryObservation>,
        nowMillis: Long,
        insertMissing: Boolean = true
    ): Map<Pair<Long, Long>, Int> {
        if (observations.isEmpty()) return emptyMap()
        val db = helper.writableDatabase
        val archived = LinkedHashMap<Pair<Long, Long>, Int>()
        db.beginTransaction()
        try {
            for (observation in observations) {
                val change = observeLocked(db, observation, nowMillis, insertMissing) ?: continue
                archived[change.first] = change.second
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return archived
    }

    /**
     * Keeps messages the other side deleted. A message without a row is kept only when [fallback] knows it, because the
     * text on screen is then the only copy there is. A message that is already deleted keeps its first deletion time.
     */
    @Synchronized
    fun markDeleted(chatId: Long, messageIds: Collection<Long>, fallback: Map<Long, MemoryObservation>, nowMillis: Long) {
        if (messageIds.isEmpty()) return
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            for (messageId in messageIds) {
                val row = readRowLocked(db, chatId, messageId)
                if (row == null) {
                    val known = fallback[messageId] ?: continue
                    // Our own messages are not kept: deleting one is our own action, or a group admin's.
                    if (known.isOutgoing) continue
                    insertMessageLocked(db, known.copy(chatId = chatId, messageId = messageId), nowMillis, deletedAtMillis = nowMillis)
                } else if (!row.isOutgoing && row.deletedAtMillis == null) {
                    val values = ContentValues().apply { put("deleted_at", nowMillis) }
                    db.update("messages", values, WHERE_MESSAGE, keyArgs(chatId, messageId))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** The deleted messages of [chatId] whose ids lie in [fromMessageId]..[toMessageId], oldest first. */
    @Synchronized
    fun deletedMessages(chatId: Long, fromMessageId: Long, toMessageId: Long): List<MemorizedMessage> {
        if (fromMessageId > toMessageId) return emptyList()
        return helper.writableDatabase.rawQuery(
            "SELECT $COLUMNS FROM messages WHERE chat_id = ? AND deleted_at IS NOT NULL AND message_id BETWEEN ? AND ? ORDER BY message_id ASC",
            arrayOf(chatId.toString(), fromMessageId.toString(), toMessageId.toString())
        ).use { cursor -> readMessages(cursor) }
    }

    /** The earlier texts of one message, oldest first. */
    @Synchronized
    fun versions(chatId: Long, messageId: Long): List<MemorizedVersion> =
        helper.writableDatabase.rawQuery(
            "SELECT text, has_attachment, replaced_at FROM versions WHERE chat_id = ? AND message_id = ? ORDER BY seq ASC",
            keyArgs(chatId, messageId)
        ).use { cursor ->
            val result = mutableListOf<MemorizedVersion>()
            while (cursor.moveToNext()) {
                result += MemorizedVersion(cursor.getString(0), cursor.getInt(1) != 0, cursor.getLong(2))
            }
            result
        }

    /** The number of earlier texts for each message of [messageIds] that has any. */
    @Synchronized
    fun versionCounts(chatId: Long, messageIds: Collection<Long>): Map<Long, Int> {
        if (messageIds.isEmpty()) return emptyMap()
        val db = helper.writableDatabase
        val counts = HashMap<Long, Int>()
        for (chunk in messageIds.chunked(QUERY_CHUNK)) {
            val placeholders = chunk.joinToString(",") { "?" }
            val args = arrayOf(chatId.toString()) + chunk.map { it.toString() }
            db.rawQuery(
                "SELECT message_id, version_count FROM messages WHERE chat_id = ? AND version_count > 0 AND message_id IN ($placeholders)",
                args
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    counts[cursor.getLong(0)] = cursor.getInt(1)
                }
            }
        }
        return counts
    }

    @Synchronized
    fun usage(): ChatMemoryUsage {
        val db = helper.writableDatabase
        val messageTotals = db.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(CASE WHEN deleted_at IS NULL THEN 0 ELSE 1 END), 0), " +
                "COALESCE(SUM(LENGTH(CAST(text AS BLOB)) + LENGTH(CAST(sender_name AS BLOB))), 0) FROM messages",
            null
        ).use { cursor ->
            cursor.moveToFirst()
            Triple(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2))
        }
        val versionTotals = db.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(LENGTH(CAST(text AS BLOB))), 0) FROM versions",
            null
        ).use { cursor ->
            cursor.moveToFirst()
            Pair(cursor.getLong(0), cursor.getLong(1))
        }
        val messages = messageTotals.first
        val deleted = messageTotals.second
        val storedVersions = versionTotals.first
        val rows = messages + storedVersions
        return ChatMemoryUsage(
            messages = messages.toInt(),
            deletedMessages = deleted.toInt(),
            versions = storedVersions.toInt(),
            bytes = messageTotals.third + versionTotals.second + rows * ROW_OVERHEAD_BYTES
        )
    }

    /**
     * Removes whole messages, with their versions, until the store fits in [limitBytes]. Messages the other side has not
     * deleted go first, least recently seen first; deleted messages go last, oldest deletion first.
     * Returns how many messages were removed.
     */
    @Synchronized
    fun trim(limitBytes: Long): Int {
        val db = helper.writableDatabase
        var removed = 0
        while (true) {
            val excess = usage().bytes - limitBytes
            if (excess <= 0L) break
            val victims = victimsLocked(db, MAX_EVICTION_BATCH.toInt())
            if (victims.isEmpty()) break
            // Only as many of the oldest victims as the excess needs go. Each one is measured, so one long edit history
            // does not take the deleted messages behind it along.
            val chosen = mutableListOf<Victim>()
            var freed = 0L
            for (victim in victims) {
                if (freed >= excess) break
                chosen += victim
                freed += victim.bytes
            }
            db.beginTransaction()
            try {
                for (victim in chosen) {
                    db.delete("versions", WHERE_MESSAGE, keyArgs(victim.chatId, victim.messageId))
                    db.delete("messages", WHERE_MESSAGE, keyArgs(victim.chatId, victim.messageId))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            removed += chosen.size
        }
        return removed
    }

    /** Removes everything the store holds, for example when the user clears Chat Memory. */
    @Synchronized
    fun clearAll() {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("versions", null, null)
            db.delete("messages", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun close() {
        helper.close()
    }

    private fun observeLocked(
        db: SQLiteDatabase,
        observation: MemoryObservation,
        nowMillis: Long,
        insertMissing: Boolean
    ): Pair<Pair<Long, Long>, Int>? {
        val row = readRowLocked(db, observation.chatId, observation.messageId)
        if (row == null) {
            if (insertMissing) insertMessageLocked(db, observation, nowMillis, deletedAtMillis = null)
            return null
        }
        // A deleted message keeps the text it had when the other side deleted it.
        if (row.deletedAtMillis != null) return null
        val snapshot = observation.snapshot
        if (row.text == snapshot.text && row.hasAttachment == snapshot.hasAttachment) {
            val values = ContentValues().apply {
                put("seen_at", nowMillis)
            }
            db.update("messages", values, WHERE_MESSAGE, keyArgs(observation.chatId, observation.messageId))
            return null
        }
        val versionNumber = row.versionCount + 1
        val version = ContentValues().apply {
            put("chat_id", observation.chatId)
            put("message_id", observation.messageId)
            put("seq", versionNumber)
            put("text", row.text)
            put("has_attachment", row.hasAttachment)
            put("replaced_at", nowMillis)
        }
        db.insertOrThrow("versions", null, version)
        val values = ContentValues().apply {
            put("text", snapshot.text)
            put("has_attachment", snapshot.hasAttachment)
            put("version_count", versionNumber)
            put("seen_at", nowMillis)
        }
        db.update("messages", values, WHERE_MESSAGE, keyArgs(observation.chatId, observation.messageId))
        return Pair(observation.chatId to observation.messageId, versionNumber)
    }

    private fun insertMessageLocked(db: SQLiteDatabase, observation: MemoryObservation, nowMillis: Long, deletedAtMillis: Long?) {
        val values = ContentValues().apply {
            put("chat_id", observation.chatId)
            put("message_id", observation.messageId)
            put("is_outgoing", observation.isOutgoing)
            put("date", observation.dateSeconds)
            put("sender_id", observation.senderId)
            put("sender_name", observation.senderName)
            put("text", observation.snapshot.text)
            put("has_attachment", observation.snapshot.hasAttachment)
            put("version_count", 0)
            put("seen_at", nowMillis)
            if (deletedAtMillis != null) put("deleted_at", deletedAtMillis) else putNull("deleted_at")
        }
        db.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun readRowLocked(db: SQLiteDatabase, chatId: Long, messageId: Long): Row? =
        db.rawQuery(
            "SELECT text, has_attachment, is_outgoing, deleted_at, version_count FROM messages WHERE chat_id = ? AND message_id = ?",
            keyArgs(chatId, messageId)
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                Row(
                    text = cursor.getString(0),
                    hasAttachment = cursor.getInt(1) != 0,
                    isOutgoing = cursor.getInt(2) != 0,
                    deletedAtMillis = if (cursor.isNull(3)) null else cursor.getLong(3),
                    versionCount = cursor.getInt(4)
                )
            }
        }

    /**
     * The messages trim would remove next, in order, each with the bytes it holds: its text, sender name, row cost and
     * versions. This is the same measure as [usage], so the bytes freed are the bytes the limit counts.
     */
    private fun victimsLocked(db: SQLiteDatabase, limit: Int): List<Victim> =
        db.rawQuery(
            "SELECT m.chat_id, m.message_id, " +
                "COALESCE(LENGTH(CAST(m.text AS BLOB)), 0) + COALESCE(LENGTH(CAST(m.sender_name AS BLOB)), 0) + $ROW_OVERHEAD_BYTES + " +
                "COALESCE((SELECT SUM(COALESCE(LENGTH(CAST(v.text AS BLOB)), 0) + $ROW_OVERHEAD_BYTES) FROM versions v " +
                "WHERE v.chat_id = m.chat_id AND v.message_id = m.message_id), 0) " +
                "FROM messages m ORDER BY (m.deleted_at IS NOT NULL) ASC, COALESCE(m.deleted_at, m.seen_at) ASC LIMIT ?",
            arrayOf(limit.toString())
        ).use { cursor ->
            val result = mutableListOf<Victim>()
            while (cursor.moveToNext()) {
                result += Victim(chatId = cursor.getLong(0), messageId = cursor.getLong(1), bytes = cursor.getLong(2))
            }
            result
        }

    /** A message trim would remove next, with the bytes that removing it frees. */
    private class Victim(val chatId: Long, val messageId: Long, val bytes: Long)

    private class Row(val text: String, val hasAttachment: Boolean, val isOutgoing: Boolean, val deletedAtMillis: Long?, val versionCount: Int)

    companion object {
        private val stores = ConcurrentHashMap<String, ChatMemoryStore>()

        /** The store of one account, opened on first use. Its database lives in the account's no-backup storage. */
        fun forAccount(context: Context, accountId: String): ChatMemoryStore {
            require(TdAccountStorageLayout.isValidAccountId(accountId)) { "Invalid account id for chat memory" }
            return stores.computeIfAbsent(accountId) {
                val appContext = context.applicationContext
                val file = databaseFile(appContext, accountId)
                file.parentFile?.mkdirs()
                ChatMemoryStore(ChatMemoryDatabase(appContext, file.path))
            }
        }

        /** Closes the store of a removed account and deletes its database files. */
        fun deleteAccountFiles(context: Context, accountId: String) {
            stores.remove(accountId)?.close()
            val file = databaseFile(context.applicationContext, accountId)
            listOf("", "-wal", "-shm", "-journal").forEach { suffix -> File(file.path + suffix).delete() }
        }

        /** Whether the account has a database on disk. Checking does not create one. */
        fun hasDatabase(context: Context, accountId: String): Boolean =
            databaseFile(context.applicationContext, accountId).exists()

        private fun databaseFile(context: Context, accountId: String): File =
            File(context.noBackupFilesDir, "chat-memory/$accountId.db")
    }
}

/** The database of Chat Memory. Version 1 is the first schema, so a later upgrade rebuilds the tables. */
internal class ChatMemoryDatabase(context: Context, path: String) : SQLiteOpenHelper(context, path, null, SCHEMA_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        createSchema(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS versions")
        db.execSQL("DROP TABLE IF EXISTS messages")
        createSchema(db)
    }
}

private const val SCHEMA_VERSION = 1
private const val QUERY_CHUNK = 500
private const val MAX_EVICTION_BATCH = 5000L

/** A fixed cost per stored row, added to the text sizes so that many short messages count for what they take. */
private const val ROW_OVERHEAD_BYTES = 64L
private const val WHERE_MESSAGE = "chat_id = ? AND message_id = ?"
private const val COLUMNS =
    "chat_id, message_id, is_outgoing, date, sender_id, sender_name, text, has_attachment, deleted_at, version_count"

private fun keyArgs(chatId: Long, messageId: Long): Array<String> = arrayOf(chatId.toString(), messageId.toString())

private fun createSchema(db: SQLiteDatabase) {
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS messages (" +
            "chat_id INTEGER NOT NULL, " +
            "message_id INTEGER NOT NULL, " +
            "is_outgoing INTEGER NOT NULL, " +
            "date INTEGER NOT NULL, " +
            "sender_id INTEGER NOT NULL, " +
            "sender_name TEXT NOT NULL, " +
            "text TEXT NOT NULL, " +
            "has_attachment INTEGER NOT NULL, " +
            "version_count INTEGER NOT NULL, " +
            "seen_at INTEGER NOT NULL, " +
            "deleted_at INTEGER, " +
            "PRIMARY KEY (chat_id, message_id))"
    )
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS versions (" +
            "chat_id INTEGER NOT NULL, " +
            "message_id INTEGER NOT NULL, " +
            "seq INTEGER NOT NULL, " +
            "text TEXT NOT NULL, " +
            "has_attachment INTEGER NOT NULL, " +
            "replaced_at INTEGER NOT NULL, " +
            "PRIMARY KEY (chat_id, message_id, seq))"
    )
}

private fun readMessages(cursor: Cursor): List<MemorizedMessage> {
    val result = mutableListOf<MemorizedMessage>()
    while (cursor.moveToNext()) {
        result += MemorizedMessage(
            chatId = cursor.getLong(0),
            messageId = cursor.getLong(1),
            isOutgoing = cursor.getInt(2) != 0,
            dateSeconds = cursor.getLong(3),
            senderId = cursor.getLong(4),
            senderName = cursor.getString(5),
            text = cursor.getString(6),
            hasAttachment = cursor.getInt(7) != 0,
            deletedAtMillis = if (cursor.isNull(8)) null else cursor.getLong(8),
            versionCount = cursor.getInt(9)
        )
    }
    return result
}
