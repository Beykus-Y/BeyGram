package com.beykus.beygram.data.music.provider

import android.content.Context

/**
 * Starts a radio of the music source from one of its tracks. A source that has radios sets [fromTrack] when it is
 * created; the shared menus show the entry only while it is set. Null in builds without such a source.
 */
object ProviderWaves {
    @Volatile
    var fromTrack: ((Context, ProviderTrack) -> Unit)? = null
}
