package com.beykus.beygram.navigation

/**
 * Destination after leaving Equalizer. The Equalizer screen and the global back handler both navigate to Settings
 * today; when that Settings transition arrives from Equalizer, restore the route that opened it instead.
 *
 * Settings stays the destination when Equalizer was opened from Settings (or when the stored return route is missing
 * or is Equalizer itself). This function works on route names so it can be tested without Compose or an Activity.
 */
internal fun equalizerBackRouteName(
    storedRouteName: String,
    previousRouteName: String,
    equalizerRouteName: String,
    settingsRouteName: String,
    returnRouteName: String
): String {
    val leavingEqualizer = previousRouteName == equalizerRouteName && storedRouteName == settingsRouteName
    val validReturn = returnRouteName != equalizerRouteName && returnRouteName.isNotBlank()
    return if (leavingEqualizer && validReturn) returnRouteName else storedRouteName
}
