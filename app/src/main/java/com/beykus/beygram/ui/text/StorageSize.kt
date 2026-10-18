package com.beykus.beygram.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.beykus.beygram.R

private const val KILOBYTE = 1024L
private const val MEGABYTE = 1024L * KILOBYTE
private const val GIGABYTE = 1024L * MEGABYTE

/** Размер для людей: «512 Б», «480 КБ», «240 МБ», «5,2 ГБ». Единицы берутся из строк, поэтому переводятся вместе с приложением. */
@Composable
internal fun storageSizeText(bytes: Long): String = when {
    bytes >= GIGABYTE -> stringResource(R.string.storage_size_gigabytes, "%.1f".format(bytes.toDouble() / GIGABYTE))
    bytes >= MEGABYTE -> stringResource(R.string.storage_size_megabytes, (bytes / MEGABYTE).toString())
    bytes >= KILOBYTE -> stringResource(R.string.storage_size_kilobytes, (bytes / KILOBYTE).toString())
    else -> stringResource(R.string.storage_size_bytes, bytes.toString())
}
