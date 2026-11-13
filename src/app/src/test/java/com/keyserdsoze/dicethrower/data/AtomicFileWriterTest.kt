package com.keyserdsoze.dicethrower.data

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AtomicFileWriterTest {
    @Test
    fun replacesContentWithoutLeavingTemporaryFilesAndKeepsOptionalBackup() {
        val directory = Files.createTempDirectory("dice-atomic").toFile()
        try {
            val target = directory.resolve("nested/state.json")
            val backup = directory.resolve("nested/state.json.bak")

            AtomicFileWriter.writeText(target, "first", backup)
            assertEquals("first", target.readText())
            assertFalse(backup.exists())

            AtomicFileWriter.writeText(target, "second", backup)
            assertEquals("second", target.readText())
            assertEquals("first", backup.readText())
            assertFalse(directory.resolve("nested/state.json.tmp").exists())

            AtomicFileWriter.writeText(target, "third")
            assertEquals("third", target.readText())
            assertEquals("first", backup.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun accountStateAndWriterIdAreWrittenAtomically() {
        val directory = Files.createTempDirectory("dice-atomic-small").toFile()
        try {
            val stateFile = directory.resolve("state.json")
            CloudAccountStateFileStore(stateFile).save(CloudAccountTransitions.useStandalone())
            assertEquals(CloudAccountTransitions.useStandalone(), CloudAccountStateFileStore(stateFile).load())

            val idFile = directory.resolve("writer-id")
            val id = InstallationWriterIdStore(idFile) { "writer-a" }.getOrCreate()
            assertEquals("writer-a", idFile.readText())
            assertEquals(id, InstallationWriterIdStore(idFile) { "writer-b" }.getOrCreate())
            assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        } finally {
            directory.deleteRecursively()
        }
    }
}
