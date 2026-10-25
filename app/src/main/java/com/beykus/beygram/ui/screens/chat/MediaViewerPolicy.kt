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
}
