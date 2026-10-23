package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlinx.coroutines.runBlocking

class TdSecondaryNativeTransportTest {
    private val config = TdLibConfig(
        apiId = 123, apiHash = "test-key", databaseDirectory = "/private/tdlib-accounts/a-test/database",
        filesDirectory = "/private/tdlib-accounts/a-test/files", systemLanguageCode = "en",
        deviceModel = "Unit Test", applicationVersion = "0.0.0.0"
    )

    private fun fakeProxy() = TdApi.Proxy("127.0.0.1", 17893, TdApi.ProxyTypeSocks5("install", "secret"))
    private fun fake(
        commands: MutableList<TdApi.Function<*>>,
        await: () -> Unit = {},
        revalidate: suspend () -> TdSecondaryRouteDecision,
        responder: (TdApi.Function<*>) -> TdApi.Object = {
            if (it is TdApi.AddProxy) TdApi.AddedProxy(42, 0, true, "BeyGram Mihomo", it.proxy)
            else TdApi.Ok()
        }
    ) = TdSecondaryNativeTransport(
        request = { function -> commands.add(function); responder(function) },
        awaitParameters = { await() },
        revalidate = revalidate,
        proxyEndpoint = ::fakeProxy,
        systemVersion = { "Android test" }
    )

    @Test fun directNetworkCannotStartBeforeParametersAndProxyDisabled() = runBlocking {
        val commands = mutableListOf<TdApi.Function<*>>()
        var observed = false
        val transport = fake(commands, await = {
            observed = true
            assertEquals(1, commands.size)
            assertTrue(commands.single() is TdApi.SetNetworkType)
            assertTrue((commands.single() as TdApi.SetNetworkType).type is TdApi.NetworkTypeNone)
        }, revalidate = { TdSecondaryRouteDecision.Direct })
        transport.initialize(config, TdSecondaryRouteDecision.Direct)
        assertTrue(observed)
        assertEquals(4, commands.size)
        assertTrue(commands[1] is TdApi.SetTdlibParameters)
        assertTrue(commands[2] is TdApi.DisableProxy)
        assertTrue((commands[3] as TdApi.SetNetworkType).type is TdApi.NetworkTypeOther)
    }

    @Test fun sharedMihomoMustEnableLocalProxyWhileOffline() = runBlocking {
        val commands = mutableListOf<TdApi.Function<*>>()
        val transport = fake(commands, revalidate = {
            assertEquals(4, commands.size)
            assertTrue(commands[2] is TdApi.AddProxy)
            assertTrue(commands[3] is TdApi.EnableProxy)
            TdSecondaryRouteDecision.SharedMihomo
        })
        transport.initialize(config, TdSecondaryRouteDecision.SharedMihomo)
        assertEquals(5, commands.size)
        val proxy = (commands[2] as TdApi.AddProxy).proxy
        assertEquals("127.0.0.1", proxy.server)
        assertEquals(17893, proxy.port)
        assertTrue(proxy.type is TdApi.ProxyTypeSocks5)
        assertEquals(42, (commands[3] as TdApi.EnableProxy).proxyId)
        assertTrue((commands[4] as TdApi.SetNetworkType).type is TdApi.NetworkTypeOther)
    }

    @Test fun changedProxyPolicyNeverUnblocksNetwork() = runBlocking {
        val commands = mutableListOf<TdApi.Function<*>>()
        val transport = fake(commands, revalidate = {
            TdSecondaryRouteDecision.Blocked(TdSecondaryRouteBlock.LISTENER_NOT_OWNED)
        })
        try {
            transport.initialize(config, TdSecondaryRouteDecision.SharedMihomo)
            fail("Must block changing SOCKS ownership")
        } catch (_: IllegalStateException) { }
        assertFalse(commands.any {
            it is TdApi.SetNetworkType && it.type is TdApi.NetworkTypeOther
        })
    }

    @Test fun initialBlockedRouteCreatesNoNativeRequests() = runBlocking {
        val commands = mutableListOf<TdApi.Function<*>>()
        val transport = fake(commands, revalidate = { TdSecondaryRouteDecision.Direct })
        try {
            transport.initialize(config,
                TdSecondaryRouteDecision.Blocked(TdSecondaryRouteBlock.PRIMARY_ROUTE_UNKNOWN))
            fail("Blocked route was initialized")
        } catch (_: IllegalArgumentException) { }
        assertTrue(commands.isEmpty())
    }

    @Test fun initializationFailureDoesNotUnlockDirectConnection() = runBlocking {
        val commands = mutableListOf<TdApi.Function<*>>()
        val transport = fake(commands,
            revalidate = { TdSecondaryRouteDecision.SharedMihomo },
            responder = { if (it is TdApi.EnableProxy) TdApi.Error(500, "reject") else
                if (it is TdApi.AddProxy) TdApi.AddedProxy(42, 0, true, "BeyGram Mihomo", it.proxy)
                else TdApi.Ok()
            }
        )
        try {
            transport.initialize(config, TdSecondaryRouteDecision.SharedMihomo)
            fail("EnableProxy rejection must fail")
        } catch (_: IllegalStateException) { }
        assertFalse(commands.any {
            it is TdApi.SetNetworkType && it.type is TdApi.NetworkTypeOther
        })
    }

    @Test fun missingApiKeyCannotMutateNativeSession() = runBlocking {
        val commands = mutableListOf<TdApi.Function<*>>()
        val transport = fake(commands, revalidate = { TdSecondaryRouteDecision.Direct })
        try {
            transport.initialize(config.copy(apiHash = ""), TdSecondaryRouteDecision.Direct)
            fail("Expected missing API key rejection")
        } catch (_: IllegalArgumentException) { }
        assertTrue(commands.isEmpty())
    }
}
