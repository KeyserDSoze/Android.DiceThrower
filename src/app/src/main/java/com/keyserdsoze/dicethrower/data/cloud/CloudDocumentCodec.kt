package com.keyserdsoze.dicethrower.data.cloud

import com.keyserdsoze.dicethrower.data.AppDataJsonCodec
import com.keyserdsoze.dicethrower.data.AppDataValidator
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.RollButtonPosition
import org.json.JSONArray
import org.json.JSONObject

object CloudDocumentCodec {
    fun buildCharacterDocument(data: AppData, characterId: String): CloudCharacterDocument {
        val character = requireNotNull(data.characters.firstOrNull { it.id == characterId }) {
            "Unknown character $characterId"
        }
        val sync = requireNotNull(data.characterSyncMetadata.firstOrNull { it.characterId == characterId }) {
            "Character $characterId has no sync metadata"
        }
        val slice = AppData(
            characters = listOf(character.copy(imageUri = null)),
            modifiers = data.modifiers.filter { it.characterId == characterId },
            groups = data.groups.filter { it.characterId == characterId },
            rolls = data.rolls.filter { it.characterId == characterId },
            logs = data.logs.filter { it.characterId == characterId },
            diceStyles = data.diceStyles.filter { it.characterId == characterId },
            characterSyncMetadata = listOf(sync),
        )
        AppDataValidator.requireValid(slice)
        return CloudCharacterDocument(
            metadata = CloudCharacterMetadata(
                characterId = characterId,
                updatedAt = sync.updatedAt,
                revision = sync.revision,
                writerId = sync.writerId,
                assetIds = listOfNotNull(character.image?.assetId, character.diceTableImage?.assetId).distinct(),
            ),
            data = slice,
        )
    }

    fun encodeCharacter(document: CloudCharacterDocument): ByteArray {
        requireSupportedSchema(document.schemaVersion)
        validateCharacterDocument(document)
        return JSONObject()
            .put("kind", "character")
            .put("schemaVersion", document.schemaVersion)
            .put("metadata", encodeMetadata(document.metadata))
            .put("data", AppDataJsonCodec.encodeDataForSync(document.data))
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    fun decodeCharacter(bytes: ByteArray): CloudCharacterDocument = decodeSafely("character") {
        val root = parseObject(bytes)
        require(root.optString("kind") == "character") { "Not a Dice Thrower cloud character" }
        val schema = root.optInt("schemaVersion", -1)
        requireSupportedSchema(schema)
        val metadata = decodeMetadata(root.getJSONObject("metadata"))
        val data = AppDataJsonCodec.decodeData(root.getJSONObject("data"))
        CloudCharacterDocument(schema, metadata, data).also(::validateCharacterDocument)
    }

    fun encodeManifest(manifest: CloudManifest): ByteArray {
        requireSupportedSchema(manifest.schemaVersion)
        require(manifest.generatedAt >= 0L) { "Cloud manifest generatedAt cannot be negative" }
        require(manifest.writerId.isNotBlank()) { "Cloud manifest writer ID cannot be blank" }
        require(manifest.characters.map { it.characterId }.distinct().size == manifest.characters.size) {
            "Cloud manifest contains duplicate character IDs"
        }
        validateManifestExtras(manifest)
        return JSONObject()
            .put("kind", "manifest")
            .put("schemaVersion", manifest.schemaVersion)
            .put("generatedAt", manifest.generatedAt)
            .put("writerId", manifest.writerId)
            .put("characters", JSONArray().apply {
                manifest.characters.sortedBy { it.characterId }.forEach { put(encodeMetadata(it)) }
            })
            .put("tombstones", JSONArray().apply {
                manifest.tombstones.sortedBy { it.characterId }.forEach { tombstone ->
                    put(JSONObject()
                        .put("characterId", tombstone.characterId)
                        .put("deletedAt", tombstone.deletedAt)
                        .put("writerId", tombstone.writerId)
                        .put("baseRevision", tombstone.baseRevision))
                }
            })
            .put("settings", manifest.settings?.let(::encodeSettings) ?: JSONObject.NULL)
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    fun decodeManifest(bytes: ByteArray): CloudManifest = decodeSafely("manifest") {
        val root = parseObject(bytes)
        require(root.optString("kind") == "manifest") { "Not a Dice Thrower cloud manifest" }
        val schema = root.optInt("schemaVersion", -1)
        requireSupportedSchema(schema)
        val charactersArray = root.optJSONArray("characters") ?: JSONArray()
        val characters = List(charactersArray.length()) { decodeMetadata(charactersArray.getJSONObject(it)) }
        require(characters.map { it.characterId }.distinct().size == characters.size) {
            "Cloud manifest contains duplicate character IDs"
        }
        val tombstonesArray = root.optJSONArray("tombstones") ?: JSONArray()
        val tombstones = List(tombstonesArray.length()) { index ->
            val item = tombstonesArray.getJSONObject(index)
            CloudCharacterTombstone(
                characterId = item.getString("characterId"),
                deletedAt = item.getLong("deletedAt"),
                writerId = item.getString("writerId"),
                baseRevision = item.getString("baseRevision"),
            )
        }
        CloudManifest(
            schemaVersion = schema,
            generatedAt = root.getLong("generatedAt").also { require(it >= 0L) },
            writerId = root.getString("writerId").also { require(it.isNotBlank()) },
            characters = characters,
            tombstones = tombstones,
            settings = root.optJSONObject("settings")?.let(::decodeSettings),
        ).also(::validateManifestExtras)
    }

    private fun encodeSettings(document: CloudSettingsDocument): JSONObject {
        validateSettings(document)
        return JSONObject()
            .put("metadata", JSONObject()
                .put("updatedAt", document.metadata.updatedAt)
                .put("revision", document.metadata.revision)
                .put("writerId", document.metadata.writerId))
            .put("values", JSONObject()
                .put("showRollButton", document.values.showRollButton)
                .put("rollButtonPosition", document.values.rollButtonPosition.name)
                .put("logRetention", document.values.logRetention))
    }

    private fun decodeSettings(json: JSONObject): CloudSettingsDocument {
        val metadata = json.getJSONObject("metadata")
        val values = json.getJSONObject("values")
        return CloudSettingsDocument(
            metadata = CloudSettingsMetadata(
                updatedAt = metadata.getLong("updatedAt"),
                revision = metadata.getString("revision"),
                writerId = metadata.getString("writerId"),
            ),
            values = CloudRoamingSettings(
                showRollButton = values.getBoolean("showRollButton"),
                rollButtonPosition = RollButtonPosition.valueOf(values.getString("rollButtonPosition")),
                logRetention = values.getInt("logRetention"),
            ),
        ).also(::validateSettings)
    }

    private fun validateManifestExtras(manifest: CloudManifest) {
        require(manifest.tombstones.map { it.characterId }.distinct().size == manifest.tombstones.size) {
            "Cloud manifest contains duplicate tombstones"
        }
        require(manifest.characters.map { it.characterId }.toSet()
            .intersect(manifest.tombstones.map { it.characterId }.toSet()).isEmpty()) {
            "Cloud manifest contains both a character and tombstone for the same ID"
        }
        manifest.tombstones.forEach { tombstone ->
            require(tombstone.characterId.isNotBlank()) { "Cloud tombstone character ID cannot be blank" }
            require(tombstone.deletedAt >= 0L) { "Cloud tombstone deletedAt cannot be negative" }
            require(tombstone.writerId.isNotBlank()) { "Cloud tombstone writer ID cannot be blank" }
            require(tombstone.baseRevision.matches(Regex("[0-9a-f]{64}"))) { "Invalid cloud tombstone base revision" }
        }
        manifest.settings?.let(::validateSettings)
    }

    private fun validateSettings(document: CloudSettingsDocument) {
        require(document.metadata.updatedAt >= 0L) { "Cloud settings updatedAt cannot be negative" }
        require(document.metadata.revision.matches(Regex("[0-9a-f]{64}"))) { "Invalid cloud settings revision" }
        require(document.metadata.writerId.isNotBlank()) { "Cloud settings writer ID cannot be blank" }
        require(document.values.logRetention >= 0) { "Cloud log retention cannot be negative" }
    }

    private fun validateCharacterDocument(document: CloudCharacterDocument) {
        AppDataValidator.requireValid(document.data)
        val character = document.data.characters.singleOrNull()
            ?: throw IllegalArgumentException("Cloud character document must contain exactly one character")
        require(character.imageUri == null) { "Cloud character document cannot contain a legacy image URI" }
        require(document.metadata.characterId == character.id) { "Cloud character ID mismatch" }
        val sync = document.data.characterSyncMetadata.singleOrNull()
            ?: throw IllegalArgumentException("Cloud character document must contain exactly one sync metadata record")
        require(sync.characterId == character.id) { "Cloud character sync ID mismatch" }
        require(document.metadata.updatedAt == sync.updatedAt) { "Cloud updatedAt mismatch" }
        require(document.metadata.revision == sync.revision) { "Cloud revision mismatch" }
        require(document.metadata.writerId == sync.writerId) { "Cloud writer mismatch" }
        require(
            document.metadata.assetIds == listOfNotNull(character.image?.assetId, character.diceTableImage?.assetId).distinct(),
        ) { "Cloud asset reference mismatch" }
    }

    private fun encodeMetadata(metadata: CloudCharacterMetadata): JSONObject {
        validateMetadata(metadata)
        return JSONObject()
            .put("characterId", metadata.characterId)
            .put("updatedAt", metadata.updatedAt)
            .put("revision", metadata.revision)
            .put("writerId", metadata.writerId)
            .put("assetIds", JSONArray(metadata.assetIds.sorted()))
    }

    private fun decodeMetadata(json: JSONObject): CloudCharacterMetadata {
        val assets = json.optJSONArray("assetIds") ?: JSONArray()
        return CloudCharacterMetadata(
            characterId = json.getString("characterId"),
            updatedAt = json.getLong("updatedAt"),
            revision = json.getString("revision"),
            writerId = json.getString("writerId"),
            assetIds = List(assets.length()) { assets.getString(it) }.sorted(),
        ).also(::validateMetadata)
    }

    private fun validateMetadata(metadata: CloudCharacterMetadata) {
        require(metadata.characterId.isNotBlank()) { "Cloud character ID cannot be blank" }
        require(metadata.updatedAt >= 0L) { "Cloud updatedAt cannot be negative" }
        require(metadata.revision.matches(Regex("[0-9a-f]{64}"))) { "Invalid cloud revision" }
        require(metadata.writerId.isNotBlank()) { "Cloud writer ID cannot be blank" }
        require(metadata.assetIds.distinct().size == metadata.assetIds.size) { "Duplicate cloud asset reference" }
        require(metadata.assetIds.size <= 1 && metadata.assetIds.all { it.matches(Regex("img_[0-9a-f]{64}")) }) {
            "Invalid cloud asset reference"
        }
    }

    private fun requireSupportedSchema(actual: Int) {
        if (actual != CLOUD_SCHEMA_VERSION) {
            throw CloudSchemaMismatchException(CLOUD_SCHEMA_VERSION, actual)
        }
    }

    private fun parseObject(bytes: ByteArray): JSONObject = try {
        JSONObject(bytes.toString(Charsets.UTF_8))
    } catch (error: Exception) {
        throw CloudProtocolException("Invalid cloud JSON document", error)
    }

    private inline fun <T> decodeSafely(kind: String, block: () -> T): T = try {
        block()
    } catch (error: CloudRepositoryException) {
        throw error
    } catch (error: Exception) {
        throw CloudProtocolException("Invalid Dice Thrower cloud $kind document", error)
    }
}
