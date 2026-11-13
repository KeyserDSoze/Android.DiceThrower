package com.keyserdsoze.dicethrower.data

import android.content.Context
import android.content.SharedPreferences
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
    private val document = VersionedDocument(prefs, KEY_DATA, KEY_DATA_VERSION)
    private val imageAssetStore = CharacterImageAssetStore(context)
    private val writerIdStore = InstallationWriterIdStore(
        File(context.noBackupFilesDir, WRITER_ID_FILE),
    )
    private val syncJournalStore = SyncJournalStore(context)

    fun loadData(): AppData {
        val raw = document.read() ?: return AppData()
        val decoded = decodeStoredData(raw)
        val portableImages = SharedDiceStyleCatalog.migrateLegacy(imageAssetStore.migrateLegacyImages(decoded))
        val migrated = SyncMetadataManager.ensureMetadata(
            data = portableImages,
            writerId = installationWriterId(),
            now = System.currentTimeMillis(),
        )
        if (migrated != decoded) persistData(migrated)
        imageAssetStore.cleanupOrphans(migrated)
        return migrated
    }

    /** Version of the stored data document; it increases with every write. */
    fun dataVersion(): Long = document.version()

    /**
     * Saves [data] as the next local edit. A caller that passes the [expectedVersion] its copy was
     * loaded at gets a [StaleDataException] instead of overwriting newer stored content.
     */
    fun saveData(data: AppData, expectedVersion: Long? = null): AppData {
        document.requireVersion(expectedVersion)
        val previous = loadData()
        val now = System.currentTimeMillis()
        val writerId = installationWriterId()
        val stamped = SyncMetadataManager.reconcileLocalEdit(
            previous = previous,
            proposed = data,
            writerId = writerId,
            now = now,
        )
        AppDataValidator.requireValid(stamped, acceptedBaseline = previous)
        persistData(stamped)
        recordDataTransition(previous, stamped, writerId, now)
        imageAssetStore.cleanupOrphans(stamped)
        return stamped
    }

    fun installationWriterId(): String = writerIdStore.getOrCreate()

    fun loadSettings(): AppSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return decodeStoredSettings(raw)
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
        val stamped = SyncMetadataManager.ensureMetadata(
            data = SharedDiceStyleCatalog.migrateLegacy(data),
            writerId = writerId,
            now = now,
        )
        val migrated = SyncTransitions.neutralizeBaseRevisions(stamped)
        AppDataValidator.requireValid(migrated)
        imageAssetStore.restorePortableAssets(imageAssets)
        imageAssetStore.requireReferencedAssetsAvailable(migrated)
        document.write(AppDataJsonCodec.encodeData(migrated).toString()) {
            putString(KEY_SETTINGS, AppDataJsonCodec.encodeSettings(settings).toString())
        }
        // Characters absent from the backup are not deliberate deletions: never propagate them.
        recordDataTransition(previousData, migrated, writerId, now, recordDeletions = false)
        recordSettingsTransition(previousSettings, settings, writerId, now)
        requireReconciliationAfterRestore()
        imageAssetStore.cleanupOrphans(migrated)
    }

    fun backupImageAssets(data: AppData = loadData()): List<PortableCharacterImageAsset> =
        imageAssetStore.exportReferenced(data)

    fun loadImageAsset(ref: CharacterImageRef): PortableCharacterImageAsset? =
        imageAssetStore.loadVerified(ref)?.let { PortableCharacterImageAsset(ref, it) }

    fun loadSyncJournal(): LocalSyncJournal = syncJournalStore.load()

    fun saveSyncJournal(journal: LocalSyncJournal) = syncJournalStore.save(journal)

    fun applySyncedData(
        data: AppData,
        imageAssets: List<PortableCharacterImageAsset>,
        acceptedBaseline: AppData? = null,
    ) {
        AppDataValidator.requireValid(data, acceptedBaseline)
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
        recordDeletions: Boolean = true,
    ) {
        val journal = syncJournalStore.load()
        val updated = SyncTransitions.pendingDeletions(
            previous, current, journal.pendingDeletions, writerId, now, recordDeletions,
        )
        val previousRevision = SharedDiceStyleCatalog.revision(previous.diceStyles.filter { it.characterId == null })
        val currentRevision = SharedDiceStyleCatalog.revision(current.diceStyles.filter { it.characterId == null })
        val priorCatalog = journal.diceCatalogMetadata
        val catalog = if (previousRevision != currentRevision) SettingsSyncMetadata(
            updatedAt = maxOf(now, (priorCatalog?.updatedAt ?: -1L) + 1L),
            revision = currentRevision,
            writerId = writerId,
            baseRevision = priorCatalog?.baseRevision,
        ) else priorCatalog
        if (updated != journal.pendingDeletions || catalog != priorCatalog) {
            syncJournalStore.save(journal.copy(pendingDeletions = updated, diceCatalogMetadata = catalog))
        }
    }

    private fun requireReconciliationAfterRestore() {
        syncJournalStore.save(SyncTransitions.journalAfterRestore(syncJournalStore.load()))
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
        document.write(AppDataJsonCodec.encodeData(data).toString())
    }

    companion object {
        private const val PREFS = "dice_thrower_store"
        private const val KEY_DATA = "data_v1"
        private const val KEY_DATA_VERSION = "data_v1_version"
        private const val KEY_SETTINGS = "settings_v1"
        private const val WRITER_ID_FILE = "installation_writer_id_v1"
    }
}

/** Thrown when a caller saves data derived from a copy that is no longer the stored one. */
class StaleDataException : IllegalStateException("Stored data changed since this copy was loaded")

/**
 * A preferences-backed document whose version increases with every write. Callers compare it
 * with the version their copy was loaded at (optimistic concurrency), so a screen that missed an
 * import, a sync or another window's save cannot silently overwrite that newer content.
 */
internal class VersionedDocument(
    private val prefs: SharedPreferences,
    private val key: String,
    private val versionKey: String,
) {
    fun read(): String? = prefs.getString(key, null)

    fun version(): Long = prefs.getLong(versionKey, 0L)

    fun requireVersion(expected: Long?) {
        if (expected != null && expected != version()) throw StaleDataException()
    }

    @Synchronized
    fun write(raw: String, alsoWrite: SharedPreferences.Editor.() -> Unit = {}) {
        val editor = prefs.edit()
            .putString(key, raw)
            .putLong(versionKey, version() + 1)
        editor.alsoWrite()
        editor.apply()
    }
}

/** Stored data was accepted when it was written; newer rollability rules must not block loading it. */
internal fun decodeStoredData(raw: String): AppData =
    AppDataJsonCodec.decodeData(JSONObject(raw)).also { AppDataValidator.requireValid(it, acceptedBaseline = it) }

internal fun decodeStoredSettings(raw: String): AppSettings =
    AppDataJsonCodec.decodeSettings(JSONObject(raw))

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
        AtomicFileWriter.writeText(file, created)
        return created
    }
}
