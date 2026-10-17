package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.MessageMedia

/**
 * Правила страниц просмотрщика. Соседние страницы компонуются заранее, пока идёт свайп, поэтому решает видимость страницы,
 * а не наличие файла: локальный путь у секретного вложения может появиться раньше, чем его открыли.
 */
internal object MediaViewerPolicy {
    /** Секретная страница, которую сейчас не видно целиком, не грузится и ничего не показывает, даже с готовым файлом. */
    fun concealed(media: MessageMedia, active: Boolean): Boolean = media.isSecret && !active

    /** Экран защищён, пока видна секретная страница. Защита следует за видимой страницей, а не за выбранным вложением. */
    fun secureWhileShowing(settled: MessageMedia): Boolean = settled.isSecret
}
