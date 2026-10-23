package com.beykus.beygram.data.tdlib

import android.os.Build
import com.beykus.beygram.data.vpn.MihomoProxyClient
import org.drinkless.tdlib.TdApi

/**
 * Starts a *secondary* TDLib database only with an explicit, verified network
 * decision. Network is disabled BEFORE TDLib parameters are supplied, then a
 * proxy is configured while offline, and the network opens only after a second
 * preflight confirms the route is still acceptable. No fallback to DIRECT.
 *
 * The caller must Close the native client on any failure, never LogOut (which
 * would destroy the account authorization). A Close leaves its DB intact.
 */
internal class TdSecondaryNativeTransport(
    private val request: suspend (TdApi.Function<*>) -> TdApi.Object,
    private val awaitParameters: suspend () -> Unit,
    private val revalidate: suspend () -> TdSecondaryRouteDecision,
    private val proxyEndpoint: () -> TdApi.Proxy,
    private val systemVersion: () -> String = { Build.VERSION.RELEASE }
) {
    suspend fun initialize(config: TdLibConfig, planned: TdSecondaryRouteDecision) {
        require(planned !is TdSecondaryRouteDecision.Blocked) { "Secondary account route is blocked" }
        require(config.hasApiCredentials) { "Missing Telegram API credentials" }

        // TDLib can accept this request before authorization, and cannot
        // establish a Telegram connection before receiving its parameters.
        perform(TdApi.SetNetworkType(TdApi.NetworkTypeNone()))
        awaitParameters()
        perform(TdApi.SetTdlibParameters(
            false, config.databaseDirectory, config.filesDirectory, ByteArray(0),
            true, true, config.useMessageDatabase, config.useSecretChats,
            config.apiId, config.apiHash, config.systemLanguageCode,
            config.deviceModel, systemVersion(), config.applicationVersion
        ))

        when (planned) {
            TdSecondaryRouteDecision.Direct -> perform(TdApi.DisableProxy())
            TdSecondaryRouteDecision.SharedMihomo -> {
                // An existing secondary DB may have a stale/proxy entry. Enable
                // the exact authenticated process-owned listener instead.
                val added = perform(TdApi.AddProxy(proxyEndpoint(), false, "BeyGram Mihomo"))
                require(added is TdApi.AddedProxy) { "TDLib did not return a local proxy ID" }
                perform(TdApi.EnableProxy(added.id))
            }
            is TdSecondaryRouteDecision.Blocked -> error("Unreachable: blocked route")
        }
        check(revalidate() == planned) { "Secondary network policy changed during initialization" }
        perform(TdApi.SetNetworkType(TdApi.NetworkTypeOther()))
    }

    private suspend fun perform(function: TdApi.Function<*>): TdApi.Object {
        val response = request(function)
        if (response is TdApi.Error) throw IllegalStateException(
            "TDLib secondary initialization failed (code ${response.code})"
        )
        return response
    }

    companion object {
        fun localMihomoProxy(username: String, password: String) = TdApi.Proxy(
            MihomoProxyClient.HOST, MihomoProxyClient.PORT, TdApi.ProxyTypeSocks5(username, password)
        )
    }
}
