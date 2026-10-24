package com.beykus.beygram.deeplink

import android.content.Context
import com.beykus.beygram.data.tdlib.TdAccountStorageLayout

/**
 * A link that arrived before its account was ready. [accountId] is filled once the app knows which signed-in
 * account will handle it. A null account is still unbound and defaults to the currently chosen account.
 */
data class PendingTelegramDeepLink(
    val raw: String,
    val token: Long,
    val accountId: String? = null
) {
    override fun toString(): String = "PendingTelegramDeepLink(token=$token, account=$accountId)"
}

/** Storage boundary kept small so the pending-link rules are testable without Android. */
internal interface PendingDeepLinkStorage {
    fun read(): PendingTelegramDeepLink?
    fun write(pending: PendingTelegramDeepLink)
    fun clear()
}

/**
 * Process-independent inbox for an external Telegram link.
 *
 * Only validated, actionable links are stored. A malformed intent therefore cannot erase a valid pending link, and
 * tg://login is never written because its token must not survive outside TDLib's QR flow. Handling is token-scoped:
 * a late result for an old link cannot clear a newer one.
 */
internal class PendingDeepLinkInbox(private val storage: PendingDeepLinkStorage) {
    @Synchronized
    fun offer(raw: String?, token: Long): PendingTelegramDeepLink? {
        if (token <= 0L) return null
        val value = raw ?: return restore()
        val parsed = TelegramDeepLinkParser.parse(value) ?: return restore()
        if (!parsed.isActionable()) return restore()
        val pending = PendingTelegramDeepLink(raw = value, token = token)
        storage.write(pending)
        return pending
    }

    @Synchronized
    fun restore(): PendingTelegramDeepLink? = sanitize(storage.read())

    /** Bind an unbound link to the account that is about to handle it. */
    @Synchronized
    fun bind(token: Long, accountId: String): PendingTelegramDeepLink? {
        if (!validAccountId(accountId)) return null
        val current = sanitize(storage.read()) ?: return null
        if (current.token != token) return null
        // Once bound, a later Activity cannot move the same link to another account.
        if (current.accountId != null && current.accountId != accountId) return current
        val bound = current.copy(accountId = accountId)
        storage.write(bound)
        return bound
    }

    /** Clear a handled link only when it is still the link named by [token]. */
    @Synchronized
    fun take(token: Long, accountId: String): PendingTelegramDeepLink? {
        val current = sanitize(storage.read()) ?: return null
        if (current.token != token) return null
        // An unbound link defaults to legacy, never to whichever secondary account asks first.
        val boundAccount = current.accountId ?: TdAccountStorageLayout.LEGACY_ACCOUNT_ID
        if (boundAccount != accountId) return null
        storage.clear()
        return current
    }

    @Synchronized
    fun clear() = storage.clear()

    private fun sanitize(pending: PendingTelegramDeepLink?): PendingTelegramDeepLink? {
        if (pending == null || pending.token <= 0L) return null
        val link = TelegramDeepLinkParser.parse(pending.raw) ?: return null
        if (!link.isActionable()) return null
        if (pending.accountId != null && !validAccountId(pending.accountId)) return null
        return pending
    }

    private fun validAccountId(accountId: String): Boolean =
        accountId == TdAccountStorageLayout.LEGACY_ACCOUNT_ID ||
            ACCOUNT_ID.matches(accountId)

    private companion object {
        val ACCOUNT_ID = Regex("[a-zA-Z0-9_-]{1,64}")
    }
}

/** App-private persistence for a link that must survive a process restart during login. */
internal class SharedPreferencesPendingDeepLinkStorage(context: Context) : PendingDeepLinkStorage {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(): PendingTelegramDeepLink? {
        val raw = preferences.getString(KEY_RAW, null) ?: return null
        val token = preferences.getLong(KEY_TOKEN, 0L)
        if (token <= 0L) return null
        return PendingTelegramDeepLink(
            raw = raw,
            token = token,
            accountId = preferences.getString(KEY_ACCOUNT, null)
        )
    }

    override fun write(pending: PendingTelegramDeepLink) {
        preferences.edit()
            .putString(KEY_RAW, pending.raw)
            .putLong(KEY_TOKEN, pending.token)
            .putString(KEY_ACCOUNT, pending.accountId)
            .apply()
    }

    override fun clear() {
        preferences.edit().remove(KEY_RAW).remove(KEY_TOKEN).remove(KEY_ACCOUNT).apply()
    }

    private companion object {
        const val PREFS_NAME = "telegram_deep_links"
        const val KEY_RAW = "pending_raw"
        const val KEY_TOKEN = "pending_token"
        const val KEY_ACCOUNT = "pending_account"
    }
}
