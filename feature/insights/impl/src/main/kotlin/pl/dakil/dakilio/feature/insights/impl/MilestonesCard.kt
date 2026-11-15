package pl.dakil.dakilio.feature.insights.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.MilestoneProgress

/** How many of the latest stamps the card lines up. */
private const val SHOWN_STAMPS = 4

/**
 * The way into Milestones from Insights: how many stamps are collected, the newest few, and the
 * closest one still ahead.
 */
@Composable
internal fun MilestonesCard(ladders: List<MilestoneProgress>, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val reached = ladders.sumOf { it.reachedCount }
    val total = ladders.sumOf { it.statuses.size }
    // Newest first; a ladder's top stamp is the one worth showing.
    val latest = ladders.mapNotNull { ladder -> ladder.statuses.lastOrNull { it.reached } }
        .sortedByDescending { it.reachedOn }
        .take(SHOWN_STAMPS)
    // The stamp ahead that's proportionally closest.
    val closest = ladders.mapNotNull { ladder -> ladder.next?.let { it to ladder.current.toFloat() / it.target } }.maxByOrNull { it.second }
    Surface(onClick = onOpen, shape = RoundedCornerShape(28.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.milestones_title), style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(
                        stringResource(R.string.milestones_collected, reached, total),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xxs),
                    )
                }
                DakilioIcon(DakilioSymbols.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
            }
            if (latest.isNotEmpty()) {
                Row(Modifier.padding(top = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    latest.forEachIndexed { index, status -> Stamp(status.milestone, reached = true, size = 56.dp, emphasized = index == 0) }
                }
            }
            closest?.let { (milestone, fraction) ->
                Text(
                    stringResource(R.string.milestones_next, milestone.label()),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = Spacing.l),
                )
                Meter(fraction.coerceIn(0f, 1f), Modifier.padding(top = Spacing.s))
            }
        }
    }
}
