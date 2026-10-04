package com.keyserdsoze.dicethrower.data

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class InstallationWriterIdStoreTest {
    @Test
    fun writerIdPersistsLocallyAndResetsWhenNoBackupFileIsRemoved() {
        val directory = Files.createTempDirectory("dice-writer-id").toFile()
        val file = directory.resolve("writer-id")
        val ids = ArrayDeque(listOf("writer-a", "writer-b"))

        try {
            val first = InstallationWriterIdStore(file) { ids.removeFirst() }.getOrCreate()
            val reopened = InstallationWriterIdStore(file) { ids.removeFirst() }.getOrCreate()
            assertEquals(first, reopened)

            file.delete()
            val reinstalled = InstallationWriterIdStore(file) { ids.removeFirst() }.getOrCreate()
            assertNotEquals(first, reinstalled)
            assertEquals("writer-b", reinstalled)
        } finally {
            directory.deleteRecursively()
        }
    }
}
