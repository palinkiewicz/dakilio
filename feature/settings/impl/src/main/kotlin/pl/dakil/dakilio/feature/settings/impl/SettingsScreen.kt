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
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.TextStyle
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
import pl.dakil.dakilio.core.model.TagStyle
import pl.dakil.dakilio.core.model.TextScale
import pl.dakil.dakilio.core.model.ThemeMode
import pl.dakil.dakilio.core.ui.canChooseAppLanguage
import pl.dakil.dakilio.core.ui.component.TextScaleSlider
import pl.dakil.dakilio.core.ui.currentAppLanguage
import pl.dakil.dakilio.core.ui.languageName
import pl.dakil.dakilio.core.ui.openAppLanguageSettings
import pl.dakil.dakilio.core.ui.scaled

@Composable
internal fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val context = LocalContext.current
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    var textSizeDialog by rememberSaveable { mutableStateOf<TextSizeTarget?>(null) }
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

                SectionHeader(stringResource(R.string.settings_text_size))
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    TextSizeTarget.entries.forEachIndexed { index, target ->
                        SegmentedListItem(
                            onClick = { textSizeDialog = target },
                            shapes = ListItemDefaults.segmentedShapes(index, TextSizeTarget.entries.size),
                            colors = segmentedItemColors(),
                            leadingContent = { ShapedIcon(target.symbol, polygon = target.polygon) },
                            supportingContent = { Text(stringResource(target.body)) },
                            trailingContent = {
                                Text(
                                    stringResource(R.string.settings_text_size_percent, (target.scaleOf(state) * 100).roundToInt()),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            },
                        ) { Text(stringResource(target.title)) }
                    }
                }

                SectionHeader(stringResource(R.string.settings_editor))
                Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.xl)) {
                        Text(stringResource(R.string.settings_tag_style), style = MaterialTheme.typography.titleMediumEmphasized)
                        Text(
                            stringResource(R.string.settings_tag_style_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xxs, bottom = Spacing.l),
                        )
                        ConnectedToggleRow(
                            options = listOf(
                                ToggleOption(TagStyle.Icons, stringResource(R.string.settings_tag_style_icons), DakilioSymbols.AutoAwesome),
                                ToggleOption(TagStyle.Chips, stringResource(R.string.settings_tag_style_chips), DakilioSymbols.Sell),
                            ),
                            selected = state.tagStyle,
                            onSelect = viewModel::setTagStyle,
                        )
                    }
                }

                SectionHeader(stringResource(R.string.settings_guidance))
                SegmentedListItem(
                    onClick = {
                        haptics.toggle(!state.showHints)
                        viewModel.setShowHints(!state.showHints)
                    },
                    shapes = ListItemDefaults.segmentedShapes(0, 1),
                    colors = segmentedItemColors(),
                    leadingContent = { ShapedIcon(DakilioSymbols.Lightbulb, polygon = MaterialShapes.Sunny) },
                    supportingContent = { Text(stringResource(R.string.settings_hints_body)) },
                    trailingContent = {
                        Switch(
                            checked = state.showHints,
                            onCheckedChange = {
                                haptics.toggle(it)
                                viewModel.setShowHints(it)
                            },
                            thumbContent = if (state.showHints) {
                                { DakilioIcon(DakilioSymbols.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                            } else {
                                null
                            },
                        )
                    },
                ) { Text(stringResource(R.string.settings_hints)) }

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

    textSizeDialog?.let { target ->
        TextSizeDialog(
            target = target,
            scale = target.scaleOf(state),
            onConfirm = { scale ->
                textSizeDialog = null
                when (target) {
                    TextSizeTarget.Timeline -> viewModel.setTimelineTextScale(scale)
                    TextSizeTarget.Editor -> viewModel.setEditorTextScale(scale)
                }
            },
            onDismiss = { textSizeDialog = null },
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

/** The two entry text sizes, each a row in Settings that opens [TextSizeDialog]. */
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

    /** The body style the entry note uses on that screen, for the dialog's sample. */
    val sampleStyle: TextStyle
        @Composable get() = when (this) {
            Timeline -> MaterialTheme.typography.bodyMedium
            Editor -> MaterialTheme.typography.bodyLarge
        }

    fun scaleOf(state: SettingsUiState): Float = when (this) {
        Timeline -> state.timelineTextScale
        Editor -> state.editorTextScale
    }
}

/**
 * Picks one entry text size: a sample line drawn at the chosen size above a stepped slider.
 * Nothing is saved until the user confirms.
 */
@Composable
private fun TextSizeDialog(
    target: TextSizeTarget,
    scale: Float,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    var value by rememberSaveable { mutableFloatStateOf(scale) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DakilioIcon(target.symbol, contentDescription = null) },
        title = { Text(stringResource(target.title)) },
        text = {
            Column {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.settings_text_size_sample),
                        style = target.sampleStyle.scaled(value),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.l),
                    )
                }
                TextScaleSlider(value = value, onValueChange = { value = it }, modifier = Modifier.padding(top = Spacing.l))
                TextButton(
                    onClick = {
                        haptics.tick()
                        value = TextScale.DEFAULT
                    },
                    enabled = value != TextScale.DEFAULT,
                    modifier = Modifier.align(Alignment.Start),
                ) { Text(stringResource(R.string.settings_text_size_reset)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                haptics.confirm()
                onConfirm(value)
            }) { Text(stringResource(R.string.settings_text_size_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}
