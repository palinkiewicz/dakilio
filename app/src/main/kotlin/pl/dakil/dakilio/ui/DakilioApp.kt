package pl.dakil.dakilio.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import pl.dakil.dakilio.R
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.navigation.LocalNavigator
import pl.dakil.dakilio.core.navigation.Navigator
import pl.dakil.dakilio.core.navigation.rememberEntries
import pl.dakil.dakilio.core.navigation.rememberNavigationState
import pl.dakil.dakilio.core.ui.LocalBottomBarPadding
import pl.dakil.dakilio.core.ui.LocalSharedTransitionScope
import pl.dakil.dakilio.core.ui.NavTransitions
import pl.dakil.dakilio.feature.archive.impl.archiveEntry
import pl.dakil.dakilio.feature.diaries.impl.diariesEntries
import pl.dakil.dakilio.feature.entry.impl.entryEditorEntry
import pl.dakil.dakilio.feature.insights.api.InsightsKey
import pl.dakil.dakilio.feature.insights.impl.insightsEntry
import pl.dakil.dakilio.feature.more.api.MoreKey
import pl.dakil.dakilio.feature.moods.impl.moodsEntry
import pl.dakil.dakilio.feature.importer.api.DaylioImportKey
import pl.dakil.dakilio.feature.importer.impl.daylioImportEntry
import pl.dakil.dakilio.feature.more.impl.moreEntry
import pl.dakil.dakilio.feature.search.impl.searchEntry
import pl.dakil.dakilio.feature.settings.impl.settingsEntry
import pl.dakil.dakilio.feature.tags.impl.tagsEntry
import pl.dakil.dakilio.feature.habits.impl.HabitProgressHost
import pl.dakil.dakilio.feature.habits.impl.habitsEntry
import pl.dakil.dakilio.feature.timeline.api.TimelineKey
import pl.dakil.dakilio.feature.timeline.impl.timelineEntry

private data class TopLevelDestination(val key: NavKey, val symbol: DakilioSymbol, val label: Int)

private val destinations = listOf(
    TopLevelDestination(TimelineKey, DakilioSymbols.Timeline, R.string.nav_timeline),
    TopLevelDestination(InsightsKey, DakilioSymbols.Insights, R.string.nav_insights),
    TopLevelDestination(MoreKey, DakilioSymbols.MoreHoriz, R.string.nav_more),
)

private val topLevelKeys = destinations.map { it.key }

/** App shell: shared-element scope, the navigation display and the bottom navigation bar. */
@Composable
fun DakilioApp(hasIncomingBackup: Boolean = false) {
    val navigationState = rememberNavigationState(topLevelKeys)
    val navigator = remember(navigationState) { Navigator(navigationState) }

    // A shared .daylio file opens the importer where it normally lives (More → Import from Daylio);
    // the import screen's ViewModel then picks the file up.
    LaunchedEffect(hasIncomingBackup) {
        if (!hasIncomingBackup) return@LaunchedEffect
        navigator.navigate(MoreKey)
        if (navigationState.currentKey != DaylioImportKey) navigator.navigate(DaylioImportKey)
    }
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }

    val provider = entryProvider<NavKey> {
        timelineEntry(navigator)
        insightsEntry()
        moreEntry(navigator)
        searchEntry(navigator)
        entryEditorEntry(navigator)
        moodsEntry(navigator)
        daylioImportEntry(
            onBack = navigator::goBack,
            onDone = {
                navigator.goBack()
                navigator.navigate(TimelineKey)
            },
        )
        tagsEntry(navigator)
        diariesEntries(navigator)
        settingsEntry(navigator)
        archiveEntry(navigator)
        habitsEntry(navigator)
    }

    SharedTransitionLayout(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this,
            LocalNavigator provides navigator,
            LocalBottomBarPadding provides barHeight,
        ) {
            Box(Modifier.fillMaxSize()) {
                NavDisplay(
                    entries = navigationState.rememberEntries(provider),
                    onBack = navigator::goBack,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    transitionSpec = { NavTransitions.forward() },
                    popTransitionSpec = { NavTransitions.backward() },
                    predictivePopTransitionSpec = { NavTransitions.backward() },
                )
                AnimatedVisibility(
                    visible = navigationState.isAtTopLevel,
                    enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + fadeIn(),
                    exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    ShortNavigationBar(
                        modifier = Modifier.onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                    ) {
                        destinations.forEach { destination ->
                            val selected = navigationState.topLevelKey == destination.key
                            ShortNavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) haptics.tick()
                                    navigator.navigate(destination.key)
                                },
                                icon = { DakilioIcon(destination.symbol, contentDescription = null, filled = selected) },
                                label = { Text(stringResource(destination.label)) },
                            )
                        }
                    }
                }
                // Habit progress after saving an entry, over whatever screen comes next.
                HabitProgressHost(
                    bottomPadding = if (navigationState.isAtTopLevel) barHeight else 0.dp,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
