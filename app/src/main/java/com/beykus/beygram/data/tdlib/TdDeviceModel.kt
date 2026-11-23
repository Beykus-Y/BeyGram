package com.beykus.beygram.data.tdlib

import java.util.Locale

/**
 * Builds the Telegram session `device_model` string, unified across the app as
 * `BG Android (<version>) <device>`, e.g. `BG Android (0.0.6.1-dev) Samsung SM-M215F`.
 *
 * Pure Kotlin with no Android types, so it can be unit tested on the JVM.
 */
internal object TdDeviceModel {
    const val PREFIX = "BG Android"

    /**
     * Telegram servers truncate or reject overly long device_model values, so the whole
     * string is kept within this many characters. Only the device part is shortened to fit.
     */
    const val MAX_LENGTH = 64

    private const val UNKNOWN = "unknown"
    private val WHITESPACE = Regex("\\s+")

    /**
     * Returns the device part only: `"<Manufacturer> <Model>"`, without repeating the
     * manufacturer when the model already starts with it. Returns "" when nothing is known.
     */
    fun device(manufacturer: String?, model: String?): String {
        val maker = capitalize(normalize(manufacturer))
        val name = normalize(model)
        if (maker.isNotEmpty() && name.startsWith(maker, ignoreCase = true) &&
            (name.length == maker.length || name[maker.length] == ' ')
        ) {
            return name
        }
        return listOf(maker, name).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * Returns `BG Android (<version>) <device>` limited to [MAX_LENGTH] characters.
     * If the device part does not fit, it is shortened; if no room is left, only the head is returned.
     */
    fun build(versionName: String, manufacturer: String?, model: String?): String {
        val version = collapse(versionName)
        val head = if (version.isEmpty()) PREFIX else "$PREFIX ($version)"
        val dev = device(manufacturer, model)
        if (dev.isEmpty()) return head

        val full = "$head $dev"
        if (full.length <= MAX_LENGTH) return full

        val available = MAX_LENGTH - head.length - 1
        if (available <= 0) return head
        return "$head ${dev.take(available).trimEnd()}"
    }

    private fun collapse(value: String?): String =
        value?.trim()?.replace(WHITESPACE, " ").orEmpty()

    private fun normalize(value: String?): String {
        val collapsed = collapse(value)
        return if (collapsed.equals(UNKNOWN, ignoreCase = true)) "" else collapsed
    }

    private fun capitalize(value: String): String =
        if (value.isEmpty()) value
        else value.substring(0, 1).uppercase(Locale.ROOT) + value.substring(1)
}
