package com.beykus.beygram.diagnostics.report

import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/**
 * Генератор одноразовых значений отчёта.
 *
 * Nonce создаётся только на устройстве: 32 случайных байта из [SecureRandom],
 * Base64URL без padding — ровно 43 символа, как требует сервер
 * (`app/security.py`: 256 бит, строгая проверка формы).
 * Каждый новый отчёт получает новый nonce.
 */
object ReportNonce {

    private const val NONCE_BYTES = 32
    private const val NONCE_LENGTH = 43

    private val secureRandom = SecureRandom()

    /** 43-символьный URL-safe unpadded nonce; аргумент позволяет подменить источник в тестах. */
    fun newNonce(random: SecureRandom = secureRandom): String {
        val bytes = ByteArray(NONCE_BYTES)
        random.nextBytes(bytes)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        check(encoded.length == NONCE_LENGTH) { "Nonce must be $NONCE_LENGTH characters" }
        return encoded
    }

    /** Каноническая строка UUID v4 — идентификатор отчёта. */
    fun newReportId(): String = UUID.randomUUID().toString()
}
