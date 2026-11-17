package pl.dakil.dakilio.feature.settings.impl

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.designsystem.component.ConnectedToggleRow
import pl.dakil.dakilio.core.designsystem.component.SectionHeader
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.component.ToggleOption
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.component.segmentedItemColors
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.PhotoSize
import pl.dakil.dakilio.core.model.ProgressStyle
import pl.dakil.dakilio.core.model.TagStyle
import pl.dakil.dakilio.core.model.TextScale
import pl.dakil.dakilio.core.model.ThemeMode
import pl.dakil.dakilio.core.model.UserSettings
import pl.dakil.dakilio.core.model.WeekStart
import java.time.DayOfWeek
import pl.dakil.dakilio.core.ui.canChooseAppLanguage
import pl.dakil.dakilio.core.ui.component.SelectListItem
import pl.dakil.dakilio.core.ui.component.SelectOption
import pl.dakil.dakilio.core.ui.component.SwitchListItem
import pl.dakil.dakilio.core.ui.currentAppLanguage
import pl.dakil.dakilio.core.ui.languageName
import pl.dakil.dakilio.core.ui.openAppLanguageSettings
import pl.dakil.dakilio.core.ui.rememberFormats

@Composable
internal fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recapAccess = rememberRecapNotificationAccess()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val formats = rememberFormats()
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    var confirmShrink by rememberSaveable { mutableStateOf(false) }
    val removedMessage = stringResource(R.string.settings_samples_removed)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        DakilioIcon(DakilioSymbols.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = Spacing.maxContentWidth)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter)
                    .navigationBarsPadding(),
            ) {
                SectionHeader(stringResource(R.string.settings_appearance))
                Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.xl)) {
                        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMediumEmphasized)
                        Text(
                            stringResource(R.string.settings_theme_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xxs, bottom = Spacing.l),
                        )
                        ConnectedToggleRow(
                            options = listOf(
                                ToggleOption(ThemeMode.System, stringResource(R.string.settings_theme_system), DakilioSymbols.BrightnessAuto),
                                ToggleOption(ThemeMode.Light, stringResource(R.string.settings_theme_light), DakilioSymbols.LightMode),
                                ToggleOption(ThemeMode.Dark, stringResource(R.string.settings_theme_dark), DakilioSymbols.DarkMode),
                            ),
                            selected = state.themeMode,
                            onSelect = viewModel::setThemeMode,
                        )
                    }
                }

                SectionHeader(stringResource(R.string.settings_feel))
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    SwitchListItem(
                        title = stringResource(R.string.settings_haptics),
                        body = stringResource(R.string.settings_haptics_body),
                        symbol = DakilioSymbols.Vibration,
                        polygon = MaterialShapes.Cookie4Sided,
                        checked = state.haptics,
                        onCheckedChange = viewModel::setHaptics,
                        index = 0,
                        count = 4,
                    )
                    SwitchListItem(
                        title = stringResource(R.string.settings_animated_numbers),
                        body = stringResource(R.string.settings_animated_numbers_body),
                        symbol = DakilioSymbols.Numbers,
                        polygon = MaterialShapes.Slanted,
                        checked = state.animatedNumbers,
                        onCheckedChange = viewModel::setAnimatedNumbers,
                        index = 1,
                        count = 4,
                    )
                    SwitchListItem(
                        title = stringResource(R.string.settings_decorative_animations),
                        body = stringResource(R.string.settings_decorative_animations_body),
                        symbol = DakilioSymbols.Animation,
                        polygon = MaterialShapes.Flower,
                        checked = state.decorativeAnimations,
                        onCheckedChange = viewModel::setDecorativeAnimations,
                        index = 2,
                        count = 4,
                    )
                    SelectListItem(
                        title = stringResource(R.string.settings_progress_style),
                        body = stringResource(R.string.settings_progress_style_body),
                        symbol = DakilioSymbols.Waves,
                        polygon = MaterialShapes.Cookie6Sided,
                        selected = state.progressStyle,
                        options = listOf(
                            SelectOption(ProgressStyle.Wavy, stringResource(R.string.settings_progress_style_wavy)),
                            SelectOption(ProgressStyle.Straight, stringResource(R.string.settings_progress_style_straight)),
                        ),
                        onSelect = viewModel::setProgressStyle,
                        index = 3,
                        count = 4,
                    )
                }

                // Older Androids have no per-app language, so the app just follows the device there.
                if (canChooseAppLanguage) {
                    SectionHeader(stringResource(R.string.settings_language))
                    SegmentedListItem(
                        onClick = { openAppLanguageSettings(context) },
                        shapes = ListItemDefaults.segmentedShapes(0, 1),
                        colors = segmentedItemColors(),
                        leadingContent = { ShapedIcon(DakilioSymbols.Language, polygon = MaterialShapes.Cookie12Sided) },
                        trailingContent = { DakilioIcon(DakilioSymbols.OpenInNew, contentDescription = null) },
                    ) { Text(languageName(currentAppLanguage())) }
                }

                SectionHeader(stringResource(R.string.settings_calendar))
                SelectListItem(
                    title = stringResource(R.string.settings_week_start),
                    body = stringResource(R.string.settings_week_start_body),
                    symbol = DakilioSymbols.CalendarMonth,
                    polygon = MaterialShapes.Cookie12Sided,
                    selected = state.weekStart,
                    options = listOf(
                        // "Auto" names the day the region means, so the choice is never a guess.
                        SelectOption(WeekStart.Region, stringResource(R.string.settings_week_start_region), note = formats.weekday(state.regionFirstDay)),
                        SelectOption(WeekStart.Monday, formats.weekday(DayOfWeek.MONDAY)),
                        SelectOption(WeekStart.Saturday, formats.weekday(DayOfWeek.SATURDAY)),
                        SelectOption(WeekStart.Sunday, formats.weekday(DayOfWeek.SUNDAY)),
                    ),
                    onSelect = viewModel::setWeekStart,
                )

                SectionHeader(stringResource(R.string.settings_text_size))
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    val scaleOptions = textScaleOptions()
                    TextSizeTarget.entries.forEachIndexed { index, target ->
                        SelectListItem(
                            title = stringResource(target.title),
                            body = stringResource(target.body),
                            symbol = target.symbol,
                            polygon = target.polygon,
                            selected = percentOf(target.scaleOf(state)),
                            options = scaleOptions,
                            onSelect = { percent ->
                                val scale = percent / 100f
                                when (target) {
                                    TextSizeTarget.Timeline -> viewModel.setTimelineTextScale(scale)
                                    TextSizeTarget.Editor -> viewModel.setEditorTextScale(scale)
                                }
                            },
                            index = index,
                            count = TextSizeTarget.entries.size,
                        )
                    }
                }

                SectionHeader(stringResource(R.string.settings_editor))
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    SelectListItem(
                        title = stringResource(R.string.settings_tag_style),
                        body = stringResource(R.string.settings_tag_style_body),
                        symbol = DakilioSymbols.Sell,
                        polygon = MaterialShapes.Cookie6Sided,
                        selected = state.tagStyle,
                        options = listOf(
                            SelectOption(TagStyle.Icons, stringResource(R.string.settings_tag_style_icons)),
                            SelectOption(TagStyle.Chips, stringResource(R.string.settings_tag_style_chips)),
                        ),
                        onSelect = viewModel::setTagStyle,
                        index = 0,
                        count = 2,
                    )
                    SwitchListItem(
                        title = stringResource(R.string.settings_close_after_save),
                        body = stringResource(R.string.settings_close_after_save_body),
                        symbol = DakilioSymbols.Check,
                        polygon = MaterialShapes.Cookie7Sided,
                        checked = state.closeAfterSave,
                        onCheckedChange = viewModel::setCloseAfterSave,
                        index = 1,
                        count = 2,
                    )
                }

                SectionHeader(stringResource(R.string.settings_photos))
                // The shrink row shows only while there's something to shrink (or it's running).
                val showShrink = state.oversizedPhotos > 0 || state.shrinkingPhotos
                val photoRows = if (showShrink) 2 else 1
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    val defaultNote = stringResource(R.string.settings_photo_size_default)
                    SelectListItem(
                        title = stringResource(R.string.settings_photo_size),
                        body = stringResource(R.string.settings_photo_size_body),
                        symbol = DakilioSymbols.PhotoSizeSelectLarge,
                        polygon = MaterialShapes.Square,
                        selected = state.photoSize,
                        options = PhotoSize.entries.map { size ->
                            SelectOption(size, photoSizeLabel(size), note = defaultNote.takeIf { size == UserSettings().photoSize })
                        },
                        onSelect = viewModel::setPhotoSize,
                        index = 0,
                        count = photoRows,
                    )
                    if (showShrink) {
                        SegmentedListItem(
                            onClick = { confirmShrink = true },
                            enabled = !state.shrinkingPhotos,
                            shapes = ListItemDefaults.segmentedShapes(1, photoRows),
                            colors = segmentedItemColors(),
                            leadingContent = {
                                if (state.shrinkingPhotos) {
                                    LoadingIndicator(Modifier.size(40.dp))
                                } else {
                                    ShapedIcon(DakilioSymbols.Compress, polygon = MaterialShapes.Cookie6Sided)
                                }
                            },
                            supportingContent = if (state.shrinkingPhotos) {
                                null
                            } else {
                                { Text(stringResource(R.string.settings_shrink_photos_body, photoSizeLabel(state.photoSize))) }
                            },
                        ) {
                            Text(
                                if (state.shrinkingPhotos) {
                                    pluralStringResource(R.plurals.settings_shrinking_photos, state.oversizedPhotos, state.oversizedPhotos)
                                } else {
                                    pluralStringResource(R.plurals.settings_shrink_photos, state.oversizedPhotos, state.oversizedPhotos)
                                },
                            )
                        }
                    }
                }

                SectionHeader(stringResource(R.string.settings_recaps_section))
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    SwitchListItem(
                        title = stringResource(R.string.settings_recaps),
                        body = stringResource(R.string.settings_recaps_body),
                        symbol = DakilioSymbols.History,
                        polygon = MaterialShapes.Cookie12Sided,
                        checked = state.recaps,
                        onCheckedChange = viewModel::setRecaps,
                        index = 0,
                        count = 2,
                    )
                    val blocked = state.recaps && state.recapNotifications && recapAccess.blocked
                    SwitchListItem(
                        title = stringResource(R.string.settings_recap_notifications),
                        body = stringResource(if (blocked) R.string.settings_recap_notifications_blocked else R.string.settings_recap_notifications_body),
                        symbol = DakilioSymbols.NotificationsActive,
                        polygon = MaterialShapes.Cookie9Sided,
                        checked = state.recaps && state.recapNotifications,
                        onCheckedChange = { on ->
                            viewModel.setRecapNotifications(on)
                            if (on) recapAccess.request()
                        },
                        enabled = state.recaps,
                        index = 1,
                        count = 2,
                    )
                    if (blocked) {
                        TextButton(onClick = recapAccess::openSettings, modifier = Modifier.padding(start = Spacing.s)) {
                            Text(stringResource(R.string.settings_recap_notifications_fix))
                        }
                    }
                }

                SectionHeader(stringResource(R.string.settings_guidance))
                SwitchListItem(
                    title = stringResource(R.string.settings_hints),
                    body = stringResource(R.string.settings_hints_body),
                    symbol = DakilioSymbols.Lightbulb,
                    polygon = MaterialShapes.Sunny,
                    checked = state.showHints,
                    onCheckedChange = viewModel::setShowHints,
                )

                // Only debug and preview builds seed samples, and the section goes once they're removed.
                if (state.samplePostCount > 0) {
                    SectionHeader(stringResource(R.string.settings_starter))
                    SegmentedListItem(
                        onClick = { confirmRemove = true },
                        shapes = ListItemDefaults.segmentedShapes(0, 1),
                        colors = segmentedItemColors(),
                        leadingContent = {
                            ShapedIcon(
                                DakilioSymbols.DeleteSweep,
                                polygon = MaterialShapes.Cookie9Sided,
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        },
                        supportingContent = {
                            Text(pluralStringResource(R.plurals.settings_remove_samples_body, state.samplePostCount, state.samplePostCount))
                        },
                    ) { Text(stringResource(R.string.settings_remove_samples)) }
                }
                Text(
                    stringResource(R.string.settings_more_soon),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xxl),
                )
            }
        }
    }

    if (confirmShrink) {
        AlertDialog(
            onDismissRequest = { confirmShrink = false },
            icon = { DakilioIcon(DakilioSymbols.Compress, contentDescription = null) },
            title = {
                Text(
                    pluralStringResource(
                        R.plurals.settings_shrink_title,
                        state.oversizedPhotos,
                        state.oversizedPhotos,
                        photoSizeLabel(state.photoSize),
                    ),
                )
            },
            text = { Text(stringResource(R.string.settings_shrink_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmShrink = false
                    haptics.confirm()
                    viewModel.shrinkPhotos()
                }) { Text(stringResource(R.string.settings_shrink_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmShrink = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            icon = { DakilioIcon(DakilioSymbols.DeleteSweep, contentDescription = null) },
            title = { Text(stringResource(R.string.settings_remove_title)) },
            text = { Text(stringResource(R.string.settings_remove_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    haptics.confirm()
                    viewModel.removeSamples { scope.launch { snackbar.showSnackbar(removedMessage) } }
                }) { Text(stringResource(R.string.settings_remove_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

@Composable
private fun photoSizeLabel(size: PhotoSize): String =
    size.maxEdge?.let { stringResource(R.string.settings_photo_size_px, it) } ?: stringResource(R.string.settings_photo_size_original)

/** The two entry text sizes, each a row in Settings with a menu of sizes. */
private enum class TextSizeTarget(
    @StringRes val title: Int,
    @StringRes val body: Int,
    val symbol: DakilioSymbol,
) {
    Timeline(R.string.settings_text_size_timeline, R.string.settings_text_size_timeline_body, DakilioSymbols.Timeline),
    Editor(R.string.settings_text_size_editor, R.string.settings_text_size_editor_body, DakilioSymbols.EditNote),
    ;

    val polygon: RoundedPolygon
        get() = when (this) {
            Timeline -> MaterialShapes.Clover4Leaf
            Editor -> MaterialShapes.Pill
        }

    fun scaleOf(state: SettingsUiState): Float = when (this) {
        Timeline -> state.timelineTextScale
        Editor -> state.editorTextScale
    }
}

/** A text scale as a whole percentage, the key of its menu option (floats don't compare reliably). */
private fun percentOf(scale: Float): Int = (TextScale.coerce(scale) * 100).roundToInt()

/** Every step of the entry text size, from [TextScale.MIN] to [TextScale.MAX], as menu options. */
@Composable
private fun textScaleOptions(): List<SelectOption<Int>> {
    val defaultNote = stringResource(R.string.settings_photo_size_default)
    val steps = ((TextScale.MAX - TextScale.MIN) / TextScale.STEP).roundToInt()
    return (0..steps).map { step ->
        val percent = percentOf(TextScale.MIN + step * TextScale.STEP)
        SelectOption(
            value = percent,
            label = stringResource(R.string.settings_text_size_percent, percent),
            note = defaultNote.takeIf { percent == percentOf(TextScale.DEFAULT) },
        )
    }
}
