package com.keyserdsoze.dicethrower.data

import android.content.Context
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.model.CharacterImageRef
import com.keyserdsoze.dicethrower.data.sync.LocalSyncJournal
import com.keyserdsoze.dicethrower.data.sync.PendingCharacterDeletion
import com.keyserdsoze.dicethrower.data.sync.RoamingSettings
import com.keyserdsoze.dicethrower.data.sync.SettingsSyncMetadata
import com.keyserdsoze.dicethrower.data.sync.SyncJournalStore
import org.json.JSONObject
import java.io.File
import java.util.UUID

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val imageAssetStore = CharacterImageAssetStore(context)
    private val writerIdStore = InstallationWriterIdStore(
        File(context.noBackupFilesDir, WRITER_ID_FILE),
    )
    private val syncJournalStore = SyncJournalStore(context)

    fun loadData(): AppData {
        val raw = prefs.getString(KEY_DATA, null) ?: return AppData()
        val decoded = runCatching {
            AppDataJsonCodec.decodeData(JSONObject(raw))
        }.getOrDefault(AppData())
        val portableImages = imageAssetStore.migrateLegacyImages(decoded)
        val migrated = SyncMetadataManager.ensureMetadata(
            data = portableImages,
            writerId = installationWriterId(),
            now = System.currentTimeMillis(),
        )
        if (migrated != decoded) persistData(migrated)
        imageAssetStore.cleanupOrphans(migrated)
        return migrated
    }

    fun saveData(data: AppData): AppData {
        val previous = loadData()
        val now = System.currentTimeMillis()
        val writerId = installationWriterId()
        val stamped = SyncMetadataManager.reconcileLocalEdit(
            previous = previous,
            proposed = data,
            writerId = writerId,
            now = now,
        )
        AppDataValidator.requireValid(stamped)
        persistData(stamped)
        recordDataTransition(previous, stamped, writerId, now)
        imageAssetStore.cleanupOrphans(stamped)
        return stamped
    }

    fun installationWriterId(): String = writerIdStore.getOrCreate()

    fun loadSettings(): AppSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return runCatching {
            AppDataJsonCodec.decodeSettings(JSONObject(raw))
        }.getOrDefault(AppSettings())
    }

    fun saveSettings(settings: AppSettings) {
        val previous = loadSettings()
        prefs.edit()
            .putString(KEY_SETTINGS, AppDataJsonCodec.encodeSettings(settings).toString())
            .apply()
        recordSettingsTransition(previous, settings, installationWriterId(), System.currentTimeMillis())
    }

    fun replaceAll(
        data: AppData,
        settings: AppSettings,
        imageAssets: List<PortableCharacterImageAsset> = emptyList(),
    ) {
        val previousData = loadData()
        val previousSettings = loadSettings()
        val now = System.currentTimeMillis()
        val writerId = installationWriterId()
        val migrated = SyncMetadataManager.ensureMetadata(
            data = data,
            writerId = writerId,
            now = now,
        )
        AppDataValidator.requireValid(migrated)
        imageAssetStore.restorePortableAssets(imageAssets)
        imageAssetStore.requireReferencedAssetsAvailable(migrated)
        prefs.edit()
            .putString(KEY_DATA, AppDataJsonCodec.encodeData(migrated).toString())
            .putString(KEY_SETTINGS, AppDataJsonCodec.encodeSettings(settings).toString())
            .apply()
        recordDataTransition(previousData, migrated, writerId, now)
        recordSettingsTransition(previousSettings, settings, writerId, now)
        imageAssetStore.cleanupOrphans(migrated)
    }

    fun backupImageAssets(data: AppData = loadData()): List<PortableCharacterImageAsset> =
        imageAssetStore.exportReferenced(data)

    fun loadImageAsset(ref: CharacterImageRef): PortableCharacterImageAsset? =
        imageAssetStore.loadVerified(ref)?.let { PortableCharacterImageAsset(ref, it) }

    fun loadSyncJournal(): LocalSyncJournal = syncJournalStore.load()

    fun saveSyncJournal(journal: LocalSyncJournal) = syncJournalStore.save(journal)

    fun applySyncedData(data: AppData, imageAssets: List<PortableCharacterImageAsset>) {
        AppDataValidator.requireValid(data)
        imageAssetStore.restorePortableAssets(imageAssets)
        persistData(data)
        imageAssetStore.cleanupOrphans(data)
    }

    fun applySyncedSettings(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_SETTINGS, AppDataJsonCodec.encodeSettings(settings).toString())
            .apply()
    }

    private fun recordDataTransition(
        previous: AppData,
        current: AppData,
        writerId: String,
        now: Long,
    ) {
        val currentIds = current.characters.mapTo(mutableSetOf()) { it.id }
        val journal = syncJournalStore.load()
        val pendingById = journal.pendingDeletions.associateByTo(linkedMapOf()) { it.characterId }
        previous.characters.filterNot { it.id in currentIds }.forEach { removed ->
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
        val updated = pendingById.values.sortedBy { it.characterId }
        if (updated != journal.pendingDeletions) {
            syncJournalStore.save(journal.copy(pendingDeletions = updated))
        }
    }

    private fun recordSettingsTransition(
        previous: AppSettings,
        current: AppSettings,
        writerId: String,
        now: Long,
    ) {
        if (RoamingSettings.from(previous) == RoamingSettings.from(current)) return
        val journal = syncJournalStore.load()
        val prior = journal.settingsMetadata
        val metadata = SettingsSyncMetadata(
            updatedAt = maxOf(now, (prior?.updatedAt ?: -1L) + 1L),
            revision = RoamingSettings.from(current).revision(),
            writerId = writerId,
            baseRevision = prior?.baseRevision,
        )
        syncJournalStore.save(journal.copy(settingsMetadata = metadata))
    }

    private fun persistData(data: AppData) {
        prefs.edit()
            .putString(KEY_DATA, AppDataJsonCodec.encodeData(data).toString())
            .apply()
    }

    companion object {
        private const val PREFS = "dice_thrower_store"
        private const val KEY_DATA = "data_v1"
        private const val KEY_SETTINGS = "settings_v1"
        private const val WRITER_ID_FILE = "installation_writer_id_v1"
    }
}

internal class InstallationWriterIdStore(
    private val file: File,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    @Synchronized
    fun getOrCreate(): String {
        runCatching { file.takeIf(File::isFile)?.readText()?.trim() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val created = idFactory().trim()
        require(created.isNotBlank()) { "Writer ID cannot be blank" }
        file.parentFile?.mkdirs()
        file.writeText(created)
        return created
    }
}
