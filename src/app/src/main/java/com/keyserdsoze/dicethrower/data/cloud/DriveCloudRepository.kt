package com.keyserdsoze.dicethrower.data.cloud

import android.content.Context
import com.keyserdsoze.dicethrower.data.CharacterImageAssets
import com.keyserdsoze.dicethrower.data.CloudAccountState
import com.keyserdsoze.dicethrower.data.PortableCharacterImageAsset
import com.keyserdsoze.dicethrower.model.CharacterImageRef

interface CloudRemoteRepository {
    suspend fun readManifest(): CloudManifest?
    suspend fun putManifest(manifest: CloudManifest)
    suspend fun listCharacters(): List<CloudCharacterMetadata>
    suspend fun readCharacter(characterId: String): CloudCharacterDocument?
    suspend fun putCharacter(document: CloudCharacterDocument)
    suspend fun deleteCharacter(characterId: String): Boolean
    suspend fun listAssets(): List<CharacterImageRef>
    suspend fun readAsset(assetId: String): CloudAssetDocument?
    suspend fun putAsset(document: CloudAssetDocument)
    suspend fun deleteAsset(assetId: String): Boolean
}

class DriveCloudRepository(
    private val transport: DriveAppDataTransport,
) : CloudRemoteRepository {
    override suspend fun readManifest(): CloudManifest? {
        val file = findUnique(MANIFEST_KEY) ?: return null
        requireLogicalFile(file, KIND_MANIFEST, MANIFEST_KEY)
        val manifest = CloudDocumentCodec.decodeManifest(transport.read(file.id))
        requireProperty(file, PROP_SCHEMA, manifest.schemaVersion.toString())
        return manifest
    }

    override suspend fun putManifest(manifest: CloudManifest) {
        val bytes = CloudDocumentCodec.encodeManifest(manifest)
        putLogical(
            key = MANIFEST_KEY,
            name = "dice-thrower-manifest-v${manifest.schemaVersion}.json",
            mimeType = JSON_MIME,
            properties = baseProperties(KIND_MANIFEST, MANIFEST_KEY) + mapOf(
                PROP_SCHEMA to manifest.schemaVersion.toString(),
                PROP_UPDATED_AT to manifest.generatedAt.toString(),
                PROP_WRITER_ID to manifest.writerId,
            ),
            bytes = bytes,
        )
    }

    override suspend fun listCharacters(): List<CloudCharacterMetadata> {
        val files = managedFiles().filter { it.appProperties[PROP_KIND] == KIND_CHARACTER }
        requireUniqueLogicalKeys(files)
        return files.map(::metadataFromProperties).sortedBy { it.characterId }
    }

    override suspend fun readCharacter(characterId: String): CloudCharacterDocument? {
        val file = findUnique(characterKey(characterId)) ?: return null
        requireLogicalFile(file, KIND_CHARACTER, characterKey(characterId))
        val document = CloudDocumentCodec.decodeCharacter(transport.read(file.id))
        val expected = metadataFromProperties(file)
        if (document.metadata != expected) {
            throw CloudProtocolException("Drive metadata does not match character ${document.metadata.characterId}")
        }
        return document
    }

    override suspend fun putCharacter(document: CloudCharacterDocument) {
        val bytes = CloudDocumentCodec.encodeCharacter(document)
        val metadata = document.metadata
        val key = characterKey(metadata.characterId)
        putLogical(
            key = key,
            name = "dice-thrower-character-${stableName(metadata.characterId)}.json",
            mimeType = JSON_MIME,
            properties = baseProperties(KIND_CHARACTER, key) + metadataProperties(metadata) +
                (metadata.assetIds.singleOrNull()?.let { mapOf(PROP_ASSET_ID to it) } ?: emptyMap()),
            bytes = bytes,
        )
    }

    override suspend fun deleteCharacter(characterId: String): Boolean = deleteLogical(characterKey(characterId))

    override suspend fun listAssets(): List<CharacterImageRef> {
        val files = managedFiles().filter { it.appProperties[PROP_KIND] == KIND_ASSET }
        requireUniqueLogicalKeys(files)
        return files.map(::assetRefFromProperties).sortedBy { it.assetId }
    }

    override suspend fun readAsset(assetId: String): CloudAssetDocument? {
        val file = findUnique(assetKey(assetId)) ?: return null
        requireLogicalFile(file, KIND_ASSET, assetKey(assetId))
        val ref = assetRefFromProperties(file)
        val bytes = transport.read(file.id)
        if (!CharacterImageAssets.validate(ref, bytes)) {
            throw CloudProtocolException("Drive image asset $assetId failed integrity validation")
        }
        return CloudAssetDocument(PortableCharacterImageAsset(ref, bytes))
    }

    override suspend fun putAsset(document: CloudAssetDocument) {
        val asset = document.asset
        if (!CharacterImageAssets.validate(asset.ref, asset.bytes)) {
            throw CloudProtocolException("Refusing to upload invalid image asset ${asset.ref.assetId}")
        }
        val ref = asset.ref
        val key = assetKey(ref.assetId)
        putLogical(
            key = key,
            name = "dice-thrower-asset-${ref.assetId}",
            mimeType = ref.mimeType,
            properties = baseProperties(KIND_ASSET, key) + mapOf(
                PROP_SCHEMA to CLOUD_SCHEMA_VERSION.toString(),
                PROP_ASSET_ID to ref.assetId,
                PROP_SHA256 to ref.sha256,
                PROP_MIME_TYPE to ref.mimeType,
                PROP_BYTE_SIZE to ref.byteSize.toString(),
            ),
            bytes = asset.bytes,
        )
    }

    override suspend fun deleteAsset(assetId: String): Boolean = deleteLogical(assetKey(assetId))

    private suspend fun putLogical(
        key: String,
        name: String,
        mimeType: String,
        properties: Map<String, String>,
        bytes: ByteArray,
    ) {
        val existing = findUnique(key)
        if (existing != null) {
            transport.update(existing.id, name, mimeType, properties, bytes)
            return
        }
        try {
            transport.create(name, mimeType, properties, bytes)
        } catch (error: CloudTransientException) {
            // A create is deliberately never replayed blindly: the server may have committed it
            // before the connection failed. Recover the stable logical key first.
            val recovered = findUnique(key) ?: throw error
            transport.update(recovered.id, name, mimeType, properties, bytes)
        }
    }

    private suspend fun deleteLogical(key: String): Boolean {
        val existing = findUnique(key) ?: return false
        return try {
            transport.delete(existing.id)
            true
        } catch (_: CloudNotFoundException) {
            // A retried DELETE can observe the result of the first successful request.
            true
        }
    }

    private suspend fun findUnique(key: String): DriveAppDataFile? {
        val matches = managedFiles().filter { it.appProperties[PROP_KEY] == key }
        if (matches.size > 1) {
            throw CloudProtocolException("Duplicate Dice Thrower cloud files for logical key $key")
        }
        return matches.singleOrNull()
    }

    private suspend fun managedFiles(): List<DriveAppDataFile> = transport.list()
        .filter { it.appProperties[PROP_APP] == APP_ID }

    private fun metadataFromProperties(file: DriveAppDataFile): CloudCharacterMetadata {
        requireProperty(file, PROP_SCHEMA, CLOUD_SCHEMA_VERSION.toString())
        val properties = file.appProperties
        val characterId = requiredProperty(file, PROP_CHARACTER_ID)
        requireProperty(file, PROP_KEY, characterKey(characterId))
        val revision = requiredProperty(file, PROP_REVISION)
        val writerId = requiredProperty(file, PROP_WRITER_ID)
        val updatedAt = requiredProperty(file, PROP_UPDATED_AT).toLongOrNull()
            ?: throw CloudProtocolException("Invalid updatedAt metadata for character $characterId")
        if (updatedAt < 0L) {
            throw CloudProtocolException("Negative updatedAt metadata for character $characterId")
        }
        if (!revision.matches(SHA256_REGEX)) {
            throw CloudProtocolException("Invalid revision metadata for character $characterId")
        }
        return CloudCharacterMetadata(
            characterId = characterId,
            updatedAt = updatedAt,
            revision = revision,
            writerId = writerId,
            assetIds = listOfNotNull(properties[PROP_ASSET_ID]),
        )
    }

    private fun assetRefFromProperties(file: DriveAppDataFile): CharacterImageRef {
        requireProperty(file, PROP_SCHEMA, CLOUD_SCHEMA_VERSION.toString())
        val assetId = requiredProperty(file, PROP_ASSET_ID)
        requireProperty(file, PROP_KEY, assetKey(assetId))
        val ref = CharacterImageRef(
            assetId = assetId,
            sha256 = requiredProperty(file, PROP_SHA256),
            mimeType = requiredProperty(file, PROP_MIME_TYPE),
            byteSize = requiredProperty(file, PROP_BYTE_SIZE).toLongOrNull()
                ?: throw CloudProtocolException("Invalid byte size for Drive image asset $assetId"),
        )
        if (
            !ref.sha256.matches(SHA256_REGEX) ||
            ref.assetId != "img_${ref.sha256}" ||
            !ref.mimeType.startsWith("image/") ||
            ref.byteSize !in 1L..CharacterImageAssets.MAX_IMAGE_BYTES.toLong()
        ) {
            throw CloudProtocolException("Invalid Drive image asset metadata for $assetId")
        }
        return ref
    }

    private fun requireLogicalFile(file: DriveAppDataFile, kind: String, key: String) {
        requireProperty(file, PROP_KIND, kind)
        requireProperty(file, PROP_KEY, key)
    }

    private fun requireUniqueLogicalKeys(files: List<DriveAppDataFile>) {
        val keys = files.map { requiredProperty(it, PROP_KEY) }
        if (keys.size != keys.distinct().size) {
            throw CloudProtocolException("Duplicate Dice Thrower cloud logical keys")
        }
    }

    private fun requireProperty(file: DriveAppDataFile, name: String, expected: String) {
        val actual = requiredProperty(file, name)
        if (name == PROP_SCHEMA && actual.toIntOrNull() != CLOUD_SCHEMA_VERSION) {
            throw CloudSchemaMismatchException(CLOUD_SCHEMA_VERSION, actual.toIntOrNull() ?: -1)
        }
        if (actual != expected) {
            throw CloudProtocolException("Unexpected $name metadata for Drive file ${file.id}")
        }
    }

    private fun requiredProperty(file: DriveAppDataFile, name: String): String =
        file.appProperties[name]?.takeIf(String::isNotBlank)
            ?: throw CloudProtocolException("Missing $name metadata for Drive file ${file.id}")

    private fun metadataProperties(metadata: CloudCharacterMetadata): Map<String, String> = mapOf(
        PROP_SCHEMA to CLOUD_SCHEMA_VERSION.toString(),
        PROP_CHARACTER_ID to metadata.characterId,
        PROP_UPDATED_AT to metadata.updatedAt.toString(),
        PROP_REVISION to metadata.revision,
        PROP_WRITER_ID to metadata.writerId,
    )

    private fun baseProperties(kind: String, key: String): Map<String, String> = mapOf(
        PROP_APP to APP_ID,
        PROP_KIND to kind,
        PROP_KEY to key,
    )

    private fun characterKey(characterId: String): String = "character:${stableName(characterId)}"
    private fun assetKey(assetId: String): String = "asset:$assetId"
    private fun stableName(value: String): String = CharacterImageAssets.sha256(value.toByteArray(Charsets.UTF_8))

    private companion object {
        const val APP_ID = "dice_thrower"
        const val JSON_MIME = "application/json"
        const val MANIFEST_KEY = "manifest:v1"
        const val KIND_MANIFEST = "manifest"
        const val KIND_CHARACTER = "character"
        const val KIND_ASSET = "asset"
        const val PROP_APP = "dt_app"
        const val PROP_KIND = "dt_kind"
        const val PROP_KEY = "dt_key"
        const val PROP_SCHEMA = "dt_schema"
        const val PROP_CHARACTER_ID = "dt_character_id"
        const val PROP_UPDATED_AT = "dt_updated_at"
        const val PROP_REVISION = "dt_revision"
        const val PROP_WRITER_ID = "dt_writer_id"
        const val PROP_ASSET_ID = "dt_asset_id"
        const val PROP_SHA256 = "dt_sha256"
        const val PROP_MIME_TYPE = "dt_mime_type"
        const val PROP_BYTE_SIZE = "dt_byte_size"
        val SHA256_REGEX = Regex("[0-9a-f]{64}")
    }
}

class GatedCloudRemoteRepository(
    private val accountState: () -> CloudAccountState,
    private val delegate: CloudRemoteRepository,
) : CloudRemoteRepository {
    private fun connected(): CloudRemoteRepository {
        if (!accountState().googleConnected) throw CloudStandaloneModeException()
        return delegate
    }

    override suspend fun readManifest() = connected().readManifest()
    override suspend fun putManifest(manifest: CloudManifest) = connected().putManifest(manifest)
    override suspend fun listCharacters() = connected().listCharacters()
    override suspend fun readCharacter(characterId: String) = connected().readCharacter(characterId)
    override suspend fun putCharacter(document: CloudCharacterDocument) = connected().putCharacter(document)
    override suspend fun deleteCharacter(characterId: String) = connected().deleteCharacter(characterId)
    override suspend fun listAssets() = connected().listAssets()
    override suspend fun readAsset(assetId: String) = connected().readAsset(assetId)
    override suspend fun putAsset(document: CloudAssetDocument) = connected().putAsset(document)
    override suspend fun deleteAsset(assetId: String) = connected().deleteAsset(assetId)
}

object CloudRepositoryFactory {
    fun create(
        context: Context,
        accountState: () -> CloudAccountState,
    ): CloudRemoteRepository {
        val tokenProvider = GoogleDriveAccessTokenProvider(context) { accountState().account?.email }
        return GatedCloudRemoteRepository(
            accountState = accountState,
            delegate = DriveCloudRepository(DriveRestTransport(tokenProvider)),
        )
    }
}
