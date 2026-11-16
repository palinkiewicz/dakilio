package pl.dakil.dakilio.feature.insights.impl.recap

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pl.dakil.dakilio.core.designsystem.component.entrance
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.ui.component.EmptyState
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.core.ui.rememberFormats
import pl.dakil.dakilio.feature.insights.impl.InsightsSource
import pl.dakil.dakilio.feature.insights.impl.R
import pl.dakil.dakilio.feature.insights.impl.logic.ArchivedPeriod
import pl.dakil.dakilio.feature.insights.impl.logic.ArchivedYear
import pl.dakil.dakilio.feature.insights.impl.logic.InsightsPeriod
import pl.dakil.dakilio.feature.insights.impl.logic.nearestMood
import pl.dakil.dakilio.feature.insights.impl.logic.recapArchive

/** `null` while loading. */
data class RecapArchiveUiState(val years: List<ArchivedYear>, val moods: List<Mood>)

@HiltViewModel
class RecapArchiveViewModel @Inject constructor(source: InsightsSource) : ViewModel() {
    val state: StateFlow<RecapArchiveUiState?> = source.data(flowOf(null))
        .map { RecapArchiveUiState(recapArchive(it.allPosts, it.today), it.primaries) }
        .flowOn(source.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Every finished month and year, newest first; those with enough entries play their recap. */
@Composable
internal fun RecapArchiveScreen(onBack: () -> Unit, onPlay: (InsightsPeriod) -> Unit, viewModel: RecapArchiveViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.insights_recaps_title)) },
                subtitle = { Text(stringResource(R.string.insights_recaps_subtitle)) },
                navigationIcon = { IconButton(onClick = onBack) { DakilioIcon(DakilioSymbols.ArrowBack, contentDescription = stringResource(R.string.insights_back)) } },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val current = state
        when {
            current == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            current.years.isEmpty() -> EmptyState(
                symbol = DakilioSymbols.History,
                title = stringResource(R.string.insights_recaps_empty_title),
                body = stringResource(R.string.insights_recaps_empty_body),
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + Spacing.xl,
                        start = Spacing.gutter,
                        end = Spacing.gutter,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    items(current.years, key = { it.year }) { year ->
                        YearCard(year, current.moods, onPlay, Modifier.entrance(current.years.indexOf(year)))
                    }
                }
            }
        }
    }
}

@Composable
private fun YearCard(year: ArchivedYear, moods: List<Mood>, onPlay: (InsightsPeriod) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(year.year.toString(), style = MaterialTheme.typography.headlineMediumEmphasized)
                    Text(
                        if (year.summary.finished) {
                            pluralStringResource(R.plurals.insights_heat_entries, year.summary.entries, year.summary.entries)
                        } else {
                            stringResource(R.string.insights_recaps_year_running)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                year.summary.average?.let { nearestMood(moods, it) }?.let { MoodBadge(it, size = 40.dp) }
                if (year.summary.playable) {
                    FilledIconButton(
                        onClick = { onPlay(InsightsPeriod.Year(year.year)) },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.tertiary, contentColor = colors.onTertiary),
                        modifier = Modifier.padding(start = Spacing.s),
                    ) {
                        DakilioIcon(DakilioSymbols.PlayArrow, contentDescription = stringResource(R.string.insights_recaps_play, year.year.toString()), filled = true)
                    }
                }
            }
            Column(Modifier.padding(top = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                year.months.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        row.forEach { (month, summary) -> MonthTile(month, summary, moods, { onPlay(InsightsPeriod.Month(month)) }, Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** A month: its name and average face; it plays its recap when it has one, and is dimmed otherwise. */
@Composable
private fun MonthTile(month: YearMonth, summary: ArchivedPeriod, moods: List<Mood>, onPlay: () -> Unit, modifier: Modifier) {
    val formats = rememberFormats()
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val name = formats.monthYear(month.atDay(1))
    val description = if (summary.playable) stringResource(R.string.insights_recaps_play, name) else name
    Surface(
        onClick = onPlay,
        enabled = summary.playable,
        shape = RoundedCornerShape(16.dp),
        color = if (summary.playable) colors.surfaceContainerHighest else colors.surfaceContainerHigh,
        interactionSource = interaction,
        modifier = modifier
            .pressScale(interaction, 0.92f)
            .alpha(if (summary.playable) 1f else 0.45f)
            .semantics { contentDescription = description },
    ) {
        Column(Modifier.padding(vertical = Spacing.m), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formats.monthShort(month), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Box(Modifier.padding(top = Spacing.xs).size(28.dp), contentAlignment = Alignment.Center) {
                // Only a recap that can be played shows its face; a month still running or too thin waits.
                val mood = summary.average?.takeIf { summary.playable }?.let { nearestMood(moods, it) }
                if (mood != null) {
                    MoodBadge(mood, size = 28.dp)
                } else if (summary.finished && summary.entries > 0) {
                    Box(Modifier.size(8.dp).background(colors.onSurfaceVariant.copy(alpha = 0.5f), CircleShape))
                }
            }
        }
    }
}
