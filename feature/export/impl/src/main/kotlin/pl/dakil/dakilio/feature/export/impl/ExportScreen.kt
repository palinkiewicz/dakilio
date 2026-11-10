package pl.dakil.dakilio.feature.export.impl

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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import pl.dakil.dakilio.core.designsystem.component.ConnectedToggleRow
import pl.dakil.dakilio.core.designsystem.component.SectionHeader
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.component.ToggleOption
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.component.segmentedItemColors
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.ui.component.StepDoneStamp
import pl.dakil.dakilio.core.ui.component.StepHero
import pl.dakil.dakilio.core.ui.component.SwitchListItem
import pl.dakil.dakilio.core.ui.rememberFormats
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey

private enum class PeriodChoice { All, ThisYear, Last12Months, Custom }

private val ExportPeriod.choice: PeriodChoice
    get() = when (this) {
        ExportPeriod.All -> PeriodChoice.All
        ExportPeriod.ThisYear -> PeriodChoice.ThisYear
        ExportPeriod.Last12Months -> PeriodChoice.Last12Months
        is ExportPeriod.Custom -> PeriodChoice.Custom
    }

@Composable
internal fun ExportScreen(viewModel: ExportViewModel, onBack: () -> Unit) {
    val options by viewModel.options.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val diaries by viewModel.diaries.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val formats = rememberFormats()
    val haptics = rememberHaptics()
    val busy = state is ExportState.Exporting

    val count = selection?.entries?.size ?: 0
    val title = stringResource(R.string.export_doc_title)
    val range = selection?.let { s ->
        if (s.first != null && s.last != null) {
            stringResource(R.string.export_doc_range, formats.dayMonthYear(s.first), formats.dayMonthYear(s.last))
        } else {
            null
        }
    }
    val entriesText = pluralStringResource(R.plurals.export_entries, count, count)
    val photoLabel = stringResource(R.string.export_doc_photo)
    val footer = stringResource(R.string.export_doc_footer)
    val labels = ExportLabels(
        title = title,
        subtitle = listOfNotNull(range, entriesText).joinToString(" · "),
        // Always with the year: a journal spans years.
        day = { formats.fullDate(it, today = LocalDate.MIN) },
        time = { formats.time(it) },
        diaryName = { id -> diaries.firstOrNull { it.id == id }?.name },
        photo = photoLabel,
        footer = footer,
    )

    val onPicked: (android.net.Uri?) -> Unit = { uri -> uri?.let { viewModel.export(it, labels) } }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf"), onPicked)
    val markdownLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown"), onPicked)
    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip"), onPicked)
    val startExport = {
        val base = "dakilio-journal-${viewModel.today()}"
        when {
            options.format == ExportFormat.Pdf -> pdfLauncher.launch("$base.pdf")
            viewModel.markdownIsZip() -> zipLauncher.launch("$base.zip")
            else -> markdownLauncher.launch("$base.md")
        }
    }
    var pickRange by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = busy) { viewModel.cancel() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.export_title)) },
                subtitle = { Text(stringResource(R.string.export_subtitle)) },
                navigationIcon = {
                    if (!busy) {
                        IconButton(onClick = onBack) {
                            DakilioIcon(DakilioSymbols.ArrowBack, contentDescription = stringResource(R.string.export_back))
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
                label = "export-step",
            ) { current ->
                Column(
                    Modifier
                        .widthIn(max = Spacing.maxContentWidth)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.gutter)
                        .padding(top = Spacing.s, bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    when (current) {
                        ExportState.Idle -> OptionsStep(
                            options = options,
                            selection = selection,
                            diaries = diaries.map { DiaryChip(it.id, it.name, it.iconKey) },
                            onFormat = viewModel::setFormat,
                            onPeriod = { choice ->
                                when (choice) {
                                    PeriodChoice.All -> viewModel.setPeriod(ExportPeriod.All)
                                    PeriodChoice.ThisYear -> viewModel.setPeriod(ExportPeriod.ThisYear)
                                    PeriodChoice.Last12Months -> viewModel.setPeriod(ExportPeriod.Last12Months)
                                    PeriodChoice.Custom -> pickRange = true
                                }
                            },
                            onEditRange = { pickRange = true },
                            onToggleDiary = viewModel::toggleDiary,
                            onAllDiaries = viewModel::allDiaries,
                            onPhotos = viewModel::setPhotos,
                            onExport = {
                                haptics.confirm()
                                startExport()
                            },
                        )
                        is ExportState.Exporting -> ExportingStep(current, onCancel = viewModel::cancel)
                        is ExportState.Done -> DoneStep(current, onDone = viewModel::reset)
                        ExportState.Failed -> FailedStep(onRetry = viewModel::reset)
                    }
                }
            }
        }
    }

    if (pickRange) {
        val today = viewModel.today()
        val todayUtc = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val custom = options.period as? ExportPeriod.Custom
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = custom?.from?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            initialSelectedEndDateMillis = custom?.to?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
                override fun isSelectableYear(year: Int) = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickRange = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null,
                    onClick = {
                        val from = pickerState.selectedStartDateMillis?.toUtcDate()
                        if (from != null) {
                            haptics.confirm()
                            viewModel.setPeriod(ExportPeriod.Custom(from, pickerState.selectedEndDateMillis?.toUtcDate() ?: from))
                        }
                        pickRange = false
                    },
                ) { Text(stringResource(R.string.export_period_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickRange = false }) { Text(stringResource(R.string.export_period_cancel)) } },
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.weight(1f))
        }
    }
}

private fun Long.toUtcDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private data class DiaryChip(val id: Long, val name: String, val iconKey: String)

@Composable
private fun ColumnScope.OptionsStep(
    options: ExportOptions,
    selection: ExportSelection?,
    diaries: List<DiaryChip>,
    onFormat: (ExportFormat) -> Unit,
    onPeriod: (PeriodChoice) -> Unit,
    onEditRange: () -> Unit,
    onToggleDiary: (Long) -> Unit,
    onAllDiaries: () -> Unit,
    onPhotos: (Boolean) -> Unit,
    onExport: () -> Unit,
) {
    val formats = rememberFormats()
    val haptics = rememberHaptics()

    SectionHeader(stringResource(R.string.export_format))
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            ConnectedToggleRow(
                options = listOf(
                    ToggleOption(ExportFormat.Pdf, stringResource(R.string.export_format_pdf), DakilioSymbols.PictureAsPdf),
                    ToggleOption(ExportFormat.Markdown, stringResource(R.string.export_format_markdown), DakilioSymbols.Description),
                ),
                selected = options.format,
                onSelect = onFormat,
            )
            Text(
                stringResource(if (options.format == ExportFormat.Pdf) R.string.export_format_pdf_body else R.string.export_format_markdown_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    SectionHeader(stringResource(R.string.export_period))
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            ConnectedToggleRow(
                options = listOf(
                    ToggleOption(PeriodChoice.All, stringResource(R.string.export_period_all)),
                    ToggleOption(PeriodChoice.ThisYear, stringResource(R.string.export_period_year)),
                    ToggleOption(PeriodChoice.Last12Months, stringResource(R.string.export_period_12_months)),
                    ToggleOption(PeriodChoice.Custom, stringResource(R.string.export_period_custom)),
                ),
                selected = options.period.choice,
                onSelect = onPeriod,
            )
            val custom = options.period as? ExportPeriod.Custom
            val expand = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
            AnimatedVisibility(custom != null, enter = expandVertically(expand) + fadeIn(), exit = shrinkVertically(expand) + fadeOut()) {
                if (custom != null) {
                    OutlinedButton(onClick = onEditRange) {
                        DakilioIcon(DakilioSymbols.CalendarToday, contentDescription = null, modifier = Modifier.padding(end = Spacing.s))
                        Text(stringResource(R.string.export_doc_range, formats.dayMonthYear(minOf(custom.from, custom.to)), formats.dayMonthYear(maxOf(custom.from, custom.to))))
                    }
                }
            }
            if (diaries.size > 1) {
                Text(stringResource(R.string.export_diaries), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.xs))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    FilterChip(
                        selected = options.diaryIds.isEmpty(),
                        onClick = {
                            haptics.toggle(true)
                            onAllDiaries()
                        },
                        label = { Text(stringResource(R.string.export_diaries_all)) },
                    )
                    diaries.forEach { diary ->
                        val selected = diary.id in options.diaryIds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                haptics.toggle(!selected)
                                onToggleDiary(diary.id)
                            },
                            leadingIcon = { DakilioIcon(symbolForKey(diary.iconKey), contentDescription = null, filled = selected, modifier = Modifier.size(18.dp)) },
                            label = { Text(diary.name) },
                        )
                    }
                }
            }
        }
    }

    SectionHeader(stringResource(R.string.export_content))
    SwitchListItem(
        title = stringResource(R.string.export_photos),
        body = stringResource(if (options.format == ExportFormat.Pdf) R.string.export_photos_pdf_body else R.string.export_photos_markdown_body),
        symbol = DakilioSymbols.PhotoLibrary,
        polygon = MaterialShapes.Cookie7Sided,
        checked = options.photos,
        onCheckedChange = onPhotos,
    )

    // What will be exported, then the button.
    val count = selection?.entries?.size ?: 0
    val photos = if (options.photos) selection?.photos ?: 0 else 0
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(0, 1),
        colors = segmentedItemColors(),
        modifier = Modifier.padding(top = Spacing.l),
        leadingContent = { ShapedIcon(DakilioSymbols.Article, polygon = MaterialShapes.Cookie9Sided) },
        supportingContent = { Text(stringResource(R.string.export_not_encrypted)) },
    ) {
        Text(
            if (photos > 0) {
                stringResource(R.string.export_summary, pluralStringResource(R.plurals.export_entries, count, count), pluralStringResource(R.plurals.export_photos, photos, photos))
            } else {
                pluralStringResource(R.plurals.export_entries, count, count)
            },
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
    }
    Button(
        onClick = onExport,
        enabled = count > 0,
        modifier = Modifier.align(Alignment.End).padding(top = Spacing.s),
    ) {
        DakilioIcon(
            if (options.format == ExportFormat.Pdf) DakilioSymbols.PictureAsPdf else DakilioSymbols.Description,
            contentDescription = null,
            modifier = Modifier.padding(end = Spacing.s),
        )
        Text(stringResource(if (options.format == ExportFormat.Pdf) R.string.export_button_pdf else R.string.export_button_markdown))
    }
}

@Composable
private fun ColumnScope.ExportingStep(state: ExportState.Exporting, onCancel: () -> Unit) {
    StepHero(DakilioSymbols.Description, MaterialShapes.Cookie12Sided)
    Text(
        stringResource(R.string.export_exporting),
        style = MaterialTheme.typography.headlineSmallEmphasized,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.total > 0) {
        LinearProgressIndicator(progress = { state.done.toFloat() / state.total }, modifier = Modifier.fillMaxWidth().padding(top = Spacing.m))
        Text(
            stringResource(R.string.export_exporting_photos, state.done, state.total),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Spacing.m))
    }
    TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.m)) {
        Text(stringResource(R.string.export_cancel))
    }
}

@Composable
private fun ColumnScope.DoneStep(state: ExportState.Done, onDone: () -> Unit) {
    StepDoneStamp()
    Text(
        stringResource(R.string.export_done_title),
        style = MaterialTheme.typography.headlineSmallEmphasized,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    val entries = pluralStringResource(R.plurals.export_entries, state.result.entries, state.result.entries)
    Text(
        if (state.result.photos > 0) {
            stringResource(R.string.export_summary, entries, pluralStringResource(R.plurals.export_photos, state.result.photos, state.result.photos))
        } else {
            entries
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.l)) {
        Text(stringResource(R.string.export_done_button))
    }
}

@Composable
private fun ColumnScope.FailedStep(onRetry: () -> Unit) {
    StepHero(DakilioSymbols.Block, MaterialShapes.Cookie6Sided, error = true)
    Text(
        stringResource(R.string.export_failed_title),
        style = MaterialTheme.typography.headlineSmallEmphasized,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        stringResource(R.string.export_failed_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth().padding(top = Spacing.l), horizontalArrangement = Arrangement.Center) {
        Button(onClick = onRetry) { Text(stringResource(R.string.export_try_again)) }
    }
}
