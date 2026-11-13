package com.keyserdsoze.dicethrower.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.keyserdsoze.dicethrower.data.AsyncDataPersistence
import com.keyserdsoze.dicethrower.data.LocalDataPersistenceStore
import com.keyserdsoze.dicethrower.data.LocalStore
import com.keyserdsoze.dicethrower.data.PersistOutcome
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.dice.DiceRollVisualEvent
import com.keyserdsoze.dicethrower.ui.v2.RollSession
import com.keyserdsoze.dicethrower.ui.v2.RollSessionKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** Retains optimistic edits and their writer across Activity recreation, without retaining it. */
class AppDataViewModel(store: LocalStore) : ViewModel() {
    var data by mutableStateOf(AppData())
        private set
    var ready by mutableStateOf(false)
        private set
    var loadFailed by mutableStateOf(false)
        private set

    enum class Notice { SAVED, STALE, INVALID, STORAGE_ERROR }

    private val noticeQueue = Channel<Notice>(Channel.UNLIMITED)
    val notices = noticeQueue.receiveAsFlow()
    private var cleared = false
    // Bounded to one active destination; neither the database nor GL resources go into a Bundle.
    internal var activeRollSession: RollSession? = null
        private set
    private val persistence = AsyncDataPersistence(LocalDataPersistenceStore(store), AppData(), 0)

    init {
        reload()
    }

    fun save(updated: AppData) {
        if (!ready) return
        data = updated
        persistence.save(updated, ::applyOutcome)
    }

    fun reload() {
        loadFailed = false
        persistence.reload(::applyOutcome)
    }

    fun refreshIfNeeded() {
        if (ready && persistence.isOutOfDate()) reload()
    }

    internal fun rollSessionFor(key: RollSessionKey, preview: DiceRollVisualEvent): RollSession {
        val previous = activeRollSession
        if (previous?.key == key) return previous
        return RollSession(key, preview).also { next ->
            // A level edit from the stats sheet invalidates the numbers, not the open sheet.
            if (previous?.key?.roll?.id == key.roll.id &&
                previous.key.roll.characterId == key.roll.characterId
            ) {
                next.showStats.value = previous.showStats.value
            }
            activeRollSession = next
        }
    }

    fun clearRollSession() {
        activeRollSession = null
    }

    private fun applyOutcome(outcome: PersistOutcome) {
        if (cleared) return
        when (outcome) {
            is PersistOutcome.Saved -> {
                if (!persistence.hasPending()) data = outcome.data
                noticeQueue.trySend(Notice.SAVED)
            }
            PersistOutcome.Skipped -> Unit
            is PersistOutcome.Reloaded -> {
                data = outcome.data
                ready = true
                loadFailed = false
            }
            is PersistOutcome.Stale -> {
                data = outcome.data
                noticeQueue.trySend(Notice.STALE)
            }
            is PersistOutcome.Invalid -> {
                data = outcome.data
                noticeQueue.trySend(Notice.INVALID)
            }
            is PersistOutcome.Failed -> {
                data = outcome.data
                loadFailed = !ready
                if (ready) noticeQueue.trySend(Notice.STORAGE_ERROR)
            }
        }
    }

    override fun onCleared() {
        // Submitted disk writes drain in the process-wide writer; a closed screen gets no events.
        cleared = true
        activeRollSession = null
        noticeQueue.close()
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AppDataViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return AppDataViewModel(LocalStore(applicationContext)) as T
        }
    }
}
