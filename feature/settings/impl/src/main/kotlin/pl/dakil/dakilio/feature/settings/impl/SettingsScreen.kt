package pl.dakil.dakilio.feature.settings.impl

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.designsystem.component.ConnectedToggleRow
import pl.dakil.dakilio.core.designsystem.component.SectionHeader
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.component.ToggleOption
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.component.segmentedItemColors
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.TagStyle
import pl.dakil.dakilio.core.model.ThemeMode

@Composable
internal fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
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

                SectionHeader(stringResource(R.string.settings_starter))
                if (state.samplePostCount > 0) {
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
                } else {
                    Text(
                        stringResource(R.string.settings_no_samples),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
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
