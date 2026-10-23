package com.beykus.beygram.data.tdlib

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Files

class TdSecondarySessionCoordinatorTest {
    private class Fake : TdSecondaryAuthSession {
        val phase = MutableStateFlow(TdAccountAuthPhase.WAIT_PHONE)
        override val authPhase: StateFlow<TdAccountAuthPhase> = phase
        var closed = false
        var autoClose = false
        var logoutCount = 0
        var failLogout = false
        override suspend fun logOutAndAwaitClosed() {
            if (failLogout) throw IllegalStateException("Logout failed")
            logoutCount++
            phase.value = TdAccountAuthPhase.CLOSED
        }
        var lastPhone: String? = null
        override var plannedRoute: TdSecondaryRouteDecision = TdSecondaryRouteDecision.Direct
        override var networkSuspended = false
        var paused = 0
        var resumed = 0
        var failPause = false
        override suspend fun pauseNetwork() {
            if (failPause) throw IllegalStateException("Cannot disable network")
            paused++; networkSuspended = true
        }
        override suspend fun resumeNetwork(verified: Boolean) {
            if (verified && networkSuspended) { resumed++; networkSuspended = false }
        }
        override suspend fun submitPhoneNumber(phone: String) {
            lastPhone = phone
            phase.value = TdAccountAuthPhase.WAIT_CODE
        }
        override suspend fun submitCode(code: String) { phase.value = TdAccountAuthPhase.READY }
        override suspend fun submitPassword(password: String) { phase.value = TdAccountAuthPhase.READY }
        override suspend fun resendCode() {}
        override fun close() {
            closed = true
            if (autoClose) phase.value = TdAccountAuthPhase.CLOSED
        }
    }

    @Test fun logoutRemovesOnlyClosedSecondaryAndPreservesLegacyAndSibling() = runBlocking {
        val root = Files.createTempDirectory("beygram-logout-").toFile()
        val work = SupervisorJob()
        try {
            val registry = TdAccountRegistry(root)
            val sessions = mutableMapOf<String, Fake>()
            val loggedOut = mutableListOf<String>()
            val manager = TdSecondarySessionCoordinator(
                registry, CoroutineScope(work + Dispatchers.Unconfined),
                onLogoutConfirmed = { loggedOut += it },
                openNative = { id, _ -> Fake().also { sessions[id] = it } }
            )
            val a = manager.reserveAccount()
            val b = manager.reserveAccount()
            registry.selectAccount(a)
            manager.connect(a, TdSecondaryRoutePreference.EXPLICIT_DIRECT)
            manager.connect(b, TdSecondaryRoutePreference.EXPLICIT_DIRECT)
            sessions[a]!!.phase.value = TdAccountAuthPhase.READY
            sessions[b]!!.phase.value = TdAccountAuthPhase.READY
            manager.logoutSecondary(a)
            assertEquals(1, sessions[a]!!.logoutCount)
            assertEquals(0, sessions[b]!!.logoutCount)
            assertEquals(listOf(a), loggedOut)
            assertEquals(listOf("legacy", b), registry.loadOrCreate().accountIds)
            assertEquals("legacy", registry.loadOrCreate().activeAccountId)
            assertEquals(TdAccountAuthPhase.READY, sessions[b]!!.phase.value)
            try { manager.connect(a, TdSecondaryRoutePreference.EXPLICIT_DIRECT); fail("Deleted account") }
            catch (_: IllegalArgumentException) {}
        } finally { work.cancel(); root.deleteRecursively() }
    }

    @Test fun failedNativeLogoutLeavesRegistrationUntouched() = runBlocking {
        val root = Files.createTempDirectory("beygram-logout-refused-").toFile()
        val work = SupervisorJob()
        try {
            val registry = TdAccountRegistry(root)
            val native = Fake().apply { failLogout = true }
            val manager = TdSecondarySessionCoordinator(
                registry, CoroutineScope(work + Dispatchers.Unconfined),
                openNative = { _, _ -> native }
            )
            val account = manager.reserveAccount()
            manager.connect(account, TdSecondaryRoutePreference.EXPLICIT_DIRECT)
            native.phase.value = TdAccountAuthPhase.READY
            try { manager.logoutSecondary(account); fail("Must not delete an active database") }
            catch (_: IllegalStateException) {}
            assertTrue(account in registry.loadOrCreate().accountIds)
            assertEquals(0, native.logoutCount)
        } finally { work.cancel(); root.deleteRecursively() }
    }

    @Test fun blockedRoutePreflightPreservesExistingSessionAndBackgroundRestorePolicy() = runBlocking {
        val root = Files.createTempDirectory("beygram-preflight-").toFile()
        val work = SupervisorJob()
        try {
            val registry = TdAccountRegistry(root)
            val native = Fake().apply { autoClose = true }
            val stopped = mutableListOf<String>()
            val manager = TdSecondarySessionCoordinator(
                registry, CoroutineScope(work + Dispatchers.Unconfined),
                onManualStop = { stopped += it },
                openNative = { _, _ -> native }
            )
            val id = manager.reserveAccount()
            manager.connect(id, TdSecondaryRoutePreference.FOLLOW_PRIMARY)
            native.phase.value = TdAccountAuthPhase.READY
            try {
                manager.reconnectWithRoute(id, TdSecondaryRoutePreference.SHARED_MIHOMO) {
                    throw IllegalStateException("Listener unavailable")
                }
                fail("Blocked route must refuse reconnect")
            } catch (_: IllegalStateException) { }
            assertFalse(native.closed)
            assertTrue(stopped.isEmpty())
            assertTrue(manager.snapshot.value.accounts.single().connectedInThisProcess)
            assertEquals(TdSecondaryRoutePreference.FOLLOW_PRIMARY,
                manager.snapshot.value.accounts.single().routePreference)
            assertEquals(listOf("legacy", id), registry.loadOrCreate().accountIds)
        } finally { work.cancel(); root.deleteRecursively() }
    }

    @Test fun changingSecondaryRouteClosesSameClientWithoutLoggingOutOrEditingSibling() = runBlocking {
        val root = Files.createTempDirectory("beygram-route-switch-").toFile()
        val work = SupervisorJob()
        try {
            val opened = mutableListOf<Pair<TdSecondaryRoutePreference, Fake>>()
            val registry = TdAccountRegistry(root)
            val manager = TdSecondarySessionCoordinator(
                registry, CoroutineScope(work + Dispatchers.Unconfined),
                openNative = { _, pref ->
                    Fake().also {
                        it.autoClose = true
                        opened += pref to it
                    }
                }
            )
            val id = manager.reserveAccount()
            manager.connect(id, TdSecondaryRoutePreference.SHARED_MIHOMO)
            opened.single().second.phase.value = TdAccountAuthPhase.READY
            manager.reconnectWithRoute(id, TdSecondaryRoutePreference.EXPLICIT_DIRECT)
            assertEquals(2, opened.size)
            assertTrue(opened.first().second.closed)
            assertEquals(0, opened.first().second.logoutCount)
            assertEquals(TdSecondaryRoutePreference.EXPLICIT_DIRECT, opened.last().first)
            assertEquals(listOf("legacy", id), registry.loadOrCreate().accountIds)
        } finally { work.cancel(); root.deleteRecursively() }
    }

    @Test fun secondAccountHasIndependentNativeHandleAndSurvivesRegistryReopen() = runBlocking {
        val root = Files.createTempDirectory("beygram-manager-").toFile()
        val job = SupervisorJob()
        try {
            val registry = TdAccountRegistry(root)
            val sessions = mutableMapOf<String, Fake>()
            val manager = TdSecondarySessionCoordinator(
                registry, CoroutineScope(job + Dispatchers.Unconfined)
            ) { id, _ -> Fake().also { sessions[id] = it } }
            manager.refresh()
            assertTrue(manager.snapshot.value.accounts.isEmpty())
            val a = manager.reserveAccount()
            val b = manager.reserveAccount()
            assertEquals(2, manager.snapshot.value.accounts.size)
            assertEquals("legacy", registry.loadOrCreate().activeAccountId)
            manager.connect(a, TdSecondaryRoutePreference.FOLLOW_PRIMARY)
            manager.connect(b, TdSecondaryRoutePreference.EXPLICIT_DIRECT)
            val c = manager.reserveAccount()
            try {
                manager.connect(c, TdSecondaryRoutePreference.EXPLICIT_DIRECT)
                fail("Process may not start a fourth native TDLib client")
            } catch (_: IllegalStateException) { }
            manager.submitPhone(a, "+123456")
            manager.submitCode(a, "12345")
            assertEquals(TdAccountAuthPhase.READY, sessions[a]?.phase?.value)
            assertFalse(sessions[a]!!.closed)
            assertTrue(manager.snapshot.value.accounts.any { it.accountId == a && it.connectedInThisProcess })
            manager.disconnect(a)
            assertTrue(sessions[a]!!.closed)
            assertFalse(sessions[b]!!.closed)
            sessions[a]!!.phase.value = TdAccountAuthPhase.CLOSED
            assertEquals(TdAccountAuthPhase.WAIT_PHONE, sessions[b]?.phase?.value)
            assertEquals(listOf("legacy", a, b, c), TdAccountRegistry(root).loadOrCreate().accountIds)
        } finally { job.cancel(); root.deleteRecursively() }
    }

    @Test fun nativeReadyPersistsRestoreChoiceAndExplicitStopDisablesAutoRestore() = runBlocking {
        val root = Files.createTempDirectory("beygram-resume-metadata-").toFile()
        val job = SupervisorJob()
        try {
            val events = mutableListOf<String>()
            val native = Fake()
            val manager = TdSecondarySessionCoordinator(
                TdAccountRegistry(root), CoroutineScope(job + Dispatchers.Unconfined),
                onAuthorized = { id, choice -> events += "ready:$id:${choice.name}" },
                onManualStop = { id -> events += "stop:$id" }
            ) { _, _ -> native }
            val id = manager.reserveAccount()
            manager.connect(id, TdSecondaryRoutePreference.SHARED_MIHOMO)
            assertTrue(events.isEmpty())
            native.phase.value = TdAccountAuthPhase.READY
            assertEquals(listOf("ready:$id:SHARED_MIHOMO"), events)
            manager.disconnect(id)
            assertEquals(listOf("ready:$id:SHARED_MIHOMO", "stop:$id"), events)
        } finally { job.cancel(); root.deleteRecursively() }
    }

    @Test fun cannotDuplicateRunningAccountOrSelectLegacy() = runBlocking {
        val root = Files.createTempDirectory("beygram-manager-").toFile()
        val job = SupervisorJob()
        try {
            val manager = TdSecondarySessionCoordinator(
                TdAccountRegistry(root), CoroutineScope(job + Dispatchers.Unconfined)
            ) { _, _ -> Fake() }
            val a = manager.reserveAccount()
            manager.connect(a, TdSecondaryRoutePreference.FOLLOW_PRIMARY)
            try { manager.connect(a, TdSecondaryRoutePreference.EXPLICIT_DIRECT); fail("Duplicate") }
            catch (_: IllegalStateException) {}
            try { manager.connect("legacy", TdSecondaryRoutePreference.FOLLOW_PRIMARY); fail("Legacy") }
            catch (_: IllegalArgumentException) {}
        } finally { job.cancel(); root.deleteRecursively() }
    }

    @Test fun closedNativeSessionCanReopenWithoutDeletingItsRegistryEntry() = runBlocking {
        val root = Files.createTempDirectory("beygram-manager-").toFile()
        val job = SupervisorJob()
        try {
            val sessionHandles = mutableListOf<Fake>()
            val registry = TdAccountRegistry(root)
            val manager = TdSecondarySessionCoordinator(
                registry, CoroutineScope(job + Dispatchers.Unconfined)
            ) { _, _ -> Fake().also(sessionHandles::add) }
            val id = manager.reserveAccount()
            manager.connect(id, TdSecondaryRoutePreference.FOLLOW_PRIMARY)
            manager.disconnect(id)
            try { manager.connect(id, TdSecondaryRoutePreference.FOLLOW_PRIMARY); fail("Must await native CLOSED") }
            catch (_: IllegalStateException) {}
            sessionHandles[0].phase.value = TdAccountAuthPhase.CLOSED
            manager.connect(id, TdSecondaryRoutePreference.FOLLOW_PRIMARY)
            assertEquals(2, sessionHandles.size)
            assertEquals(2, registry.loadOrCreate().accountIds.size)
        } finally { job.cancel(); root.deleteRecursively() }
    }

    @Test fun sharedMihomoFailurePausesOnlyProxyClientsWithoutDirectFallback() = runBlocking {
        val root = Files.createTempDirectory("beygram-route-guard-").toFile()
        val job = SupervisorJob()
        try {
            val native = mutableMapOf<String, Fake>()
            val manager = TdSecondarySessionCoordinator(
                TdAccountRegistry(root), CoroutineScope(job + Dispatchers.Unconfined)
            ) { id, preference ->
                Fake().also {
                    it.plannedRoute = if (preference == TdSecondaryRoutePreference.SHARED_MIHOMO)
                        TdSecondaryRouteDecision.SharedMihomo else TdSecondaryRouteDecision.Direct
                    native[id] = it
                }
            }
            val sharedId = manager.reserveAccount()
            val directId = manager.reserveAccount()
            manager.connect(sharedId, TdSecondaryRoutePreference.SHARED_MIHOMO)
            manager.reconcileRoutes { TdSecondaryRouteDecision.Blocked(TdSecondaryRouteBlock.LISTENER_NOT_READY) }
            assertEquals(1, native[sharedId]!!.paused)
            manager.reconcileRoutes { preference ->
                if (preference == TdSecondaryRoutePreference.SHARED_MIHOMO)
                    TdSecondaryRouteDecision.SharedMihomo else TdSecondaryRouteDecision.Direct
            }
            assertEquals(1, native[sharedId]!!.resumed)
            manager.disconnect(sharedId)
            native[sharedId]!!.phase.value = TdAccountAuthPhase.CLOSED
            manager.connect(directId, TdSecondaryRoutePreference.EXPLICIT_DIRECT)
            manager.reconcileRoutes { TdSecondaryRouteDecision.Direct }
            assertEquals(0, native[directId]!!.paused)
            assertEquals(0, native[directId]!!.resumed)
        } finally { job.cancel(); root.deleteRecursively() }
    }

    @Test fun initiallyDirectFollowPrimaryGoesOfflineWhenPrimarySwitchesToMihomo() = runBlocking {
        val root = Files.createTempDirectory("beygram-route-direct-").toFile()
        val job = SupervisorJob()
        try {
            val native = Fake()
            val manager = TdSecondarySessionCoordinator(
                TdAccountRegistry(root), CoroutineScope(job + Dispatchers.Unconfined)
            ) { _, _ -> native }
            val id = manager.reserveAccount()
            manager.connect(id, TdSecondaryRoutePreference.FOLLOW_PRIMARY)
            manager.reconcileRoutes { TdSecondaryRouteDecision.Direct }
            assertEquals(0, native.paused)
            manager.reconcileRoutes { TdSecondaryRouteDecision.SharedMihomo }
            assertEquals(1, native.paused)
            assertTrue(manager.snapshot.value.accounts.single().networkSuspended)
            manager.reconcileRoutes { TdSecondaryRouteDecision.Direct }
            assertEquals(1, native.resumed)
            assertFalse(manager.snapshot.value.accounts.single().networkSuspended)
        } finally { job.cancel(); root.deleteRecursively() }
    }

    @Test fun unsuccessfulNetworkPauseClosesOnlySecondaryWithoutLoggingOut() = runBlocking {
        val root = Files.createTempDirectory("beygram-route-failure-").toFile()
        val job = SupervisorJob()
        try {
            val native = Fake().apply { failPause = true }
            val manager = TdSecondarySessionCoordinator(
                TdAccountRegistry(root), CoroutineScope(job + Dispatchers.Unconfined)
            ) { _, _ -> native }
            val id = manager.reserveAccount()
            manager.connect(id, TdSecondaryRoutePreference.FOLLOW_PRIMARY)
            manager.reconcileRoutes { TdSecondaryRouteDecision.SharedMihomo }
            assertTrue(native.closed)
            assertEquals(listOf("legacy", id), TdAccountRegistry(root).loadOrCreate().accountIds)
        } finally { job.cancel(); root.deleteRecursively() }
    }

    @Test fun startupFailureLeavesAccountMetadataButNeverClaimsRunning() = runBlocking {
        val root = Files.createTempDirectory("beygram-manager-").toFile()
        val job = SupervisorJob()
        try {
            val manager = TdSecondarySessionCoordinator(
                TdAccountRegistry(root), CoroutineScope(job + Dispatchers.Unconfined)
            ) { _, _ -> throw IllegalStateException("Proxy not ready") }
            val id = manager.reserveAccount()
            try { manager.connect(id, TdSecondaryRoutePreference.FOLLOW_PRIMARY); fail("Must fail closed") }
            catch (_: IllegalStateException) {}
            assertFalse(manager.snapshot.value.busy)
            assertFalse(manager.snapshot.value.accounts.single().connectedInThisProcess)
        } finally { job.cancel(); root.deleteRecursively() }
    }

    @Test fun discardingUnfinishedAccountClosesOnlyThatSlot() = runBlocking {
        val root = Files.createTempDirectory("beygram-discard-").toFile()
        val job = SupervisorJob()
        try {
            val registry = TdAccountRegistry(root)
            val sessions = mutableMapOf<String, Fake>()
            val authorized = mutableSetOf<String>()
            val manager = TdSecondarySessionCoordinator(
                registry, CoroutineScope(job + Dispatchers.Unconfined),
                isAuthorized = { it in authorized },
                openNative = { id, _ ->
                    Fake().also {
                        it.autoClose = true
                        sessions[id] = it
                    }
                }
            )
            val unfinished = manager.reserveAccount()
            val ready = manager.reserveAccount()
            manager.connect(unfinished, TdSecondaryRoutePreference.FOLLOW_PRIMARY)
            manager.connect(ready, TdSecondaryRoutePreference.FOLLOW_PRIMARY)
            authorized += ready
            sessions[ready]!!.phase.value = TdAccountAuthPhase.READY

            manager.discardIncompleteAccount(unfinished)

            assertEquals(TdAccountAuthPhase.CLOSED, sessions[unfinished]!!.phase.value)
            assertEquals(TdAccountAuthPhase.READY, sessions[ready]!!.phase.value)
            assertFalse(sessions[ready]!!.closed)
            assertEquals(listOf("legacy", ready), registry.loadOrCreate().accountIds)
        } finally { job.cancel(); root.deleteRecursively() }
    }

    @Test fun authorizedAccountIsNeverDiscardedAsAnUnfinishedSignIn() = runBlocking {
        val root = Files.createTempDirectory("beygram-discard-authorized-").toFile()
        val job = SupervisorJob()
        try {
            val registry = TdAccountRegistry(root)
            val manager = TdSecondarySessionCoordinator(
                registry, CoroutineScope(job + Dispatchers.Unconfined),
                isAuthorized = { true },
                openNative = { _, _ -> Fake() }
            )
            val id = manager.reserveAccount()
            try {
                manager.discardIncompleteAccount(id)
                fail("Authorized account must use Logout, not discard")
            } catch (_: IllegalStateException) {}
            assertEquals(listOf("legacy", id), registry.loadOrCreate().accountIds)
        } finally { job.cancel(); root.deleteRecursively() }
    }

    @Test fun sessionLimitFailureIsTypedForTheSharedSwitcher() = runBlocking {
        val root = Files.createTempDirectory("beygram-session-limit-").toFile()
        val job = SupervisorJob()
        try {
            val manager = TdSecondarySessionCoordinator(
                TdAccountRegistry(root), CoroutineScope(job + Dispatchers.Unconfined)
            ) { _, _ -> Fake() }
            val a = manager.reserveAccount()
            val b = manager.reserveAccount()
            val c = manager.reserveAccount()
            manager.connect(a, TdSecondaryRoutePreference.FOLLOW_PRIMARY)
            manager.connect(b, TdSecondaryRoutePreference.FOLLOW_PRIMARY)
            try {
                manager.connect(c, TdSecondaryRoutePreference.FOLLOW_PRIMARY)
                fail("A third secondary client must be refused")
            } catch (_: TdSecondarySessionLimitException) {}
            assertEquals(listOf("legacy", a, b, c), TdAccountRegistry(root).loadOrCreate().accountIds)
        } finally { job.cancel(); root.deleteRecursively() }
    }

    @Test fun snapshotExposesRestoreAuthorizationAndManualStopWithoutClaimingReady() = runBlocking {
        val root = Files.createTempDirectory("beygram-snapshot-").toFile()
        val job = SupervisorJob()
        try {
            val authorized = mutableSetOf<String>()
            val stopped = mutableSetOf<String>()
            val manager = TdSecondarySessionCoordinator(
                TdAccountRegistry(root), CoroutineScope(job + Dispatchers.Unconfined),
                isAuthorized = { it in authorized },
                isManuallyStopped = { it in stopped },
                openNative = { _, _ -> Fake() }
            )
            val id = manager.reserveAccount()
            authorized += id
            stopped += id
            manager.refresh()
            val row = manager.snapshot.value.accounts.single { it.accountId == id }
            assertTrue(row.authorized)
            assertTrue(row.manualStop)
            assertFalse(row.connectedInThisProcess)
        } finally { job.cancel(); root.deleteRecursively() }
    }
}
