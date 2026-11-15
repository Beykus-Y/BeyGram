package com.beykus.beygram.data.yandex

import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.ProviderResult
import org.json.JSONObject
import java.io.IOException

/** Code the user types on the Yandex page. The app never sees the user's password. */
internal data class YandexDeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresInSec: Int,
    val intervalSec: Int,
)

internal data class YandexTokens(val accessToken: String, val refreshToken: String?, val expiresAtMs: Long)

internal sealed interface YandexPollOutcome {
    data class Tokens(val tokens: YandexTokens) : YandexPollOutcome
    object Pending : YandexPollOutcome
    object SlowDown : YandexPollOutcome
    data class Failed(val failure: MusicProviderFailure) : YandexPollOutcome
}

/** OAuth device flow against the Yandex OAuth service. */
internal class YandexAuth(private val http: YandexHttp, private val now: () -> Long = { System.currentTimeMillis() }) {

    fun requestDeviceCode(deviceId: String): ProviderResult<YandexDeviceCode> {
        val response = try {
            http.post(
                "${YandexConfig.OAUTH_BASE}/device/code",
                mapOf("client_id" to YandexConfig.clientId, "device_id" to deviceId, "device_name" to DEVICE_NAME),
            )
        } catch (e: IOException) {
            return ProviderResult.Failure(MusicProviderFailure.Network)
        }
        if (response.code !in 200..299) return ProviderResult.Failure(failureForCode(response.code))
        val json = parse(response.body) ?: return ProviderResult.Failure(MusicProviderFailure.ServiceChanged)
        val deviceCode = json.optString("device_code").takeIf { it.isNotBlank() }
        val userCode = json.optString("user_code").takeIf { it.isNotBlank() }
        val verification = json.optString("verification_url").takeIf { it.isNotBlank() }
        if (deviceCode == null || userCode == null || verification == null) {
            return ProviderResult.Failure(MusicProviderFailure.ServiceChanged)
        }
        return ProviderResult.Success(
            YandexDeviceCode(
                deviceCode = deviceCode,
                userCode = userCode,
                verificationUrl = verification,
                expiresInSec = json.optInt("expires_in", 300),
                intervalSec = json.optInt("interval", 5).coerceAtLeast(1),
            )
        )
    }

    /** One poll of the token endpoint. Call again after the interval while the outcome is Pending or SlowDown. */
    fun pollToken(deviceCode: String): YandexPollOutcome {
        val response = try {
            http.post(
                "${YandexConfig.OAUTH_BASE}/token",
                mapOf(
                    "grant_type" to "device_code",
                    "code" to deviceCode,
                    "client_id" to YandexConfig.clientId,
                    "client_secret" to YandexConfig.clientSecret,
                ),
            )
        } catch (e: IOException) {
            return YandexPollOutcome.Failed(MusicProviderFailure.Network)
        }
        if (response.code in 200..299) {
            return tokensFrom(response.body)?.let { YandexPollOutcome.Tokens(it) }
                ?: YandexPollOutcome.Failed(MusicProviderFailure.ServiceChanged)
        }
        return when (parse(response.body)?.optString("error")) {
            "authorization_pending" -> YandexPollOutcome.Pending
            "slow_down" -> YandexPollOutcome.SlowDown
            "access_denied" -> YandexPollOutcome.Failed(MusicProviderFailure.Unknown)
            "expired_token" -> YandexPollOutcome.Failed(MusicProviderFailure.SessionExpired)
            else -> YandexPollOutcome.Failed(failureForCode(response.code))
        }
    }

    fun refresh(refreshToken: String): ProviderResult<YandexTokens> {
        val response = try {
            http.post(
                "${YandexConfig.OAUTH_BASE}/token",
                mapOf(
                    "grant_type" to "refresh_token",
                    "refresh_token" to refreshToken,
                    "client_id" to YandexConfig.clientId,
                    "client_secret" to YandexConfig.clientSecret,
                ),
            )
        } catch (e: IOException) {
            return ProviderResult.Failure(MusicProviderFailure.Network)
        }
        if (response.code in 200..299) {
            val tokens = tokensFrom(response.body) ?: return ProviderResult.Failure(MusicProviderFailure.ServiceChanged)
            return ProviderResult.Success(tokens)
        }
        // A refused refresh token means the user must sign in again.
        return ProviderResult.Failure(
            if (response.code in 400..403) MusicProviderFailure.SessionExpired else failureForCode(response.code)
        )
    }

    private fun tokensFrom(body: String): YandexTokens? {
        val json = parse(body) ?: return null
        val access = json.optString("access_token").takeIf { it.isNotBlank() } ?: return null
        val refresh = json.optString("refresh_token").takeIf { it.isNotBlank() }
        val expiresIn = json.optLong("expires_in", DEFAULT_TOKEN_LIFETIME_SEC)
        return YandexTokens(access, refresh, now() + expiresIn * 1_000L)
    }

    private fun parse(body: String): JSONObject? = try {
        JSONObject(body)
    } catch (e: org.json.JSONException) {
        null
    }

    companion object {
        private const val DEVICE_NAME = "BeyGram"
        /** Used only if the service omits expires_in. Tokens last about a year. */
        private const val DEFAULT_TOKEN_LIFETIME_SEC = 30L * 24 * 3600

        fun failureForCode(code: Int): MusicProviderFailure = when (code) {
            401 -> MusicProviderFailure.SessionExpired
            403 -> MusicProviderFailure.Unavailable
            404 -> MusicProviderFailure.NotFound
            429 -> MusicProviderFailure.RateLimited
            in 500..599 -> MusicProviderFailure.Unavailable
            else -> MusicProviderFailure.Unknown
        }
    }
}
