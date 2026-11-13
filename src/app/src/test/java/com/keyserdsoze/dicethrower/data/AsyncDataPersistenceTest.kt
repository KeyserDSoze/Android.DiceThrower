package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.CoroutineContext

class AsyncDataPersistenceTest {
    private fun data(name: String) = AppData(characters = listOf(CharacterProfile("hero", name)))

    @Test
    fun savesRunInOrderWithTheVersionOfEachCommittedSnapshot() {
        Rig(data("Initial")).use { rig ->
            val first = data("First")
            val second = data("Second")
            rig.save(first)
            rig.save(second)
            assertEquals(data("Initial"), rig.store.current.data)
            assertTrue(rig.persistence.hasPending())
            rig.drain()
            assertEquals(listOf(0L, 1L), rig.store.expectedVersions)
            assertEquals(second, rig.store.current.data)
            assertEquals(listOf(PersistOutcome.Saved(first), PersistOutcome.Saved(second)), rig.outcomes)
            assertFalse(rig.persistence.hasPending())
        }
    }

    @Test
    fun staleCopyAndAllItsQueuedDescendantsCannotOverwriteAnImport() {
        Rig(data("Initial")).use { rig ->
            val imported = data("Imported elsewhere")
            rig.store.externalWrite(imported)
            assertTrue(rig.persistence.isOutOfDate())
            rig.save(data("Stale edit"))
            rig.save(data("Descendant of stale edit"))
            rig.drain()
            assertEquals(listOf(PersistOutcome.Stale(imported), PersistOutcome.Skipped), rig.outcomes)
            assertEquals(imported, rig.store.current.data)
            assertFalse(rig.persistence.isOutOfDate())
            val retried = data("Edit after reload")
            rig.save(retried)
            rig.drain()
            assertEquals(PersistOutcome.Saved(retried), rig.outcomes.last())
            assertEquals(retried, rig.store.current.data)
        }
    }

    @Test
    fun snapshotTokenCannotAdoptAnExternalWriteBetweenIoAndUiDelivery() {
        Rig(data("Initial")).use { rig ->
            rig.save(data("First"))
            rig.worker.runAll()
            // Save already returned a content/version pair; a different writer commits before
            // the callback reaches UI. It must be detected, not mistaken for our saved version.
            val remote = data("Remote")
            rig.store.externalWrite(remote)
            rig.save(data("Second"))
            rig.drain()
            assertEquals(PersistOutcome.Stale(remote), rig.outcomes.last())
            assertEquals(remote, rig.store.current.data)
        }
    }

    @Test
    fun invalidEditRevertsToLastStoredCopyAndDropsQueuedDescendants() {
        Rig(data("Initial")).use { rig ->
            rig.store.beforeSave = { throw IllegalArgumentException("Invalid formula") }
            rig.save(data("Invalid"))
            rig.save(data("Derived from invalid"))
            rig.drain()
            assertEquals(listOf(PersistOutcome.Invalid(data("Initial")), PersistOutcome.Skipped), rig.outcomes)
            rig.store.beforeSave = null
            rig.save(data("Valid retry"))
            rig.drain()
            assertEquals(data("Valid retry"), rig.store.current.data)
            assertFalse(rig.persistence.hasPending())
        }
    }

    @Test
    fun explicitReloadRunsAfterPendingSavesAndDropsEditsBeforeItsUiReply() {
        Rig(data("Initial")).use { rig ->
            val saved = data("Saved")
            rig.save(saved)
            rig.persistence.reload(rig.outcomes::add)
            rig.save(data("Edit from the old screen"))
            rig.drain()
            assertEquals(listOf(PersistOutcome.Saved(saved), PersistOutcome.Reloaded(saved), PersistOutcome.Skipped), rig.outcomes)
            assertEquals(saved, rig.store.current.data)
            rig.save(data("Edit after the reload reply"))
            rig.drain()
            assertEquals(data("Edit after the reload reply"), rig.store.current.data)
        }
    }

    @Test
    fun writeFailureIsReportedAndDoesNotLeaveTheWriterPermanentlyBusy() {
        Rig(data("Initial")).use { rig ->
            val failure = IOException("Disk unavailable")
            rig.store.beforeSave = { throw failure }
            rig.save(data("Not written"))
            rig.save(data("Queued descendant"))
            rig.drain()
            assertEquals(PersistOutcome.Failed(data("Initial"), failure), rig.outcomes.first())
            assertEquals(PersistOutcome.Skipped, rig.outcomes.last())
            assertFalse(rig.persistence.hasPending())
            rig.store.beforeSave = null
            rig.save(data("Retried"))
            rig.drain()
            assertEquals(data("Retried"), rig.store.current.data)
        }
    }

    @Test
    fun failureAfterTheDocumentWriteAdoptsWhatActuallyReachedStorage() {
        Rig(data("Initial")).use { rig ->
            val failure = IOException("Journal write failed")
            rig.store.afterSave = { throw failure }
            val written = data("Already written")
            rig.save(written)
            rig.drain()
            assertEquals(written, rig.store.current.data)
            assertEquals(PersistOutcome.Failed(written, failure), rig.outcomes.single())
            rig.store.afterSave = null
            rig.save(data("Next edit"))
            rig.drain()
            assertEquals(listOf(0L, 1L), rig.store.expectedVersions)
        }
    }

    @Test
    fun unreadableReloadCanBeRetriedWithoutLosingTheLastReadableCopy() {
        Rig(data("Initial")).use { rig ->
            val failure = IOException("Cannot read")
            rig.store.loadFailure = failure
            rig.persistence.reload(rig.outcomes::add)
            rig.drain()
            assertEquals(PersistOutcome.Failed(data("Initial"), failure), rig.outcomes.single())
            assertFalse(rig.persistence.hasPending())
            rig.store.loadFailure = null
            val imported = data("Imported")
            rig.store.externalWrite(imported)
            rig.persistence.reload(rig.outcomes::add)
            rig.drain()
            assertEquals(PersistOutcome.Reloaded(imported), rig.outcomes.last())
        }
    }

    @Test
    fun cancellationIsNotReportedAsAStorageFailureAndClearsPending() {
        Rig(data("Initial")).use { rig ->
            rig.store.beforeSave = { throw CancellationException("Cancelled") }
            rig.save(data("Not saved"))
            rig.drain()
            assertTrue(rig.outcomes.isEmpty())
            assertFalse(rig.persistence.hasPending())
        }
    }

    private class Rig(initial: AppData) : AutoCloseable {
        val store = MemoryStore(initial)
        val worker = QueuedDispatcher()
        private val main = QueuedDispatcher()
        private val scope = CoroutineScope(SupervisorJob() + worker)
        val persistence = AsyncDataPersistence(store, initial, 0L, main, scope)
        val outcomes = mutableListOf<PersistOutcome>()

        fun save(data: AppData) = persistence.save(data, outcomes::add)

        fun drain() {
            repeat(100) {
                if (!worker.runAll() && !main.runAll()) return
                main.runAll()
            }
            error("Writer did not become idle")
        }

        override fun close() {
            scope.cancel()
            drain()
        }
    }

    private class MemoryStore(initial: AppData) : DataPersistenceStore {
        var current = DataSnapshot(initial, 0L)
        val expectedVersions = mutableListOf<Long>()
        var beforeSave: (() -> Unit)? = null
        var afterSave: (() -> Unit)? = null
        var loadFailure: Exception? = null

        override fun load(): DataSnapshot {
            loadFailure?.let { throw it }
            return current
        }

        override fun save(data: AppData, expectedVersion: Long): DataSnapshot {
            expectedVersions += expectedVersion
            if (current.version != expectedVersion) throw StaleDataException()
            beforeSave?.invoke()
            current = DataSnapshot(data, current.version + 1)
            afterSave?.invoke()
            return current
        }

        override fun version() = current.version

        fun externalWrite(data: AppData) {
            current = DataSnapshot(data, current.version + 1)
        }
    }

    /** Deterministic scheduling, no real threads, timers or coroutine-test dependency. */
    private class QueuedDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }

        fun runAll(): Boolean {
            val hadWork = queue.isNotEmpty()
            var steps = 0
            while (queue.isNotEmpty()) {
                check(++steps < 1000) { "Dispatcher did not become idle" }
                queue.removeFirst().run()
            }
            return hadWork
        }
    }
}
