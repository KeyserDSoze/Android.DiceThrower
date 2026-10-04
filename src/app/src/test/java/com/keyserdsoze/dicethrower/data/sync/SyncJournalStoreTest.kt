package com.keyserdsoze.dicethrower.data.sync

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncJournalStoreTest {
    @Test
    fun pendingDeletionSettingsAndLastSuccessRoundTrip() {
        val directory = Files.createTempDirectory("dice-sync-journal").toFile()
        val file = directory.resolve("journal.json")
        val store = SyncJournalFileStore(file)
        val revision = "a".repeat(64)
        val journal = LocalSyncJournal(
            pendingDeletions = listOf(
                PendingCharacterDeletion("hero", 20L, "device", revision, revision),
            ),
            settingsMetadata = SettingsSyncMetadata(30L, revision, "device", revision),
            lastSuccessfulSyncAt = 40L,
        )

        try {
            store.save(journal)
            assertEquals(journal, store.load())
        } finally {
            directory.deleteRecursively()
        }
    }
}
