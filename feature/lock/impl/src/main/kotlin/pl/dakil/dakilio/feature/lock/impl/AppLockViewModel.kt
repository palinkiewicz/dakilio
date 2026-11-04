package pl.dakil.dakilio.feature.lock.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.data.repository.AppLock
import pl.dakil.dakilio.core.data.repository.LockRepository
import pl.dakil.dakilio.core.model.AutoLockDelay
import pl.dakil.dakilio.core.model.BiometricStrength
import pl.dakil.dakilio.core.model.LockMethod
import pl.dakil.dakilio.core.model.LockSettings
import pl.dakil.dakilio.core.security.PasscodeRules

/**
 * Choosing a PIN or password: type it, type it again, then (the first time, when the device can)
 * an offer to unlock with biometrics too.
 *
 * @property first the first entry, once the user moved on to confirming it.
 * @property mismatch bumps when the confirmation didn't match, for the shake and the message.
 */
internal data class SetupState(
    val method: LockMethod,
    val first: String? = null,
    val input: String = "",
    val mismatch: Int = 0,
    val saving: Boolean = false,
    val offerBiometrics: Boolean = false,
) {
    val confirming: Boolean get() = first != null

    val inputValid: Boolean
        get() = if (method == LockMethod.Pin) PasscodeRules.isValidPin(input) else PasscodeRules.isValidPassword(input)
}

/** One-off confirmations for the snackbar. */
internal enum class AppLockMessage { LockOn, CredentialChanged, LockOff, BiometricsOn, RecoverySaved }

@HiltViewModel
internal class AppLockViewModel @Inject constructor(
    private val repository: LockRepository,
    private val appLock: AppLock,
    val biometrics: BiometricAuth,
) : ViewModel() {
    val settings: StateFlow<LockSettings?> = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val lockedUntil: StateFlow<Long?> = repository.lockedUntil.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The current PIN or password was confirmed on this screen (or the user came in through recovery). */
    private val _verified = MutableStateFlow(appLock.recovered.value)
    val verified: StateFlow<Boolean> = _verified.asStateFlow()

    val unlock = UnlockController(repository, viewModelScope) { _verified.value = true }

    private val _setup = MutableStateFlow<SetupState?>(null)
    val setup: StateFlow<SetupState?> = _setup.asStateFlow()

    private val _message = MutableStateFlow<AppLockMessage?>(null)
    val message: StateFlow<AppLockMessage?> = _message.asStateFlow()

    /** Re-read when the screen resumes: the user may have just enrolled a fingerprint. */
    private val _availability = MutableStateFlow(BiometricAvailability.Unavailable)
    val availability: StateFlow<BiometricAvailability> = _availability.asStateFlow()

    /** Whether the phone has a screen lock, which screen-lock recovery needs; re-read on resume too. */
    private val _deviceSecure = MutableStateFlow(biometrics.deviceSecure)
    val deviceSecure: StateFlow<Boolean> = _deviceSecure.asStateFlow()

    val recovered: StateFlow<Boolean> get() = appLock.recovered

    init {
        viewModelScope.launch { refreshBiometrics(repository.settings.first().biometricStrength) }
        // In through a forgotten PIN (even with this screen already open): straight to choosing a new one.
        viewModelScope.launch {
            appLock.recovered.collect { recovered ->
                if (!recovered) return@collect
                val current = repository.settings.first()
                _verified.value = true
                if (current.enabled && _setup.value == null) _setup.value = SetupState(current.method)
            }
        }
    }

    /** Re-reads what the device offers; the user may have just added a fingerprint or a screen lock. */
    fun refreshBiometrics(strength: BiometricStrength? = settings.value?.biometricStrength) {
        _availability.value = biometrics.availability(strength ?: BiometricStrength.Standard)
        _deviceSecure.value = biometrics.deviceSecure
    }

    fun onVerifyBiometricResult(result: BiometricResult) {
        when (result) {
            BiometricResult.Success -> viewModelScope.launch {
                repository.recordSuccess()
                unlock.reset()
                _verified.value = true
            }
            BiometricResult.Invalidated -> viewModelScope.launch { repository.invalidateBiometrics() }
            else -> Unit
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    // --- Choosing a PIN or password ---

    fun startSetup(method: LockMethod) {
        _setup.value = SetupState(method)
    }

    fun setupDigit(digit: Char) {
        val state = _setup.value ?: return
        if (state.saving || state.input.length >= PasscodeRules.PIN_MAX) return
        val input = state.input + digit
        _setup.value = state.copy(input = input)
        // Confirming knows the length, so it checks as soon as it's reached.
        if (state.first != null && input.length == state.first.length) setupNext()
    }

    fun setupBackspace() = _setup.update { it?.copy(input = it.input.dropLast(1)) }

    fun setupText(text: String) = _setup.update { it?.copy(input = text.take(PasscodeRules.PASSWORD_MAX)) }

    fun setupNext() {
        val state = _setup.value ?: return
        if (state.saving) return
        when {
            state.first == null -> if (state.inputValid) _setup.value = state.copy(first = state.input, input = "")
            state.input != state.first -> _setup.value = SetupState(state.method, mismatch = state.mismatch + 1)
            else -> save(state)
        }
    }

    /** Back inside the flow: from confirming to choosing again, else out of it. Returns false when out. */
    fun setupBack(): Boolean {
        val state = _setup.value ?: return false
        _setup.value = when {
            state.offerBiometrics -> null
            state.first != null -> SetupState(state.method)
            else -> null
        }
        return true
    }

    fun cancelSetup() {
        _setup.value = null
    }

    private fun save(state: SetupState) {
        _setup.value = state.copy(saving = true)
        viewModelScope.launch {
            val wasOn = repository.settings.first().enabled
            repository.setCredential(state.method, state.input.toCharArray())
            _verified.value = true
            // A new credential ends a recovery: from now on the new one is needed.
            if (appLock.recovered.value) appLock.unlock(recovered = false)
            val current = repository.settings.first()
            refreshBiometrics(current.biometricStrength)
            val offer = !current.biometrics && _availability.value == BiometricAvailability.Available
            _message.value = if (wasOn) AppLockMessage.CredentialChanged else AppLockMessage.LockOn
            _setup.value = if (offer) SetupState(state.method, offerBiometrics = true) else null
        }
    }

    // --- Settings ---

    /** Called after a confirming prompt succeeded (and, for strong unlock, its key was made). */
    fun enableBiometrics(strength: BiometricStrength) {
        viewModelScope.launch {
            repository.setBiometricStrength(strength)
            repository.setBiometrics(true)
            _message.value = AppLockMessage.BiometricsOn
            if (_setup.value?.offerBiometrics == true) _setup.value = null
        }
    }

    fun disableBiometrics() {
        viewModelScope.launch {
            repository.setBiometrics(false)
            biometrics.deleteStrongKey()
        }
    }

    /** Switching to standard needs no prompt; strong goes through [enableBiometrics] after one. */
    fun useStandardBiometrics() {
        viewModelScope.launch {
            repository.setBiometricStrength(BiometricStrength.Standard)
            biometrics.deleteStrongKey()
            refreshBiometrics(BiometricStrength.Standard)
        }
    }

    fun setNoRecovery() = launchAndConfirm(null) { repository.setNoRecovery() }

    fun setDeviceCredentialRecovery() = launchAndConfirm(AppLockMessage.RecoverySaved) { repository.setDeviceCredentialRecovery() }

    fun setRecoveryQuestion(question: String, answer: String) =
        launchAndConfirm(AppLockMessage.RecoverySaved) { repository.setRecoveryQuestion(question, answer) }

    fun setAutoLock(delay: AutoLockDelay) = launchAndConfirm(null) { repository.setAutoLock(delay) }

    fun setSecureScreen(secure: Boolean) = launchAndConfirm(null) { repository.setSecureScreen(secure) }

    fun setShuffleKeypad(shuffle: Boolean) = launchAndConfirm(null) { repository.setShuffleKeypad(shuffle) }

    fun turnOff() {
        viewModelScope.launch {
            repository.disable()
            biometrics.deleteStrongKey()
            _setup.value = null
            _message.value = AppLockMessage.LockOff
        }
    }

    private fun launchAndConfirm(message: AppLockMessage?, block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            if (message != null) _message.value = message
        }
    }
}
