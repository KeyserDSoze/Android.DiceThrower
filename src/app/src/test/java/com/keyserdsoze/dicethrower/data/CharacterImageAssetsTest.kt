package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterProfile
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterImageAssetsTest {
    @Test
    fun identicalContentUsesSameStableAssetIdentity() {
        val bytes = "same image".toByteArray()
        val first = CharacterImageAssets.createRef(bytes, "image/png")
        val second = CharacterImageAssets.createRef(bytes.copyOf(), "image/png")

        assertEquals(first.sha256, second.sha256)
        assertEquals(first.assetId, second.assetId)
        assertTrue(first.assetId.startsWith("img_"))
    }

    @Test
    fun missingOrCorruptLocalAssetDegradesToNoBytes() {
        val directory = Files.createTempDirectory("dice-character-images").toFile()
        val bytes = "valid image bytes".toByteArray()
        val ref = CharacterImageAssets.createRef(bytes, "image/png")
        val store = CharacterImageAssetFileStore(directory)

        try {
            assertNull(store.readVerified(ref))
            store.save(ref, bytes)
            assertTrue(store.readVerified(ref)!!.contentEquals(bytes))

            directory.resolve(ref.assetId).writeBytes("corrupt".toByteArray())
            assertNull(store.readVerified(ref))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun orphanCleanupUsesRetentionWindow() {
        val directory = Files.createTempDirectory("dice-character-orphans").toFile()
        val bytes = "orphan".toByteArray()
        val ref = CharacterImageAssets.createRef(bytes, "image/png")
        val store = CharacterImageAssetFileStore(directory)

        try {
            store.save(ref, bytes)
            val file = directory.resolve(ref.assetId)
            val modifiedAt = file.lastModified()

            store.cleanupOrphans(AppData(), now = modifiedAt + 500L, retentionMillis = 1_000L)
            assertTrue(file.exists())
            store.cleanupOrphans(AppData(), now = modifiedAt + 1_000L, retentionMillis = 1_000L)
            assertTrue(!file.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun referencedAssetIsNeverConsideredOrphan() {
        val bytes = "portrait".toByteArray()
        val ref = CharacterImageAssets.createRef(bytes, "image/jpeg")
        val data = AppData(
            characters = listOf(CharacterProfile(id = "character", name = "Hero", image = ref)),
        )

        assertTrue(CharacterImageAssets.orphanAssetIds(listOf(ref.assetId), data).isEmpty())
    }

    @Test
    fun legacyMigrationClearsUriOnlyAfterSuccessfulImport() {
        val bytes = "portrait".toByteArray()
        val ref = CharacterImageAssets.createRef(bytes, "image/png")
        val legacy = AppData(
            characters = listOf(
                CharacterProfile(id = "character", name = "Hero", imageUri = "content://legacy/portrait"),
            ),
        )

        val migrated = CharacterImageMigration.migrateLegacy(legacy) { ref }
        assertNull(migrated.characters.single().imageUri)
        assertEquals(ref, migrated.characters.single().image)

        val unavailable = CharacterImageMigration.migrateLegacy(legacy) { null }
        assertEquals(legacy, unavailable)
    }

    @Test
    fun syncSerializationNeverExportsLegacyContentUri() {
        val legacy = AppData(
            characters = listOf(
                CharacterProfile(id = "character", name = "Hero", imageUri = "content://source-device/portrait"),
            ),
        )

        val encoded = AppDataJsonCodec.encodeDataForSync(legacy).toString()

        assertTrue(!encoded.contains("content://source-device/portrait"))
        assertTrue(encoded.contains("\"imageUri\":null"))
    }
}
