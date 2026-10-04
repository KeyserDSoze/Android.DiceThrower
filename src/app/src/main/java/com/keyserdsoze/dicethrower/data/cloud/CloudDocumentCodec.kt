package com.keyserdsoze.dicethrower.data.cloud

import com.keyserdsoze.dicethrower.data.AppDataJsonCodec
import com.keyserdsoze.dicethrower.data.AppDataValidator
import com.keyserdsoze.dicethrower.model.AppData
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
                assetIds = listOfNotNull(character.image?.assetId),
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
        return JSONObject()
            .put("kind", "manifest")
            .put("schemaVersion", manifest.schemaVersion)
            .put("generatedAt", manifest.generatedAt)
            .put("writerId", manifest.writerId)
            .put("characters", JSONArray().apply {
                manifest.characters.sortedBy { it.characterId }.forEach { put(encodeMetadata(it)) }
            })
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
        CloudManifest(
            schemaVersion = schema,
            generatedAt = root.getLong("generatedAt").also { require(it >= 0L) },
            writerId = root.getString("writerId").also { require(it.isNotBlank()) },
            characters = characters,
        )
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
        require(document.metadata.assetIds == listOfNotNull(character.image?.assetId)) { "Cloud asset reference mismatch" }
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
