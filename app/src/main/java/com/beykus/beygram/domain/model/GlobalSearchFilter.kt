package com.beykus.beygram.domain.model

/** Тип чата для глобального поиска: соответствует фильтрам chat type в TDLib SearchMessages. */
enum class GlobalChatType { Private, Group, Channel }

/** Период поиска: показываются сообщения не старше указанного срока. */
enum class GlobalSearchPeriod(private val seconds: Long?) {
    Any(null),
    Week(7 * 24 * 60 * 60L),
    Month(30 * 24 * 60 * 60L);

    /** Нижняя граница даты (секунды Unix) для TDLib или null, если период не ограничен. */
    fun minDate(nowSeconds: Long): Int? = seconds?.let { (nowSeconds - it).coerceAtLeast(0L).toInt() }
}

/**
 * Фильтры глобального поиска. Значения по умолчанию означают «без фильтра».
 * Поиска по отправителю здесь нет: TDLib SearchMessages его не принимает.
 */
data class GlobalSearchFilter(
    val kind: SharedMediaKind? = null,
    val chatType: GlobalChatType? = null,
    val period: GlobalSearchPeriod = GlobalSearchPeriod.Any,
)
