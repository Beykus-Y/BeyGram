package com.beykus.beygram.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.beykus.beygram.data.vpn.VpnBackend
import com.beykus.beygram.domain.model.TelegramRoute
import com.beykus.beygram.domain.repository.TelegramRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/**
 * Проверки для экрана диагностики. Сеть трогает только запуск экрана и кнопка «Проверить снова»:
 * фоновых запросов нет.
 */

/** Сервис, который по запросу называет IP-адрес и страну выхода в интернет. */
private const val ADDRESS_SERVICE_URL = "https://ipapi.co/json/"
private const val ADDRESS_TIMEOUT_MILLIS = 10_000
private const val LATENCY_TIMEOUT_MILLIS = 5_000

/** Один из серверов дата-центров Telegram: замер идёт до него, когда прокси нет. */
private const val TELEGRAM_DC_ADDRESS = "149.154.167.51"
private const val TELEGRAM_DC_PORT = 443

internal data class PublicAddress(val ip: String, val country: String)

internal enum class NetworkKind { Wifi, Mobile, Other, None }

internal data class NetworkSnapshot(val vpnActive: Boolean, val kind: NetworkKind)

internal sealed interface LatencyCheck {
    data class Measured(val millis: Long) : LatencyCheck

    /** Замер был, но сервер не ответил. */
    data object NoAnswer : LatencyCheck

    /** Замер не делается или данных нет, например у подписки без выбранного сервера. */
    data object NotMeasured : LatencyCheck
}

internal data class ConnectionCheck(
    val address: Result<PublicAddress>,
    val network: NetworkSnapshot,
    val route: Result<TelegramRoute>,
    val latency: LatencyCheck,
)

/** Все проверки одним проходом. Ошибки возвращаются в результате, кроме отмены корутины. */
internal suspend fun runConnectionCheck(
    context: Context,
    repository: TelegramRepository,
    vpnBackend: VpnBackend
): ConnectionCheck = coroutineScope {
    val address = async { lookupPublicAddress() }
    val route = repository.currentRoute()
    val latency = measureLatency(route.getOrNull(), repository, vpnBackend)
    ConnectionCheck(
        address = address.await(),
        network = readNetworkSnapshot(context),
        route = route,
        latency = latency
    )
}

private suspend fun measureLatency(
    route: TelegramRoute?,
    repository: TelegramRepository,
    vpnBackend: VpnBackend
): LatencyCheck = when (route) {
    null -> LatencyCheck.NotMeasured
    TelegramRoute.Direct -> measureDirectTelegramLatency().fold(
        onSuccess = { LatencyCheck.Measured(it) },
        onFailure = { recordConnectionFailure(ConnectionFailure.Timeout); LatencyCheck.NoAnswer }
    )
    is TelegramRoute.Proxy -> repository.measureProxyLatency(route.server, route.port).fold(
        onSuccess = { LatencyCheck.Measured(it) },
        onFailure = { recordConnectionFailure(ConnectionFailure.Proxy); LatencyCheck.NoAnswer }
    )
    TelegramRoute.Subscription -> measureSubscriptionLatency(vpnBackend)
}

/** Замер через локальный прокси подписки ничего не скажет о сервере, поэтому проверяем выбранный сервер заново. */
private suspend fun measureSubscriptionLatency(vpnBackend: VpnBackend): LatencyCheck {
    val selected = vpnBackend.refreshConnections().getOrNull()?.firstOrNull { it.selected }
        ?: return LatencyCheck.NotMeasured
    return vpnBackend.probeConnection(selected.name).fold(
        onSuccess = { probe ->
            when {
                !probe.telegramReachable -> LatencyCheck.NoAnswer
                else -> probe.delayMillis?.let { LatencyCheck.Measured(it) } ?: LatencyCheck.NotMeasured
            }
        },
        onFailure = { LatencyCheck.NoAnswer }
    )
}

/** HTTPS-запрос к сервису адреса. Возвращает адрес или ошибку, но не бросает исключений. */
internal suspend fun lookupPublicAddress(): Result<PublicAddress> = withContext(Dispatchers.IO) {
    try {
        Result.success(fetchPublicAddress())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }
}

private fun fetchPublicAddress(): PublicAddress {
    val connection = URL(ADDRESS_SERVICE_URL).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = ADDRESS_TIMEOUT_MILLIS
        connection.readTimeout = ADDRESS_TIMEOUT_MILLIS
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "BeyGram")
        check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
        val json = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        check(!json.optBoolean("error")) { "address service reported an error" }
        return PublicAddress(
            ip = json.getString("ip"),
            country = json.optString("country_name").ifBlank { json.optString("country") }
        )
    } finally {
        connection.disconnect()
    }
}

/** Время TCP-соединения с сервером Telegram напрямую, без прокси. */
internal suspend fun measureDirectTelegramLatency(): Result<Long> = withContext(Dispatchers.IO) {
    try {
        val started = System.nanoTime()
        Socket().use { it.connect(InetSocketAddress(TELEGRAM_DC_ADDRESS, TELEGRAM_DC_PORT), LATENCY_TIMEOUT_MILLIS) }
        Result.success((System.nanoTime() - started) / 1_000_000L)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }
}

/** Системный VPN и тип активной сети. VPN определяется по транспорту активной сети. */
internal fun readNetworkSnapshot(context: Context): NetworkSnapshot {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val capabilities = manager?.run { activeNetwork?.let { getNetworkCapabilities(it) } }
        ?: return NetworkSnapshot(vpnActive = false, kind = NetworkKind.None)
    val kind = when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.Wifi
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.Mobile
        else -> NetworkKind.Other
    }
    return NetworkSnapshot(vpnActive = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN), kind = kind)
}
