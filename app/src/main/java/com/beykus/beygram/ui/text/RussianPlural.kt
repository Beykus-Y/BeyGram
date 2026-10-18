package com.beykus.beygram.ui.text

/**
 * Русская форма числа: «1 минута», «2 минуты», «5 минут». Форму выбирает по числу, а не по локали телефона,
 * поэтому работает и с ресурсами строк, и с готовым текстом.
 */
internal fun <T> russianPlural(count: Int, one: T, few: T, many: T): T {
    val lastTwo = count % 100
    val last = count % 10
    return when {
        last == 1 && lastTwo != 11 -> one
        last in 2..4 && lastTwo !in 12..14 -> few
        else -> many
    }
}
