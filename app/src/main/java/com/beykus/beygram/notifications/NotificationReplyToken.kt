package com.beykus.beygram.notifications

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Token of a reply action. It covers the routing values, so a holder of the action that replaces one of them
 * (for example the chat) cannot make a token that matches the changed values.
 */
internal fun replyRoutingToken(secret: String, chatId: Long, groupId: Int, maxId: Int, replyToMessageId: Long, topicId: Long = 0L): String {
    val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")) }
    // Without a topic the routing text stays as it was, so a reply in a chat that is not a forum keeps its token.
    val routing = "$chatId/$groupId/$maxId/$replyToMessageId" + if (topicId != 0L) "/$topicId" else ""
    return mac.doFinal(routing.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
