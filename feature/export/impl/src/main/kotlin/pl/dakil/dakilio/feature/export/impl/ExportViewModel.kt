package pl.dakil.dakilio.feature.export.impl

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.data.repository.DiaryRepository
import pl.dakil.dakilio.core.data.repository.PostRepository
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Post

/** What the options select right now. */
internal data class ExportSelection(val entries: List<Post>, val photos: Int, val first: LocalDate?, val last: LocalDate?)

internal sealed interface ExportState {
    data object Idle : ExportState

    data class Exporting(val done: Int, val total: Int) : ExportState

    data class Done(val result: ExportResult, val format: ExportFormat) : ExportState

    data object Failed : ExportState
}

@HiltViewModel
internal class ExportViewModel @Inject constructor(
    posts: PostRepository,
    diaryRepository: DiaryRepository,
    private val exporter: JournalExporter,
    private val clock: Clock,
    @Dispatcher(DakilioDispatchers.Default) default: CoroutineDispatcher,
) : ViewModel() {
    private val _options = MutableStateFlow(ExportOptions())
    val options: StateFlow<ExportOptions> = _options.asStateFlow()

    val diaries: StateFlow<List<Diary>> = diaryRepository.diaries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selection: StateFlow<ExportSelection?> = combine(posts.posts, _options) { all, options ->
        val entries = selectEntries(all, options, today())
        ExportSelection(
            entries = entries,
            photos = entries.sumOf { post -> post.photos.count { File(it.path).isFile } },
            first = entries.firstOrNull()?.date,
            last = entries.lastOrNull()?.date,
        )
    }.flowOn(default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    private var job: Job? = null

    fun today(): LocalDate = LocalDate.now(clock.withZone(ZoneId.systemDefault()))

    fun setFormat(format: ExportFormat) = _options.update { it.copy(format = format) }

    fun setPeriod(period: ExportPeriod) = _options.update { it.copy(period = period) }

    fun setPhotos(photos: Boolean) = _options.update { it.copy(photos = photos) }

    /** Ticks or unticks a diary; with every diary ticked (or none), the filter is off. */
    fun toggleDiary(id: Long) = _options.update { options ->
        val ids = if (id in options.diaryIds) options.diaryIds - id else options.diaryIds + id
        options.copy(diaryIds = if (ids.size == diaries.value.size) emptySet() else ids)
    }

    fun allDiaries() = _options.update { it.copy(diaryIds = emptySet()) }

    /** Whether the Markdown export of the current options is a ZIP (it carries photos). */
    fun markdownIsZip(): Boolean = _options.value.photos && (selection.value?.photos ?: 0) > 0

    fun export(uri: Uri, labels: ExportLabels) {
        if (_state.value is ExportState.Exporting) return
        val entries = selection.value?.entries ?: return
        val options = _options.value
        _state.value = ExportState.Exporting(0, 0)
        job = viewModelScope.launch {
            _state.value = try {
                val result = exporter.export(uri, entries, options, labels) { done, total -> _state.update { ExportState.Exporting(done, total) } }
                ExportState.Done(result, options.format)
            } catch (e: CancellationException) {
                _state.value = ExportState.Idle
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Export failed", e)
                ExportState.Failed
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = ExportState.Idle
    }

    fun reset() {
        if (_state.value !is ExportState.Exporting) _state.value = ExportState.Idle
    }

    private companion object {
        const val TAG = "DakilioExport"
    }
}
