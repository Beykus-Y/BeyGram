package com.beykus.beygram.data.update

/**
 * Deduplicates taps on the "update available" notification.
 *
 * A tap is identified by the Intent data URI, which carries the package, the version code and the notification post
 * time. A re-delivered Intent of an already processed notification is therefore ignored, while a genuinely new tap (a
 * later post, another version) is accepted. The processed URI survives Activity recreation through saved state.
 *
 * Pure logic on purpose: the rules are tested on the JVM without an Activity or the Android framework.
 */
internal class UpdateIntentGate(initialHandledData: String? = null) {
    var handledData: String? = initialHandledData
        private set

    /**
     * Returns true when this tap should open the Updates screen. [action] and [data] come from the delivered Intent;
     * nothing else of the Intent is needed, so an old Intent cannot smuggle state through.
     */
    /** Replaces the processed URI when an Activity is recreated from saved state. */
    fun restore(data: String?) {
        if (data != null) handledData = data
    }

    fun accept(action: String?, data: String?): Boolean {
        if (action != UpdateNotifications.ACTION_OPEN_UPDATES) return false
        val uri = data.orEmpty()
        if (uri.isNotEmpty() && uri == handledData) return false
        if (uri.isNotEmpty()) handledData = uri
        return true
    }
}