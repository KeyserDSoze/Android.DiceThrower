package com.keyserdsoze.dicethrower.data.cloud

import com.keyserdsoze.dicethrower.data.PortableCharacterImageAsset
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.RollButtonPosition

const val CLOUD_SCHEMA_VERSION = 1
const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

data class CloudCharacterMetadata(
    val characterId: String,
    val updatedAt: Long,
    val revision: String,
    val writerId: String,
    val assetIds: List<String> = emptyList(),
)

data class CloudManifest(
    val schemaVersion: Int = CLOUD_SCHEMA_VERSION,
    val generatedAt: Long,
    val writerId: String,
    val characters: List<CloudCharacterMetadata>,
    val tombstones: List<CloudCharacterTombstone> = emptyList(),
    val settings: CloudSettingsDocument? = null,
)

data class CloudCharacterTombstone(
    val characterId: String,
    val deletedAt: Long,
    val writerId: String,
    val baseRevision: String,
)

data class CloudSettingsMetadata(
    val updatedAt: Long,
    val revision: String,
    val writerId: String,
)

data class CloudRoamingSettings(
    val showRollButton: Boolean,
    val rollButtonPosition: RollButtonPosition,
    val logRetention: Int,
)

data class CloudSettingsDocument(
    val metadata: CloudSettingsMetadata,
    val values: CloudRoamingSettings,
)

data class CloudCharacterDocument(
    val schemaVersion: Int = CLOUD_SCHEMA_VERSION,
    val metadata: CloudCharacterMetadata,
    val data: AppData,
)

data class CloudAssetDocument(
    val asset: PortableCharacterImageAsset,
)

open class CloudRepositoryException(message: String, cause: Throwable? = null) : Exception(message, cause)

class CloudSchemaMismatchException(
    val expected: Int,
    val actual: Int,
) : CloudRepositoryException("Unsupported cloud schema $actual; expected $expected")

class CloudStandaloneModeException :
    CloudRepositoryException("Google Drive repository is unavailable in standalone mode")

class CloudAuthorizationException(message: String, cause: Throwable? = null) :
    CloudRepositoryException(message, cause)

class CloudTransientException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : CloudRepositoryException(message, cause)

class CloudNotFoundException(message: String) : CloudRepositoryException(message)

class CloudProtocolException(message: String, cause: Throwable? = null) :
    CloudRepositoryException(message, cause)
