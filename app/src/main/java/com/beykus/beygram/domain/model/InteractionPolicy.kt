package com.beykus.beygram.domain.model

/** Keep privacy decisions independent from message loading, rendering and transport. */
enum class InteractionEffect { ReadHistory, OpenContent, OnlinePresence, Typing, StoryView }

/** Stealth mode refused [effect]. A failure of this type is a decision, not a connection problem. */
class StealthBlockedException(val effect: InteractionEffect) : IllegalStateException("Blocked by stealth mode: $effect")

fun interface InteractionPolicy {
    fun allows(effect: InteractionEffect, chatId: Long?): Boolean

    companion object {
        val Standard = InteractionPolicy { _, _ -> true }
    }
}

/** Can be backed by account preferences and per-chat overrides without changing screens. */
class ConfigurableInteractionPolicy(
    private val account: () -> Set<InteractionEffect>,
    private val chatOverride: (Long) -> Set<InteractionEffect>? = { null }
) : InteractionPolicy {
    override fun allows(effect: InteractionEffect, chatId: Long?): Boolean =
        effect in (chatId?.let(chatOverride) ?: account())
}
