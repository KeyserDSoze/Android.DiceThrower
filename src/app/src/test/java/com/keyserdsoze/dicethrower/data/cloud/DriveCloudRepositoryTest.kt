package com.keyserdsoze.dicethrower.data.cloud

import com.keyserdsoze.dicethrower.data.CharacterImageAssets
import com.keyserdsoze.dicethrower.data.CloudAccountState
import com.keyserdsoze.dicethrower.data.CloudMode
import com.keyserdsoze.dicethrower.data.PortableCharacterImageAsset
import com.keyserdsoze.dicethrower.data.SyncMetadataManager
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterImageRef
import com.keyserdsoze.dicethrower.model.CharacterProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveCloudRepositoryTest {
    @Test
    fun twoRepositoryInstancesShareManifestCharacterAndAssetDataset() = runBlocking {
        val transport = FakeDriveTransport()
        val deviceA = DriveCloudRepository(transport)
        val deviceB = DriveCloudRepository(transport)
        val bytes = "image-from-device-a".toByteArray()
        val ref = CharacterImageAssets.createRef(bytes, "image/png")
        val data = SyncMetadataManager.ensureMetadata(
            AppData(characters = listOf(CharacterProfile("hero", "Hero", image = ref))),
            writerId = "device-a",
            now = 10L,
        )
        val character = CloudDocumentCodec.buildCharacterDocument(data, "hero")
        val manifest = CloudManifest(1, 10L, "device-a", listOf(character.metadata))

        deviceA.putAsset(CloudAssetDocument(PortableCharacterImageAsset(ref, bytes)))
        deviceA.putCharacter(character)
        deviceA.putManifest(manifest)

        assertEquals(manifest, deviceB.readManifest())
        assertEquals(character, deviceB.readCharacter("hero"))
        assertTrue(deviceB.readAsset(ref.assetId)!!.asset.bytes.contentEquals(bytes))
        assertEquals(listOf(character.metadata), deviceB.listCharacters())
        assertEquals(listOf(ref), deviceB.listAssets())
    }

    @Test
    fun repeatedPutUpdatesStableLogicalFileInsteadOfCreatingDuplicate() = runBlocking {
        val transport = FakeDriveTransport()
        val repository = DriveCloudRepository(transport)
        val character = characterDocument()

        repository.putCharacter(character)
        repository.putCharacter(character)

        assertEquals(1, transport.files.size)
        assertEquals(1, transport.createCalls)
        assertEquals(1, transport.updateCalls)
    }

    @Test
    fun uncertainCreateRecoversCommittedStableKeyBeforeRetryingWrite() = runBlocking {
        val transport = FakeDriveTransport(failFirstCreateAfterCommit = true)
        val repository = DriveCloudRepository(transport)

        repository.putCharacter(characterDocument())

        assertEquals(1, transport.files.size)
        assertEquals(1, transport.createCalls)
        assertEquals(1, transport.updateCalls)
    }

    @Test
    fun standaloneGateRejectsBeforeAnyDriveTransportCall() = runBlocking {
        val transport = FakeDriveTransport()
        val repository = GatedCloudRemoteRepository(
            accountState = { CloudAccountState(mode = CloudMode.STANDALONE) },
            delegate = DriveCloudRepository(transport),
        )

        val error = runCatching { repository.readManifest() }.exceptionOrNull()

        assertTrue(error is CloudStandaloneModeException)
        assertEquals(0, transport.listCalls)
    }

    @Test
    fun deleteIsIdempotentAtLogicalKeyBoundary() = runBlocking {
        val transport = FakeDriveTransport()
        val repository = DriveCloudRepository(transport)
        repository.putCharacter(characterDocument())

        assertTrue(repository.deleteCharacter("hero"))
        assertEquals(false, repository.deleteCharacter("hero"))
        assertEquals(0, transport.files.size)
    }

    @Test
    fun deleteAllDataRemovesOnlyDiceThrowerManagedFilesAndIsRetrySafe() = runBlocking {
        val transport = FakeDriveTransport()
        val repository = DriveCloudRepository(transport)
        val bytes = "portrait".toByteArray()
        val ref = CharacterImageAssets.createRef(bytes, "image/png")
        val character = characterDocument()
        repository.putCharacter(character)
        repository.putAsset(CloudAssetDocument(PortableCharacterImageAsset(ref, bytes)))
        repository.putManifest(CloudManifest(1, 10L, "device-a", listOf(character.metadata)))
        transport.create(
            "other-app.json",
            "application/json",
            mapOf("dt_app" to "another_app"),
            "other".toByteArray(),
        )

        assertEquals(3, repository.deleteAllData())
        assertEquals(0, repository.deleteAllData())
        assertEquals(1, transport.files.size)
        assertEquals("other-app.json", transport.files.values.single().file.name)
    }

    private fun characterDocument(): CloudCharacterDocument {
        val data = SyncMetadataManager.ensureMetadata(
            AppData(characters = listOf(CharacterProfile("hero", "Hero"))),
            writerId = "device-a",
            now = 10L,
        )
        return CloudDocumentCodec.buildCharacterDocument(data, "hero")
    }

    private fun imageDocument(portrait: CharacterImageRef?, table: CharacterImageRef?, now: Long): CloudCharacterDocument {
        val data = SyncMetadataManager.ensureMetadata(
            AppData(characters = listOf(CharacterProfile("hero", "Hero", image = portrait, diceTableImage = table))),
            writerId = "device-a",
            now = now,
        )
        return CloudDocumentCodec.buildCharacterDocument(data, "hero")
    }

    private fun assetKeys(transport: FakeDriveTransport): Map<String, String> =
        transport.files.values.single().file.appProperties.filterKeys { it.startsWith("dt_asset_id") }

    @Test
    fun characterWithPortraitAndTableImageStoresEachAssetIdAndReadsBack() = runBlocking {
        val transport = FakeDriveTransport()
        val repository = DriveCloudRepository(transport)
        val portrait = CharacterImageAssets.createRef("portrait".toByteArray(), "image/png")
        val table = CharacterImageAssets.createRef("table".toByteArray(), "image/jpeg")
        val document = imageDocument(portrait, table, now = 10L)
        assertEquals(listOf(portrait.assetId, table.assetId).sorted(), document.metadata.assetIds)

        repository.putCharacter(document)

        val properties = assetKeys(transport)
        assertEquals(document.metadata.assetIds, properties.values.sorted())
        // Each id gets its own property, within Drive's 124-byte key plus value limit.
        properties.forEach { (key, value) ->
            assertTrue(key.toByteArray().size + value.toByteArray().size <= 124)
        }
        assertEquals(listOf(document.metadata), repository.listCharacters())
        assertEquals(document, repository.readCharacter("hero"))
    }

    @Test
    fun singleAssetKeepsLegacyPropertyAndRemovedImagesAreCleared() = runBlocking {
        val transport = FakeDriveTransport()
        val repository = DriveCloudRepository(transport)
        val portrait = CharacterImageAssets.createRef("portrait".toByteArray(), "image/png")
        val table = CharacterImageAssets.createRef("table".toByteArray(), "image/jpeg")

        repository.putCharacter(imageDocument(portrait, null, now = 10L))
        // Versions that only know one asset read exactly this key.
        assertEquals(mapOf("dt_asset_id" to portrait.assetId), assetKeys(transport))

        repository.putCharacter(imageDocument(portrait, table, now = 11L))
        assertEquals(2, assetKeys(transport).size)

        val tableOnly = imageDocument(null, table, now = 12L)
        repository.putCharacter(tableOnly)
        assertEquals(mapOf("dt_asset_id" to table.assetId), assetKeys(transport))
        assertEquals(tableOnly, repository.readCharacter("hero"))

        val noImages = imageDocument(null, null, now = 13L)
        repository.putCharacter(noImages)
        assertTrue(assetKeys(transport).isEmpty())
        assertEquals(listOf(noImages.metadata), repository.listCharacters())
        assertEquals(noImages, repository.readCharacter("hero"))
    }

    @Test
    fun documentsWithAnIncompleteLegacyAssetIndexStayReadable() = runBlocking {
        val transport = FakeDriveTransport()
        val repository = DriveCloudRepository(transport)
        val portrait = CharacterImageAssets.createRef("portrait".toByteArray(), "image/png")
        val table = CharacterImageAssets.createRef("table".toByteArray(), "image/jpeg")
        val document = imageDocument(portrait, table, now = 10L)
        repository.putCharacter(document)
        // Earlier versions wrote no asset property at all for a character with two images.
        val stored = transport.files.values.single()
        transport.files[stored.file.id] = stored.copy(
            file = stored.file.copy(appProperties = stored.file.appProperties.filterKeys { !it.startsWith("dt_asset_id") }),
        )

        assertEquals(document, repository.readCharacter("hero"))
    }

    @Test
    fun identityMismatchBetweenPropertiesAndDocumentIsStillRejected() = runBlocking {
        val transport = FakeDriveTransport()
        val repository = DriveCloudRepository(transport)
        repository.putCharacter(characterDocument())
        val stored = transport.files.values.single()
        transport.files[stored.file.id] = stored.copy(
            file = stored.file.copy(appProperties = stored.file.appProperties + ("dt_revision" to "f".repeat(64))),
        )

        val error = runCatching { repository.readCharacter("hero") }.exceptionOrNull()
        assertTrue(error is CloudProtocolException)
    }

    private class FakeDriveTransport(
        private var failFirstCreateAfterCommit: Boolean = false,
    ) : DriveAppDataTransport {
        val files = linkedMapOf<String, Stored>()
        var listCalls = 0
        var createCalls = 0
        var updateCalls = 0
        private var nextId = 1

        override suspend fun list(): List<DriveAppDataFile> {
            listCalls++
            return files.values.map { it.file }
        }

        override suspend fun read(fileId: String): ByteArray = files[fileId]?.bytes
            ?: throw CloudNotFoundException("missing $fileId")

        override suspend fun create(
            name: String,
            mimeType: String,
            appProperties: Map<String, String>,
            bytes: ByteArray,
        ): DriveAppDataFile {
            createCalls++
            val id = "file-${nextId++}"
            val file = DriveAppDataFile(id, name, mimeType, appProperties = appProperties)
            files[id] = Stored(file, bytes.copyOf())
            if (failFirstCreateAfterCommit) {
                failFirstCreateAfterCommit = false
                throw CloudTransientException("response lost")
            }
            return file
        }

        override suspend fun update(
            fileId: String,
            name: String,
            mimeType: String,
            appProperties: Map<String, String>,
            bytes: ByteArray,
            removedAppProperties: Set<String>,
        ): DriveAppDataFile {
            val existing = files[fileId]
            assertNotNull(existing)
            updateCalls++
            // Like Drive, files.update merges appProperties and only clears explicitly removed keys.
            val merged = existing!!.file.appProperties + appProperties - removedAppProperties
            val file = DriveAppDataFile(fileId, name, mimeType, appProperties = merged)
            files[fileId] = Stored(file, bytes.copyOf())
            return file
        }

        override suspend fun delete(fileId: String) {
            if (files.remove(fileId) == null) throw CloudNotFoundException("missing $fileId")
        }

        data class Stored(val file: DriveAppDataFile, val bytes: ByteArray)
    }
}
