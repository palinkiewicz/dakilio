package pl.dakil.dakilio.core.data.repository

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.common.ApplicationScope
import pl.dakil.dakilio.core.model.LockSettings
import pl.dakil.dakilio.core.security.AutoLockPolicy

/**
 * Whether the app is locked right now. Lives as long as the process: a cold start with the lock on
 * starts locked, and coming back from the background locks again once the auto-lock delay passed.
 *
 * The lock guards the UI only. The database isn't encrypted; Android's app sandbox protects it.
 */
@Singleton
class AppLock @Inject constructor(
    repository: LockRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    private val settings: StateFlow<LockSettings?> = repository.settings
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val _locked = MutableStateFlow<Boolean?>(null)

    /** `null` until the settings are read, so the first frame never flashes the journal. */
    val locked: StateFlow<Boolean?> = _locked.asStateFlow()

    private val _recovered = MutableStateFlow(false)

    /**
     * The app was unlocked through recovery (the PIN was forgotten). The lock settings then let the
     * user set a new credential without the old one, until the app locks again.
     */
    val recovered: StateFlow<Boolean> = _recovered.asStateFlow()

    private var backgroundedAt: Long? = null
    private var pickerLaunchedAt: Long? = null

    init {
        scope.launch {
            settings.collect { current ->
                current ?: return@collect
                when {
                    _locked.value == null -> _locked.value = current.enabled
                    // Turned off (or never on): nothing to stay locked behind.
                    !current.enabled -> {
                        _locked.value = false
                        _recovered.value = false
                    }
                }
            }
        }
    }

    fun unlock(recovered: Boolean = false) {
        _recovered.value = recovered
        _locked.value = false
    }

    fun lockNow() {
        if (settings.value?.enabled != true) return
        _recovered.value = false
        _locked.value = true
    }

    /** The app is about to open a picker or prompt of its own; leaving for it isn't leaving the app. */
    fun expectReturnFromPicker() {
        pickerLaunchedAt = SystemClock.elapsedRealtime()
    }

    fun onBackgrounded() {
        if (_locked.value == false) backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun onForegrounded() {
        val at = backgroundedAt ?: return
        backgroundedAt = null
        // Only a launch just before leaving counts; a stale one (the launch failed) doesn't.
        val fromPicker = pickerLaunchedAt?.let { at - it in 0..PICKER_LAUNCH_WINDOW } ?: false
        pickerLaunchedAt = null
        val current = settings.value ?: return
        if (AutoLockPolicy.shouldLock(current.enabled, at, SystemClock.elapsedRealtime(), current.autoLock.millis, fromPicker)) {
            lockNow()
        }
    }

    private companion object {
        const val PICKER_LAUNCH_WINDOW = 3_000L
    }
}
