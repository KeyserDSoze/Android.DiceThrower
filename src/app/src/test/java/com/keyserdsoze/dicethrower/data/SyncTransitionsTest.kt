package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.data.sync.LocalSyncJournal
import com.keyserdsoze.dicethrower.data.sync.SettingsSyncMetadata
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTransitionsTest {
    private fun stamped(vararg ids: String) = SyncMetadataManager.ensureMetadata(
        AppData(characters = ids.map { CharacterProfile(it, it) }), "device-a", 10L,
    )

    @Test
    fun ordinaryRemovalRecordsADeletionButRestoreNeverDoes() {
        val previous = stamped("a", "b")
        val backup = stamped("a")
        val normal = SyncTransitions.pendingDeletions(previous, backup, emptyList(), "w", 20L)
        assertEquals(listOf("b"), normal.map { it.characterId })

        val restored = SyncTransitions.pendingDeletions(previous, backup, emptyList(), "w", 20L, recordDeletions = false)
        assertTrue(restored.isEmpty())
    }

    @Test
    fun restoredCharacterDropsItsStalePendingDeletion() {
        val previous = stamped("a")
        val pending = SyncTransitions.pendingDeletions(previous, stamped(), emptyList(), "w", 20L)
        assertEquals(1, pending.size)
        assertTrue(SyncTransitions.pendingDeletions(stamped(), previous, pending, "w", 30L, false).isEmpty())
    }

    @Test
    fun restoreRemovesBaseRevisionsAndForcesReconciliation() {
        val synced = stamped("a").let { SyncMetadataManager.markSynced(it, "a", "f".repeat(64)) }
        assertEquals("f".repeat(64), synced.characterSyncMetadata.single().baseRevision)
        val neutral = SyncTransitions.neutralizeBaseRevisions(synced)
        assertNull(neutral.characterSyncMetadata.single().baseRevision)
        assertEquals(synced.characterSyncMetadata.single().revision, neutral.characterSyncMetadata.single().revision)

        val journal = LocalSyncJournal(
            settingsMetadata = SettingsSyncMetadata(1L, "r", "w", baseRevision = "b"),
            diceCatalogMetadata = SettingsSyncMetadata(1L, "r", "w", baseRevision = "b"),
        )
        val after = SyncTransitions.journalAfterRestore(journal)
        assertTrue(after.reconciliationRequired)
        assertNull(after.settingsMetadata?.baseRevision)
        assertNull(after.diceCatalogMetadata?.baseRevision)
    }
}
