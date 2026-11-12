package com.beykus.beygram.deeplink

import android.content.Context
import com.beykus.beygram.data.music.provider.MusicProviderState
import com.beykus.beygram.data.music.provider.MusicProviders
import java.net.URI

/**
 * What a Yandex Music address opens inside the app. Only the forms the app can open natively are listed. Every other
 * address (home, charts, radio, artists, playlists until they are supported) stays a plain web link in the browser.
 */
sealed interface YandexMusicTarget {
    /** `/album/<id>`: the album screen. */
    data class Album(val albumId: String) : YandexMusicTarget

    /** `/track/<id>` or `/album/<id>/track/<id>`: the track starts playing. [albumId] is kept when the address has one. */
    data class Track(val trackId: String, val albumId: String?) : YandexMusicTarget
}

private val YANDEX_MUSIC_HOSTS = setOf(
    "music.yandex.ru",
    "music.yandex.com",
    "music.yandex.by",
    "music.yandex.kz",
    "music.yandex.uz"
)

private val NUMERIC_ID = Regex("[0-9]{1,20}")

/**
 * Parses a Yandex Music web address. The query and the fragment (`utm_*` tags, share markers) do not change the target,
 * so a link with or without them opens the same screen. Null when the address is not one of the supported forms.
 */
fun yandexMusicTarget(raw: String): YandexMusicTarget? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    val normalized = if (value.contains("://")) value else "https://$value"
    val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    if (uri.userInfo != null) return null
    val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
    if (host !in YANDEX_MUSIC_HOSTS) return null
    val parts = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
    return when {
        parts.size == 2 && parts[0] == "album" && parts[1].isNumericId() -> YandexMusicTarget.Album(parts[1])
        parts.size == 4 && parts[0] == "album" && parts[2] == "track" &&
            parts[1].isNumericId() && parts[3].isNumericId() -> YandexMusicTarget.Track(parts[3], parts[1])
        parts.size == 2 && parts[0] == "track" && parts[1].isNumericId() -> YandexMusicTarget.Track(parts[1], null)
        else -> null
    }
}

private fun String.isNumericId(): Boolean = NUMERIC_ID.matches(this)

/** The link entity of a Yandex Music address the app can open natively, or null. [url] is the address the browser opens. */
fun yandexMusicEntityLink(url: String): MessageEntityLink.YandexMusic? =
    yandexMusicTarget(url)?.let { MessageEntityLink.YandexMusic(url.trim(), it) }

/**
 * Whether a tap may open a Yandex Music link inside the app: the DEV build with a signed-in account. Everywhere else
 * (Stable, signed out, no provider) the link keeps opening in the browser, as before.
 */
fun yandexMusicNativeReady(context: Context): Boolean =
    MusicProviders.yandex(context)?.state?.value is MusicProviderState.SignedIn
