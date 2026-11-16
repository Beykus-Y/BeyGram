package com.beykus.beygram.ui.screens.chat

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PendingTextSendFilesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun legacyPlaintextAndAtomicSidecarsMoveOutsideBackupWithoutImport() {
        val files = folder.newFolder("files")
        val noBackup = folder.newFolder("no_backup")
        val name = PendingTextSendFiles.name("legacy")
        val payloads = mapOf(name to "old snapshot", "$name.bak" to "backup", "$name.new" to "partial")
        payloads.forEach { (path, text) -> File(files, path).writeText(text) }
        File(files, "unrelated.json").writeText("keep")
        File(noBackup, name).writeText("current snapshot")

        PendingTextSendFiles.quarantineLegacy(files, noBackup)
        PendingTextSendFiles.quarantineLegacy(files, noBackup)

        assertEquals(listOf("unrelated.json"), files.list().orEmpty().toList())
        assertEquals("current snapshot", File(noBackup, name).readText())
        val quarantined = File(noBackup, "pending-text-legacy").listFiles().orEmpty()
        assertEquals(payloads.values.toSet(), quarantined.map { it.readText() }.toSet())
        assertEquals(payloads.size, quarantined.size)
    }

    @Test fun accountCleanupRemovesOnlyItsActiveAndLegacySnapshots() {
        val files = folder.newFolder("files")
        val noBackup = folder.newFolder("no_backup")
        val first = PendingTextSendFiles.name("first")
        val second = PendingTextSendFiles.name("second")
        for (name in listOf(first, second)) {
            for (suffix in listOf("", ".bak", ".new")) {
                File(files, name + suffix).writeText("legacy")
                File(noBackup, name + suffix).writeText("current")
            }
        }
        PendingTextSendFiles.quarantineLegacy(files, noBackup)
        File(files, first).writeText("late legacy file")

        PendingTextSendFiles.purge(files, noBackup, "first")
        PendingTextSendFiles.purge(files, noBackup, "first")

        assertFalse(File(files, first).exists())
        for (suffix in listOf("", ".bak", ".new")) {
            assertFalse(File(noBackup, first + suffix).exists())
            assertTrue(File(noBackup, second + suffix).exists())
        }
        assertFalse(PendingTextSendFiles.marker(noBackup, "first").exists())
        val quarantine = File(noBackup, "pending-text-legacy").listFiles().orEmpty()
        assertEquals(3, quarantine.size)
        assertTrue(quarantine.all { it.name.startsWith(second + ".") })
    }

    @Test fun corruptQuarantinePreservesSidecarsAndPurgeRemovesTargetAccountOnly() {
        val files = folder.newFolder("files")
        val noBackup = folder.newFolder("no_backup")
        val corruptDir = File(noBackup, PendingTextSendFiles.CORRUPT_QUARANTINE)
        val first = PendingTextSendFiles.name("first")
        val second = PendingTextSendFiles.name("second")
        for (name in listOf(first, second)) {
            File(noBackup, name).writeText("broken-$name")
            File(noBackup, "$name.bak").writeText("bak-$name")
            PendingTextSendFiles.quarantineCorrupt(File(noBackup, name), corruptDir)
        }
        assertEquals(4, corruptDir.listFiles().orEmpty().size)

        PendingTextSendFiles.purge(files, noBackup, "first")

        val remaining = corruptDir.listFiles().orEmpty()
        assertEquals(2, remaining.size)
        assertTrue(remaining.all { it.name.startsWith(second + ".") })
    }
}
