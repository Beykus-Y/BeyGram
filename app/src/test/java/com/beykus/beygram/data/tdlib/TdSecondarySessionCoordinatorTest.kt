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
            manager.connect(b, TdSecondaryRoutePreference.EXPLICIT_DIRECT)
            manager.submitPhone(a, "+123456")
            manager.submitCode(a, "12345")
            assertEquals(TdAccountAuthPhase.READY, sessions[a]?.phase?.value)
            assertEquals(TdAccountAuthPhase.WAIT_PHONE, sessions[b]?.phase?.value)
            manager.disconnect(b)
            assertTrue(sessions[b]!!.closed)
            assertFalse(sessions[a]!!.closed)
            assertTrue(manager.snapshot.value.accounts.any { it.accountId == a && it.connectedInThisProcess })
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
