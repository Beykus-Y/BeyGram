package com.beykus.beygram.ui.text

import android.content.Context
import androidx.annotation.StringRes
import com.beykus.beygram.domain.UserMessageException

/** Текст ошибки для показа: сообщение [UserMessageException] или запасная строка экрана. */
internal fun Throwable?.userMessage(context: Context, @StringRes fallback: Int): String =
    (this as? UserMessageException)?.text?.resolve(context) ?: context.getString(fallback)
