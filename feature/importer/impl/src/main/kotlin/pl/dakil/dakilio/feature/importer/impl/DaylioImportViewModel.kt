package pl.dakil.dakilio.feature.importer.impl

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.data.repository.DaylioImportRepository
import pl.dakil.dakilio.core.data.repository.IncomingBackups
import pl.dakil.dakilio.core.data.repository.DaylioImportResult
import pl.dakil.dakilio.core.data.repository.DaylioPreview
import pl.dakil.dakilio.core.daylio.DaylioError
import pl.dakil.dakilio.core.daylio.DaylioImportException

internal sealed interface ImportUiState {
    data object Pick : ImportUiState

    data object Reading : ImportUiState

    data class Preview(val preview: DaylioPreview) : ImportUiState

    /** [total] photos to copy; once [done] reaches it, the database swap is running. */
    data class Importing(val done: Int, val total: Int) : ImportUiState

    data class Done(val result: DaylioImportResult) : ImportUiState

    /** [reason] is null for an unexpected failure (I/O, database…). */
    data class Failed(val reason: DaylioError?, val duringImport: Boolean) : ImportUiState
}

@HiltViewModel
internal class DaylioImportViewModel @Inject constructor(
    private val repository: DaylioImportRepository,
    incoming: IncomingBackups,
) : ViewModel() {
    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Pick)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        // A file shared to Dakilio replaces whatever is on screen, unless an import is already running.
        viewModelScope.launch {
            incoming.pending.filterNotNull().collect {
                val uri = incoming.take(dakilio = false) ?: return@collect
                if (_state.value !is ImportUiState.Importing) open(uri)
            }
        }
    }

    fun open(uri: Uri) {
        if (_state.value is ImportUiState.Importing) return
        job?.cancel()
        _state.value = ImportUiState.Reading
        job = viewModelScope.launch {
            _state.value = try {
                ImportUiState.Preview(repository.open(uri))
            } catch (e: CancellationException) {
                throw e
            } catch (e: DaylioImportException) {
                repository.close()
                ImportUiState.Failed(e.reason, duringImport = false)
            } catch (e: Exception) {
                Log.w(TAG, "Opening the backup failed", e)
                repository.close()
                ImportUiState.Failed(null, duringImport = false)
            }
        }
    }

    fun import() {
        if (_state.value !is ImportUiState.Preview) return
        _state.value = ImportUiState.Importing(0, 0)
        job = viewModelScope.launch {
            _state.value = try {
                val result = repository.import { done, total -> _state.update { ImportUiState.Importing(done, total) } }
                ImportUiState.Done(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Import failed", e)
                ImportUiState.Failed((e as? DaylioImportException)?.reason, duringImport = true)
            } finally {
                repository.close()
            }
        }
    }

    /** Back to picking a file (from a preview or an error). */
    fun reset() {
        if (_state.value is ImportUiState.Importing) return
        job?.cancel()
        repository.close()
        _state.value = ImportUiState.Pick
    }

    override fun onCleared() {
        repository.close()
    }

    private companion object {
        const val TAG = "DaylioImport"
    }
}
