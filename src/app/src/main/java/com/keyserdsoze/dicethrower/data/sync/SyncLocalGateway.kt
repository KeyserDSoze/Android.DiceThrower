package com.keyserdsoze.dicethrower.data.sync

import com.keyserdsoze.dicethrower.data.AppDataValidator
import com.keyserdsoze.dicethrower.data.LocalStore
import com.keyserdsoze.dicethrower.data.PortableCharacterImageAsset
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.model.CharacterImageRef

data class LocalSyncSnapshot(
    val data: AppData,
    val settings: AppSettings,
    val journal: LocalSyncJournal,
    val writerId: String,
)

data class CharacterSyncCommit(
    val characterId: String,
    val expectedRevision: String?,
    val replacement: AppData? = null,
    val delete: Boolean = false,
    val syncedBaseRevision: String? = null,
    val originalBaseRevision: String? = null,
    val allowDescendantBaseAdvance: Boolean = false,
)

data class SettingsSyncCommit(
    val expectedRevision: String,
    val replacement: RoamingSettings? = null,
    val metadataAfter: SettingsSyncMetadata,
    val originalBaseRevision: String? = null,
    val allowDescendantBaseAdvance: Boolean = false,
)

data class LocalSyncCommitPlan(
    val characters: List<CharacterSyncCommit>,
    val downloadedAssets: List<PortableCharacterImageAsset>,
    val resolvedDeletions: List<PendingCharacterDeletion>,
    val settings: SettingsSyncCommit?,
    val successfulAt: Long,
)

data class LocalSyncCommitResult(
    val dataChanged: Boolean,
    val settingsChanged: Boolean,
    val skippedCharacterIds: Set<String>,
    val settingsSkipped: Boolean,
    val snapshot: LocalSyncSnapshot,
)

interface SyncLocalGateway {
    fun snapshot(now: Long): LocalSyncSnapshot
    fun readImageAsset(ref: CharacterImageRef): PortableCharacterImageAsset?
    fun commit(plan: LocalSyncCommitPlan): LocalSyncCommitResult
}

class AndroidSyncLocalGateway(private val store: LocalStore) : SyncLocalGateway {
    override fun snapshot(now: Long): LocalSyncSnapshot {
        val data = store.loadData()
        val settings = store.loadSettings()
        val writerId = store.installationWriterId()
        var journal = store.loadSyncJournal()
        val roaming = RoamingSettings.from(settings)
        val currentMetadata = journal.settingsMetadata
        if (currentMetadata == null || currentMetadata.revision != roaming.revision()) {
            journal = journal.copy(
                settingsMetadata = SettingsSyncMetadata(
                    updatedAt = maxOf(now, (currentMetadata?.updatedAt ?: -1L) + 1L),
                    revision = roaming.revision(),
                    writerId = writerId,
                    baseRevision = currentMetadata?.baseRevision,
                ),
            )
            store.saveSyncJournal(journal)
        }
        return LocalSyncSnapshot(data, settings, journal, writerId)
    }

    override fun readImageAsset(ref: CharacterImageRef): PortableCharacterImageAsset? =
        store.loadImageAsset(ref)

    override fun commit(plan: LocalSyncCommitPlan): LocalSyncCommitResult {
        val writerId = store.installationWriterId()
        var data = store.loadData()
        var dataChanged = false
        val skipped = mutableSetOf<String>()

        plan.characters.forEach { change ->
            val currentMetadata = data.characterSyncMetadata.firstOrNull { it.characterId == change.characterId }
            if (currentMetadata?.revision != change.expectedRevision) {
                val canAdvanceDescendant = change.allowDescendantBaseAdvance &&
                    currentMetadata != null &&
                    currentMetadata.writerId == writerId &&
                    currentMetadata.baseRevision == change.originalBaseRevision &&
                    change.syncedBaseRevision != null
                if (canAdvanceDescendant) {
                    data = data.copy(
                        characterSyncMetadata = data.characterSyncMetadata.map { metadata ->
                            if (metadata.characterId == change.characterId) {
                                metadata.copy(baseRevision = change.syncedBaseRevision)
                            } else {
                                metadata
                            }
                        },
                    )
                    dataChanged = true
                } else {
                    skipped += change.characterId
                }
                return@forEach
            }

            data = when {
                change.delete -> removeCharacterGraph(data, change.characterId)
                change.replacement != null -> replaceCharacterGraph(data, change.replacement, change.syncedBaseRevision)
                change.syncedBaseRevision != null && currentMetadata != null -> data.copy(
                    characterSyncMetadata = data.characterSyncMetadata.map { metadata ->
                        if (metadata.characterId == change.characterId) {
                            metadata.copy(baseRevision = change.syncedBaseRevision)
                        } else {
                            metadata
                        }
                    },
                )
                else -> data
            }
            dataChanged = true
        }

        if (dataChanged) {
            AppDataValidator.requireValid(data)
            store.applySyncedData(data, plan.downloadedAssets)
        }

        var settings = store.loadSettings()
        var journal = store.loadSyncJournal()
        var settingsChanged = false
        var settingsSkipped = false
        plan.settings?.let { change ->
            val current = journal.settingsMetadata
            if (current?.revision == change.expectedRevision) {
                change.replacement?.let { replacement ->
                    settings = replacement.applyTo(settings)
                    store.applySyncedSettings(settings)
                    settingsChanged = true
                }
                journal = journal.copy(settingsMetadata = change.metadataAfter)
            } else if (
                change.allowDescendantBaseAdvance &&
                current != null &&
                current.writerId == writerId &&
                current.baseRevision == change.originalBaseRevision
            ) {
                journal = journal.copy(settingsMetadata = current.copy(baseRevision = change.metadataAfter.baseRevision))
            } else {
                settingsSkipped = true
            }
        }

        val resolved = plan.resolvedDeletions.associateBy { it.characterId }
        journal = journal.copy(
            pendingDeletions = journal.pendingDeletions.filterNot { current ->
                resolved[current.characterId] == current
            },
            lastSuccessfulSyncAt = plan.successfulAt,
        )
        store.saveSyncJournal(journal)

        return LocalSyncCommitResult(
            dataChanged = dataChanged,
            settingsChanged = settingsChanged,
            skippedCharacterIds = skipped,
            settingsSkipped = settingsSkipped,
            snapshot = snapshot(plan.successfulAt),
        )
    }

    private fun removeCharacterGraph(data: AppData, characterId: String): AppData = data.copy(
        characters = data.characters.filterNot { it.id == characterId },
        modifiers = data.modifiers.filterNot { it.characterId == characterId },
        groups = data.groups.filterNot { it.characterId == characterId },
        rolls = data.rolls.filterNot { it.characterId == characterId },
        logs = data.logs.filterNot { it.characterId == characterId },
        diceStyles = data.diceStyles.filterNot { it.characterId == characterId },
        characterSyncMetadata = data.characterSyncMetadata.filterNot { it.characterId == characterId },
    )

    private fun replaceCharacterGraph(data: AppData, replacement: AppData, baseRevision: String?): AppData {
        val character = replacement.characters.single()
        val clean = removeCharacterGraph(data, character.id)
        val metadata = replacement.characterSyncMetadata.single().copy(baseRevision = baseRevision)
        return clean.copy(
            characters = clean.characters + replacement.characters,
            modifiers = clean.modifiers + replacement.modifiers,
            groups = clean.groups + replacement.groups,
            rolls = clean.rolls + replacement.rolls,
            logs = clean.logs + replacement.logs,
            diceStyles = clean.diceStyles + replacement.diceStyles,
            characterSyncMetadata = clean.characterSyncMetadata + metadata,
        )
    }
}
