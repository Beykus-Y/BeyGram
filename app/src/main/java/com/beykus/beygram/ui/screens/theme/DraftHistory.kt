package com.beykus.beygram.ui.screens.theme

import androidx.compose.runtime.mutableStateListOf

/**
 * Earlier states of an editor draft, oldest first. Undo returns the newest one. Once [limit] states are kept, the oldest is dropped.
 */
internal class DraftHistory<T>(private val limit: Int = DEFAULT_LIMIT) {
    private val steps = mutableStateListOf<T>()

    /** Whether Undo has a state to return to. Reading it during composition updates the button when a state is added or removed. */
    val canUndo: Boolean get() = steps.isNotEmpty()

    /** Keeps [state] as the one that the next [undo] returns to. */
    fun checkpoint(state: T) {
        steps.add(state)
        if (steps.size > limit) steps.removeAt(0)
    }

    /** Removes the newest state and returns it, or returns null when there is none. */
    fun undo(): T? = if (steps.isEmpty()) null else steps.removeAt(steps.lastIndex)

    companion object {
        /** Enough steps for a long editing session. Each one is a small immutable config. */
        const val DEFAULT_LIMIT = 50
    }
}
