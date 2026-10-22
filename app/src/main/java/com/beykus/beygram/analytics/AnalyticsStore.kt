package com.beykus.beygram.analytics

import android.content.Context
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/** Идентичность установки: псевдонимный UUID и отдельный долговременный секрет. */
internal data class AnalyticsIdentity(val installationId: String, val credential: String)

/** Ревизия, уже записанная локально и ждущая подтверждения. Повтор отправляет те же байты. */
internal data class AnalyticsPending(val revision: Long, val signature: String, val body: String)

/**
 * Локальное состояние аналитики в приватных prefs приложения. Файл `analytics.xml` исключён из
 * backup и переноса между устройствами (см. backup_rules.xml и data_extraction_rules.xml), чтобы
 * идентичность одной установки не клонировалась на другое устройство.
 */
internal class AnalyticsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    /** Сбой удаления или выключение сбора: серверную запись ещё нужно удалить. */
    var pendingDeletion: Boolean
        get() = prefs.getBoolean(KEY_PENDING_DELETION, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PENDING_DELETION, value).commit()
        }

    /** Сервер вернул 401: credential не принимается, пока пользователь не включит сбор заново. */
    var blocked: Boolean
        get() = prefs.getBoolean(KEY_BLOCKED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_BLOCKED, value).apply()
        }

    /** Последние известные авторизованные собственные ID; нужны, пока TDLib ещё не запущен. */
    val accountIds: List<Long>
        get() = prefs.getString(KEY_ACCOUNT_IDS, "").orEmpty()
            .split(',').mapNotNull { it.toLongOrNull()?.takeIf { id -> id > 0 } }

    fun saveAccountIds(ids: List<Long>) {
        prefs.edit().putString(KEY_ACCOUNT_IDS, ids.filter { it > 0 }.distinct().joinToString(",")).apply()
    }

    /** Возвращает существующую идентичность или `null`, если регистрации ещё не было. */
    @Synchronized
    fun identity(): AnalyticsIdentity? {
        val id = prefs.getString(KEY_INSTALLATION_ID, null) ?: return null
        val credential = prefs.getString(KEY_CREDENTIAL, null) ?: return null
        return AnalyticsIdentity(id, credential)
    }

    /** Создаёт id и credential одной записью, чтобы не остаться с половиной пары после сбоя. */
    @Synchronized
    fun createIdentity(): AnalyticsIdentity {
        identity()?.let { return it }
        val created = AnalyticsIdentity(UUID.randomUUID().toString(), newCredential())
        prefs.edit()
            .putString(KEY_INSTALLATION_ID, created.installationId)
            .putString(KEY_CREDENTIAL, created.credential)
            .commit()
        return created
    }

    /** Стирает локальную идентичность и состояние синхронизации после подтверждённого удаления. */
    @Synchronized
    fun resetIdentity() {
        prefs.edit()
            .remove(KEY_INSTALLATION_ID)
            .remove(KEY_CREDENTIAL)
            .remove(KEY_REVISION)
            .remove(KEY_PENDING_REVISION)
            .remove(KEY_PENDING_SIGNATURE)
            .remove(KEY_PENDING_BODY)
            .remove(KEY_SENT_SIGNATURE)
            .remove(KEY_SENT_AT)
            .putBoolean(KEY_BLOCKED, false)
            .putBoolean(KEY_PENDING_DELETION, false)
            .commit()
    }

    /** Последняя выданная ревизия; следующая всегда строго больше. */
    fun lastRevision(): Long = prefs.getLong(KEY_REVISION, 0L)

    fun pending(): AnalyticsPending? {
        val body = prefs.getString(KEY_PENDING_BODY, null) ?: return null
        val signature = prefs.getString(KEY_PENDING_SIGNATURE, null) ?: return null
        return AnalyticsPending(prefs.getLong(KEY_PENDING_REVISION, 0L), signature, body)
    }

    /** Фиксирует ревизию и тело до сетевого вызова: при сбое повтор уйдёт с теми же данными. */
    @Synchronized
    fun savePending(pending: AnalyticsPending) {
        prefs.edit()
            .putLong(KEY_REVISION, pending.revision)
            .putLong(KEY_PENDING_REVISION, pending.revision)
            .putString(KEY_PENDING_SIGNATURE, pending.signature)
            .putString(KEY_PENDING_BODY, pending.body)
            .commit()
    }

    @Synchronized
    fun markSent(signature: String, nowMillis: Long) {
        prefs.edit()
            .remove(KEY_PENDING_REVISION)
            .remove(KEY_PENDING_SIGNATURE)
            .remove(KEY_PENDING_BODY)
            .putString(KEY_SENT_SIGNATURE, signature)
            .putLong(KEY_SENT_AT, nowMillis)
            .commit()
    }

    fun clearPending() {
        prefs.edit()
            .remove(KEY_PENDING_REVISION)
            .remove(KEY_PENDING_SIGNATURE)
            .remove(KEY_PENDING_BODY)
            .apply()
    }

    fun sentSignature(): String? = prefs.getString(KEY_SENT_SIGNATURE, null)

    fun sentAt(): Long = prefs.getLong(KEY_SENT_AT, 0L)

    private fun newCredential(): String {
        val bytes = ByteArray(CREDENTIAL_BYTES)
        SecureRandom().nextBytes(bytes)
        // 32 байта в base64url без паддинга дают ровно 43 символа, как требует сервер.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        const val PREFS = "analytics"
        const val CREDENTIAL_BYTES = 32
        const val KEY_ENABLED = "enabled"
        const val KEY_PENDING_DELETION = "pending_deletion"
        const val KEY_BLOCKED = "blocked"
        const val KEY_ACCOUNT_IDS = "account_ids"
        const val KEY_INSTALLATION_ID = "installation_id"
        const val KEY_CREDENTIAL = "credential"
        const val KEY_REVISION = "revision"
        const val KEY_PENDING_REVISION = "pending_revision"
        const val KEY_PENDING_SIGNATURE = "pending_signature"
        const val KEY_PENDING_BODY = "pending_body"
        const val KEY_SENT_SIGNATURE = "sent_signature"
        const val KEY_SENT_AT = "sent_at"
    }
}
