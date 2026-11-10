package pl.dakil.dakilio.feature.backup.impl

import pl.dakil.dakilio.core.model.AutoBackupFrequency
import pl.dakil.dakilio.core.designsystem.component.SectionHeader
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import pl.dakil.dakilio.core.backup.BackupError
import pl.dakil.dakilio.core.data.repository.BackupExportResult
import pl.dakil.dakilio.core.data.repository.BackupPreview
import pl.dakil.dakilio.core.data.repository.BackupRestoreResult
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.ui.Hint
import pl.dakil.dakilio.core.ui.component.StatTile
import pl.dakil.dakilio.core.ui.component.StepDoneStamp
import pl.dakil.dakilio.core.ui.component.StepHero
import pl.dakil.dakilio.core.ui.rememberFormats

/** The suggested name of a new backup file. */
private fun backupFileName(date: LocalDate) = "dakilio-$date.dakilio"

private fun TextFieldState.toCharArray(): CharArray = text.let { text -> CharArray(text.length) { text[it] } }

/**
 * [restoreOnly] (the first-run intro) hides creating a backup, and Back from any step leaves the
 * screen instead of returning to its start.
 */
@Composable
internal fun BackupScreen(
    viewModel: BackupViewModel,
    restoreOnly: Boolean,
    onBack: () -> Unit,
    onRestored: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val auto by viewModel.auto.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val busy = state.isBusy

    // Not saveable on purpose: passwords never go into the saved instance state.
    val password = remember { TextFieldState() }
    val repeat = remember { TextFieldState() }
    var protect by rememberSaveable { mutableStateOf(false) }
    val creator = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // The fields can only be empty here if the screen was recreated while the picker was open.
        if (protect && password.text.isEmpty()) return@rememberLauncherForActivityResult
        viewModel.export(uri, if (protect) password.toCharArray() else null)
        password.clearText()
        repeat.clearText()
        protect = false
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::open) }
    val pick = { picker.launch(arrayOf("*/*")) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(viewModel::chooseFolder) }
    var editingPassword by rememberSaveable { mutableStateOf(false) }
    var turningOff by rememberSaveable { mutableStateOf(false) }
    val autoActions = remember(viewModel) {
        AutoBackupActions(
            chooseFolder = { folderPicker.launch(null) },
            setFrequency = viewModel::setFrequency,
            setCharging = viewModel::setOnlyWhileCharging,
            setKeep = viewModel::setKeep,
            editPassword = { editingPassword = true },
            backUpNow = viewModel::backUpNow,
            turnOff = { turningOff = true },
        )
    }

    // Leaving mid-way would cancel the work; keep the user here. Elsewhere, Back returns to the start.
    BackHandler(enabled = busy) {}
    BackHandler(enabled = !busy && state != BackupUiState.Home) { if (restoreOnly) onBack() else viewModel.reset() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(if (restoreOnly) R.string.backup_restore_title else R.string.backup_title)) },
                subtitle = { Text(stringResource(R.string.backup_subtitle)) },
                navigationIcon = {
                    if (!busy) {
                        IconButton(onClick = onBack) {
                            DakilioIcon(DakilioSymbols.ArrowBack, contentDescription = stringResource(R.string.backup_back))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val enter = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val exit = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = { fadeIn(enter) togetherWith fadeOut(exit) },
                label = "backup-step",
            ) { current ->
                Column(
                    Modifier
                        .widthIn(max = Spacing.maxContentWidth)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.gutter)
                        .padding(top = Spacing.s, bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    when (current) {
                        BackupUiState.Home -> HomeStep(
                            restoreOnly = restoreOnly,
                            auto = auto,
                            protect = protect,
                            onProtectChange = { protect = it },
                            password = password,
                            repeat = repeat,
                            onCreate = { creator.launch(backupFileName(LocalDate.now())) },
                            onRestore = pick,
                            onOpenFolderBackup = viewModel::open,
                            autoActions = autoActions,
                        )
                        is BackupUiState.Exporting -> ProgressStep(
                            symbol = DakilioSymbols.Save,
                            title = stringResource(R.string.backup_exporting_title),
                            done = current.done,
                            total = current.total,
                            photosLabel = R.string.backup_exporting_photos,
                            otherLabel = stringResource(R.string.backup_exporting_writing),
                        )
                        is BackupUiState.Exported -> ExportedStep(current.result, onDone = viewModel::reset)
                        BackupUiState.Reading -> Busy(stringResource(R.string.backup_reading))
                        is BackupUiState.Password -> PasswordStep(
                            wrong = current.wrong,
                            onUnlock = viewModel::unlock,
                            onPickOther = { viewModel.reset(); pick() },
                        )
                        BackupUiState.Unlocking -> Busy(stringResource(R.string.backup_unlocking))
                        is BackupUiState.Preview -> PreviewStep(current.preview, onRestore = { confirming = true }, onPickOther = { viewModel.reset(); pick() })
                        is BackupUiState.Restoring -> ProgressStep(
                            symbol = DakilioSymbols.History,
                            title = stringResource(R.string.backup_restoring_title),
                            done = current.done,
                            total = current.total,
                            photosLabel = R.string.backup_restoring_photos,
                            otherLabel = stringResource(R.string.backup_restoring_saving),
                        )
                        is BackupUiState.Restored -> RestoredStep(current.result, onRestored)
                        is BackupUiState.Failed -> FailedStep(
                            current,
                            onRetry = {
                                viewModel.reset()
                                if (current.stage != BackupStage.Export) pick()
                            },
                        )
                    }
                }
            }
        }
    }

    val autoState = auto
    if (editingPassword && autoState != null && autoState.config.isOn) {
        AutoBackupPasswordSheet(
            hasPassword = autoState.config.hasPassword,
            onSave = {
                viewModel.setAutoPassword(it)
                editingPassword = false
            },
            onDismiss = { editingPassword = false },
        )
    }
    if (turningOff && autoState != null && autoState.config.isOn) {
        TurnOffAutoBackupDialog(
            folderName = autoState.config.folderName,
            onConfirm = {
                turningOff = false
                viewModel.turnOffAutoBackup()
            },
            onDismiss = { turningOff = false },
        )
    }

    if (confirming && state is BackupUiState.Preview) {
        val haptics = rememberHaptics()
        AlertDialog(
            onDismissRequest = { confirming = false },
            icon = { DakilioIcon(DakilioSymbols.DeleteSweep, contentDescription = null) },
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = { Text(stringResource(R.string.backup_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        haptics.confirm()
                        viewModel.restore()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.backup_confirm_yes)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.backup_confirm_no)) } },
        )
    }
}

@Composable
private fun ColumnScope.HomeStep(
    restoreOnly: Boolean,
    auto: AutoBackupUi?,
    protect: Boolean,
    onProtectChange: (Boolean) -> Unit,
    password: TextFieldState,
    repeat: TextFieldState,
    onCreate: () -> Unit,
    onRestore: () -> Unit,
    onOpenFolderBackup: (Uri) -> Unit,
    autoActions: AutoBackupActions,
) {
    val haptics = rememberHaptics()
    Text(
        stringResource(R.string.backup_home_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
    )

    if (!restoreOnly && auto != null) {
        SectionHeader(stringResource(R.string.backup_auto_title))
        if (auto.config.isOn) {
            AutoBackupOnSection(
                auto = auto,
                onChooseFolder = autoActions.chooseFolder,
                onFrequency = autoActions.setFrequency,
                onCharging = autoActions.setCharging,
                onKeep = autoActions.setKeep,
                onPassword = autoActions.editPassword,
                onBackUpNow = autoActions.backUpNow,
                onTurnOff = autoActions.turnOff,
            )
        } else {
            AutoBackupOffCard(onChooseFolder = autoActions.chooseFolder)
        }
    }

    if (!restoreOnly) SectionHeader(stringResource(R.string.backup_file_section))
    // The two cards read as one segmented block.
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
    if (!restoreOnly) Surface(shape = RoundedCornerShape(28.dp, 28.dp, 6.dp, 6.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            CardTitle(DakilioSymbols.Save, stringResource(R.string.backup_create_title))
            Text(
                stringResource(R.string.backup_create_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Hint {
                Text(
                    stringResource(R.string.backup_create_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.backup_protect), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(
                    checked = protect,
                    onCheckedChange = {
                        haptics.toggle(it)
                        onProtectChange(it)
                    },
                    thumbContent = if (protect) {
                        { DakilioIcon(DakilioSymbols.Lock, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                    } else {
                        null
                    },
                )
            }
            val expand = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
            AnimatedVisibility(visible = protect, enter = expandVertically(expand) + fadeIn(), exit = shrinkVertically(expand) + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    PasswordField(password, stringResource(R.string.backup_password))
                    val mismatch = repeat.text.isNotEmpty() && password.text.toString() != repeat.text.toString()
                    PasswordField(
                        repeat,
                        stringResource(R.string.backup_password_repeat),
                        isError = mismatch,
                        supportingText = if (mismatch) stringResource(R.string.backup_password_mismatch) else null,
                    )
                    Row(verticalAlignment = Alignment.Top) {
                        DakilioIcon(DakilioSymbols.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            stringResource(R.string.backup_password_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Spacing.s),
                        )
                    }
                }
            }
            val ready = !protect || (password.text.isNotEmpty() && password.text.toString() == repeat.text.toString())
            Button(
                onClick = {
                    haptics.confirm()
                    onCreate()
                },
                enabled = ready,
                modifier = Modifier.align(Alignment.End).padding(top = Spacing.xs),
            ) {
                DakilioIcon(DakilioSymbols.Save, contentDescription = null, modifier = Modifier.padding(end = Spacing.s))
                Text(stringResource(R.string.backup_create_button))
            }
        }
    }
    Surface(
        shape = if (restoreOnly) RoundedCornerShape(28.dp) else RoundedCornerShape(6.dp, 6.dp, 28.dp, 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            CardTitle(DakilioSymbols.History, stringResource(R.string.backup_restore_title))
            Text(
                stringResource(R.string.backup_restore_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val recent = auto?.folderBackups.orEmpty().take(RECENT_FOLDER_BACKUPS)
            if (recent.isNotEmpty()) {
                Text(
                    stringResource(R.string.backup_folder_backups),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.s),
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    recent.forEach { backup -> FolderBackupRow(backup.time, backup.bytes, onClick = { onOpenFolderBackup(backup.uri) }) }
                }
            }
            OutlinedButton(onClick = onRestore, modifier = Modifier.align(Alignment.End).padding(top = Spacing.xs)) {
                DakilioIcon(DakilioSymbols.FolderOpen, contentDescription = null, modifier = Modifier.padding(end = Spacing.s))
                Text(stringResource(if (recent.isEmpty()) R.string.backup_restore_button else R.string.backup_restore_other))
            }
        }
    }
    }

    Row(Modifier.fillMaxWidth().padding(top = Spacing.l), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        DakilioIcon(DakilioSymbols.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.backup_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.s),
        )
    }
}

/** What the automatic backup section can do, bundled so [HomeStep] stays readable. */
internal class AutoBackupActions(
    val chooseFolder: () -> Unit,
    val setFrequency: (AutoBackupFrequency) -> Unit,
    val setCharging: (Boolean) -> Unit,
    val setKeep: (Int) -> Unit,
    val editPassword: () -> Unit,
    val backUpNow: () -> Unit,
    val turnOff: () -> Unit,
)

private const val RECENT_FOLDER_BACKUPS = 3

@Composable
internal fun CardTitle(symbol: DakilioSymbol, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DakilioIcon(symbol, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(start = Spacing.m))
    }
}

@Composable
internal fun PasswordField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    onDone: (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedSecureTextField(
        state = state,
        label = { Text(label) },
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        textObfuscationMode = if (visible) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
        onKeyboardAction = onDone?.let { done -> { done() } },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                DakilioIcon(
                    if (visible) DakilioSymbols.VisibilityOff else DakilioSymbols.Visibility,
                    contentDescription = stringResource(if (visible) R.string.backup_password_hide else R.string.backup_password_show),
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColumnScope.PasswordStep(wrong: Boolean, onUnlock: (CharArray) -> Unit, onPickOther: () -> Unit) {
    val password = remember { TextFieldState() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val unlock = {
        if (password.text.isNotEmpty()) {
            onUnlock(password.toCharArray())
            password.clearText()
        }
    }
    StepHero(DakilioSymbols.Lock, MaterialShapes.Gem, error = wrong)
    Text(
        stringResource(R.string.backup_locked_title),
        style = MaterialTheme.typography.headlineSmallEmphasized,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        stringResource(R.string.backup_locked_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    PasswordField(
        password,
        stringResource(R.string.backup_password),
        modifier = Modifier.padding(top = Spacing.s).focusRequester(focus),
        isError = wrong,
        supportingText = if (wrong) stringResource(R.string.backup_password_wrong) else null,
        onDone = unlock,
    )
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onPickOther) { Text(stringResource(R.string.backup_other_file)) }
        Button(onClick = unlock, enabled = password.text.isNotEmpty(), modifier = Modifier) {
            Text(stringResource(R.string.backup_unlock))
        }
    }
}

@Composable
private fun ColumnScope.PreviewStep(preview: BackupPreview, onRestore: () -> Unit, onPickOther: () -> Unit) {
    val stats = preview.stats
    val formats = rememberFormats()
    Text(stringResource(R.string.backup_preview_title), style = MaterialTheme.typography.headlineSmallEmphasized)
    preview.exportedAt?.let { millis ->
        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        Text(
            stringResource(R.string.backup_preview_made, formats.dayMonthYear(date)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val first = stats.firstDate
    val last = stats.lastDate
    if (first != null && last != null) {
        Text(
            stringResource(R.string.backup_preview_range, formats.dayMonthYear(first), formats.dayMonthYear(last)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // Two rows of tiles that read as one segmented block.
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            StatTile(stringResource(R.string.backup_stat_entries), stats.entries, RoundedCornerShape(28.dp, 6.dp, 6.dp, 6.dp), Modifier.weight(1f))
            StatTile(stringResource(R.string.backup_stat_photos), stats.photos - preview.missingPhotos, RoundedCornerShape(6.dp, 28.dp, 6.dp, 6.dp), Modifier.weight(1f))
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            StatTile(stringResource(R.string.backup_stat_tags), stats.tags, RoundedCornerShape(6.dp, 6.dp, 6.dp, 28.dp), Modifier.weight(1f))
            StatTile(stringResource(R.string.backup_stat_diaries), stats.diaries, RoundedCornerShape(6.dp), Modifier.weight(1f))
            StatTile(stringResource(R.string.backup_stat_habits), stats.habits, RoundedCornerShape(6.dp, 6.dp, 28.dp, 6.dp), Modifier.weight(1f))
        }
    }

    val skipped = buildList {
        if (preview.missingPhotos > 0) add(pluralStringResource(R.plurals.backup_skipped_photos, preview.missingPhotos, preview.missingPhotos))
        if (stats.entriesSkipped > 0) add(pluralStringResource(R.plurals.backup_skipped_entries, stats.entriesSkipped, stats.entriesSkipped))
        if (stats.missingReferences > 0) add(pluralStringResource(R.plurals.backup_skipped_references, stats.missingReferences, stats.missingReferences))
    }
    if (skipped.isNotEmpty()) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.backup_skipped_title), style = MaterialTheme.typography.titleMediumEmphasized)
                skipped.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }

    val current = preview.current
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.xl)) {
            DakilioIcon(DakilioSymbols.DeleteSweep, contentDescription = null, filled = true)
            Column(Modifier.padding(start = Spacing.l)) {
                Text(stringResource(R.string.backup_warning_title), style = MaterialTheme.typography.titleMediumEmphasized)
                Text(
                    stringResource(
                        R.string.backup_warning_body,
                        pluralStringResource(R.plurals.backup_count_entries, current.entries, current.entries),
                        pluralStringResource(R.plurals.backup_count_photos, current.photos, current.photos),
                        pluralStringResource(R.plurals.backup_count_diaries, current.diaries, current.diaries),
                        pluralStringResource(R.plurals.backup_count_tags, current.tags, current.tags),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onPickOther) { Text(stringResource(R.string.backup_other_file)) }
        Button(
            onClick = onRestore,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
        ) { Text(stringResource(R.string.backup_restore_start)) }
    }
}

@Composable
private fun ColumnScope.ProgressStep(symbol: DakilioSymbol, title: String, done: Int, total: Int, photosLabel: Int, otherLabel: String) {
    StepHero(symbol, MaterialShapes.Cookie12Sided)
    Text(title, style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    val copyingPhotos = total > 0 && done < total
    if (copyingPhotos) {
        LinearWavyProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth().padding(top = Spacing.l))
    } else {
        // The key derivation and the database swap have no measurable progress; this goes once they're done.
        LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(top = Spacing.l))
    }
    Text(
        if (copyingPhotos) stringResource(photosLabel, done, total) else otherLabel,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        stringResource(R.string.backup_keep_open),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColumnScope.ExportedStep(result: BackupExportResult, onDone: () -> Unit) {
    StepDoneStamp()
    Text(
        stringResource(R.string.backup_exported_title),
        style = MaterialTheme.typography.headlineMediumEmphasized,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    val summary = buildString {
        append(pluralStringResource(R.plurals.backup_done_entries, result.entries, result.entries))
        if (result.photos > 0) append(' ').append(pluralStringResource(R.plurals.backup_done_photos, result.photos, result.photos))
    }
    Text(summary, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    if (result.missingPhotos > 0) {
        Text(
            pluralStringResource(R.plurals.backup_exported_missing, result.missingPhotos, result.missingPhotos),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Button(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.l)) {
        Text(stringResource(R.string.backup_exported_button))
    }
}

@Composable
private fun ColumnScope.RestoredStep(result: BackupRestoreResult, onDone: () -> Unit) {
    StepDoneStamp()
    Text(
        stringResource(R.string.backup_restored_title),
        style = MaterialTheme.typography.headlineMediumEmphasized,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    val summary = buildString {
        append(pluralStringResource(R.plurals.backup_done_entries, result.entries, result.entries))
        if (result.photos > 0) append(' ').append(pluralStringResource(R.plurals.backup_done_photos, result.photos, result.photos))
    }
    Text(summary, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    if (result.photosFailed > 0) {
        Text(
            pluralStringResource(R.plurals.backup_restored_photos_failed, result.photosFailed, result.photosFailed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Button(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.l)) {
        Text(stringResource(R.string.backup_restored_button))
    }
}

@Composable
private fun ColumnScope.FailedStep(state: BackupUiState.Failed, onRetry: () -> Unit) {
    StepHero(DakilioSymbols.Block, MaterialShapes.Cookie6Sided, error = true)
    Text(
        stringResource(
            when (state.stage) {
                BackupStage.Export -> R.string.backup_error_export_title
                BackupStage.Open -> R.string.backup_error_open_title
                BackupStage.Restore -> R.string.backup_error_restore_title
            },
        ),
        style = MaterialTheme.typography.headlineSmallEmphasized,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    val message = when (state.reason) {
        BackupError.NotABackup -> R.string.backup_error_not_backup
        BackupError.WrongPassword, BackupError.Corrupted -> R.string.backup_error_corrupted
        BackupError.TooLarge -> R.string.backup_error_too_large
        BackupError.NewerVersion -> R.string.backup_error_newer
        BackupError.Malformed -> R.string.backup_error_malformed
        null -> when (state.stage) {
            BackupStage.Export -> R.string.backup_error_export_failed
            BackupStage.Open -> R.string.backup_error_unknown
            BackupStage.Restore -> R.string.backup_error_restore_failed
        }
    }
    Text(
        stringResource(message),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = onRetry, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.s)) {
        Text(stringResource(if (state.stage == BackupStage.Export) R.string.backup_try_again else R.string.backup_other_file))
    }
}

@Composable
private fun ColumnScope.Busy(label: String) {
    LoadingIndicator(Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.xxl))
    Text(label, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}
