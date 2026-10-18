package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap

/** Область общих настроек уведомлений: личные чаты, группы и каналы. */
internal enum class NotificationScope {
    Private, Group, Channel;

    fun toTd(): TdApi.NotificationSettingsScope = when (this) {
        Private -> TdApi.NotificationSettingsScopePrivateChats()
        Group -> TdApi.NotificationSettingsScopeGroupChats()
        Channel -> TdApi.NotificationSettingsScopeChannelChats()
    }

    companion object {
        fun ofChat(type: TdApi.ChatType): NotificationScope = when (type) {
            is TdApi.ChatTypeBasicGroup -> Group
            is TdApi.ChatTypeSupergroup -> if (type.isChannel) Channel else Group
            else -> Private
        }

        fun ofScope(scope: TdApi.NotificationSettingsScope): NotificationScope = when (scope) {
            is TdApi.NotificationSettingsScopeGroupChats -> Group
            is TdApi.NotificationSettingsScopeChannelChats -> Channel
            else -> Private
        }
    }
}

/**
 * Заглушён ли чат. Если у чата нет своей настройки звука, действует общая настройка его области.
 * TDLib не присылает обновление каждого чата при смене общей настройки, поэтому общие значения хранятся здесь.
 */
internal class ChatMuteState {
    private val scopeMuted = ConcurrentHashMap<NotificationScope, Boolean>()
    private val chats = ConcurrentHashMap<Long, ChatMute>()

    fun putChat(chatId: Long, scope: NotificationScope, settings: TdApi.ChatNotificationSettings?) {
        chats[chatId] = ChatMute(scope, useDefault = settings?.useDefaultMuteFor == true, muteFor = settings?.muteFor ?: 0)
    }

    /** Меняет только известный чат: чат без записи ещё не загружен, его зададут через [putChat]. */
    fun updateChat(chatId: Long, settings: TdApi.ChatNotificationSettings) {
        chats.computeIfPresent(chatId) { _, old -> old.copy(useDefault = settings.useDefaultMuteFor, muteFor = settings.muteFor) }
    }

    fun isMuted(chatId: Long): Boolean = chats[chatId]?.isMuted(scopeMuted) == true

    /** Известна ли заглушённость чата: своя настройка известна сразу, настройка по умолчанию — когда прочитана её область. */
    fun isKnown(chatId: Long): Boolean = chats[chatId]?.let { !it.useDefault || scopeMuted.containsKey(it.scope) } == true

    /** Запоминает общую настройку области и возвращает чаты этой области, которые её используют. */
    fun updateScope(scope: NotificationScope, settings: TdApi.ScopeNotificationSettings): List<Long> {
        scopeMuted[scope] = settings.muteFor > 0
        return chats.filterValues { it.scope == scope && it.useDefault }.keys.toList()
    }

    private data class ChatMute(val scope: NotificationScope, val useDefault: Boolean, val muteFor: Int) {
        fun isMuted(scopeMuted: Map<NotificationScope, Boolean>): Boolean =
            if (useDefault) scopeMuted[scope] ?: (muteFor > 0) else muteFor > 0
    }
}
