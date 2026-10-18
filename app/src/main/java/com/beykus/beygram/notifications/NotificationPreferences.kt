package com.beykus.beygram.notifications

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationPreferences(val enabled: Boolean = true, val background: Boolean = true, val previews: Boolean = true)

class NotificationPreferencesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("notifications", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state = _state.asStateFlow()
    @Synchronized internal fun intentToken(): String = preferences.getString("intent_token", null) ?: java.util.UUID.randomUUID().toString().also { preferences.edit().putString("intent_token", it).apply() }
    @Synchronized internal fun replyToken(): String = preferences.getString("reply_token", null) ?: java.util.UUID.randomUUID().toString().also { preferences.edit().putString("reply_token", it).apply() }
    /** Retires reply actions that listeners may still hold from an earlier session; the next render posts them with the new token. */
    @Synchronized private fun rotateReplyToken() { preferences.edit().putString("reply_token", java.util.UUID.randomUUID().toString()).apply() }
    private fun read() = NotificationPreferences(preferences.getBoolean("enabled", true), preferences.getBoolean("background", true), preferences.getBoolean("previews", true))
    fun update(value: NotificationPreferences) {
        preferences.edit().putBoolean("enabled", value.enabled).putBoolean("background", value.background).putBoolean("previews", value.previews).apply()
        _state.value = value
    }
    var permissionAsked: Boolean
        get() = preferences.getBoolean("permission_asked", false)
        set(value) { preferences.edit().putBoolean("permission_asked", value).apply() }
    var authorized: Boolean
        get() = preferences.getBoolean("authorized", false)
        set(value) {
            // Sign-out retires outstanding reply actions, so a listener cannot send from them into the next account.
            if (!value && authorized) rotateReplyToken()
            preferences.edit().putBoolean("authorized", value).apply()
        }
    fun handled(groupId: Int) = preferences.getInt("handled_$groupId", 0)
    fun handled(groupId: Int, maxId: Int) { if (maxId > handled(groupId)) preferences.edit().putInt("handled_$groupId", maxId).apply() }
    fun dismissed(groupId: Int) = preferences.getInt("dismissed_$groupId", 0)
    fun dismiss(groupId: Int, maxId: Int) {
        preferences.edit().putInt("dismissed_$groupId", maxOf(dismissed(groupId), maxId)).putInt("pending_$groupId", maxOf(preferences.getInt("pending_$groupId", 0), maxId)).apply()
    }
    fun pendingDismissals(): Map<Int, Int> = preferences.all.mapNotNull { (key, value) ->
        key.removePrefix("pending_").toIntOrNull()?.takeIf { key.startsWith("pending_") }?.let { it to (value as? Int ?: 0) }
    }.toMap()
    fun acknowledge(groupId: Int, maxId: Int) { if (preferences.getInt("pending_$groupId", 0) <= maxId) preferences.edit().remove("pending_$groupId").apply() }
    /** Returns true when another account was stored before; its live reply actions then carry a retired token until they are re-posted. */
    fun account(id: Long): Boolean {
        val old = preferences.getLong("account", 0)
        val switched = old != 0L && old != id
        if (switched) {
            val editor = preferences.edit()
            preferences.all.keys.filter { it.startsWith("handled_") || it.startsWith("dismissed_") || it.startsWith("pending_") }.forEach(editor::remove)
            editor.apply()
            rotateReplyToken()
        }
        preferences.edit().putLong("account", id).apply()
        return switched
    }
}
