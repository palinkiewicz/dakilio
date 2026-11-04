package pl.dakil.dakilio.feature.lock.impl

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.data.repository.LockRepository
import pl.dakil.dakilio.core.data.repository.VerifyResult
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.LockMethod
import pl.dakil.dakilio.core.model.LockSettings
import pl.dakil.dakilio.core.security.PasscodeRules

/**
 * What's typed into an unlock prompt and how the last try went.
 *
 * @property shake bumps on every wrong try, for the shake and the haptic.
 * @property attemptsLeft free tries left after the last wrong one; `null` before any.
 */
internal data class UnlockInput(
    val text: String = "",
    val checking: Boolean = false,
    val shake: Int = 0,
    val attemptsLeft: Int? = null,
)

/**
 * Checks a PIN or password typed on the lock screen or when changing the lock's settings. A PIN is
 * checked as soon as it has [LockSettings.pinLength] digits; a password when submitted.
 */
internal class UnlockController(
    private val repository: LockRepository,
    private val scope: CoroutineScope,
    private val onUnlocked: () -> Unit,
) {
    private val _state = MutableStateFlow(UnlockInput())
    val state: StateFlow<UnlockInput> = _state.asStateFlow()

    fun typeDigit(digit: Char, pinLength: Int) {
        val current = _state.value
        if (current.checking || current.text.length >= PasscodeRules.PIN_MAX) return
        val text = current.text + digit
        _state.value = current.copy(text = text)
        if (text.length == pinLength) submit()
    }

    fun backspace() = _state.update { if (it.checking) it else it.copy(text = it.text.dropLast(1)) }

    fun setPassword(text: String) = _state.update { if (it.checking) it else it.copy(text = text) }

    fun submit() {
        val current = _state.value
        if (current.checking || current.text.isEmpty()) return
        _state.value = current.copy(checking = true)
        scope.launch {
            when (val result = repository.verify(current.text.toCharArray())) {
                VerifyResult.Ok -> {
                    _state.value = UnlockInput()
                    onUnlocked()
                }
                is VerifyResult.Wrong -> _state.update {
                    it.copy(text = "", checking = false, shake = it.shake + 1, attemptsLeft = result.attemptsLeft)
                }
                is VerifyResult.LockedOut -> _state.update { it.copy(text = "", checking = false, shake = it.shake + 1) }
            }
        }
    }

    fun reset() {
        _state.value = UnlockInput()
    }
}

/**
 * The PIN dots and keypad, or the password field, with the line under them: a lockout countdown,
 * the free tries left, or a biometric error. [biometricKey] goes in the keypad's free corner (or
 * under the password field).
 */
@Composable
internal fun UnlockPanel(
    settings: LockSettings,
    input: UnlockInput,
    lockedUntil: Long?,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    biometricKey: (@Composable () -> Unit)? = null,
    message: String? = null,
    shuffleKey: Any? = null,
) {
    val haptics = rememberHaptics()
    LaunchedEffect(input.shake) { if (input.shake > 0) haptics.reject() }
    val remaining = rememberCountdown(lockedUntil)
    val blocked = remaining > 0
    val status = when {
        blocked -> stringResource(R.string.lock_locked_out, formatCountdown(remaining))
        input.attemptsLeft != null && input.attemptsLeft <= 3 && input.attemptsLeft > 0 ->
            pluralStringResource(R.plurals.lock_attempts_left, input.attemptsLeft, input.attemptsLeft)
        input.attemptsLeft != null -> stringResource(if (settings.method == LockMethod.Pin) R.string.lock_wrong_pin else R.string.lock_wrong_password)
        else -> message
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.shakeOn(input.shake).heightIn(min = 24.dp), contentAlignment = Alignment.Center) {
            if (settings.method == LockMethod.Pin) {
                if (input.checking) {
                    LoadingIndicator(Modifier.size(32.dp))
                } else {
                    PinDots(filled = input.text.length, slots = settings.pinLength)
                }
            } else {
                PasswordField(
                    value = input.text,
                    onValueChange = onPasswordChange,
                    label = stringResource(R.string.lock_password_label),
                    onDone = onSubmit,
                    enabled = !blocked,
                    readOnly = input.checking,
                    isError = input.attemptsLeft != null,
                    focusKey = input.shake,
                    modifier = Modifier.widthIn(max = 360.dp),
                )
            }
        }
        AnimatedContent(status, label = "unlockStatus", modifier = Modifier.padding(top = Spacing.l)) { text ->
            Text(
                text.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (blocked || input.attemptsLeft != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                minLines = 2,
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
        if (settings.method == LockMethod.Pin) {
            PinKeypad(
                onDigit = onDigit,
                onBackspace = onBackspace,
                enabled = !blocked && !input.checking,
                shuffle = settings.shuffleKeypad,
                shuffleKey = shuffleKey,
                leading = biometricKey,
                modifier = Modifier.padding(top = Spacing.m),
            )
        } else if (biometricKey != null) {
            Box(Modifier.padding(top = Spacing.m)) { biometricKey() }
        }
    }
}

/** The tonal round button that opens the biometric prompt. */
@Composable
internal fun BiometricKey(kind: BiometricKind, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(64.dp)) {
        DakilioIcon(kind.symbol, contentDescription = kind.useLabel())
    }
}

/** Milliseconds left until [until] (wall clock), ticking once a second and stopping at zero. */
@Composable
internal fun rememberCountdown(until: Long?): Long {
    var remaining by remember(until) { mutableLongStateOf(until?.let { it - System.currentTimeMillis() }?.coerceAtLeast(0) ?: 0) }
    LaunchedEffect(until) {
        until ?: return@LaunchedEffect
        while (true) {
            remaining = (until - System.currentTimeMillis()).coerceAtLeast(0)
            if (remaining == 0L) break
            delay(remaining % 1_000 + 1)
        }
    }
    return remaining
}

private fun formatCountdown(millis: Long): String {
    val seconds = (millis + 999) / 1_000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
