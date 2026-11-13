package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/** Result of one queued persistence job, delivered on the main dispatcher. */
sealed interface PersistOutcome {
    /** Stored successfully; [data] is the stamped copy. */
    data class Saved(val data: AppData) : PersistOutcome

    /** Dropped because an earlier job invalidated the copy it was derived from. */
    data object Skipped : PersistOutcome

    /** Stored data changed elsewhere; [data] is the stored document to show instead. */
    data class Stale(val data: AppData) : PersistOutcome

    /** Validation refused the edit; [data] is the last stored state. */
    data class Invalid(val data: AppData) : PersistOutcome

    /** Result of an explicit [AsyncDataPersistence.reload]. */
    data class Reloaded(val data: AppData) : PersistOutcome

    /** A storage operation failed. [data] is the latest readable copy, not an assumed rollback. */
    data class Failed(val data: AppData, val error: Exception) : PersistOutcome
}

/** Content and optimistic-concurrency token are captured within the same store transaction. */
internal data class DataSnapshot(val data: AppData, val version: Long)

internal interface DataPersistenceStore {
    fun load(): DataSnapshot
    fun save(data: AppData, expectedVersion: Long): DataSnapshot
    fun version(): Long
}

internal class LocalDataPersistenceStore(private val store: LocalStore) : DataPersistenceStore {
    override fun load(): DataSnapshot = store.withDataLock {
        DataSnapshot(store.loadData(), store.dataVersion())
    }

    override fun save(data: AppData, expectedVersion: Long): DataSnapshot = store.withDataLock {
        DataSnapshot(store.saveData(data, expectedVersion), store.dataVersion())
    }

    override fun version(): Long = store.dataVersion()
}

/**
 * Serializes [LocalStore] writes on one background dispatcher lane so edits never re-encode and validate
 * the whole document on the main thread. The UI keeps its in-memory copy as source of truth and
 * updates it immediately; jobs run strictly in order and every semantic of [LocalStore.saveData]
 * (stale-version check, validation, metadata stamping) is preserved.
 *
 * Once a job fails (stale/invalid/storage error) or a reload runs, jobs derived from the old copy are skipped
 * until the UI has received the replacement data (epoch handshake), so an outdated edit can never
 * overwrite newer content. All public methods must be called from the main thread.
 */
class AsyncDataPersistence internal constructor(
    private val store: DataPersistenceStore,
    initialData: AppData,
    initialVersion: Long,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val writerScope: CoroutineScope = scope,
) {
    constructor(store: LocalStore, initialData: AppData, initialVersion: Long) :
        this(LocalDataPersistenceStore(store), initialData, initialVersion)

    private val epochIo = AtomicInteger(0)
    private var epochUi = 0
    private var pending = 0

    // Written only on the serialized writer dispatcher.
    @Volatile private var expectedVersion = initialVersion
    private var lastStored = initialData

    fun hasPending(): Boolean = pending > 0

    /** True when the stored document changed behind this copy and nothing is queued. */
    fun isOutOfDate(): Boolean = pending == 0 && store.version() != expectedVersion

    fun save(updated: AppData, onOutcome: (PersistOutcome) -> Unit) {
        val epoch = epochUi
        submit(onOutcome) {
            if (epoch != epochIo.get()) return@submit PersistOutcome.Skipped
            try {
                val saved = store.save(updated, expectedVersion)
                expectedVersion = saved.version
                lastStored = saved.data
                PersistOutcome.Saved(saved.data)
            } catch (_: StaleDataException) {
                epochIo.incrementAndGet()
                PersistOutcome.Stale(adoptStored())
            } catch (_: IllegalArgumentException) {
                epochIo.incrementAndGet()
                PersistOutcome.Invalid(lastStored)
            }
        }
    }

    /** Reloads the stored document after every previously queued save has run. */
    fun reload(onOutcome: (PersistOutcome) -> Unit) {
        submit(onOutcome) {
            epochIo.incrementAndGet()
            PersistOutcome.Reloaded(adoptStored())
        }
    }

    private fun adoptStored(): AppData {
        val loaded = store.load()
        expectedVersion = loaded.version
        lastStored = loaded.data
        return loaded.data
    }

    private fun submit(onOutcome: (PersistOutcome) -> Unit, job: () -> PersistOutcome) {
        pending++
        writerScope.launch {
            var outcome: PersistOutcome? = null
            try {
                outcome = job()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // An IOException used to leave pending > 0 forever and silently break the writer.
                // A write might already have reached disk before a journal failure: re-read it
                // rather than claiming the edit was rolled back.
                epochIo.incrementAndGet()
                val readable = runCatching { adoptStored() }.getOrElse { lastStored }
                outcome = PersistOutcome.Failed(readable, error)
            } finally {
                val epochAfter = epochIo.get()
                withContext(NonCancellable + mainDispatcher) {
                    pending--
                    val completed = outcome
                    if (completed is PersistOutcome.Stale || completed is PersistOutcome.Invalid ||
                        completed is PersistOutcome.Reloaded || completed is PersistOutcome.Failed
                    ) {
                        epochUi = epochAfter
                    }
                    if (completed != null) onOutcome(completed)
                }
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO.limitedParallelism(1),
        )
    }
}
