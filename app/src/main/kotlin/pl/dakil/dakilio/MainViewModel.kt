package pl.dakil.dakilio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.data.repository.OnboardingRepository
import pl.dakil.dakilio.core.data.repository.SettingsRepository
import pl.dakil.dakilio.core.model.ThemeMode
import pl.dakil.dakilio.core.ui.EntryTextScale

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val onboarding: OnboardingRepository,
) : ViewModel() {
    /** `null` until settings are read, so the first frame already uses the chosen theme. */
    val themeMode: StateFlow<ThemeMode?> = settingsRepository.settings
        .map<_, ThemeMode?> { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val showHints: StateFlow<Boolean> = settingsRepository.settings
        .map { it.showHints }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val entryTextScale: StateFlow<EntryTextScale> = settingsRepository.settings
        .map { EntryTextScale(timeline = it.timelineTextScale, editor = it.editorTextScale) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EntryTextScale())

    /** Whether the first-run intro is on screen; `null` until known. Decided once per launch. */
    private val _showIntro = MutableStateFlow<Boolean?>(null)
    val showIntro: StateFlow<Boolean?> = _showIntro.asStateFlow()

    init {
        viewModelScope.launch {
            val show = onboarding.shouldShowIntro()
            if (show) onboarding.markIntroStarted()
            _showIntro.value = show
        }
    }

    fun finishIntro() {
        if (_showIntro.value != true) return
        _showIntro.value = false
        viewModelScope.launch { onboarding.finishIntro() }
    }
}
