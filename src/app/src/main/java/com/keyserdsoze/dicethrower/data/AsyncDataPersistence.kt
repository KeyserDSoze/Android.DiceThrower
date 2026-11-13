package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
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
}

/**
 * Serializes [LocalStore] writes on one background thread so edits never re-encode and validate
 * the whole document on the main thread. The UI keeps its in-memory copy as source of truth and
 * updates it immediately; jobs run strictly in order and every semantic of [LocalStore.saveData]
 * (stale-version check, validation, metadata stamping) is preserved.
 *
 * Once a job fails (stale/invalid) or a reload runs, jobs derived from the old copy are skipped
 * until the UI has received the replacement data (epoch handshake), so an outdated edit can never
 * overwrite newer content. All public methods must be called from the main thread.
 */
class AsyncDataPersistence(
    private val store: LocalStore,
    initialData: AppData,
    initialVersion: Long,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    private val epochIo = AtomicInteger(0)
    private var epochUi = 0
    private var pending = 0

    // Written only by the writer thread.
    @Volatile private var expectedVersion = initialVersion
    private var lastStored = initialData

    fun hasPending(): Boolean = pending > 0

    /** True when the stored document changed behind this copy and nothing is queued. */
    fun isOutOfDate(): Boolean = pending == 0 && store.dataVersion() != expectedVersion

    fun save(updated: AppData, onOutcome: (PersistOutcome) -> Unit) {
        val epoch = epochUi
        submit(onOutcome) {
            if (epoch != epochIo.get()) return@submit PersistOutcome.Skipped
            try {
                val saved = store.saveData(updated, expectedVersion = expectedVersion)
                expectedVersion = store.dataVersion()
                lastStored = saved
                PersistOutcome.Saved(saved)
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
        val loaded = store.loadData()
        expectedVersion = store.dataVersion()
        lastStored = loaded
        return loaded
    }

    private fun submit(onOutcome: (PersistOutcome) -> Unit, job: () -> PersistOutcome) {
        pending++
        scope.launch {
            val outcome = job()
            val epochAfter = epochIo.get()
            withContext(mainDispatcher) {
                pending--
                if (outcome is PersistOutcome.Stale || outcome is PersistOutcome.Invalid ||
                    outcome is PersistOutcome.Reloaded
                ) {
                    epochUi = epochAfter
                }
                onOutcome(outcome)
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(
            SupervisorJob() + Executors.newSingleThreadExecutor { r ->
                Thread(r, "dice-store-writer").apply { isDaemon = false }
            }.asCoroutineDispatcher(),
        )
    }
}
