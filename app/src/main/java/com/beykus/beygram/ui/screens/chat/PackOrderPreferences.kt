package com.beykus.beygram.ui.screens.chat

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.beyGramPalette

/** Sticker packs and custom emoji packs keep separate usage, but share the opt-in mode. */
internal enum class PackKind(val storageKey: String) {
    Sticker("sticker"),
    CustomEmoji("emoji")
}

/**
 * Per-account pack usage and the optional usage-based ordering. The server/manual order stays the base order and the
 * tiebreaker, so enabling the mode without usage does not reorder anything and equal usage never jumps between packs.
 * Usage is applied when the panel opens; recording a send does not reshuffle the list under the user's finger.
 */
internal class PackUsageStore(
    context: Context,
    accountId: String,
    private val kind: PackKind,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val dynamicKey = "pack_order_dynamic_$accountId"
    private val countsKey = "pack_usage_${kind.storageKey}_$accountId"
    private val usedAtKey = "pack_used_at_${kind.storageKey}_$accountId"

    /** On by default, as agreed for J2.1; the switch restores the server/manual order. */
    fun isDynamic(): Boolean = runCatching { preferences.getBoolean(dynamicKey, true) }.getOrDefault(true)

    fun setDynamic(enabled: Boolean) {
        runCatching { preferences.edit().putBoolean(dynamicKey, enabled).apply() }
    }

    fun counts(): Map<Long, Long> = decode(preferences.getString(countsKey, null).orEmpty())

    fun lastUsed(): Map<Long, Long> = decode(preferences.getString(usedAtKey, null).orEmpty())

    /** One sent sticker or inserted custom emoji counts as usage of its pack. */
    fun record(setId: Long) {
        if (setId == 0L) return
        val counts = counts().toMutableMap()
        counts[setId] = (counts[setId] ?: 0L) + 1L
        if (counts.size > MAX_TRACKED_PACKS) {
            // Keep the store bounded on accounts with many temporary/recommended packs.
            val keep = counts.entries.sortedWith(compareByDescending<Map.Entry<Long, Long>> { it.value }.thenBy { it.key })
                .take(MAX_TRACKED_PACKS)
            counts.clear()
            keep.forEach { counts[it.key] = it.value }
        }
        val usedAt = lastUsed().toMutableMap()
        usedAt[setId] = now()
        if (usedAt.size > MAX_TRACKED_PACKS) {
            val keep = usedAt.entries.sortedByDescending { it.value }.take(MAX_TRACKED_PACKS)
            usedAt.clear()
            keep.forEach { usedAt[it.key] = it.value }
        }
        runCatching {
            preferences.edit()
                .putString(countsKey, encode(counts))
                .putString(usedAtKey, encode(usedAt))
                .apply()
        }
    }

    /**
     * Usage order with the incoming (server/manual) order as the stable base. Disabled mode and fewer than two packs
     * return the list untouched, so loading never reorders anything by itself.
     */
    fun <T> ordered(items: List<T>, idOf: (T) -> Long): List<T> {
        if (!isDynamic() || items.size < 2) return items
        val counts = counts()
        val usedAt = lastUsed()
        return items.sortedWith(
            compareByDescending<T> { counts[idOf(it)] ?: 0L }
                .thenByDescending { usedAt[idOf(it)] ?: 0L }
        )
    }

    private fun decode(raw: String): Map<Long, Long> {
        if (raw.isBlank()) return emptyMap()
        val result = HashMap<Long, Long>()
        raw.split(ENTRY_SEPARATOR).forEach { entry ->
            val id = entry.substringBefore(VALUE_SEPARATOR).toLongOrNull() ?: return@forEach
            val value = entry.substringAfter(VALUE_SEPARATOR, "").toLongOrNull() ?: return@forEach
            if (id > 0L && value >= 0L) result[id] = value
        }
        return result
    }

    private fun encode(values: Map<Long, Long>): String =
        values.entries.joinToString(ENTRY_SEPARATOR.toString()) { "${it.key}$VALUE_SEPARATOR${it.value}" }

    private companion object {
        const val PREFERENCES = "emoji_panel"
        const val ENTRY_SEPARATOR = ','
        const val VALUE_SEPARATOR = ':'
        const val MAX_TRACKED_PACKS = 200
    }
}

/** One switch for both sticker and custom emoji packs; the usage lists stay separate per kind. */
@Composable
internal fun PackOrderDialog(
    dynamic: Boolean,
    onToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = beyGramPalette()
    BeyGramDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.pack_order_title),
        content = {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pack_order_dynamic), color = palette.textPrimary, fontSize = 15.sp)
                    Text(
                        stringResource(R.string.pack_order_dynamic_hint),
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Switch(checked = dynamic, onCheckedChange = onToggle)
            }
        },
        confirmText = stringResource(R.string.common_close),
        onConfirm = onDismiss
    )
}