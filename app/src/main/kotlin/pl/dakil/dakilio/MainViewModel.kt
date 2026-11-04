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
import pl.dakil.dakilio.core.data.repository.AppLock
import pl.dakil.dakilio.core.data.repository.LockRepository
import pl.dakil.dakilio.core.data.repository.OnboardingRepository
import pl.dakil.dakilio.core.data.repository.SettingsRepository
import pl.dakil.dakilio.core.model.ThemeMode
import pl.dakil.dakilio.core.ui.EntryDisplay

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    lockRepository: LockRepository,
    private val onboarding: OnboardingRepository,
    appLock: AppLock,
) : ViewModel() {
    /** Whether the app lock covers the screen; `null` until known, so the first frame never shows the journal. */
    val locked: StateFlow<Boolean?> = appLock.locked

    /** Unlocked through recovery (a forgotten PIN): the app opens the lock settings to set a new one. */
    val recovered: StateFlow<Boolean> = appLock.recovered

    /** `FLAG_SECURE`: blank in recent apps, no screenshots. */
    val secureScreen: StateFlow<Boolean> = lockRepository.settings
        .map { it.secureScreen }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** `null` until settings are read, so the first frame already uses the chosen theme. */
    val themeMode: StateFlow<ThemeMode?> = settingsRepository.settings
        .map<_, ThemeMode?> { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val showHints: StateFlow<Boolean> = settingsRepository.settings
        .map { it.showHints }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val entryDisplay: StateFlow<EntryDisplay> = settingsRepository.settings
        .map { EntryDisplay(it.timelineTextScale, it.editorTextScale, it.previewLength, it.compactTags) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EntryDisplay())

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
