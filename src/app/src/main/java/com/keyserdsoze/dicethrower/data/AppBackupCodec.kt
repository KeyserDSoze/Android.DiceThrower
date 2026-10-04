package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import org.json.JSONObject

/**
 * Portable textual backup for Dice Thrower configuration and roll history.
 *
 * Character imageUri values are preserved as references, but Android document URIs are not
 * guaranteed to be readable on another device. A future backup version can embed image bytes
 * without changing the application data model.
 */
object AppBackupCodec {
    const val FORMAT = "dice-thrower-backup"
    const val FORMAT_VERSION = 1

    data class BackupPayload(
        val data: AppData,
        val settings: AppSettings,
        val language: String,
        val exportedAt: Long,
    )

    fun encode(
        data: AppData,
        settings: AppSettings,
        language: String,
        exportedAt: Long = System.currentTimeMillis(),
    ): String {
        AppDataValidator.requireValid(data)

        return JSONObject()
            .put("format", FORMAT)
            .put("formatVersion", FORMAT_VERSION)
            .put("exportedAt", exportedAt)
            .put("imageMode", "uri-reference")
            .put("language", language)
            .put("data", AppDataJsonCodec.encodeData(data))
            .put("settings", AppDataJsonCodec.encodeSettings(settings))
            .toString(2)
    }

    fun decode(raw: String): BackupPayload {
        require(raw.isNotBlank()) { "Backup file is empty" }
        val root = JSONObject(raw)
        require(root.optString("format") == FORMAT) { "Not a Dice Thrower backup" }
        require(root.optInt("formatVersion", -1) == FORMAT_VERSION) {
            "Unsupported Dice Thrower backup version"
        }

        val dataObject = root.optJSONObject("data")
            ?: throw IllegalArgumentException("Backup does not contain application data")
        val settingsObject = root.optJSONObject("settings") ?: JSONObject()
        val data = AppDataJsonCodec.decodeData(dataObject)
        AppDataValidator.requireValid(data)

        return BackupPayload(
            data = data,
            settings = AppDataJsonCodec.decodeSettings(settingsObject),
            language = root.optString("language", "system"),
            exportedAt = root.optLong("exportedAt", 0L),
        )
    }
}
