package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.data.sync.LocalSyncJournal
import com.keyserdsoze.dicethrower.data.sync.PendingCharacterDeletion
import com.keyserdsoze.dicethrower.model.AppData

/** Pure sync-journal rules applied by [LocalStore] when stored data is replaced. */
internal object SyncTransitions {
    /**
     * Pending deletions after [previous] became [current]. With [recordDeletions] false (a restored
     * backup) characters that merely are absent are never recorded as deleted; deletions already
     * pending for characters that exist again are dropped.
     */
    fun pendingDeletions(
        previous: AppData,
        current: AppData,
        existing: List<PendingCharacterDeletion>,
        writerId: String,
        now: Long,
        recordDeletions: Boolean = true,
    ): List<PendingCharacterDeletion> {
        val currentIds = current.characters.mapTo(mutableSetOf()) { it.id }
        val pendingById = existing.associateByTo(linkedMapOf()) { it.characterId }
        if (recordDeletions) previous.characters.filterNot { it.id in currentIds }.forEach { removed ->
            val metadata = previous.characterSyncMetadata.firstOrNull { it.characterId == removed.id }
                ?: return@forEach
            pendingById[removed.id] = PendingCharacterDeletion(
                characterId = removed.id,
                deletedAt = maxOf(now, metadata.updatedAt + 1L),
                writerId = writerId,
                baseRevision = metadata.baseRevision ?: metadata.revision,
                deletedRevision = metadata.revision,
            )
        }
        currentIds.forEach(pendingById::remove)
        return pendingById.values.sortedBy { it.characterId }
    }

    /**
     * Base revisions of a restored backup describe another point in time. Without a base any
     * difference from the cloud is a conflict, never an automatic overwrite.
     */
    fun neutralizeBaseRevisions(data: AppData): AppData = data.copy(
        characterSyncMetadata = data.characterSyncMetadata.map { it.copy(baseRevision = null) },
    )

    fun journalAfterRestore(journal: LocalSyncJournal): LocalSyncJournal = journal.copy(
        reconciliationRequired = true,
        settingsMetadata = journal.settingsMetadata?.copy(baseRevision = null),
        diceCatalogMetadata = journal.diceCatalogMetadata?.copy(baseRevision = null),
    )
}
