package com.keyserdsoze.dicethrower.data.sync

import com.keyserdsoze.dicethrower.data.CharacterImageAssets
import com.keyserdsoze.dicethrower.data.PortableCharacterImageAsset
import com.keyserdsoze.dicethrower.data.SyncMetadataManager
import com.keyserdsoze.dicethrower.data.cloud.CloudAssetDocument
import com.keyserdsoze.dicethrower.data.cloud.CloudCharacterDocument
import com.keyserdsoze.dicethrower.data.cloud.CloudCharacterMetadata
import com.keyserdsoze.dicethrower.data.cloud.CloudManifest
import com.keyserdsoze.dicethrower.data.cloud.CloudRemoteRepository
import com.keyserdsoze.dicethrower.data.cloud.CloudTransientException
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.model.CharacterImageRef
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.ConflictPolicy
import com.keyserdsoze.dicethrower.model.RollButtonPosition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncEngineTest {
    @Test
    fun deviceAUploadIsDownloadedByDeviceBWithoutDuplicateTransfers() = runBlocking {
        val remote = FakeRemote()
        val deviceA = FakeLocal("device-a", characterData("Hero", "device-a", 10L))
        val deviceB = FakeLocal("device-b", AppData())
        val engineA = engine(deviceA, remote, 100L)
        val engineB = engine(deviceB, remote, 200L)

        assertEquals(SyncStatusKind.SYNCED, engineA.sync().status.kind)
        assertEquals(SyncStatusKind.SYNCED, engineB.sync().status.kind)
        assertEquals("Hero", deviceB.data.characters.single().name)
        assertEquals(1, remote.putCharacterCalls)
        assertEquals(1, remote.readCharacterCalls)

        engineB.sync()

        assertEquals(1, remote.putCharacterCalls)
        assertEquals(1, remote.readCharacterCalls)
    }

    @Test
    fun offlineEditSurvivesFailureAndUploadsWhenRemoteRecovers() = runBlocking {
        val remote = FakeRemote()
        val local = FakeLocal("device-a", characterData("Hero", "device-a", 10L))
        val engine = engine(local, remote, 100L)
        engine.sync()
        local.rename("Offline Hero", 200L)
        val offlineRevision = local.data.characterSyncMetadata.single().revision
        remote.fail = true

        val failed = engine.sync()

        assertEquals(SyncStatusKind.ERROR, failed.status.kind)
        assertEquals("Offline Hero", local.data.characters.single().name)
        assertEquals(offlineRevision, local.data.characterSyncMetadata.single().revision)
        remote.fail = false

        val recovered = engine.sync()

        assertEquals(SyncStatusKind.SYNCED, recovered.status.kind)
        assertEquals(offlineRevision, remote.characters.getValue(CHARACTER_ID).metadata.revision)
    }

    @Test
    fun tombstonePropagatesDeletionWithoutResurrectingCharacter() = runBlocking {
        val remote = FakeRemote()
        val deviceA = FakeLocal("device-a", characterData("Hero", "device-a", 10L))
        val deviceB = FakeLocal("device-b", AppData())
        val engineA = engine(deviceA, remote, 100L)
        val engineB = engine(deviceB, remote, 200L)
        engineA.sync()
        engineB.sync()
        deviceA.deleteCharacter(300L)

        assertEquals(SyncStatusKind.SYNCED, engineA.sync().status.kind)
        assertTrue(remote.manifest!!.tombstones.any { it.characterId == CHARACTER_ID })
        assertNull(remote.characters[CHARACTER_ID])

        assertEquals(SyncStatusKind.SYNCED, engineB.sync().status.kind)
        assertTrue(deviceB.data.characters.isEmpty())
        assertTrue(deviceB.journal.pendingDeletions.isEmpty())
    }

    @Test
    fun concurrentCharacterEditsSurfaceConflictAndPreserveLocalCopy() = runBlocking {
        val remote = FakeRemote()
        val deviceA = FakeLocal("device-a", characterData("Hero", "device-a", 10L))
        val deviceB = FakeLocal("device-b", AppData())
        val engineA = engine(deviceA, remote, 100L)
        val engineB = engine(deviceB, remote, 200L)
        engineA.sync()
        engineB.sync()
        deviceA.rename("Hero A", 300L)
        deviceB.rename("Hero B", 310L)
        engineA.sync()

        val result = engineB.sync()

        assertEquals(SyncStatusKind.CONFLICT, result.status.kind)
        assertEquals(SyncConflictKind.CHARACTER_DIVERGED, result.status.conflicts.single().kind)
        assertEquals("Hero B", deviceB.data.characters.single().name)
        assertEquals("Hero A", remote.characters.getValue(CHARACTER_ID).data.characters.single().name)
    }

    @Test
    fun manualKeepLocalResolvesConflictAndDoesNotRepeatOnNextSync() = runBlocking {
        val remote = FakeRemote()
        val deviceA = FakeLocal("device-a", characterData("Hero", "device-a", 10L))
        val deviceB = FakeLocal("device-b", AppData())
        val engineA = engine(deviceA, remote, 100L)
        val engineB = engine(deviceB, remote, 200L)
        engineA.sync()
        engineB.sync()
        deviceA.rename("Hero A", 300L)
        deviceB.rename("Hero B", 310L)
        engineA.sync()

        val resolved = engineB.sync(
            resolutions = mapOf(CHARACTER_ID to SyncConflictResolution.KEEP_LOCAL),
        )

        assertEquals(SyncStatusKind.SYNCED, resolved.status.kind)
        assertEquals("Hero B", remote.characters.getValue(CHARACTER_ID).data.characters.single().name)
        assertEquals(
            deviceB.data.characterSyncMetadata.single().revision,
            deviceB.data.characterSyncMetadata.single().baseRevision,
        )
        assertEquals(SyncStatusKind.SYNCED, engineB.sync().status.kind)
    }

    @Test
    fun latestWinsUsesUpdatedAtOnlyAfterRealConflict() = runBlocking {
        val remote = FakeRemote()
        val deviceA = FakeLocal("device-a", characterData("Hero", "device-a", 10L))
        val deviceB = FakeLocal("device-b", AppData())
        val engineA = engine(deviceA, remote, 100L)
        val engineB = engine(deviceB, remote, 200L)
        engineA.sync()
        engineB.sync()
        deviceA.rename("Older remote edit", 300L)
        deviceB.rename("Newer local edit", 400L)
        engineA.sync()

        val result = engineB.sync(ConflictPolicy.LATEST_WINS)

        assertEquals(SyncStatusKind.SYNCED, result.status.kind)
        assertEquals(1, result.status.autoResolvedCount)
        assertEquals("Newer local edit", remote.characters.getValue(CHARACTER_ID).data.characters.single().name)
    }

    @Test
    fun latestWinsDownloadsNewerRemoteVersion() = runBlocking {
        val remote = FakeRemote()
        val deviceA = FakeLocal("device-a", characterData("Hero", "device-a", 10L))
        val deviceB = FakeLocal("device-b", AppData())
        val engineA = engine(deviceA, remote, 100L)
        val engineB = engine(deviceB, remote, 200L)
        engineA.sync()
        engineB.sync()
        deviceB.rename("Older local edit", 300L)
        deviceA.rename("Newer remote edit", 400L)
        engineA.sync()

        val result = engineB.sync(ConflictPolicy.LATEST_WINS)

        assertEquals(SyncStatusKind.SYNCED, result.status.kind)
        assertEquals("Newer remote edit", deviceB.data.characters.single().name)
        assertEquals(1, result.status.autoResolvedCount)
    }

    @Test
    fun latestWinsTieBreakIsDeterministicByRevisionThenWriter() {
        assertTrue(
            CloudSyncEngine.compareConflictVersions(100L, "bbb", "device-a", 100L, "aaa", "device-z") > 0,
        )
        assertTrue(
            CloudSyncEngine.compareConflictVersions(100L, "aaa", "device-z", 100L, "aaa", "device-a") > 0,
        )
        assertEquals(
            0,
            CloudSyncEngine.compareConflictVersions(100L, "aaa", "device-a", 100L, "aaa", "device-a"),
        )
    }

    @Test
    fun roamingSettingsSyncWhileDeviceSpecificPreferencesStayLocal() = runBlocking {
        val remote = FakeRemote()
        val deviceA = FakeLocal("device-a", AppData(), AppSettings(themeMode = com.keyserdsoze.dicethrower.model.ThemeMode.DARK))
        val deviceB = FakeLocal("device-b", AppData(), AppSettings(themeMode = com.keyserdsoze.dicethrower.model.ThemeMode.LIGHT))
        val engineA = engine(deviceA, remote, 100L)
        val engineB = engine(deviceB, remote, 200L)
        engineA.sync()
        engineB.sync()
        deviceA.editSettings(
            deviceA.settings.copy(
                showRollButton = false,
                rollButtonPosition = RollButtonPosition.TOP_LEFT,
                logRetention = 42,
            ),
            300L,
        )
        engineA.sync()

        assertEquals(SyncStatusKind.SYNCED, engineB.sync().status.kind)
        assertEquals(false, deviceB.settings.showRollButton)
        assertEquals(RollButtonPosition.TOP_LEFT, deviceB.settings.rollButtonPosition)
        assertEquals(42, deviceB.settings.logRetention)
        assertEquals(com.keyserdsoze.dicethrower.model.ThemeMode.LIGHT, deviceB.settings.themeMode)
        assertEquals(true, deviceB.settings.shakeEnabled)
        assertEquals(true, deviceB.settings.animationsEnabled)
    }

    @Test
    fun currentStatusReturnsErrorWhenLocalSnapshotFails() {
        val failingLocal = object : SyncLocalGateway {
            override fun snapshot(now: Long): LocalSyncSnapshot = error("corrupt sync journal")

            override fun readImageAsset(ref: CharacterImageRef): PortableCharacterImageAsset? = null

            override fun commit(plan: LocalSyncCommitPlan): LocalSyncCommitResult = error("not used")
        }

        val status = CloudSyncEngine(failingLocal, FakeRemote()) { 100L }.currentStatus(true)

        assertEquals(SyncStatusKind.ERROR, status.kind)
        assertEquals(SyncErrorKind.UNKNOWN, status.error)
    }

    @Test
    fun syncReturnsErrorWhenInitialSnapshotFails() = runBlocking {
        val failingLocal = object : SyncLocalGateway {
  override fun snapshot(now: Long): LocalSyncSnapshot = error("corrupt sync journal")

  override fun readImageAsset(ref: CharacterImageRef): PortableCharacterImageAsset? = null

  override fun commit(plan: LocalSyncCommitPlan): LocalSyncCommitResult = error("not used")
        }

        val result = CloudSyncEngine(failingLocal, FakeRemote()) { 100L }.sync()

        assertEquals(SyncStatusKind.ERROR, result.status.kind)
        assertEquals(SyncErrorKind.UNKNOWN, result.status.error)
    }

    private fun engine(local: FakeLocal, remote: FakeRemote, now: Long): CloudSyncEngine =
        CloudSyncEngine(local, remote) { now }

    private fun characterData(name: String, writer: String, now: Long): AppData =
        SyncMetadataManager.ensureMetadata(
            AppData(characters = listOf(CharacterProfile(CHARACTER_ID, name))),
            writer,
            now,
        )

    private class FakeRemote : CloudRemoteRepository {
        var manifest: CloudManifest? = null
        val characters = linkedMapOf<String, CloudCharacterDocument>()
        val assets = linkedMapOf<String, CloudAssetDocument>()
        var fail = false
        var putCharacterCalls = 0
        var readCharacterCalls = 0

        private fun checkAvailable() {
            if (fail) throw CloudTransientException("offline")
        }

        override suspend fun deleteAllData(): Int {
            checkAvailable()
            val count = characters.size + assets.size + if (manifest != null) 1 else 0
            characters.clear()
            assets.clear()
            manifest = null
            return count
        }

        override suspend fun readManifest(): CloudManifest? = manifest.also { checkAvailable() }
        override suspend fun putManifest(manifest: CloudManifest) {
            checkAvailable()
            this.manifest = manifest
        }
        override suspend fun listCharacters(): List<CloudCharacterMetadata> {
            checkAvailable()
            return characters.values.map { it.metadata }
        }
        override suspend fun readCharacter(characterId: String): CloudCharacterDocument? {
            checkAvailable()
            readCharacterCalls++
            return characters[characterId]
        }
        override suspend fun putCharacter(document: CloudCharacterDocument) {
            checkAvailable()
            putCharacterCalls++
            characters[document.metadata.characterId] = document
        }
        override suspend fun deleteCharacter(characterId: String): Boolean {
            checkAvailable()
            return characters.remove(characterId) != null
        }
        override suspend fun listAssets(): List<CharacterImageRef> {
            checkAvailable()
            return assets.values.map { it.asset.ref }
        }
        override suspend fun readAsset(assetId: String): CloudAssetDocument? {
            checkAvailable()
            return assets[assetId]
        }
        override suspend fun putAsset(document: CloudAssetDocument) {
            checkAvailable()
            assets[document.asset.ref.assetId] = document
        }
        override suspend fun deleteAsset(assetId: String): Boolean {
            checkAvailable()
            return assets.remove(assetId) != null
        }
    }

    private class FakeLocal(
        private val writerId: String,
        var data: AppData,
        var settings: AppSettings = AppSettings(),
    ) : SyncLocalGateway {
        var journal = LocalSyncJournal()
        private val assets = mutableMapOf<String, PortableCharacterImageAsset>()

        override fun snapshot(now: Long): LocalSyncSnapshot {
            if (journal.settingsMetadata == null) {
                val roaming = RoamingSettings.from(settings)
                journal = journal.copy(
                    settingsMetadata = SettingsSyncMetadata(now, roaming.revision(), writerId),
                )
            }
            return LocalSyncSnapshot(data, settings, journal, writerId)
        }

        override fun readImageAsset(ref: CharacterImageRef): PortableCharacterImageAsset? = assets[ref.assetId]

        override fun commit(plan: LocalSyncCommitPlan): LocalSyncCommitResult {
            var dataChanged = false
            plan.characters.forEach { change ->
                val current = data.characterSyncMetadata.firstOrNull { it.characterId == change.characterId }
                if (current?.revision != change.expectedRevision) return@forEach
                data = when {
                    change.delete -> remove(data, change.characterId)
                    change.replacement != null -> replace(data, change.replacement, change.syncedBaseRevision)
                    change.syncedBaseRevision != null && current != null -> data.copy(
                        characterSyncMetadata = data.characterSyncMetadata.map {
                            if (it.characterId == change.characterId) it.copy(baseRevision = change.syncedBaseRevision) else it
                        },
                    )
                    else -> data
                }
                dataChanged = true
            }
            plan.downloadedAssets.forEach { assets[it.ref.assetId] = it }
            var settingsChanged = false
            plan.settings?.let { change ->
                if (journal.settingsMetadata?.revision == change.expectedRevision) {
                    change.replacement?.let {
                        settings = it.applyTo(settings)
                        settingsChanged = true
                    }
                    journal = journal.copy(settingsMetadata = change.metadataAfter)
                }
            }
            val resolved = plan.resolvedDeletions.associateBy { it.characterId }
            journal = journal.copy(
                pendingDeletions = journal.pendingDeletions.filterNot { resolved[it.characterId] == it },
                lastSuccessfulSyncAt = plan.successfulAt,
            )
            return LocalSyncCommitResult(
                dataChanged,
                settingsChanged,
                emptySet(),
                false,
                snapshot(plan.successfulAt),
            )
        }

        fun rename(name: String, now: Long) {
            data = SyncMetadataManager.reconcileLocalEdit(
                data,
                data.copy(characters = data.characters.map { it.copy(name = name) }),
                writerId,
                now,
            )
        }

        fun deleteCharacter(now: Long) {
            val metadata = data.characterSyncMetadata.single()
            journal = journal.copy(
                pendingDeletions = listOf(
                    PendingCharacterDeletion(
                        CHARACTER_ID,
                        now,
                        writerId,
                        metadata.baseRevision ?: metadata.revision,
                        metadata.revision,
                    ),
                ),
            )
            data = AppData()
        }

        fun editSettings(updated: AppSettings, now: Long) {
            val prior = journal.settingsMetadata
            settings = updated
            journal = journal.copy(
                settingsMetadata = SettingsSyncMetadata(
                    now,
                    RoamingSettings.from(updated).revision(),
                    writerId,
                    prior?.baseRevision,
                ),
            )
        }

        private fun remove(source: AppData, id: String) = source.copy(
            characters = source.characters.filterNot { it.id == id },
            modifiers = source.modifiers.filterNot { it.characterId == id },
            groups = source.groups.filterNot { it.characterId == id },
            rolls = source.rolls.filterNot { it.characterId == id },
            logs = source.logs.filterNot { it.characterId == id },
            diceStyles = source.diceStyles.filterNot { it.characterId == id },
            characterSyncMetadata = source.characterSyncMetadata.filterNot { it.characterId == id },
        )

        private fun replace(source: AppData, incoming: AppData, base: String?): AppData {
            val id = incoming.characters.single().id
            val clean = remove(source, id)
            return clean.copy(
                characters = clean.characters + incoming.characters,
                modifiers = clean.modifiers + incoming.modifiers,
                groups = clean.groups + incoming.groups,
                rolls = clean.rolls + incoming.rolls,
                logs = clean.logs + incoming.logs,
                diceStyles = clean.diceStyles + incoming.diceStyles,
                characterSyncMetadata = clean.characterSyncMetadata +
                    incoming.characterSyncMetadata.single().copy(baseRevision = base),
            )
        }
    }

    private companion object {
        const val CHARACTER_ID = "hero"
    }
}
