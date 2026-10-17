package com.beykus.beygram.ui.text

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.beykus.beygram.domain.UiText

/** Готовая строка для показа вне композиции, например в обработчиках. */
internal fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Res -> if (args.isEmpty()) context.getString(id) else context.getString(id, *args.toTypedArray())
    is UiText.Raw -> text
}

/** Готовая строка для показа в композиции. */
@Composable
internal fun UiText.resolve(): String = when (this) {
    is UiText.Res -> if (args.isEmpty()) stringResource(id) else stringResource(id, *args.toTypedArray())
    is UiText.Raw -> text
}
