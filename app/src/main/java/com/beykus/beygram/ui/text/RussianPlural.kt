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

/**
 * Форма числа по языку интерфейса: русские правила для ru/uk/be, во французском 0 и 1 — «one»,
 * в остальных языках (en, de, es) «one» только для 1, иначе «many».
 */
internal fun <T> pluralForm(language: String, count: Int, one: T, few: T, many: T): T = when (language) {
    "ru", "uk", "be" -> russianPlural(count, one, few, many)
    "fr" -> if (count == 0 || count == 1) one else many
    else -> if (count == 1) one else many
}
