package pl.dakil.dakilio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pl.dakil.dakilio.core.data.repository.SettingsRepository
import pl.dakil.dakilio.core.model.ThemeMode

@HiltViewModel
class MainViewModel @Inject constructor(settingsRepository: SettingsRepository) : ViewModel() {
    /** `null` until settings are read, so the first frame already uses the chosen theme. */
    val themeMode: StateFlow<ThemeMode?> = settingsRepository.settings
        .map<_, ThemeMode?> { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
