package com.keyserdsoze.dicethrower.data.cloud

import com.keyserdsoze.dicethrower.data.CharacterImageAssets
import com.keyserdsoze.dicethrower.data.SyncMetadataManager
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.RollButtonPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudDocumentCodecTest {
    @Test
    fun characterRoundTripContainsExactlyOnePortableCharacterGraph() {
        val imageBytes = "portable-image".toByteArray()
        val tableBytes = "portable-table".toByteArray()
        val image = CharacterImageAssets.createRef(imageBytes, "image/png")
        val tableImage = CharacterImageAssets.createRef(tableBytes, "image/jpeg")
        val data = SyncMetadataManager.ensureMetadata(
            AppData(
                characters = listOf(
                    CharacterProfile(
                        id = "character-a",
                        name = "Alyndra",
                        imageUri = "content://source-device/image",
                        image = image,
                        diceTableImage = tableImage,
                    ),
                ),
                modifiers = listOf(CharacterModifier("mod-a", "character-a", "Mind", 4)),
            ),
            writerId = "device-a",
            now = 123L,
        )

        val encoded = CloudDocumentCodec.encodeCharacter(
            CloudDocumentCodec.buildCharacterDocument(data, "character-a"),
        )
        val decoded = CloudDocumentCodec.decodeCharacter(encoded)

        assertEquals("character-a", decoded.metadata.characterId)
        assertEquals(listOf(image.assetId, tableImage.assetId).sorted(), decoded.metadata.assetIds)
        assertEquals("Alyndra", decoded.data.characters.single().name)
        assertEquals(tableImage, decoded.data.characters.single().diceTableImage)
        assertEquals(null, decoded.data.characters.single().imageUri)
        assertFalse(encoded.toString(Charsets.UTF_8).contains("content://"))
    }

    @Test
    fun manifestEncodingIsStableAcrossCharacterOrdering() {
        val first = metadata("a", 'a')
        val second = metadata("b", 'b')
        val left = CloudManifest(generatedAt = 100L, writerId = "writer", characters = listOf(second, first))
        val right = left.copy(characters = listOf(first, second))

        assertTrue(CloudDocumentCodec.encodeManifest(left).contentEquals(CloudDocumentCodec.encodeManifest(right)))
        assertEquals(listOf("a", "b"), CloudDocumentCodec.decodeManifest(CloudDocumentCodec.encodeManifest(left))
            .characters.map { it.characterId })
    }

    @Test
    fun newerCloudSchemaFailsExplicitly() {
        val encoded = CloudDocumentCodec.encodeManifest(
            CloudManifest(generatedAt = 1L, writerId = "writer", characters = emptyList()),
        ).toString(Charsets.UTF_8)
        val future = encoded.replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":99")

        val error = runCatching { CloudDocumentCodec.decodeManifest(future.toByteArray()) }.exceptionOrNull()

        assertTrue(error is CloudSchemaMismatchException)
        assertEquals(99, (error as CloudSchemaMismatchException).actual)
    }

    @Test
    fun manifestRoundTripPreservesTombstonesAndRoamingSettings() {
        val revision = "c".repeat(64)
        val manifest = CloudManifest(
            generatedAt = 50L,
            writerId = "device-a",
            characters = emptyList(),
            tombstones = listOf(CloudCharacterTombstone("deleted", 40L, "device-a", revision)),
            settings = CloudSettingsDocument(
                CloudSettingsMetadata(45L, revision, "device-a"),
                CloudRoamingSettings(false, RollButtonPosition.TOP_LEFT, 50),
            ),
        )

        assertEquals(manifest, CloudDocumentCodec.decodeManifest(CloudDocumentCodec.encodeManifest(manifest)))
    }

    private fun metadata(id: String, hash: Char) = CloudCharacterMetadata(
        characterId = id,
        updatedAt = 1L,
        revision = hash.toString().repeat(64),
        writerId = "writer",
    )
}
