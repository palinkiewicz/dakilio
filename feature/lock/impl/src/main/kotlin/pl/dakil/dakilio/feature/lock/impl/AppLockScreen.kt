package pl.dakil.dakilio.feature.lock.impl

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.dakil.dakilio.core.designsystem.component.ConnectedToggleRow
import pl.dakil.dakilio.core.designsystem.component.SectionHeader
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.component.ToggleOption
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.component.segmentedItemColors
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.AutoLockDelay
import pl.dakil.dakilio.core.model.BiometricStrength
import pl.dakil.dakilio.core.model.LockMethod
import pl.dakil.dakilio.core.model.LockSettings
import pl.dakil.dakilio.core.model.RecoveryMethod
import pl.dakil.dakilio.core.security.PasscodeRules
import pl.dakil.dakilio.core.ui.component.SwitchListItem

private enum class Stage { Loading, Verify, Setup, Settings }

@Composable
internal fun AppLockScreen(viewModel: AppLockViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val verified by viewModel.verified.collectAsStateWithLifecycle()
    val setup by viewModel.setup.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val biometrics = viewModel.biometrics

    LifecycleResumeEffect(Unit) {
        viewModel.refreshBiometrics()
        onPauseOrDispose { }
    }

    val messageText = message?.let { stringResource(it.text) }
    LaunchedEffect(message) {
        if (messageText != null) {
            viewModel.consumeMessage()
            snackbar.showSnackbar(messageText)
        }
    }

    // Turning biometrics on (or switching to strong) asks for them once, so a broken sensor shows now.
    val confirmTitle = stringResource(R.string.lock_biometrics_confirm_title)
    val cancelText = stringResource(R.string.lock_cancel)
    val failedText = stringResource(R.string.lock_biometrics_failed)
    val scope = rememberCoroutineScope()
    val showFailure = {
        viewModel.refreshBiometrics()
        scope.launch { snackbar.showSnackbar(failedText) }
    }
    var cancelPrompt by remember { mutableStateOf<(() -> Unit)?>(null) }
    DisposableEffect(Unit) { onDispose { cancelPrompt?.invoke() } }
    val enableBiometrics: (BiometricStrength) -> Unit = enable@{ strength ->
        if (strength == BiometricStrength.Strong && !biometrics.prepareStrongKey()) {
            showFailure()
            return@enable
        }
        cancelPrompt = biometrics.authenticate(context, strength, confirmTitle, null, cancelText) { result ->
            cancelPrompt = null
            when (result) {
                BiometricResult.Success -> viewModel.enableBiometrics(strength)
                BiometricResult.Cancelled -> if (strength == BiometricStrength.Strong && settings?.biometricStrength != BiometricStrength.Strong) biometrics.deleteStrongKey()
                else -> showFailure()
            }
        }
    }

    val current = settings
    val stage = when {
        current == null -> Stage.Loading
        setup != null -> Stage.Setup
        current.enabled && !verified -> Stage.Verify
        else -> Stage.Settings
    }
    BackHandler(enabled = stage == Stage.Setup) { viewModel.setupBack() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_lock_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (!viewModel.setupBack()) onBack() }) {
                        DakilioIcon(DakilioSymbols.ArrowBack, contentDescription = stringResource(R.string.lock_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val motion = MaterialTheme.motionScheme
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            AnimatedContent(
                stage,
                transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                label = "appLockStage",
            ) { shown ->
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    val column = Modifier
                        .widthIn(max = Spacing.maxContentWidth)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.gutter)
                        .navigationBarsPadding()
                        .imePadding()
                    // The verify and setup steps sit in the middle of the screen, like the lock screen;
                    // at least a screen tall so they can center, and still scroll when they don't fit.
                    val centered = Modifier
                        .widthIn(max = Spacing.maxContentWidth)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight)
                        .padding(horizontal = Spacing.gutter)
                        .navigationBarsPadding()
                        .imePadding()
                    when (shown) {
                        Stage.Loading -> Unit
                        Stage.Verify -> VerifyContent(viewModel, current ?: LockSettings(), centered)
                        Stage.Setup -> setup?.let { SetupContent(viewModel, it, enableBiometrics, centered) }
                        Stage.Settings -> SettingsContent(viewModel, current ?: LockSettings(), enableBiometrics, column)
                    }
                }
            }
        }
    }
}

private val AppLockMessage.text: Int
    get() = when (this) {
        AppLockMessage.LockOn -> R.string.lock_message_on
        AppLockMessage.CredentialChanged -> R.string.lock_message_changed
        AppLockMessage.LockOff -> R.string.lock_message_off
        AppLockMessage.BiometricsOn -> R.string.lock_message_biometrics
        AppLockMessage.RecoverySaved -> R.string.lock_message_recovery
    }

/** The lock is on: the current PIN or password first, so a borrowed phone can't just switch it off. */
@Composable
private fun VerifyContent(viewModel: AppLockViewModel, settings: LockSettings, modifier: Modifier) {
    val input by viewModel.unlock.state.collectAsStateWithLifecycle()
    val lockedUntil by viewModel.lockedUntil.collectAsStateWithLifecycle()
    val availability by viewModel.availability.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val canUseBiometrics = settings.biometrics && availability == BiometricAvailability.Available
    val title = stringResource(R.string.lock_prompt_title)
    val negative = stringResource(if (settings.method == LockMethod.Pin) R.string.lock_prompt_use_pin else R.string.lock_prompt_use_password)
    var cancelPrompt by remember { mutableStateOf<(() -> Unit)?>(null) }
    DisposableEffect(Unit) { onDispose { cancelPrompt?.invoke() } }
    val prompt = {
        cancelPrompt?.invoke()
        cancelPrompt = viewModel.biometrics.authenticate(context, settings.biometricStrength, title, null, negative) { result ->
            cancelPrompt = null
            viewModel.onVerifyBiometricResult(result)
        }
    }
    var autoPrompted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(canUseBiometrics) {
        if (canUseBiometrics && !autoPrompted) {
            autoPrompted = true
            prompt()
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        StageHeader(
            symbol = DakilioSymbols.Lock,
            title = stringResource(R.string.lock_verify_title),
            body = stringResource(if (settings.method == LockMethod.Pin) R.string.lock_verify_body_pin else R.string.lock_verify_body_password),
        )
        UnlockPanel(
            settings = settings,
            input = input,
            lockedUntil = lockedUntil,
            onDigit = { viewModel.unlock.typeDigit(it, settings.pinLength) },
            onBackspace = viewModel.unlock::backspace,
            onPasswordChange = viewModel.unlock::setPassword,
            onSubmit = viewModel.unlock::submit,
            biometricKey = if (canUseBiometrics) {
                { BiometricKey(viewModel.biometrics.kind, onClick = prompt) }
            } else {
                null
            },
            modifier = Modifier.padding(bottom = Spacing.xl),
        )
    }
}

@Composable
private fun StageHeader(symbol: DakilioSymbol, title: String, body: String?, error: Boolean = false) {
    Column(
        Modifier.fillMaxWidth().padding(top = Spacing.l, bottom = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        ShapedIcon(symbol, polygon = MaterialShapes.Cookie9Sided, size = 64.dp)
        Text(title, style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center)
        AnimatedContent(body, label = "stageBody") { text ->
            Text(
                text.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
    }
}

/** Choosing and confirming a PIN or password, then the offer to add biometrics. */
@Composable
private fun SetupContent(
    viewModel: AppLockViewModel,
    setup: SetupState,
    enableBiometrics: (BiometricStrength) -> Unit,
    modifier: Modifier,
) {
    val recovered by viewModel.recovered.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    LaunchedEffect(setup.mismatch) { if (setup.mismatch > 0) haptics.reject() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (setup.offerBiometrics) {
            OfferBiometrics(viewModel, enableBiometrics)
            return@Column
        }
        val pin = setup.method == LockMethod.Pin
        val title = when {
            setup.confirming -> if (pin) R.string.lock_setup_confirm_pin else R.string.lock_setup_confirm_password
            recovered -> if (pin) R.string.lock_setup_new_pin else R.string.lock_setup_new_password
            else -> if (pin) R.string.lock_setup_choose_pin else R.string.lock_setup_choose_password
        }
        val body = when {
            setup.mismatch > 0 && !setup.confirming -> stringResource(R.string.lock_setup_mismatch)
            setup.confirming -> stringResource(R.string.lock_setup_confirm_body)
            pin -> stringResource(R.string.lock_setup_pin_rules, PasscodeRules.PIN_MIN, PasscodeRules.PIN_MAX)
            else -> stringResource(R.string.lock_setup_password_rules, PasscodeRules.PASSWORD_MIN)
        }
        StageHeader(
            symbol = if (pin) DakilioSymbols.Pin else DakilioSymbols.Password,
            title = stringResource(title),
            body = body,
            error = setup.mismatch > 0 && !setup.confirming,
        )
        if (pin) {
            Box(Modifier.shakeOn(setup.mismatch)) {
                PinDots(filled = setup.input.length, slots = setup.first?.length ?: PasscodeRules.PIN_MIN)
            }
            PinKeypad(
                onDigit = viewModel::setupDigit,
                onBackspace = viewModel::setupBackspace,
                enabled = !setup.saving,
                modifier = Modifier.padding(top = Spacing.xl),
            )
            // Confirming checks itself once the length matches; choosing needs a "Next" as the length is free.
            if (!setup.confirming) {
                Button(
                    onClick = viewModel::setupNext,
                    enabled = setup.inputValid,
                    modifier = Modifier.padding(vertical = Spacing.xl),
                ) { Text(stringResource(R.string.lock_setup_next)) }
            }
        } else {
            PasswordField(
                value = setup.input,
                onValueChange = viewModel::setupText,
                label = stringResource(if (setup.confirming) R.string.lock_setup_password_again else R.string.lock_password_label),
                onDone = viewModel::setupNext,
                readOnly = setup.saving,
                isError = setup.mismatch > 0 && !setup.confirming,
                focusKey = setup.confirming to setup.mismatch,
                modifier = Modifier.widthIn(max = 360.dp).shakeOn(setup.mismatch),
            )
            Button(
                onClick = viewModel::setupNext,
                enabled = if (setup.confirming) setup.input.isNotEmpty() && !setup.saving else setup.inputValid,
                modifier = Modifier.padding(vertical = Spacing.xl),
            ) { Text(stringResource(if (setup.confirming) R.string.lock_setup_save else R.string.lock_setup_next)) }
        }
    }
}

@Composable
private fun OfferBiometrics(viewModel: AppLockViewModel, enableBiometrics: (BiometricStrength) -> Unit) {
    val kind = viewModel.biometrics.kind
    StageHeader(symbol = kind.symbol, title = kind.unlockWithLabel(), body = stringResource(R.string.lock_offer_biometrics_body))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), modifier = Modifier.padding(bottom = Spacing.xl)) {
        TextButton(onClick = viewModel::cancelSetup) { Text(stringResource(R.string.lock_offer_not_now)) }
        Button(onClick = { enableBiometrics(BiometricStrength.Standard) }) { Text(stringResource(R.string.lock_offer_turn_on)) }
    }
}

@Composable
private fun SettingsContent(
    viewModel: AppLockViewModel,
    settings: LockSettings,
    enableBiometrics: (BiometricStrength) -> Unit,
    modifier: Modifier,
) {
    var autoLockDialog by rememberSaveable { mutableStateOf(false) }
    var questionDialog by rememberSaveable { mutableStateOf(false) }
    var confirmOff by rememberSaveable { mutableStateOf(false) }

    Column(modifier) {
        if (!settings.enabled) {
            OffHero(onPin = { viewModel.startSetup(LockMethod.Pin) }, onPassword = { viewModel.startSetup(LockMethod.Password) })
        } else {
            UnlockSection(viewModel, settings, enableBiometrics)

            SectionHeader(stringResource(R.string.lock_section_auto))
            SegmentedListItem(
                onClick = { autoLockDialog = true },
                shapes = ListItemDefaults.segmentedShapes(0, 1),
                colors = segmentedItemColors(),
                leadingContent = { ShapedIcon(DakilioSymbols.Timer, polygon = MaterialShapes.Cookie7Sided) },
                supportingContent = { Text(stringResource(settings.autoLock.label)) },
            ) { Text(stringResource(R.string.lock_auto_title)) }

            RecoverySection(viewModel, settings, onEditQuestion = { questionDialog = true })
        }

        SectionHeader(stringResource(R.string.lock_section_privacy))
        val privacyRows = if (settings.method == LockMethod.Pin) 2 else 1
        SwitchListItem(
            title = stringResource(R.string.lock_secure_screen),
            body = stringResource(R.string.lock_secure_screen_body),
            symbol = DakilioSymbols.VisibilityOff,
            polygon = MaterialShapes.Pill,
            checked = settings.secureScreen,
            onCheckedChange = viewModel::setSecureScreen,
            index = 0,
            count = privacyRows,
        )
        if (settings.method == LockMethod.Pin) {
            SwitchListItem(
                title = stringResource(R.string.lock_shuffle),
                body = stringResource(R.string.lock_shuffle_body),
                symbol = DakilioSymbols.Shuffle,
                polygon = MaterialShapes.Clover4Leaf,
                checked = settings.shuffleKeypad,
                onCheckedChange = viewModel::setShuffleKeypad,
                index = 1,
                count = privacyRows,
                modifier = Modifier.padding(top = ListItemDefaults.SegmentedGap),
            )
        }

        if (settings.enabled) {
            SectionHeader(stringResource(R.string.lock_section_off))
            SegmentedListItem(
                onClick = { confirmOff = true },
                shapes = ListItemDefaults.segmentedShapes(0, 1),
                colors = segmentedItemColors(),
                leadingContent = {
                    ShapedIcon(
                        DakilioSymbols.LockOpen,
                        polygon = MaterialShapes.Cookie9Sided,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                },
                supportingContent = { Text(stringResource(R.string.lock_turn_off_body)) },
            ) { Text(stringResource(R.string.lock_turn_off)) }
        }

        Text(
            stringResource(R.string.lock_scope_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xxl, bottom = Spacing.xl),
        )
    }

    if (autoLockDialog) {
        AutoLockDialog(
            selected = settings.autoLock,
            onSelect = {
                autoLockDialog = false
                viewModel.setAutoLock(it)
            },
            onDismiss = { autoLockDialog = false },
        )
    }
    if (questionDialog) {
        RecoveryQuestionEditor(
            initialQuestion = settings.recoveryQuestion.orEmpty(),
            onSave = { question, answer ->
                questionDialog = false
                viewModel.setRecoveryQuestion(question, answer)
            },
            onDismiss = { questionDialog = false },
        )
    }
    if (confirmOff) {
        val haptics = rememberHaptics()
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            icon = { DakilioIcon(DakilioSymbols.LockOpen, contentDescription = null) },
            title = { Text(stringResource(R.string.lock_turn_off_title)) },
            text = { Text(stringResource(R.string.lock_turn_off_dialog)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmOff = false
                    haptics.confirm()
                    viewModel.turnOff()
                }) { Text(stringResource(R.string.lock_turn_off_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmOff = false }) { Text(stringResource(R.string.lock_cancel)) } },
        )
    }
}

@Composable
private fun OffHero(onPin: () -> Unit, onPassword: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
    ) {
        Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            ShapedIcon(DakilioSymbols.Lock, polygon = MaterialShapes.Cookie9Sided, size = 56.dp)
            Text(
                stringResource(R.string.lock_off_title),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier = Modifier.padding(top = Spacing.s),
            )
            Text(
                stringResource(R.string.lock_off_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(top = Spacing.m)) {
                Button(onClick = onPin, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    DakilioIcon(DakilioSymbols.Pin, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(R.string.lock_set_pin), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                }
                FilledTonalButton(onClick = onPassword, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    DakilioIcon(DakilioSymbols.Password, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(R.string.lock_set_password), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                }
            }
        }
    }
}

@Composable
private fun UnlockSection(viewModel: AppLockViewModel, settings: LockSettings, enableBiometrics: (BiometricStrength) -> Unit) {
    val availability by viewModel.availability.collectAsStateWithLifecycle()
    val biometrics = viewModel.biometrics
    val context = LocalContext.current
    val pin = settings.method == LockMethod.Pin
    // A device without any sensor gets no biometric row at all.
    val showBiometrics = availability != BiometricAvailability.Unavailable || settings.biometrics
    val rows = if (showBiometrics) 3 else 2

    SectionHeader(stringResource(R.string.lock_section_unlock))
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        SegmentedListItem(
            onClick = { viewModel.startSetup(settings.method) },
            shapes = ListItemDefaults.segmentedShapes(0, rows),
            colors = segmentedItemColors(),
            leadingContent = { ShapedIcon(if (pin) DakilioSymbols.Pin else DakilioSymbols.Password, polygon = MaterialShapes.Cookie9Sided) },
        ) { Text(stringResource(if (pin) R.string.lock_change_pin else R.string.lock_change_password)) }
        SegmentedListItem(
            onClick = { viewModel.startSetup(if (pin) LockMethod.Password else LockMethod.Pin) },
            shapes = ListItemDefaults.segmentedShapes(1, rows),
            colors = segmentedItemColors(),
            leadingContent = { ShapedIcon(if (pin) DakilioSymbols.Password else DakilioSymbols.Dialpad, polygon = MaterialShapes.Pill) },
            supportingContent = { Text(stringResource(if (pin) R.string.lock_switch_to_password_body else R.string.lock_switch_to_pin_body)) },
        ) { Text(stringResource(if (pin) R.string.lock_switch_to_password else R.string.lock_switch_to_pin)) }
        if (showBiometrics) {
            if (availability == BiometricAvailability.NoneEnrolled && !settings.biometrics) {
                SegmentedListItem(
                    onClick = {
                        biometrics.expectReturn()
                        runCatching { context.startActivity(biometrics.enrollIntent(settings.biometricStrength)) }
                    },
                    shapes = ListItemDefaults.segmentedShapes(2, rows),
                    colors = segmentedItemColors(),
                    leadingContent = { ShapedIcon(biometrics.kind.symbol, polygon = MaterialShapes.Sunny) },
                    supportingContent = { Text(stringResource(R.string.lock_biometrics_enroll)) },
                    trailingContent = { DakilioIcon(DakilioSymbols.OpenInNew, contentDescription = null) },
                ) { Text(biometrics.kind.unlockWithLabel()) }
            } else {
                SwitchListItem(
                    title = biometrics.kind.unlockWithLabel(),
                    body = stringResource(R.string.lock_biometrics_body),
                    symbol = biometrics.kind.symbol,
                    polygon = MaterialShapes.Sunny,
                    checked = settings.biometrics,
                    onCheckedChange = { on -> if (on) enableBiometrics(settings.biometricStrength) else viewModel.disableBiometrics() },
                    index = 2,
                    count = rows,
                )
            }
        }
    }
    if (settings.biometrics) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
        ) {
            Column(Modifier.padding(Spacing.xl)) {
                Text(stringResource(R.string.lock_strength), style = MaterialTheme.typography.titleMediumEmphasized)
                Text(
                    stringResource(
                        if (settings.biometricStrength == BiometricStrength.Strong) R.string.lock_strength_strong_body else R.string.lock_strength_standard_body,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xxs, bottom = Spacing.l),
                )
                ConnectedToggleRow(
                    options = listOf(
                        ToggleOption(BiometricStrength.Standard, stringResource(R.string.lock_strength_standard), DakilioSymbols.Face),
                        ToggleOption(BiometricStrength.Strong, stringResource(R.string.lock_strength_strong), DakilioSymbols.Shield),
                    ),
                    selected = settings.biometricStrength,
                    onSelect = { strength ->
                        if (strength == settings.biometricStrength) return@ConnectedToggleRow
                        if (strength == BiometricStrength.Strong) enableBiometrics(strength) else viewModel.useStandardBiometrics()
                    },
                )
            }
        }
    }
}

@Composable
private fun RecoverySection(viewModel: AppLockViewModel, settings: LockSettings, onEditQuestion: () -> Unit) {
    val deviceSecure by viewModel.deviceSecure.collectAsStateWithLifecycle()
    val pin = settings.method == LockMethod.Pin
    SectionHeader(stringResource(if (pin) R.string.lock_section_recovery_pin else R.string.lock_section_recovery_password))
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        RecoveryOption(
            index = 0,
            selected = settings.recovery == RecoveryMethod.None,
            symbol = DakilioSymbols.Block,
            title = stringResource(R.string.lock_recovery_none),
            body = stringResource(R.string.lock_recovery_none_body),
            onClick = viewModel::setNoRecovery,
        )
        RecoveryOption(
            index = 1,
            selected = settings.recovery == RecoveryMethod.DeviceCredential,
            symbol = DakilioSymbols.PhonelinkLock,
            title = stringResource(R.string.lock_recovery_device),
            body = stringResource(if (deviceSecure) R.string.lock_recovery_device_body else R.string.lock_recovery_device_unavailable),
            enabled = deviceSecure || settings.recovery == RecoveryMethod.DeviceCredential,
            onClick = viewModel::setDeviceCredentialRecovery,
        )
        RecoveryOption(
            index = 2,
            selected = settings.recovery == RecoveryMethod.Question,
            symbol = DakilioSymbols.Help,
            title = stringResource(R.string.lock_recovery_question),
            body = settings.recoveryQuestion?.takeIf { settings.recovery == RecoveryMethod.Question }
                ?: stringResource(R.string.lock_recovery_question_body),
            onClick = onEditQuestion,
        )
    }
}

@Composable
private fun RecoveryOption(
    index: Int,
    selected: Boolean,
    symbol: DakilioSymbol,
    title: String,
    body: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val haptics = rememberHaptics()
    SegmentedListItem(
        onClick = {
            haptics.tick()
            onClick()
        },
        enabled = enabled,
        shapes = ListItemDefaults.segmentedShapes(index, 3),
        colors = segmentedItemColors(),
        leadingContent = { ShapedIcon(symbol, polygon = MaterialShapes.Cookie6Sided) },
        supportingContent = { Text(body) },
        trailingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
    ) { Text(title) }
}

private val AutoLockDelay.label: Int
    get() = when (this) {
        AutoLockDelay.Immediately -> R.string.lock_auto_immediately
        AutoLockDelay.After30s -> R.string.lock_auto_30s
        AutoLockDelay.After1m -> R.string.lock_auto_1m
        AutoLockDelay.After5m -> R.string.lock_auto_5m
        AutoLockDelay.After15m -> R.string.lock_auto_15m
    }

@Composable
private fun AutoLockDialog(selected: AutoLockDelay, onSelect: (AutoLockDelay) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DakilioIcon(DakilioSymbols.Timer, contentDescription = null) },
        title = { Text(stringResource(R.string.lock_auto_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                AutoLockDelay.entries.forEach { delay ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = delay == selected, role = Role.RadioButton) { onSelect(delay) }
                            .padding(vertical = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = delay == selected, onClick = null)
                        Text(stringResource(delay.label), modifier = Modifier.padding(start = Spacing.l))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_cancel)) } },
    )
}

@Composable
private fun RecoveryQuestionEditor(initialQuestion: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var question by rememberSaveable { mutableStateOf(initialQuestion) }
    var answer by rememberSaveable { mutableStateOf("") }
    val valid = PasscodeRules.isValidQuestion(question) && PasscodeRules.isValidAnswer(answer)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DakilioIcon(DakilioSymbols.Help, contentDescription = null) },
        title = { Text(stringResource(R.string.lock_question_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(stringResource(R.string.lock_question_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it.take(PasscodeRules.QUESTION_MAX) },
                    label = { Text(stringResource(R.string.lock_question_label)) },
                    placeholder = { Text(stringResource(R.string.lock_question_placeholder)) },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                PasswordField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = stringResource(R.string.lock_recover_answer),
                    onDone = { if (valid) onSave(question, answer) },
                    supportingText = stringResource(R.string.lock_question_answer_hint),
                    autoFocus = false,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(question, answer) }, enabled = valid) { Text(stringResource(R.string.lock_setup_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_cancel)) } },
    )
}
