package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.model.CharacterImageRef
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringWriter
import java.io.Writer
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Portable textual backup for Dice Thrower configuration and roll history.
 *
 * Version 2 embeds referenced app-owned character images so restore is portable across devices.
 * Legacy imageUri values remain readable for version-1 compatibility but are not portable assets.
 */
class BackupTooLargeException : IllegalArgumentException("Backup file is too large")

object AppBackupCodec {
    const val FORMAT = "dice-thrower-backup"
    const val FORMAT_VERSION = 2
    const val MAX_BACKUP_BYTES = 256L * 1024 * 1024

    data class BackupPayload(
        val data: AppData,
        val settings: AppSettings,
        val language: String,
        val exportedAt: Long,
        val imageAssets: List<PortableCharacterImageAsset> = emptyList(),
    )

    fun encode(
        data: AppData,
        settings: AppSettings,
        language: String,
        exportedAt: Long = System.currentTimeMillis(),
        imageAssets: List<PortableCharacterImageAsset> = emptyList(),
    ): String = StringWriter().also { encodeTo(it, data, settings, language, exportedAt, imageAssets) }.toString()

    /**
     * Writes the compact backup JSON to [out], one image at a time, so the whole Base64 payload
     * is never held in memory at once. The output is the same v2 document [encode] returns.
     */
    fun encodeTo(
        out: Writer,
        data: AppData,
        settings: AppSettings,
        language: String,
        exportedAt: Long = System.currentTimeMillis(),
        imageAssets: List<PortableCharacterImageAsset> = emptyList(),
    ) {
        AppDataValidator.requireValid(data)
        CharacterImageAssets.requirePortableAssetsMatch(data, imageAssets)

        val head = JSONObject()
            .put("format", FORMAT)
            .put("formatVersion", FORMAT_VERSION)
            .put("exportedAt", exportedAt)
            .put("imageMode", "portable-assets")
            .put("language", language)
            .put("data", AppDataJsonCodec.encodeDataForSync(data))
            .put("settings", AppDataJsonCodec.encodeSettings(settings))
            .toString()
        check(head.endsWith("}"))
        out.write(head, 0, head.length - 1)
        out.write(",\"imageAssets\":[")
        imageAssets.sortedBy { it.ref.assetId }.forEachIndexed { index, asset ->
            if (index > 0) out.write(",")
            out.write(
                JSONObject()
                    .put("assetId", asset.ref.assetId)
                    .put("sha256", asset.ref.sha256)
                    .put("mimeType", asset.ref.mimeType)
                    .put("byteSize", asset.ref.byteSize)
                    .put("base64", Base64.getEncoder().encodeToString(asset.bytes))
                    .toString(),
            )
        }
        out.write("]}")
        out.flush()
    }

    /** Reads at most [maxBytes] bytes of UTF-8 text, failing with [BackupTooLargeException] beyond that. */
    fun readCapped(input: InputStream, maxBytes: Long = MAX_BACKUP_BYTES): String {
        var total = 0L
        val limited = object : InputStream() {
            override fun read(): Int {
                val value = input.read()
                if (value >= 0 && ++total > maxBytes) throw BackupTooLargeException()
                return value
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val count = input.read(buffer, offset, length)
                if (count > 0) {
                    total += count
                    if (total > maxBytes) throw BackupTooLargeException()
                }
                return count
            }
        }
        val builder = StringBuilder()
        InputStreamReader(limited, Charsets.UTF_8).use { reader ->
            val chars = CharArray(64 * 1024)
            while (true) {
                val count = reader.read(chars)
                if (count < 0) break
                builder.append(chars, 0, count)
            }
        }
        return builder.toString()
    }

    fun decode(raw: String): BackupPayload {
        require(raw.isNotBlank()) { "Backup file is empty" }
        val root = JsonNestingGuard.parse(raw) { JSONObject(it) }
        require(root.optString("format") == FORMAT) { "Not a Dice Thrower backup" }
        val formatVersion = root.optInt("formatVersion", -1)
        require(formatVersion in 1..FORMAT_VERSION) {
            "Unsupported Dice Thrower backup version"
        }

        val dataObject = root.optJSONObject("data")
            ?: throw IllegalArgumentException("Backup does not contain application data")
        val settingsObject = root.optJSONObject("settings") ?: JSONObject()
        val decodedData = AppDataJsonCodec.decodeData(dataObject)
        val data = if (formatVersion == 1) migrateVersionOneMetadata(decodedData) else decodedData
        AppDataValidator.requireValid(data)
        val imageAssets = if (formatVersion >= 2) decodePortableAssets(root.optJSONArray("imageAssets")) else emptyList()
        CharacterImageAssets.requirePortableAssetsMatch(data, imageAssets)

        return BackupPayload(
            data = data,
            settings = AppDataJsonCodec.decodeSettings(settingsObject),
            language = root.optString("language", "system"),
            exportedAt = root.optLong("exportedAt", 0L),
            imageAssets = imageAssets,
        )
    }

    private fun decodePortableAssets(array: JSONArray?): List<PortableCharacterImageAsset> {
        if (array == null) return emptyList()
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val ref = CharacterImageRef(
                assetId = item.getString("assetId"),
                sha256 = item.getString("sha256"),
                mimeType = item.getString("mimeType"),
                byteSize = item.getLong("byteSize"),
            )
            val bytes = runCatching { Base64.getDecoder().decode(item.getString("base64")) }
                .getOrElse { throw IllegalArgumentException("Invalid character image Base64", it) }
            require(CharacterImageAssets.validate(ref, bytes)) { "Corrupt character image asset ${ref.assetId}" }
            PortableCharacterImageAsset(ref, bytes)
        }
    }

    private fun migrateVersionOneMetadata(data: AppData): AppData {
        val legacyImageCharacterIds = data.characters
            .filter { it.image == null && !it.imageUri.isNullOrBlank() }
            .mapTo(mutableSetOf()) { it.id }
        if (legacyImageCharacterIds.isEmpty()) return data
        // v4 revisions included source-device imageUri values; v5 intentionally does not.
        // LocalStore will recreate metadata after it imports the URI or accepts the fallback.
        return data.copy(
            characterSyncMetadata = data.characterSyncMetadata.filterNot {
                it.characterId in legacyImageCharacterIds
            },
        )
    }

}
