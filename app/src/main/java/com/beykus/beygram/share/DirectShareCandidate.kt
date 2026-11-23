package com.beykus.beygram.share

import com.beykus.beygram.domain.model.ChatPreview

/**
 * Все чаты, которым можно предложить отправку: основной список, архив и папки.
 * Тот же состав, что у выбора получателя в пересылке; дубли по id убираются.
 */
internal fun shareRecipientCatalog(
    chats: List<ChatPreview>,
    archived: List<ChatPreview>,
    folders: Map<Int, List<ChatPreview>>,
): List<ChatPreview> = (chats + archived + folders.values.flatten()).distinctBy { it.id }

/**
 * Чат для предвыбора по Direct Share: только личный чат, куда можно писать.
 * Ищется во всём каталоге получателей, включая архив и папки. Ноль означает «не задан».
 */
internal fun directShareCandidate(initialChatId: Long, catalog: List<ChatPreview>): ChatPreview? =
    if (initialChatId == 0L) null
    else catalog.firstOrNull { it.id == initialChatId && it.canPost && !it.isGroup }
