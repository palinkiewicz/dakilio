package pl.dakil.dakilio.feature.settings.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.data.repository.PostRepository
import pl.dakil.dakilio.core.data.repository.SettingsRepository
import pl.dakil.dakilio.core.model.TagStyle
import pl.dakil.dakilio.core.model.TextScale
import pl.dakil.dakilio.core.model.ThemeMode
import pl.dakil.dakilio.core.model.WeekStart

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.System,
    val tagStyle: TagStyle = TagStyle.Icons,
    val showHints: Boolean = true,
    val timelineTextScale: Float = TextScale.DEFAULT,
    val editorTextScale: Float = TextScale.DEFAULT,
    val closeAfterSave: Boolean = true,
    val weekStart: WeekStart = WeekStart.Region,
    /** What [WeekStart.Region] means on this device. */
    val regionFirstDay: DayOfWeek = DayOfWeek.MONDAY,
    val samplePostCount: Int = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> =
        combine(settingsRepository.settings, postRepository.samplePostCount) { settings, samples ->
            SettingsUiState(
                themeMode = settings.themeMode,
                tagStyle = settings.tagStyle,
                showHints = settings.showHints,
                timelineTextScale = settings.timelineTextScale,
                editorTextScale = settings.editorTextScale,
                closeAfterSave = settings.closeAfterSave,
                weekStart = settings.weekStart,
                regionFirstDay = settingsRepository.regionFirstDayOfWeek(),
                samplePostCount = samples,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setTagStyle(style: TagStyle) {
        viewModelScope.launch { settingsRepository.setTagStyle(style) }
    }

    fun setShowHints(show: Boolean) {
        viewModelScope.launch { settingsRepository.setShowHints(show) }
    }

    fun setTimelineTextScale(scale: Float) {
        viewModelScope.launch { settingsRepository.setTimelineTextScale(scale) }
    }

    fun setEditorTextScale(scale: Float) {
        viewModelScope.launch { settingsRepository.setEditorTextScale(scale) }
    }

    fun setCloseAfterSave(close: Boolean) {
        viewModelScope.launch { settingsRepository.setCloseAfterSave(close) }
    }

    fun setWeekStart(start: WeekStart) {
        viewModelScope.launch { settingsRepository.setWeekStart(start) }
    }

    fun removeSamples(onDone: () -> Unit) {
        viewModelScope.launch {
            postRepository.removeSamplePosts()
            onDone()
        }
    }
}
