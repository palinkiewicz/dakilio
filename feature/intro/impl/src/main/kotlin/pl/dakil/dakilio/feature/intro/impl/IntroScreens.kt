package pl.dakil.dakilio.feature.intro.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.dakil.dakilio.core.designsystem.component.SectionHeader
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.component.segmentedItemColors
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.SegmentedShapes
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.ui.Hint
import pl.dakil.dakilio.core.ui.component.DaylioBackupCard
import pl.dakil.dakilio.core.ui.component.SuggestedGroupCard
import pl.dakil.dakilio.core.ui.openDaylioBackup
import pl.dakil.dakilio.core.ui.rememberDaylioInstalled

@Composable
internal fun LanguageStep(onContinue: (daylioInstalled: Boolean) -> Unit) {
    val daylioInstalled by rememberDaylioInstalled()
    val haptics = rememberHaptics()
    IntroPage(
        onBack = null,
        bottomBar = {
            Button(onClick = { onContinue(daylioInstalled) }) { Text(stringResource(R.string.intro_continue)) }
        },
    ) {
        Hero(DakilioSymbols.Language, MaterialShapes.Cookie9Sided)
        Title(stringResource(R.string.intro_welcome_title))
        Body(stringResource(R.string.intro_welcome_body))
        SectionHeader(stringResource(R.string.intro_language_header), modifier = Modifier.padding(top = Spacing.l))
        // TODO: once there are translations, list them here and apply the pick with
        //  LocaleManager.setApplicationLocales before continuing. The database must still not be
        //  opened before this step, so the seed data is created in the chosen language.
        val languages = listOf(stringResource(R.string.intro_language_english))
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            languages.forEachIndexed { index, language ->
                SegmentedListItem(
                    onClick = { haptics.tick() },
                    shapes = ListItemDefaults.segmentedShapes(index, languages.size),
                    colors = segmentedItemColors(),
                    trailingContent = {
                        DakilioIcon(DakilioSymbols.CheckCircle, contentDescription = null, filled = true, tint = MaterialTheme.colorScheme.primary)
                    },
                ) { Text(language) }
            }
        }
        Text(
            stringResource(R.string.intro_language_more),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.l),
        )
    }
}

@Composable
internal fun DaylioStep(onBack: () -> Unit, onPickFile: () -> Unit, onStartFresh: () -> Unit) {
    val context = LocalContext.current
    var daylioInstalled by rememberDaylioInstalled()
    val haptics = rememberHaptics()
    IntroPage(
        onBack = onBack,
        bottomBar = {
            TextButton(onClick = onStartFresh) { Text(stringResource(R.string.intro_daylio_skip)) }
        },
    ) {
        Hero(DakilioSymbols.DriveFileMove, MaterialShapes.Cookie12Sided)
        Title(stringResource(R.string.intro_daylio_title))
        Body(stringResource(R.string.intro_daylio_body))
        if (daylioInstalled) {
            DaylioBackupCard(
                title = stringResource(R.string.intro_daylio_card_title),
                body = stringResource(R.string.intro_daylio_card_body),
                onOpenDaylio = {
                    haptics.confirm()
                    if (!openDaylioBackup(context)) daylioInstalled = false
                },
                modifier = Modifier.padding(top = Spacing.l),
            )
        }
        OutlinedButton(onClick = onPickFile, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.s)) {
            DakilioIcon(DakilioSymbols.FolderOpen, contentDescription = null, modifier = Modifier.padding(end = Spacing.s))
            Text(stringResource(R.string.intro_daylio_pick))
        }
    }
}

@Composable
internal fun GroupsStep(viewModel: IntroGroupsViewModel, onBack: (() -> Unit)?, onFinished: () -> Unit) {
    val suggestions = viewModel.suggestions
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    // Everything starts picked: that's what a fresh install used to get.
    var selected by rememberSaveable(stateSaver = keySetSaver) { mutableStateOf(suggestions.map { it.key }.toSet()) }
    val haptics = rememberHaptics()
    IntroPage(
        onBack = onBack,
        bottomBar = {
            Text(
                pluralStringResource(R.plurals.intro_groups_count, selected.size, selected.size, suggestions.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = Spacing.s),
            )
            Button(
                onClick = {
                    haptics.confirm()
                    viewModel.getStarted(selected, onFinished)
                },
                enabled = !saving,
            ) { Text(stringResource(R.string.intro_get_started)) }
        },
    ) {
        Hero(DakilioSymbols.Sell, MaterialShapes.Clover4Leaf)
        Title(stringResource(R.string.intro_groups_title))
        Body(stringResource(R.string.intro_groups_body))
        Hint { Body(stringResource(R.string.intro_groups_hint)) }
        Column(
            verticalArrangement = Arrangement.spacedBy(SegmentedShapes.gap),
            modifier = Modifier.padding(top = Spacing.s),
        ) {
            suggestions.forEachIndexed { index, group ->
                SuggestedGroupCard(
                    group = group,
                    selected = group.key in selected,
                    onToggle = {
                        haptics.tick()
                        selected = if (group.key in selected) selected - group.key else selected + group.key
                    },
                    shape = SegmentedShapes.of(index, suggestions.size),
                )
            }
        }
    }
}

private val keySetSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })

/** A full-screen step: optional back button, scrolling content and a bottom action row. */
@Composable
private fun IntroPage(
    onBack: (() -> Unit)?,
    bottomBar: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            DakilioIcon(DakilioSymbols.ArrowBack, contentDescription = stringResource(R.string.intro_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            } else {
                Spacer(Modifier.statusBarsPadding())
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
                    Row(
                        Modifier
                            .widthIn(max = Spacing.maxContentWidth)
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.gutter, vertical = Spacing.m),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        content = bottomBar,
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = Spacing.maxContentWidth)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter)
                    .padding(top = Spacing.s, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
                content = content,
            )
        }
    }
}

@Composable
private fun Hero(symbol: DakilioSymbol, polygon: RoundedPolygon) {
    Box(Modifier.fillMaxWidth().padding(top = Spacing.xl, bottom = Spacing.s), contentAlignment = Alignment.Center) {
        ShapedIcon(
            symbol = symbol,
            polygon = polygon,
            size = 96.dp,
            iconSize = 48.dp,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMediumEmphasized, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
}
