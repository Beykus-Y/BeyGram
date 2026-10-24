package com.beykus.beygram.data.tdlib

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An on-device path assertion without opening TDLib. Never rename or clear the
 * installation's existing database during the multi-account metadata migration.
 */
@RunWith(AndroidJUnit4::class)
class TdAccountLegacyMigrationTest {
    @Test fun defaultRuntimeKeepsOriginalTelegramDatabaseAndFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = TdLibConfig.from(context)
        assertEquals(
            context.filesDir.resolve("tdlib/database").canonicalPath,
            config.databaseDirectory
        )
        assertEquals(
            context.filesDir.resolve("tdlib/files").canonicalPath,
            config.filesDirectory
        )
        val state = TdAccountRegistry(context.filesDir).loadOrCreate()
        assertTrue(state.accountIds.contains(TdAccountStorageLayout.LEGACY_ACCOUNT_ID))
    }

    @Test fun secondaryConfigurationNeverAliasesPrimarySession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Inspect an opaque candidate, without registering, opening or deleting a TDLib database.
        val secondary = TdLibConfig.forAccount(context, "a-test-isolation")
        val legacy = TdLibConfig.from(context)
        assertTrue(secondary.databaseDirectory.startsWith(
            context.filesDir.resolve("tdlib-accounts").canonicalPath + "/"
        ))
        assertTrue(secondary.filesDirectory.startsWith(
            context.filesDir.resolve("tdlib-accounts").canonicalPath + "/"
        ))
        assertTrue(secondary.databaseDirectory != legacy.databaseDirectory)
        assertTrue(secondary.filesDirectory != legacy.filesDirectory)
    }
}
