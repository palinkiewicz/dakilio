package pl.dakil.dakilio.feature.insights.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.MilestoneProgress
import pl.dakil.dakilio.core.model.MilestoneStatus
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.rememberFormats

private val StampCell = 104.dp

/**
 * Insights → Milestones: every stamp there is to collect, kind by kind. Collected ones carry the
 * day they were reached; each kind's header says how far the next one is.
 */
@Composable
internal fun MilestonesScreen(viewModel: MilestonesViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val formats = rememberFormats()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.milestones_title)) },
                subtitle = { state?.let { Text(stringResource(R.string.milestones_collected, it.reached, it.total)) } },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        DakilioIcon(DakilioSymbols.ArrowBack, contentDescription = stringResource(R.string.milestones_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val current = state
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(StampCell),
                modifier = Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + Spacing.xl,
                    start = Spacing.gutter,
                    end = Spacing.gutter,
                ),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                current.ladders.forEach { ladder ->
                    item(key = "header-${ladder.kind}", span = { GridItemSpan(maxLineSpan) }) { LadderHeader(ladder) }
                    items(ladder.statuses, key = { "${it.milestone.kind}-${it.milestone.target}" }) { status ->
                        StampCell(status, newest = status.reached && status == ladder.statuses.last { it.reached }, formats, current.today)
                    }
                }
            }
        }
    }
}

/** The kind's name, and the next stamp with a meter of the way there. */
@Composable
private fun LadderHeader(ladder: MilestoneProgress) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(top = Spacing.s)) {
        Column(Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DakilioIcon(ladder.kind.symbol, contentDescription = null, tint = colors.primary, filled = true, modifier = Modifier.size(20.dp))
                Text(
                    ladder.kind.title(),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.padding(start = Spacing.s).weight(1f).semantics { heading() },
                )
                Text(
                    stringResource(R.string.milestones_count, ladder.reachedCount, ladder.statuses.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                )
            }
            val next = ladder.next
            if (next == null) {
                Text(
                    stringResource(R.string.milestones_all_collected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            } else {
                Text(
                    stringResource(R.string.milestones_next, next.label()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                Meter((ladder.current.toFloat() / next.target).coerceIn(0f, 1f), Modifier.padding(top = Spacing.m))
            }
        }
    }
}

@Composable
private fun StampCell(status: MilestoneStatus, newest: Boolean, formats: DakilioFormats, today: LocalDate) {
    val colors = MaterialTheme.colorScheme
    val label = status.milestone.label()
    val reachedOn = status.reachedOn
    val date = reachedOn?.let { if (it.year == today.year) formats.dayMonthShort(it) else formats.dayMonthYear(it) }
    val description = if (date != null) stringResource(R.string.milestones_reached_on, label, date) else stringResource(R.string.milestones_ahead, label)
    Column(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Stamp(status.milestone, reached = status.reached, emphasized = newest)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (status.reached) colors.onSurface else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = Spacing.s),
        )
        if (date != null) {
            Text(date, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}
