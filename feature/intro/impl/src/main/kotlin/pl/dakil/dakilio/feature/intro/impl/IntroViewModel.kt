package pl.dakil.dakilio.feature.intro.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.data.repository.OnboardingRepository
import pl.dakil.dakilio.core.model.SuggestedGroup

@HiltViewModel
internal class IntroGroupsViewModel @Inject constructor(
    private val repository: OnboardingRepository,
) : ViewModel() {
    val suggestions: List<SuggestedGroup> = repository.suggestedGroups

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** Drops the groups that weren't picked (this is when the database is created), then leaves the intro. */
    fun getStarted(keys: Set<String>, onFinished: () -> Unit) {
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            repository.keepStarterGroups(keys)
            onFinished()
        }
    }
}
