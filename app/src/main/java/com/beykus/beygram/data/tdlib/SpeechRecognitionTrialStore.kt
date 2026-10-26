package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.SpeechRecognitionTrial
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

/**
 * Account-scoped copy of Telegram's free speech recognition quota. Every repository owns one instance, so a
 * login, logout or account switch can never mix another account's remaining attempts or reset date.
 */
internal class SpeechRecognitionTrialStore {
    private val _state = MutableStateFlow<SpeechRecognitionTrial?>(null)
    val state: StateFlow<SpeechRecognitionTrial?> = _state.asStateFlow()

    fun update(update: TdApi.UpdateSpeechRecognitionTrial) {
        _state.value = SpeechRecognitionTrial(
            weeklyCount = update.weeklyCount.coerceAtLeast(0),
            leftCount = update.leftCount.coerceAtLeast(0),
            maxMediaDurationSeconds = update.maxMediaDuration.coerceAtLeast(0),
            nextResetDateSeconds = update.nextResetDate.toLong().takeIf { it > 0L }
        )
    }

    /** No data is different from an exhausted quota, so logout/account loss clears the state to null. */
    fun clear() {
        _state.value = null
    }
}
