package pl.dakil.dakilio.feature.settings.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.data.repository.PostRepository
import pl.dakil.dakilio.core.data.repository.SettingsRepository
import pl.dakil.dakilio.core.model.TagStyle
import pl.dakil.dakilio.core.model.ThemeMode

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.System,
    val tagStyle: TagStyle = TagStyle.Icons,
    val showHints: Boolean = true,
    val samplePostCount: Int = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> =
        combine(settingsRepository.settings, postRepository.samplePostCount) { settings, samples ->
            SettingsUiState(settings.themeMode, settings.tagStyle, settings.showHints, samples)
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

    fun removeSamples(onDone: () -> Unit) {
        viewModelScope.launch {
            postRepository.removeSamplePosts()
            onDone()
        }
    }
}
