package com.beykus.beygram.ui.screens.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.beykus.beygram.R

internal fun movePickerPack(order: List<Long>, setId: Long, offset: Int): List<Long>? {
    val index = order.indexOf(setId)
    val next = index + offset
    if (index < 0 || next !in order.indices) return null
    return order.toMutableList().apply { add(next, removeAt(index)) }
}

@Composable
internal fun PickerPackOrderButtons(order: List<Long>, setId: Long, busy: Boolean, onReorder: (List<Long>) -> Unit) {
    val index = order.indexOf(setId)
    if (index < 0 || order.size < 2) return
    IconButton(enabled = !busy && index > 0, onClick = { movePickerPack(order, setId, -1)?.let(onReorder) }) {
        Icon(Icons.Rounded.ArrowUpward, contentDescription = stringResource(R.string.folder_move_up))
    }
    IconButton(enabled = !busy && index < order.lastIndex, onClick = { movePickerPack(order, setId, 1)?.let(onReorder) }) {
        Icon(Icons.Rounded.ArrowDownward, contentDescription = stringResource(R.string.folder_move_down))
    }
}
