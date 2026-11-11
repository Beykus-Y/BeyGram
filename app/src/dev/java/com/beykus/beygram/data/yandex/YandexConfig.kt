package com.beykus.beygram.data.yandex

import com.beykus.beygram.BuildConfig

/**
 * Everything that identifies this client to Yandex sits in this one file, so replacing it is a one-place change.
 * DEV flavor only. The OAuth pair is read from secrets.properties (yandexMusicClientId / yandexMusicClientSecret);
 * when it is blank the provider reports itself unavailable and the sign-in entry stays hidden.
 */
internal object YandexConfig {
    const val OAUTH_BASE = "https://oauth.yandex.ru"
    const val API_BASE = "https://api.music.yandex.net"

    /** Header values the music service expects from a client. Kept identical to the public protocol notes. */
    const val CLIENT_HEADER = "YandexMusicAndroid/24023621"
    const val USER_AGENT = "Yandex-Music-Android/5.0"

    /** Salt of the legacy download link signature (download-info XML). */
    const val DOWNLOAD_SIGN_SALT = "XGRlBW9FXlekgbPrRHuSiA"

    /** Minimum gap between two requests from this app to the service. */
    const val MIN_REQUEST_GAP_MS = 250L

    /** Pause when the service answers 429 without a Retry-After header. */
    const val DEFAULT_RETRY_AFTER_MS = 5_000L

    const val CONNECT_TIMEOUT_MS = 10_000
    const val READ_TIMEOUT_MS = 15_000

    /** Only the first page of search results is used; the UI shows a fixed number of rows. */
    const val SEARCH_PAGE_SIZE = 20

    /** Track lists are requested in chunks of this size. */
    const val TRACK_CHUNK = 100

    val clientId: String get() = BuildConfig.YANDEX_CLIENT_ID
    val clientSecret: String get() = BuildConfig.YANDEX_CLIENT_SECRET

    val isConfigured: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()
}
