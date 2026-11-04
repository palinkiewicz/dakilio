package pl.dakil.dakilio.feature.lock.impl

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.LockMethod
import pl.dakil.dakilio.core.model.LockSettings
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.RecoveryMethod
import pl.dakil.dakilio.core.ui.mood.MoodBadge

/**
 * Wraps the whole app. While [locked], [content] stays composed underneath (so navigation, screens and
 * an unsaved entry survive) but is hidden from accessibility and covered by the lock screen, which
 * takes every touch. Unlocking fades the lock screen out while its face perks up.
 */
@Composable
fun LockGate(locked: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(if (locked) Modifier.clearAndSetSemantics { } else Modifier) { content() }
        AnimatedVisibility(
            visible = locked,
            // Locking is instant: the journal must never show through a fade.
            enter = EnterTransition.None,
            exit = fadeOut(MaterialTheme.motionScheme.slowEffectsSpec()) + scaleOut(MaterialTheme.motionScheme.slowSpatialSpec(), targetScale = 1.06f),
        ) {
            val leaving = transition.targetState == EnterExitState.PostExit
            LockScreen(viewModel = hiltViewModel(), unlocking = leaving)
        }
    }
}

// The brand mark: the launcher icon's happy face.
private val markMood = Mood(id = 0, level = 5, label = "", isPrimary = true, position = 0)

@Composable
private fun LockScreen(viewModel: LockViewModel, unlocking: Boolean) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val input by viewModel.unlock.state.collectAsStateWithLifecycle()
    val lockedUntil by viewModel.lockedUntil.collectAsStateWithLifecycle()
    val biometricError by viewModel.biometricError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val current = settings ?: LockSettings()
    var forgot by rememberSaveable { mutableStateOf(false) }

    // Back leaves the app instead of reaching the screens underneath.
    BackHandler { (context as? Activity)?.moveTaskToBack(true) }
    // A field focused in the journal underneath would keep the keyboard up.
    LaunchedEffect(Unit) { if (current.method != LockMethod.Password) focus.clearFocus(force = true) }

    val biometrics = viewModel.biometrics
    val canUseBiometrics = current.biometrics && biometrics.availability(current.biometricStrength) == BiometricAvailability.Available
    var cancelPrompt by remember { mutableStateOf<(() -> Unit)?>(null) }
    val promptTitle = stringResource(R.string.lock_prompt_title)
    val promptNegative = stringResource(
        if (current.method == LockMethod.Pin) R.string.lock_prompt_use_pin else R.string.lock_prompt_use_password,
    )
    val prompt = {
        cancelPrompt?.invoke()
        cancelPrompt = biometrics.authenticate(context, current.biometricStrength, promptTitle, null, promptNegative) { result ->
            cancelPrompt = null
            viewModel.onBiometricResult(result)
        }
    }
    // Biometrics open by themselves once per lock, as soon as the app is in front.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var autoPrompted by remember { mutableStateOf(false) }
    LaunchedEffect(canUseBiometrics) {
        if (!canUseBiometrics) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!autoPrompted) {
                autoPrompted = true
                prompt()
            }
        }
    }
    DisposableEffect(Unit) { onDispose { cancelPrompt?.invoke() } }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxSize()
            // Swallows touches so nothing underneath reacts.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter, vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
        ) {
            MoodBadge(markMood, size = 72.dp, active = unlocking)
            Text(
                stringResource(if (current.method == LockMethod.Password) R.string.lock_title_password else R.string.lock_title_pin),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                textAlign = TextAlign.Center,
            )
            if (current.biometricsInvalidated) InvalidatedNote()
            UnlockPanel(
                settings = current,
                input = input,
                lockedUntil = lockedUntil,
                onDigit = { viewModel.unlock.typeDigit(it, current.pinLength) },
                onBackspace = viewModel.unlock::backspace,
                onPasswordChange = viewModel.unlock::setPassword,
                onSubmit = viewModel.unlock::submit,
                biometricKey = if (canUseBiometrics) {
                    { BiometricKey(biometrics.kind, onClick = prompt) }
                } else {
                    null
                },
                message = biometricError,
            )
            val recoverTitle = stringResource(R.string.lock_recover_device_title)
            val recoverSubtitle = stringResource(R.string.lock_recover_device_subtitle)
            TextButton(
                onClick = {
                    if (current.recovery == RecoveryMethod.DeviceCredential) {
                        cancelPrompt?.invoke()
                        cancelPrompt = biometrics.authenticateDeviceCredential(context, recoverTitle, recoverSubtitle) { result ->
                            cancelPrompt = null
                            viewModel.onDeviceCredentialResult(result)
                        }
                    } else {
                        forgot = true
                    }
                },
            ) {
                Text(stringResource(if (current.method == LockMethod.Password) R.string.lock_forgot_password else R.string.lock_forgot_pin))
            }
        }
    }

    if (forgot) {
        when (current.recovery) {
            RecoveryMethod.Question -> RecoveryQuestionDialog(
                question = current.recoveryQuestion.orEmpty(),
                viewModel = viewModel,
                lockedUntil = lockedUntil,
                onDismiss = { forgot = false },
            )
            // Screen-lock recovery opens its prompt straight from the button.
            RecoveryMethod.None, RecoveryMethod.DeviceCredential -> NoRecoveryDialog(method = current.method, onDismiss = { forgot = false })
        }
    }
}

@Composable
private fun InvalidatedNote() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
    ) {
        Text(
            stringResource(R.string.lock_biometrics_invalidated),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(Spacing.l),
        )
    }
}

@Composable
private fun RecoveryQuestionDialog(question: String, viewModel: LockViewModel, lockedUntil: Long?, onDismiss: () -> Unit) {
    val state by viewModel.answer.collectAsStateWithLifecycle()
    var answer by rememberSaveable { mutableStateOf("") }
    val remaining = rememberCountdown(lockedUntil)
    LaunchedEffect(state.wrong) { if (state.wrong > 0) answer = "" }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DakilioIcon(DakilioSymbols.Help, contentDescription = null) },
        title = { Text(stringResource(R.string.lock_recover_question_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                Text(question, style = MaterialTheme.typography.titleMedium)
                PasswordField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = stringResource(R.string.lock_recover_answer),
                    onDone = { viewModel.submitAnswer(answer) },
                    enabled = remaining == 0L,
                    readOnly = state.checking,
                    isError = state.wrong > 0,
                    focusKey = state.wrong,
                    supportingText = when {
                        remaining > 0 -> stringResource(R.string.lock_locked_out_short)
                        state.wrong > 0 -> stringResource(R.string.lock_recover_wrong)
                        else -> null
                    },
                    modifier = Modifier.shakeOn(state.wrong),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.submitAnswer(answer) }, enabled = answer.isNotBlank() && remaining == 0L && !state.checking) {
                Text(stringResource(R.string.lock_recover_unlock))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_cancel)) } },
    )
}

@Composable
private fun NoRecoveryDialog(method: LockMethod, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DakilioIcon(DakilioSymbols.LockReset, contentDescription = null) },
        title = { Text(stringResource(if (method == LockMethod.Password) R.string.lock_forgot_password else R.string.lock_forgot_pin)) },
        text = { Text(stringResource(R.string.lock_no_recovery_body)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_ok)) } },
    )
}
