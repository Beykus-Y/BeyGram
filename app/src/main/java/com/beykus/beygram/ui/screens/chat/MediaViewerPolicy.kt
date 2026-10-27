package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.MessageCapabilities
import com.beykus.beygram.domain.model.MessageMedia

/** Права на сообщение, которые TDLib вернул для конкретного вложения. */
internal class MessageRights(val messageId: Long, val capabilities: MessageCapabilities)

/**
 * Правила страниц просмотрщика. Соседние страницы компонуются заранее, пока идёт свайп, поэтому решает видимость страницы,
 * а не наличие файла: локальный путь у секретного вложения может появиться раньше, чем его открыли.
 */
internal object MediaViewerPolicy {
    /** Секретная страница, которую сейчас не видно целиком, не грузится и ничего не показывает, даже с готовым файлом. */
    fun concealed(media: MessageMedia, active: Boolean): Boolean = media.isSecret && !active

    /** Экран защищён, пока видна секретная страница. Защита следует за видимой страницей, а не за выбранным вложением. */
    fun secureWhileShowing(settled: MessageMedia, viewOnce: Boolean = false): Boolean = settled.isSecret || viewOnce

    /**
     * Права действуют только для того сообщения, для которого их загрузили. Ответ на соседнее вложение, пришедший позже
     * переключения, сюда не попадает, а действия над текущим вложением закрыты, пока не придёт его собственный ответ.
     */
    fun rightsFor(messageId: Long, loaded: MessageRights?): MessageCapabilities? =
        loaded?.takeIf { it.messageId == messageId }?.capabilities

    /**
     * TDLib deletes a view-once message as soon as its content was opened. The viewer already holds that page, so keep
     * it until the user closes the viewer; otherwise the deletion would dismiss the media mid-view. The page goes back
     * to its place by message id, in the list's own direction, so neighbours keep their order while it is open.
     */
    fun retainViewOncePage(pages: List<MediaViewerItem>, retained: MediaViewerItem?): List<MediaViewerItem> {
        if (retained == null || !retained.viewOnce) return pages
        if (pages.any { it.messageId == retained.messageId }) return pages
        val descending = pages.size > 1 && pages.first().messageId > pages.last().messageId
        val index = pages.indexOfFirst {
            if (descending) it.messageId < retained.messageId else it.messageId > retained.messageId
        }.takeIf { it >= 0 } ?: pages.size
        return pages.toMutableList().apply { add(index, retained) }
    }
}
