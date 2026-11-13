package com.keyserdsoze.dicethrower.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.keyserdsoze.dicethrower.data.sync.AndroidSyncLocalGateway
import com.keyserdsoze.dicethrower.data.sync.CharacterSyncCommit
import com.keyserdsoze.dicethrower.data.sync.LocalSyncCommitPlan
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.model.CharacterProfile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LocalStoreConcurrencyTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun store() = LocalStore(context)

    @Before
    fun seedData() {
        store().replaceAll(
            AppData(characters = listOf(CharacterProfile("hero", "Initial"))),
            AppSettings(),
        )
    }

    @After
    fun clearData() {
        store().replaceAll(AppData(), AppSettings())
    }

    @Test
    fun twoStoreInstancesCannotBothCommitFromTheSameVersion() {
        val first = LocalDataPersistenceStore(store())
        val second = LocalDataPersistenceStore(store())
        val baseline = first.load()
        val writers = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val results = listOf(first to "First writer", second to "Second writer").map { (backend, name) ->
                writers.submit(Callable {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    runCatching {
                        backend.save(baseline.data.copy(
                            characters = listOf(baseline.data.characters.single().copy(name = name)),
                        ), baseline.version)
                    }
                })
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            val completed = results.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, completed.count { it.isSuccess })
            assertEquals(1, completed.count { it.exceptionOrNull() is StaleDataException })
            val saved = completed.single { it.isSuccess }.getOrThrow()
            assertEquals(saved, first.load())
            assertEquals(baseline.version + 1, saved.version)
        } finally {
            start.countDown()
            writers.shutdownNow()
            writers.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun syncCommitStillRefusesToReplaceACharacterEditedAfterItsSnapshot() {
        val local = store()
        val gateway = AndroidSyncLocalGateway(local)
        val snapshot = gateway.snapshot(100L)
        val revision = snapshot.data.characterSyncMetadata.single().revision
        local.saveData(snapshot.data.copy(characters = listOf(CharacterProfile("hero", "Local edit"))))

        val result = gateway.commit(LocalSyncCommitPlan(
            characters = listOf(CharacterSyncCommit(
                characterId = "hero",
                expectedRevision = revision,
                replacement = AppData(characters = listOf(CharacterProfile("hero", "Drive version"))),
            )),
            downloadedAssets = emptyList(), resolvedDeletions = emptyList(), settings = null,
            successfulAt = 200L,
        ))
        assertEquals(setOf("hero"), result.skippedCharacterIds)
        assertEquals("Local edit", store().loadData().characters.single().name)
    }
}
