package pl.dakil.dakilio.feature.importer.impl

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.dakil.dakilio.core.data.repository.DaylioImportResult
import pl.dakil.dakilio.core.data.repository.DaylioPreview
import pl.dakil.dakilio.core.daylio.DaylioError
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.ui.component.DaylioBackupCard
import pl.dakil.dakilio.core.ui.openDaylioBackup
import pl.dakil.dakilio.core.ui.rememberDaylioInstalled
import pl.dakil.dakilio.core.ui.rememberFormats

@Composable
internal fun DaylioImportScreen(viewModel: DaylioImportViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::open) }
    val pick = { picker.launch(arrayOf("*/*")) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val importing = state is ImportUiState.Importing

    val context = LocalContext.current
    var daylioInstalled by rememberDaylioInstalled()
    val openHaptics = rememberHaptics()
    val openDaylio = {
        openHaptics.confirm()
        if (!openDaylioBackup(context)) daylioInstalled = false
    }

    // Leaving mid-import would cancel it; the swap itself is atomic, but keep the user here.
    BackHandler(enabled = importing) {}

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                subtitle = { Text(stringResource(R.string.import_subtitle)) },
                navigationIcon = {
                    if (!importing) {
                        IconButton(onClick = onBack) {
                            DakilioIcon(DakilioSymbols.ArrowBack, contentDescription = stringResource(R.string.import_back))
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
                transitionSpec = {
                    fadeIn(enter) togetherWith fadeOut(exit)
                },
                label = "import-step",
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
                        ImportUiState.Pick -> PickStep(onPick = pick, daylioInstalled = daylioInstalled, onOpenDaylio = openDaylio)
                        ImportUiState.Reading -> Busy(stringResource(R.string.import_reading))
                        is ImportUiState.Preview -> PreviewStep(current.preview, onImport = { confirming = true }, onPickOther = viewModel::reset)
                        is ImportUiState.Importing -> ImportingStep(current.done, current.total)
                        is ImportUiState.Done -> DoneStep(current.result, onDone)
                        is ImportUiState.Failed -> FailedStep(current, onPickOther = { viewModel.reset(); pick() })
                    }
                }
            }
        }
    }

    if (confirming && state is ImportUiState.Preview) {
        val haptics = rememberHaptics()
        AlertDialog(
            onDismissRequest = { confirming = false },
            icon = { DakilioIcon(DakilioSymbols.DeleteSweep, contentDescription = null) },
            title = { Text(stringResource(R.string.import_confirm_title)) },
            text = { Text(stringResource(R.string.import_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        haptics.confirm()
                        viewModel.import()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.import_confirm_yes)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.import_confirm_no)) } },
        )
    }
}

@Composable
private fun ColumnScope.PickStep(onPick: () -> Unit, daylioInstalled: Boolean, onOpenDaylio: () -> Unit) {
    Hero(DakilioSymbols.DriveFileMove, MaterialShapes.Cookie9Sided)
    Text(
        stringResource(R.string.import_pick_title),
        style = MaterialTheme.typography.headlineSmallEmphasized,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        stringResource(R.string.import_pick_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = onPick, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.s)) {
        DakilioIcon(DakilioSymbols.FolderOpen, contentDescription = null, modifier = Modifier.padding(end = Spacing.s))
        Text(stringResource(R.string.import_pick_button))
    }
    if (daylioInstalled) {
        DaylioBackupCard(
            title = stringResource(R.string.import_daylio_title),
            body = stringResource(R.string.import_daylio_body),
            onOpenDaylio = onOpenDaylio,
            modifier = Modifier.padding(top = Spacing.l),
        )
    }
    Row(Modifier.fillMaxWidth().padding(top = Spacing.l), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        DakilioIcon(DakilioSymbols.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.import_pick_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.s),
        )
    }
}

@Composable
private fun ColumnScope.PreviewStep(preview: DaylioPreview, onImport: () -> Unit, onPickOther: () -> Unit) {
    val stats = preview.stats
    val formats = rememberFormats()
    Text(stringResource(R.string.import_preview_title), style = MaterialTheme.typography.headlineSmallEmphasized)
    val first = stats.firstDate
    val last = stats.lastDate
    if (first != null && last != null) {
        Text(
            stringResource(R.string.import_preview_range, formats.dayMonthYear(first), formats.dayMonthYear(last)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // Two rows of tiles that read as one segmented block.
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            StatTile(stringResource(R.string.import_stat_entries), stats.entries, RoundedCornerShape(28.dp, 6.dp, 6.dp, 6.dp), Modifier.weight(1f))
            StatTile(stringResource(R.string.import_stat_photos), stats.photos - preview.missingPhotos, RoundedCornerShape(6.dp, 28.dp, 6.dp, 6.dp), Modifier.weight(1f))
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            StatTile(stringResource(R.string.import_stat_tags), stats.tags, RoundedCornerShape(6.dp, 6.dp, 6.dp, 28.dp), Modifier.weight(1f))
            StatTile(stringResource(R.string.import_stat_groups), stats.groups, RoundedCornerShape(6.dp), Modifier.weight(1f))
            StatTile(stringResource(R.string.import_stat_moods), stats.aliases, RoundedCornerShape(6.dp, 6.dp, 28.dp, 6.dp), Modifier.weight(1f))
        }
    }

    val skipped = buildList {
        if (stats.voiceNotesSkipped > 0) add(pluralStringResource(R.plurals.import_skipped_voice, stats.voiceNotesSkipped, stats.voiceNotesSkipped))
        if (stats.favoritesSkipped > 0) add(pluralStringResource(R.plurals.import_skipped_favorites, stats.favoritesSkipped, stats.favoritesSkipped))
        if (preview.missingPhotos > 0) add(pluralStringResource(R.plurals.import_skipped_photos, preview.missingPhotos, preview.missingPhotos))
        if (stats.entriesSkipped > 0) add(pluralStringResource(R.plurals.import_skipped_entries, stats.entriesSkipped, stats.entriesSkipped))
        // Unknown moods are already counted as skipped entries.
        val references = stats.missingReferences - stats.entriesSkipped
        if (references > 0) add(pluralStringResource(R.plurals.import_skipped_references, references, references))
    }
    if (skipped.isNotEmpty() || preview.untestedVersion) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (skipped.isNotEmpty()) {
                    Text(stringResource(R.string.import_skipped_title), style = MaterialTheme.typography.titleMediumEmphasized)
                    skipped.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (preview.untestedVersion) {
                    Text(
                        stringResource(R.string.import_untested_version),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = if (skipped.isNotEmpty()) Spacing.s else 0.dp),
                    )
                }
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
                Text(stringResource(R.string.import_warning_title), style = MaterialTheme.typography.titleMediumEmphasized)
                Text(
                    stringResource(
                        R.string.import_warning_body,
                        pluralStringResource(R.plurals.import_count_entries, current.entries, current.entries),
                        pluralStringResource(R.plurals.import_count_photos, current.photos, current.photos),
                        pluralStringResource(R.plurals.import_count_diaries, current.diaries, current.diaries),
                        pluralStringResource(R.plurals.import_count_tags, current.tags, current.tags),
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
        OutlinedButton(onClick = onPickOther) { Text(stringResource(R.string.import_other_file)) }
        Button(
            onClick = onImport,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
        ) { Text(stringResource(R.string.import_start)) }
    }
}

@Composable
private fun ColumnScope.ImportingStep(done: Int, total: Int) {
    Hero(DakilioSymbols.Route, MaterialShapes.Cookie12Sided)
    Text(
        stringResource(R.string.import_progress_title),
        style = MaterialTheme.typography.headlineSmallEmphasized,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    val copyingPhotos = total > 0 && done < total
    if (copyingPhotos) {
        LinearWavyProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth().padding(top = Spacing.l))
    } else {
        // The database swap has no measurable progress; this disappears as soon as it's done.
        LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(top = Spacing.l))
    }
    Text(
        if (copyingPhotos) stringResource(R.string.import_progress_photos, done, total) else stringResource(R.string.import_progress_saving),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        stringResource(R.string.import_progress_keep_open),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColumnScope.DoneStep(result: DaylioImportResult, onDone: () -> Unit) {
    // A one-shot "passport stamp": the badge lands with a springy scale and a slight twist.
    val haptics = rememberHaptics()
    val stamp = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        haptics.confirm()
        stamp.animateTo(1f, spec)
    }
    Box(
        Modifier.fillMaxWidth().padding(top = Spacing.xxl, bottom = Spacing.l).graphicsLayer {
            val t = stamp.value
            scaleX = 1.6f - 0.6f * t
            scaleY = 1.6f - 0.6f * t
            rotationZ = -18f * (1f - t)
            alpha = t.coerceIn(0f, 1f)
        },
        contentAlignment = Alignment.Center,
    ) {
        ShapedIcon(
            symbol = DakilioSymbols.CheckCircle,
            polygon = MaterialShapes.SoftBurst,
            size = 120.dp,
            iconSize = 56.dp,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
    Text(
        stringResource(R.string.import_done_title),
        style = MaterialTheme.typography.headlineMediumEmphasized,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    val summary = buildString {
        append(pluralStringResource(R.plurals.import_done_entries, result.entries, result.entries))
        if (result.photos > 0) append(' ').append(pluralStringResource(R.plurals.import_done_photos, result.photos, result.photos))
    }
    Text(summary, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    if (result.photosFailed > 0) {
        Text(
            pluralStringResource(R.plurals.import_done_photos_failed, result.photosFailed, result.photosFailed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Button(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.l)) {
        Text(stringResource(R.string.import_done_button))
    }
}

@Composable
private fun ColumnScope.FailedStep(state: ImportUiState.Failed, onPickOther: () -> Unit) {
    Hero(DakilioSymbols.Block, MaterialShapes.Cookie6Sided, error = true)
    Text(
        stringResource(R.string.import_error_title),
        style = MaterialTheme.typography.headlineSmallEmphasized,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    val message = when (state.reason) {
        DaylioError.NotAZip -> R.string.import_error_not_zip
        DaylioError.MissingBackup -> R.string.import_error_missing_backup
        DaylioError.TooLarge -> R.string.import_error_too_large
        DaylioError.Malformed -> R.string.import_error_malformed
        DaylioError.UnsupportedVersion -> R.string.import_error_version
        null -> if (state.duringImport) R.string.import_error_import_failed else R.string.import_error_unknown
    }
    Text(
        stringResource(message),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = onPickOther, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.s)) {
        Text(stringResource(R.string.import_other_file))
    }
}

@Composable
private fun ColumnScope.Busy(label: String) {
    LoadingIndicator(Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.xxl))
    Text(label, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Hero(symbol: DakilioSymbol, polygon: RoundedPolygon, error: Boolean = false) {
    Box(Modifier.fillMaxWidth().padding(top = Spacing.xl, bottom = Spacing.s), contentAlignment = Alignment.Center) {
        ShapedIcon(
            symbol = symbol,
            polygon = polygon,
            size = 96.dp,
            iconSize = 48.dp,
            containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun StatTile(label: String, value: Int, shape: RoundedCornerShape, modifier: Modifier) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxHeight()) {
        Column(Modifier.padding(Spacing.l)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineMediumEmphasized)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}
