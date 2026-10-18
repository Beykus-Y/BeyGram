package com.beykus.beygram.notifications

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Token of a reply action. It covers the routing values, so a holder of the action that replaces one of them
 * (for example the chat) cannot make a token that matches the changed values.
 */
internal fun replyRoutingToken(secret: String, chatId: Long, groupId: Int, maxId: Int, replyToMessageId: Long): String {
    val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")) }
    return mac.doFinal("$chatId/$groupId/$maxId/$replyToMessageId".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
