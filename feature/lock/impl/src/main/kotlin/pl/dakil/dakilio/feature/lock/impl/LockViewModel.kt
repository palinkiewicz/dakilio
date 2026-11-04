package pl.dakil.dakilio.feature.lock.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.data.repository.AppLock
import pl.dakil.dakilio.core.data.repository.LockRepository
import pl.dakil.dakilio.core.data.repository.VerifyResult
import pl.dakil.dakilio.core.model.LockSettings

/** The state of the recovery-question dialog. */
internal data class AnswerState(val checking: Boolean = false, val wrong: Int = 0)

@HiltViewModel
internal class LockViewModel @Inject constructor(
    private val repository: LockRepository,
    private val appLock: AppLock,
    val biometrics: BiometricAuth,
) : ViewModel() {
    val settings: StateFlow<LockSettings?> = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val lockedUntil: StateFlow<Long?> = repository.lockedUntil.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val unlock = UnlockController(repository, viewModelScope) {
        // The lock screen said why biometrics went off; once the PIN got the user in, it's been read.
        if (settings.value?.biometricsInvalidated == true) viewModelScope.launch { repository.clearBiometricsInvalidated() }
        appLock.unlock()
    }

    private val _biometricError = MutableStateFlow<String?>(null)
    val biometricError: StateFlow<String?> = _biometricError.asStateFlow()

    private val _answer = MutableStateFlow(AnswerState())
    val answer: StateFlow<AnswerState> = _answer.asStateFlow()

    fun onBiometricResult(result: BiometricResult) {
        _biometricError.value = null
        when (result) {
            BiometricResult.Success -> viewModelScope.launch {
                repository.recordSuccess()
                unlock.reset()
                appLock.unlock()
            }
            BiometricResult.Invalidated -> viewModelScope.launch { repository.invalidateBiometrics() }
            is BiometricResult.Error -> _biometricError.value = result.message.toString()
            BiometricResult.Cancelled -> Unit
        }
    }

    /** The phone's screen lock was confirmed: in, with the chance to set a new PIN without the old one. */
    fun onDeviceCredentialResult(result: BiometricResult) {
        if (result != BiometricResult.Success) return
        viewModelScope.launch {
            repository.recordSuccess()
            unlock.reset()
            appLock.unlock(recovered = true)
        }
    }

    fun submitAnswer(answer: String) {
        if (_answer.value.checking || answer.isBlank()) return
        _answer.value = _answer.value.copy(checking = true)
        viewModelScope.launch {
            when (repository.verifyRecoveryAnswer(answer)) {
                VerifyResult.Ok -> {
                    _answer.value = AnswerState()
                    unlock.reset()
                    appLock.unlock(recovered = true)
                }
                else -> _answer.value = AnswerState(checking = false, wrong = _answer.value.wrong + 1)
            }
        }
    }
}
