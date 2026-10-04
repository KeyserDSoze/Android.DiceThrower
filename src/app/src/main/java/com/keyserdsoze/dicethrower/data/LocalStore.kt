package com.keyserdsoze.dicethrower.data

import android.content.Context
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import org.json.JSONObject
import java.io.File
import java.util.UUID

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val writerIdStore = InstallationWriterIdStore(
        File(context.noBackupFilesDir, WRITER_ID_FILE),
    )

    fun loadData(): AppData {
        val raw = prefs.getString(KEY_DATA, null) ?: return AppData()
        val decoded = runCatching {
            AppDataJsonCodec.decodeData(JSONObject(raw))
        }.getOrDefault(AppData())
        val migrated = SyncMetadataManager.ensureMetadata(
            data = decoded,
            writerId = installationWriterId(),
            now = System.currentTimeMillis(),
        )
        if (migrated != decoded) persistData(migrated)
        return migrated
    }

    fun saveData(data: AppData): AppData {
        val stamped = SyncMetadataManager.reconcileLocalEdit(
            previous = loadData(),
            proposed = data,
            writerId = installationWriterId(),
            now = System.currentTimeMillis(),
        )
        AppDataValidator.requireValid(stamped)
        persistData(stamped)
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
        prefs.edit()
            .putString(KEY_SETTINGS, AppDataJsonCodec.encodeSettings(settings).toString())
            .apply()
    }

    fun replaceAll(
        data: AppData,
        settings: AppSettings,
    ) {
        val migrated = SyncMetadataManager.ensureMetadata(
            data = data,
            writerId = installationWriterId(),
            now = System.currentTimeMillis(),
        )
        AppDataValidator.requireValid(migrated)
        prefs.edit()
            .putString(KEY_DATA, AppDataJsonCodec.encodeData(migrated).toString())
            .putString(KEY_SETTINGS, AppDataJsonCodec.encodeSettings(settings).toString())
            .apply()
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
