package com.keyserdsoze.dicethrower.data.sync

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncJournalStoreTest {
    private val revision = "a".repeat(64)

    private fun journal(at: Long) = LocalSyncJournal(
        pendingDeletions = listOf(PendingCharacterDeletion("hero", at, "device", revision, revision)),
        settingsMetadata = SettingsSyncMetadata(at + 10L, revision, "device", revision),
        lastSuccessfulSyncAt = at + 20L,
    )

    private fun withJournalFile(block: (File, SyncJournalFileStore) -> Unit) {
        val directory = Files.createTempDirectory("dice-sync-journal").toFile()
        try {
            val file = directory.resolve("journal.json")
            block(file, SyncJournalFileStore(file))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun pendingDeletionSettingsAndLastSuccessRoundTrip() = withJournalFile { _, store ->
        store.save(journal(20L))
        assertEquals(journal(20L), store.load())
    }

    @Test
    fun missingJournalIsAFreshEmptyJournal() = withJournalFile { _, store ->
        assertEquals(LocalSyncJournal(), store.load())
    }

    @Test
    fun saveReplacesAtomicallyAndKeepsThePreviousVersion() = withJournalFile { file, store ->
        store.save(journal(20L))
        store.save(journal(30L))

        assertEquals(journal(30L), store.load())
        assertTrue(File(file.path + ".bak").isFile)
        assertFalse(File(file.path + ".tmp").exists())
    }

    @Test
    fun truncatedJournalFallsBackToTheLastGoodCopyAndRequiresReconciliation() = withJournalFile { file, store ->
        store.save(journal(20L))
        store.save(journal(30L))
        // An older, non-atomic version killed mid-write leaves a truncated document.
        val truncated = file.readText().take(25)
        file.writeText(truncated)

        val recovered = store.load()

        assertEquals(journal(20L).copy(reconciliationRequired = true), recovered)
        assertEquals(truncated, File(file.path + ".corrupt").readText())
        // The recovered journal is persisted: later loads and saves work normally again.
        assertEquals(recovered, store.load())
        store.save(recovered.copy(lastSuccessfulSyncAt = 99L))
        assertEquals(99L, store.load().lastSuccessfulSyncAt)
    }

    @Test
    fun unrecoverableJournalResetsToEmptyButRequiresReconciliation() = withJournalFile { file, store ->
        file.writeText("{not-json")

        val recovered = store.load()

        assertEquals(LocalSyncJournal(reconciliationRequired = true), recovered)
        assertEquals(recovered, store.load())
    }

    @Test
    fun interruptedReplacementUsesTheBackup() = withJournalFile { file, store ->
        store.save(journal(20L))
        store.save(journal(30L))
        // A crash between moving the old file aside and renaming the new one leaves no journal.
        file.delete()

        assertEquals(journal(20L).copy(reconciliationRequired = true), store.load())
    }

    @Test
    fun reconciliationFlagRoundTripsAndOlderFilesDefaultToFalse() = withJournalFile { file, store ->
        store.save(journal(20L).copy(reconciliationRequired = true))
        assertTrue(store.load().reconciliationRequired)

        file.writeText("""{"version":1,"lastSuccessfulSyncAt":null,"pendingDeletions":[]}""")
        assertFalse(store.load().reconciliationRequired)
    }
}
