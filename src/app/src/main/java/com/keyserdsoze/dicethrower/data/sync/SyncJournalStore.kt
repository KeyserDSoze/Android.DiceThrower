package com.keyserdsoze.dicethrower.data.sync

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class SyncJournalStore(context: Context) {
    private val fileStore = SyncJournalFileStore(File(context.noBackupFilesDir, FILE_NAME))

    @Synchronized
    fun load(): LocalSyncJournal = fileStore.load()

    @Synchronized
    fun save(journal: LocalSyncJournal) = fileStore.save(journal)

    private companion object {
        const val FILE_NAME = "cloud_sync_journal_v1.json"
    }
}

internal class SyncJournalFileStore(private val file: File) {
    fun load(): LocalSyncJournal {
        val raw = file.takeIf(File::isFile)?.readText() ?: return LocalSyncJournal()
        return decode(JSONObject(raw))
    }

    fun save(journal: LocalSyncJournal) {
        file.parentFile?.mkdirs()
        file.writeText(encode(journal).toString())
    }

    private fun encode(journal: LocalSyncJournal): JSONObject = JSONObject()
        .put("version", 1)
        .put("lastSuccessfulSyncAt", journal.lastSuccessfulSyncAt ?: JSONObject.NULL)
        .put("pendingDeletions", JSONArray().apply {
            journal.pendingDeletions.sortedBy { it.characterId }.forEach { deletion ->
                put(JSONObject()
                    .put("characterId", deletion.characterId)
                    .put("deletedAt", deletion.deletedAt)
                    .put("writerId", deletion.writerId)
                    .put("baseRevision", deletion.baseRevision)
                    .put("deletedRevision", deletion.deletedRevision))
            }
        })
        .put("settingsMetadata", journal.settingsMetadata?.let { metadata ->
            JSONObject()
                .put("updatedAt", metadata.updatedAt)
                .put("revision", metadata.revision)
                .put("writerId", metadata.writerId)
                .put("baseRevision", metadata.baseRevision ?: JSONObject.NULL)
        } ?: JSONObject.NULL)

    private fun decode(json: JSONObject): LocalSyncJournal {
        require(json.optInt("version", -1) == 1) { "Unsupported sync journal" }
        val deletions = json.optJSONArray("pendingDeletions") ?: JSONArray()
        val pending = List(deletions.length()) { index ->
            val item = deletions.getJSONObject(index)
            PendingCharacterDeletion(
                characterId = item.getString("characterId"),
                deletedAt = item.getLong("deletedAt"),
                writerId = item.getString("writerId"),
                baseRevision = item.getString("baseRevision"),
                deletedRevision = item.getString("deletedRevision"),
            ).also(::validateDeletion)
        }
        require(pending.map { it.characterId }.distinct().size == pending.size) { "Duplicate pending deletion" }
        val settings = json.optJSONObject("settingsMetadata")?.let { item ->
            SettingsSyncMetadata(
                updatedAt = item.getLong("updatedAt"),
                revision = item.getString("revision"),
                writerId = item.getString("writerId"),
                baseRevision = if (item.isNull("baseRevision")) null else item.getString("baseRevision"),
            ).also(::validateSettingsMetadata)
        }
        val lastSuccess = if (json.isNull("lastSuccessfulSyncAt")) null else json.getLong("lastSuccessfulSyncAt")
        require(lastSuccess == null || lastSuccess >= 0L) { "Invalid last sync timestamp" }
        return LocalSyncJournal(pending, settings, lastSuccess)
    }

    private fun validateDeletion(deletion: PendingCharacterDeletion) {
        require(deletion.characterId.isNotBlank() && deletion.writerId.isNotBlank())
        require(deletion.deletedAt >= 0L)
        require(deletion.baseRevision.matches(SHA256_REGEX) && deletion.deletedRevision.matches(SHA256_REGEX))
    }

    private fun validateSettingsMetadata(metadata: SettingsSyncMetadata) {
        require(metadata.updatedAt >= 0L && metadata.writerId.isNotBlank())
        require(metadata.revision.matches(SHA256_REGEX))
        require(metadata.baseRevision == null || metadata.baseRevision.matches(SHA256_REGEX))
    }

    private companion object {
        val SHA256_REGEX = Regex("[0-9a-f]{64}")
    }
}
