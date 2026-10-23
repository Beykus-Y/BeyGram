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
        override fun close() { closed = true }
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
            try {
                manager.connect(b, TdSecondaryRoutePreference.EXPLICIT_DIRECT)
                fail("Beta may not start a third native TDLib session")
            } catch (_: IllegalStateException) { }
            manager.submitPhone(a, "+123456")
            manager.submitCode(a, "12345")
            assertEquals(TdAccountAuthPhase.READY, sessions[a]?.phase?.value)
            assertFalse(sessions[a]!!.closed)
            assertTrue(manager.snapshot.value.accounts.any { it.accountId == a && it.connectedInThisProcess })
            manager.disconnect(a)
            assertTrue(sessions[a]!!.closed)
            sessions[a]!!.phase.value = TdAccountAuthPhase.CLOSED
            manager.connect(b, TdSecondaryRoutePreference.EXPLICIT_DIRECT)
            assertEquals(TdAccountAuthPhase.WAIT_PHONE, sessions[b]?.phase?.value)
            assertEquals(listOf("legacy", a, b), TdAccountRegistry(root).loadOrCreate().accountIds)
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
}
