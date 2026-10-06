package com.keyserdsoze.dicethrower.data.sync

import com.keyserdsoze.dicethrower.data.SyncChangeState
import com.keyserdsoze.dicethrower.data.SyncMetadataManager
import com.keyserdsoze.dicethrower.data.cloud.CLOUD_SCHEMA_VERSION
import com.keyserdsoze.dicethrower.data.cloud.CloudAuthorizationException
import com.keyserdsoze.dicethrower.data.cloud.CloudCharacterMetadata
import com.keyserdsoze.dicethrower.data.cloud.CloudCharacterTombstone
import com.keyserdsoze.dicethrower.data.cloud.CloudManifest
import com.keyserdsoze.dicethrower.data.cloud.CloudProtocolException
import com.keyserdsoze.dicethrower.data.cloud.CloudRemoteRepository
import com.keyserdsoze.dicethrower.data.cloud.CloudRepositoryException
import com.keyserdsoze.dicethrower.data.cloud.CloudRoamingSettings
import com.keyserdsoze.dicethrower.data.cloud.CloudSchemaMismatchException
import com.keyserdsoze.dicethrower.data.cloud.CloudSettingsDocument
import com.keyserdsoze.dicethrower.data.cloud.CloudSettingsMetadata
import com.keyserdsoze.dicethrower.data.cloud.CloudTransientException
import com.keyserdsoze.dicethrower.data.cloud.CloudDocumentCodec
import com.keyserdsoze.dicethrower.data.cloud.CloudAssetDocument
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.ConflictPolicy
import kotlinx.coroutines.CancellationException

class CloudSyncEngine(
    private val local: SyncLocalGateway,
    private val remote: CloudRemoteRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun currentStatus(connected: Boolean): SyncStatus {
    if (!connected) return SyncStatus(SyncStatusKind.LOCAL_ONLY)
    return try {
        val snapshot = local.snapshot(clock())
        val pending = pendingCount(snapshot)
        SyncStatus(
  kind = if (pending > 0 || snapshot.journal.lastSuccessfulSyncAt == null) {
      SyncStatusKind.PENDING
  } else {
      SyncStatusKind.SYNCED
  },
  pendingCount = pending,
  lastSuccessfulSyncAt = snapshot.journal.lastSuccessfulSyncAt,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        SyncStatus(
  kind = SyncStatusKind.ERROR,
  error = errorKind(error),
        )
    }
}

    suspend fun sync(
        conflictPolicy: ConflictPolicy = ConflictPolicy.ASK,
        resolutions: Map<String, SyncConflictResolution> = emptyMap(),
    ): SyncRunResult = try {
        val snapshot = local.snapshot(clock())
        sync(snapshot, conflictPolicy, resolutions)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        val latest = try {
  local.snapshot(clock())
        } catch (cancellation: CancellationException) {
  throw cancellation
        } catch (_: Exception) {
  null
        }
        SyncRunResult(
  status = SyncStatus(
      kind = SyncStatusKind.ERROR,
      pendingCount = latest?.let(::pendingCount) ?: 0,
      lastSuccessfulSyncAt = latest?.journal?.lastSuccessfulSyncAt,
      error = errorKind(error),
  ),
        )
    }

    private suspend fun sync(
        snapshot: LocalSyncSnapshot,
        conflictPolicy: ConflictPolicy,
        resolutions: Map<String, SyncConflictResolution>,
    ): SyncRunResult {
        val manifest = remote.readManifest()
        val remoteById = remote.listCharacters().associateByTo(linkedMapOf()) { it.characterId }
        val remoteAssetIds = remote.listAssets().mapTo(mutableSetOf()) { it.assetId }
        val tombstones = manifest?.tombstones.orEmpty().associateByTo(linkedMapOf()) { it.characterId }
        val inconsistent = remoteById.keys.intersect(tombstones.keys)
        if (inconsistent.isNotEmpty()) {
            throw CloudProtocolException("Drive contains live characters and tombstones for the same IDs")
        }

        val localById = snapshot.data.characterSyncMetadata.associateBy { it.characterId }
        val deletionsById = snapshot.journal.pendingDeletions.associateBy { it.characterId }
        val allIds = (localById.keys + remoteById.keys + tombstones.keys + deletionsById.keys).sorted()
        val commits = mutableListOf<CharacterSyncCommit>()
        val resolvedDeletions = mutableListOf<PendingCharacterDeletion>()
        val downloadedAssets = linkedMapOf<String, com.keyserdsoze.dicethrower.data.PortableCharacterImageAsset>()
        val conflicts = mutableListOf<SyncConflict>()
        var autoResolvedCount = 0

        for (characterId in allIds) {
            val localMetadata = localById[characterId]
            val remoteMetadata = remoteById[characterId]
            val localDeletion = deletionsById[characterId]
            val remoteDeletion = tombstones[characterId]

            when {
                localMetadata != null && remoteMetadata != null -> {
                    when (SyncMetadataManager.classify(
                        localRevision = localMetadata.revision,
                        remoteRevision = remoteMetadata.revision,
                        baseRevision = localMetadata.baseRevision,
                    )) {
                        SyncChangeState.SAME -> commits += markSynced(localMetadata.characterId, localMetadata.revision,
                            localMetadata.baseRevision)
                        SyncChangeState.LOCAL_ONLY -> {
                            uploadLocal(snapshot, characterId, remoteAssetIds)
                            remoteById[characterId] = CloudDocumentCodec.buildCharacterDocument(snapshot.data, characterId).metadata
                            commits += markSynced(characterId, localMetadata.revision, localMetadata.baseRevision, true)
                        }
                        SyncChangeState.REMOTE_ONLY -> commits += downloadRemote(
                            characterId,
                            localMetadata.revision,
                            remoteMetadata,
                            downloadedAssets,
                        )
                        SyncChangeState.CONFLICT -> {
                            val resolution = resolutions[characterId] ?: latestResolution(
                                conflictPolicy,
                                localMetadata.updatedAt,
                                localMetadata.revision,
                                localMetadata.writerId,
                                remoteMetadata.updatedAt,
                                remoteMetadata.revision,
                                remoteMetadata.writerId,
                            )
                            when (resolution) {
                                SyncConflictResolution.KEEP_LOCAL -> {
                                    uploadLocal(snapshot, characterId, remoteAssetIds)
                                    remoteById[characterId] = CloudDocumentCodec
                                        .buildCharacterDocument(snapshot.data, characterId).metadata
                                    commits += markSynced(
                                        characterId,
                                        localMetadata.revision,
                                        localMetadata.baseRevision,
                                        true,
                                    )
                                    if (characterId !in resolutions) autoResolvedCount++
                                }
                                SyncConflictResolution.USE_REMOTE -> {
                                    commits += downloadRemote(
                                        characterId,
                                        localMetadata.revision,
                                        remoteMetadata,
                                        downloadedAssets,
                                    )
                                    if (characterId !in resolutions) autoResolvedCount++
                                }
                                null -> conflicts += buildCharacterConflict(
                                    snapshot.data,
                                    characterId,
                                    localMetadata.updatedAt,
                                    localMetadata.revision,
                                    localMetadata.writerId,
                                    remoteMetadata,
                                )
                            }
                        }
                    }
                }

                localMetadata != null && remoteDeletion != null -> {
                    if (localMetadata.revision == remoteDeletion.baseRevision) {
                        commits += CharacterSyncCommit(
                            characterId = characterId,
                            expectedRevision = localMetadata.revision,
                            delete = true,
                        )
                    } else {
                        val resolution = resolutions[characterId] ?: latestResolution(
                            conflictPolicy,
                            localMetadata.updatedAt,
                            localMetadata.revision,
                            localMetadata.writerId,
                            remoteDeletion.deletedAt,
                            remoteDeletion.baseRevision,
                            remoteDeletion.writerId,
                        )
                        when (resolution) {
                            SyncConflictResolution.KEEP_LOCAL -> {
                                uploadLocal(snapshot, characterId, remoteAssetIds)
                                val uploaded = CloudDocumentCodec.buildCharacterDocument(snapshot.data, characterId).metadata
                                remoteById[characterId] = uploaded
                                tombstones.remove(characterId)
                                commits += markSynced(characterId, localMetadata.revision, localMetadata.baseRevision, true)
                                if (characterId !in resolutions) autoResolvedCount++
                            }
                            SyncConflictResolution.USE_REMOTE -> {
                                commits += CharacterSyncCommit(characterId, localMetadata.revision, delete = true)
                                if (characterId !in resolutions) autoResolvedCount++
                            }
                            null -> conflicts += SyncConflict(
                                kind = SyncConflictKind.LOCAL_EDIT_REMOTE_DELETE,
                                characterId = characterId,
                                characterName = characterName(snapshot.data, characterId),
                                localRevision = localMetadata.revision,
                                remoteRevision = remoteDeletion.baseRevision,
                                localUpdatedAt = localMetadata.updatedAt,
                                remoteUpdatedAt = remoteDeletion.deletedAt,
                                localWriterId = localMetadata.writerId,
                                remoteWriterId = remoteDeletion.writerId,
                                changedAreas = listOf(ConflictArea.DELETION),
                            )
                        }
                    }
                }

                localMetadata != null -> {
                    if (localMetadata.baseRevision == null) {
                        uploadLocal(snapshot, characterId, remoteAssetIds)
                        val uploaded = CloudDocumentCodec.buildCharacterDocument(snapshot.data, characterId).metadata
                        remoteById[characterId] = uploaded
                        tombstones.remove(characterId)
                        commits += markSynced(characterId, localMetadata.revision, null, true)
                    } else when (resolutions[characterId]) {
                        SyncConflictResolution.KEEP_LOCAL -> {
                            uploadLocal(snapshot, characterId, remoteAssetIds)
                            remoteById[characterId] = CloudDocumentCodec
                                .buildCharacterDocument(snapshot.data, characterId).metadata
                            commits += markSynced(characterId, localMetadata.revision, localMetadata.baseRevision, true)
                        }
                        SyncConflictResolution.USE_REMOTE -> {
                            tombstones[characterId] = CloudCharacterTombstone(
                                characterId = characterId,
                                deletedAt = clock(),
                                writerId = snapshot.writerId,
                                baseRevision = localMetadata.baseRevision,
                            )
                            commits += CharacterSyncCommit(characterId, localMetadata.revision, delete = true)
                        }
                        null -> conflicts += SyncConflict(
                            kind = SyncConflictKind.REMOTE_MISSING_WITHOUT_TOMBSTONE,
                            characterId = characterId,
                            characterName = characterName(snapshot.data, characterId),
                            localRevision = localMetadata.revision,
                            localUpdatedAt = localMetadata.updatedAt,
                            localWriterId = localMetadata.writerId,
                            changedAreas = listOf(ConflictArea.DELETION),
                        )
                    }
                }

                remoteMetadata != null && localDeletion != null -> {
                    if (
                        remoteMetadata.revision == localDeletion.baseRevision ||
                        remoteMetadata.revision == localDeletion.deletedRevision
                    ) {
                        remote.deleteCharacter(characterId)
                        remoteById.remove(characterId)
                        tombstones[characterId] = CloudCharacterTombstone(
                            characterId = characterId,
                            deletedAt = localDeletion.deletedAt,
                            writerId = localDeletion.writerId,
                            baseRevision = remoteMetadata.revision,
                        )
                        resolvedDeletions += localDeletion
                    } else {
                        val resolution = resolutions[characterId] ?: latestResolution(
                            conflictPolicy,
                            localDeletion.deletedAt,
                            localDeletion.deletedRevision,
                            localDeletion.writerId,
                            remoteMetadata.updatedAt,
                            remoteMetadata.revision,
                            remoteMetadata.writerId,
                        )
                        when (resolution) {
                            SyncConflictResolution.KEEP_LOCAL -> {
                                remote.deleteCharacter(characterId)
                                remoteById.remove(characterId)
                                tombstones[characterId] = CloudCharacterTombstone(
                                    characterId,
                                    localDeletion.deletedAt,
                                    localDeletion.writerId,
                                    remoteMetadata.revision,
                                )
                                resolvedDeletions += localDeletion
                                if (characterId !in resolutions) autoResolvedCount++
                            }
                            SyncConflictResolution.USE_REMOTE -> {
                                commits += downloadRemote(characterId, null, remoteMetadata, downloadedAssets)
                                resolvedDeletions += localDeletion
                                if (characterId !in resolutions) autoResolvedCount++
                            }
                            null -> conflicts += SyncConflict(
                                kind = SyncConflictKind.LOCAL_DELETE_REMOTE_EDIT,
                                characterId = characterId,
                                characterName = remoteCharacterName(characterId),
                                localRevision = localDeletion.deletedRevision,
                                remoteRevision = remoteMetadata.revision,
                                localUpdatedAt = localDeletion.deletedAt,
                                remoteUpdatedAt = remoteMetadata.updatedAt,
                                localWriterId = localDeletion.writerId,
                                remoteWriterId = remoteMetadata.writerId,
                                changedAreas = listOf(ConflictArea.DELETION),
                            )
                        }
                    }
                }

                remoteMetadata != null -> commits += downloadRemote(
                    characterId,
                    expectedLocalRevision = null,
                    remoteMetadata = remoteMetadata,
                    downloadedAssets = downloadedAssets,
                )

                localDeletion != null -> {
                    if (remoteDeletion == null) {
                        tombstones[characterId] = CloudCharacterTombstone(
                            characterId = characterId,
                            deletedAt = localDeletion.deletedAt,
                            writerId = localDeletion.writerId,
                            baseRevision = localDeletion.baseRevision,
                        )
                    }
                    resolvedDeletions += localDeletion
                }
            }
        }

        val settingsResult = reconcileSettings(
            snapshot,
            manifest?.settings,
            conflicts,
            conflictPolicy,
            resolutions[SETTINGS_CONFLICT_KEY],
        )
        autoResolvedCount += settingsResult.autoResolvedCount
        val desiredCharacters = remoteById.values.sortedBy { it.characterId }
        val desiredTombstones = tombstones.values.sortedBy { it.characterId }
        val desiredSettings = settingsResult.remote
        val remoteDirty = manifest == null ||
            manifest.characters.sortedBy { it.characterId } != desiredCharacters ||
            manifest.tombstones.sortedBy { it.characterId } != desiredTombstones ||
            manifest.settings != desiredSettings
        if (remoteDirty) {
            remote.putManifest(
                CloudManifest(
                    schemaVersion = CLOUD_SCHEMA_VERSION,
                    generatedAt = maxOf(clock(), (manifest?.generatedAt ?: -1L) + 1L),
                    writerId = snapshot.writerId,
                    characters = desiredCharacters,
                    tombstones = desiredTombstones,
                    settings = desiredSettings,
                ),
            )
        }

        val completedAt = clock()
        val commit = local.commit(
            LocalSyncCommitPlan(
                characters = commits,
                downloadedAssets = downloadedAssets.values.toList(),
                resolvedDeletions = resolvedDeletions,
                settings = settingsResult.commit,
                successfulAt = completedAt,
            ),
        )
        val finalPending = pendingCount(commit.snapshot)
        val wasSkipped = commit.skippedCharacterIds.isNotEmpty() || commit.settingsSkipped
        val kind = when {
            conflicts.isNotEmpty() -> SyncStatusKind.CONFLICT
            finalPending > 0 || wasSkipped -> SyncStatusKind.PENDING
            else -> SyncStatusKind.SYNCED
        }
        return SyncRunResult(
            status = SyncStatus(
                kind = kind,
                pendingCount = finalPending,
                lastSuccessfulSyncAt = completedAt,
                conflicts = conflicts,
                autoResolvedCount = autoResolvedCount,
            ),
            localDataChanged = commit.dataChanged,
            localSettingsChanged = commit.settingsChanged,
            initialReconciliationComplete = conflicts.isEmpty() && !wasSkipped,
        )
    }

    private suspend fun uploadLocal(
        snapshot: LocalSyncSnapshot,
        characterId: String,
        remoteAssetIds: MutableSet<String>,
    ) {
        val document = CloudDocumentCodec.buildCharacterDocument(snapshot.data, characterId)
        val character = document.data.characters.single()
        listOfNotNull(character.image, character.diceTableImage).distinctBy { it.assetId }.forEach { ref ->
            if (ref.assetId !in remoteAssetIds) {
                val localAsset = local.readImageAsset(ref)
                    ?: throw CloudProtocolException("Local image asset ${ref.assetId} is missing or corrupt")
                remote.putAsset(CloudAssetDocument(localAsset))
                remoteAssetIds += ref.assetId
            }
        }
        remote.putCharacter(document)
    }

    private suspend fun downloadRemote(
        characterId: String,
        expectedLocalRevision: String?,
        remoteMetadata: CloudCharacterMetadata,
        downloadedAssets: MutableMap<String, com.keyserdsoze.dicethrower.data.PortableCharacterImageAsset>,
    ): CharacterSyncCommit {
        val document = remote.readCharacter(characterId)
            ?: throw CloudProtocolException("Remote character $characterId disappeared during sync")
        if (document.metadata != remoteMetadata) {
            throw CloudProtocolException("Remote character $characterId changed during sync")
        }
        val character = document.data.characters.single()
        listOfNotNull(character.image, character.diceTableImage).distinctBy { it.assetId }.forEach { ref ->
            if (local.readImageAsset(ref) == null && ref.assetId !in downloadedAssets) {
                val asset = remote.readAsset(ref.assetId)
                    ?: throw CloudProtocolException("Remote image asset ${ref.assetId} is missing")
                downloadedAssets[ref.assetId] = asset.asset
            }
        }
        return CharacterSyncCommit(
            characterId = characterId,
            expectedRevision = expectedLocalRevision,
            replacement = document.data,
            syncedBaseRevision = remoteMetadata.revision,
        )
    }

    private fun markSynced(
        characterId: String,
        revision: String,
        originalBase: String?,
        advanceDescendant: Boolean = false,
    ): CharacterSyncCommit = CharacterSyncCommit(
        characterId = characterId,
        expectedRevision = revision,
        syncedBaseRevision = revision,
        originalBaseRevision = originalBase,
        allowDescendantBaseAdvance = advanceDescendant,
    )

    private fun reconcileSettings(
        snapshot: LocalSyncSnapshot,
        remoteSettings: CloudSettingsDocument?,
        conflicts: MutableList<SyncConflict>,
        conflictPolicy: ConflictPolicy,
        explicitResolution: SyncConflictResolution?,
    ): SettingsResult {
        val localValues = RoamingSettings.from(snapshot.settings)
        val localMetadata = requireNotNull(snapshot.journal.settingsMetadata)
        val localCloud = CloudSettingsDocument(localMetadata.toCloud(), localValues.toCloud())
        if (remoteSettings == null) {
            return SettingsResult(
                remote = localCloud,
                commit = SettingsSyncCommit(
                    expectedRevision = localMetadata.revision,
                    metadataAfter = localMetadata.copy(baseRevision = localMetadata.revision),
                    originalBaseRevision = localMetadata.baseRevision,
                    allowDescendantBaseAdvance = true,
                ),
            )
        }
        val remoteValues = remoteSettings.values.toLocal()
        if (remoteValues.revision() != remoteSettings.metadata.revision) {
            throw CloudProtocolException("Remote roaming settings revision does not match content")
        }
        return when (SyncMetadataManager.classify(
            localMetadata.revision,
            remoteSettings.metadata.revision,
            localMetadata.baseRevision,
        )) {
            SyncChangeState.SAME -> SettingsResult(
                remoteSettings,
                SettingsSyncCommit(
                    expectedRevision = localMetadata.revision,
                    metadataAfter = localMetadata.copy(baseRevision = localMetadata.revision),
                    originalBaseRevision = localMetadata.baseRevision,
                ),
            )
            SyncChangeState.LOCAL_ONLY -> SettingsResult(
                localCloud,
                SettingsSyncCommit(
                    expectedRevision = localMetadata.revision,
                    metadataAfter = localMetadata.copy(baseRevision = localMetadata.revision),
                    originalBaseRevision = localMetadata.baseRevision,
                    allowDescendantBaseAdvance = true,
                ),
            )
            SyncChangeState.REMOTE_ONLY -> SettingsResult(
                remoteSettings,
                SettingsSyncCommit(
                    expectedRevision = localMetadata.revision,
                    replacement = remoteValues,
                    metadataAfter = SettingsSyncMetadata(
                        updatedAt = remoteSettings.metadata.updatedAt,
                        revision = remoteSettings.metadata.revision,
                        writerId = remoteSettings.metadata.writerId,
                        baseRevision = remoteSettings.metadata.revision,
                    ),
                ),
            )
            SyncChangeState.CONFLICT -> {
                val resolution = explicitResolution ?: latestResolution(
                    conflictPolicy,
                    localMetadata.updatedAt,
                    localMetadata.revision,
                    localMetadata.writerId,
                    remoteSettings.metadata.updatedAt,
                    remoteSettings.metadata.revision,
                    remoteSettings.metadata.writerId,
                )
                when (resolution) {
                    SyncConflictResolution.KEEP_LOCAL -> SettingsResult(
                        localCloud,
                        SettingsSyncCommit(
                            expectedRevision = localMetadata.revision,
                            metadataAfter = localMetadata.copy(baseRevision = localMetadata.revision),
                            originalBaseRevision = localMetadata.baseRevision,
                            allowDescendantBaseAdvance = true,
                        ),
                        autoResolvedCount = if (explicitResolution == null) 1 else 0,
                    )
                    SyncConflictResolution.USE_REMOTE -> SettingsResult(
                        remoteSettings,
                        SettingsSyncCommit(
                            expectedRevision = localMetadata.revision,
                            replacement = remoteValues,
                            metadataAfter = SettingsSyncMetadata(
                                remoteSettings.metadata.updatedAt,
                                remoteSettings.metadata.revision,
                                remoteSettings.metadata.writerId,
                                remoteSettings.metadata.revision,
                            ),
                        ),
                        autoResolvedCount = if (explicitResolution == null) 1 else 0,
                    )
                    null -> {
                        conflicts += SyncConflict(
                            kind = SyncConflictKind.SETTINGS_DIVERGED,
                            localRevision = localMetadata.revision,
                            remoteRevision = remoteSettings.metadata.revision,
                            localUpdatedAt = localMetadata.updatedAt,
                            remoteUpdatedAt = remoteSettings.metadata.updatedAt,
                            localWriterId = localMetadata.writerId,
                            remoteWriterId = remoteSettings.metadata.writerId,
                            changedAreas = listOf(ConflictArea.SETTINGS),
                        )
                        SettingsResult(remoteSettings, null)
                    }
                }
            }
        }
    }

    private fun latestResolution(
        policy: ConflictPolicy,
        localUpdatedAt: Long,
        localRevision: String,
        localWriterId: String,
        remoteUpdatedAt: Long,
        remoteRevision: String,
        remoteWriterId: String,
    ): SyncConflictResolution? {
        if (policy != ConflictPolicy.LATEST_WINS) return null
        val comparison = compareConflictVersions(
            localUpdatedAt, localRevision, localWriterId,
            remoteUpdatedAt, remoteRevision, remoteWriterId,
        )
        return if (comparison >= 0) SyncConflictResolution.KEEP_LOCAL else SyncConflictResolution.USE_REMOTE
    }

    private suspend fun buildCharacterConflict(
        localData: AppData,
        characterId: String,
        localUpdatedAt: Long,
        localRevision: String,
        localWriterId: String,
        remoteMetadata: CloudCharacterMetadata,
    ): SyncConflict {
        val remoteData = remote.readCharacter(characterId)?.data
        return SyncConflict(
            kind = SyncConflictKind.CHARACTER_DIVERGED,
            characterId = characterId,
            characterName = characterName(localData, characterId) ?: remoteData?.let { characterName(it, characterId) },
            localRevision = localRevision,
            remoteRevision = remoteMetadata.revision,
            localUpdatedAt = localUpdatedAt,
            remoteUpdatedAt = remoteMetadata.updatedAt,
            localWriterId = localWriterId,
            remoteWriterId = remoteMetadata.writerId,
            changedAreas = remoteData?.let { changedAreas(localData, it, characterId) }.orEmpty(),
        )
    }

    private suspend fun remoteCharacterName(characterId: String): String? =
        remote.readCharacter(characterId)?.data?.let { characterName(it, characterId) }

    private fun characterName(data: AppData, characterId: String): String? =
        data.characters.firstOrNull { it.id == characterId }?.name

    private fun changedAreas(localData: AppData, remoteData: AppData, characterId: String): List<ConflictArea> = buildList {
        val localCharacter = localData.characters.firstOrNull { it.id == characterId }
        val remoteCharacter = remoteData.characters.firstOrNull { it.id == characterId }
        if (
            localCharacter?.copy(image = null, imageUri = null, diceTableImage = null) !=
            remoteCharacter?.copy(image = null, imageUri = null, diceTableImage = null)
        ) add(ConflictArea.PROFILE)
        if (
            localCharacter?.image != remoteCharacter?.image ||
            localCharacter?.diceTableImage != remoteCharacter?.diceTableImage
        ) add(ConflictArea.IMAGE)
        if (localData.modifiers.filter { it.characterId == characterId } != remoteData.modifiers.filter { it.characterId == characterId }) add(ConflictArea.MODIFIERS)
        if (
            localData.groups.filter { it.characterId == characterId } != remoteData.groups.filter { it.characterId == characterId } ||
            localData.rolls.filter { it.characterId == characterId } != remoteData.rolls.filter { it.characterId == characterId }
        ) add(ConflictArea.ROLLS)
        if (localData.diceStyles.filter { it.characterId == characterId } != remoteData.diceStyles.filter { it.characterId == characterId }) add(ConflictArea.DICE_STYLES)
        if (localData.logs.filter { it.characterId == characterId } != remoteData.logs.filter { it.characterId == characterId }) add(ConflictArea.HISTORY)
    }

    private fun pendingCount(snapshot: LocalSyncSnapshot): Int =
        snapshot.data.characterSyncMetadata.count { it.baseRevision != it.revision } +
            snapshot.journal.pendingDeletions.size +
            if (snapshot.journal.settingsMetadata?.let { it.baseRevision != it.revision } != false) 1 else 0

    private fun errorKind(error: Exception): SyncErrorKind = when (error) {
        is CloudAuthorizationException -> SyncErrorKind.AUTHORIZATION
        is CloudTransientException -> SyncErrorKind.TRANSIENT
        is CloudSchemaMismatchException -> SyncErrorKind.SCHEMA
        is CloudProtocolException, is CloudRepositoryException -> SyncErrorKind.REMOTE_PROTOCOL
        else -> SyncErrorKind.UNKNOWN
    }

    private fun SettingsSyncMetadata.toCloud() = CloudSettingsMetadata(updatedAt, revision, writerId)
    private fun RoamingSettings.toCloud() = CloudRoamingSettings(showRollButton, rollButtonPosition, logRetention)
    private fun CloudRoamingSettings.toLocal() = RoamingSettings(showRollButton, rollButtonPosition, logRetention)

    private data class SettingsResult(
        val remote: CloudSettingsDocument,
        val commit: SettingsSyncCommit?,
        val autoResolvedCount: Int = 0,
    )

    companion object {
        const val SETTINGS_CONFLICT_KEY = "__settings__"

        internal fun compareConflictVersions(
            localUpdatedAt: Long,
            localRevision: String,
            localWriterId: String,
            remoteUpdatedAt: Long,
            remoteRevision: String,
            remoteWriterId: String,
        ): Int = compareValues(localUpdatedAt, remoteUpdatedAt)
            .takeIf { it != 0 }
            ?: localRevision.compareTo(remoteRevision).takeIf { it != 0 }
            ?: localWriterId.compareTo(remoteWriterId)
    }
}
