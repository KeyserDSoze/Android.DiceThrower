package com.keyserdsoze.dicethrower.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keyserdsoze.dicethrower.data.CharacterFileReader
import com.keyserdsoze.dicethrower.model.AppData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** File preview only: never imports, remaps IDs or writes data before explicit confirmation. */
internal class CharacterFileImportViewModel(
    private val decode: suspend (Uri) -> AppData,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    var preview by mutableStateOf<AppData?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    private var generation = 0L
    private var loadingJob: Job? = null

    fun load(uri: Uri) {
        clear()
        val request = generation
        loading = true
        loadingJob = viewModelScope.launch {
            try {
                val incoming = withContext(io) { decode(uri) }
                if (request == generation) {
                    preview = incoming
                    loading = false
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                reject(request)
            } catch (_: StackOverflowError) {
                // Untrusted, deeply nested JSON must remain a recoverable preview error.
                reject(request)
            }
        }
    }

    fun clear() {
        generation++
        loadingJob?.cancel()
        loadingJob = null
        preview = null
        loading = false
        failed = false
    }

    private fun reject(request: Long) {
        if (request != generation) return
        preview = null
        loading = false
        failed = true
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val resolver = context.applicationContext.contentResolver
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CharacterFileImportViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return CharacterFileImportViewModel(decode = { uri ->
                val coroutine = currentCoroutineContext()
                val stream = resolver.openInputStream(uri) ?: error("Unable to open character file")
                CharacterFileReader.decode(stream) { coroutine.ensureActive() }
            }) as T
        }
    }
}
