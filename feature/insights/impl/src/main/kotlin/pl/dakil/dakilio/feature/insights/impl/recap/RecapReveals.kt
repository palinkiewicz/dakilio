package pl.dakil.dakilio.feature.insights.impl.recap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.feature.insights.impl.R
import pl.dakil.dakilio.feature.insights.impl.logic.brighter
import pl.dakil.dakilio.feature.insights.impl.overview.TagIcon
import pl.dakil.dakilio.feature.insights.impl.overview.dayPartLabel

@Composable
internal fun TagsPage(state: InsightsRecapUiState.Ready, steps: RecapSteps, formats: DakilioFormats) {
    val overview = state.recap.overview
    PageColumn {
        Kicker(stringResource(R.string.insights_recap_tags))
        overview.tags.take(3).forEachIndexed { i, stat ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.recapEntrance(i + 1)) {
                Text("#${i + 1}", style = MaterialTheme.typography.headlineMediumEmphasized, modifier = Modifier.widthIn(min = 56.dp))
                TagIcon(stat.tag, size = 48.dp)
                Column(Modifier.padding(start = Spacing.m)) {
                    Text(stat.tag.label, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pluralStringResource(R.plurals.insights_days_count, stat.days, stat.days), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        overview.tags.brighter(1).firstOrNull()?.let { stat ->
            Text(
                stringResource(R.string.insights_recap_tag_brighter, stat.tag.label, formats.signedDecimal(stat.link!!.lift)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.recapEntrance(4).padding(top = Spacing.m),
            )
        }
    }
}

@Composable
internal fun NewTagsPage(state: InsightsRecapUiState.Ready) {
    PageColumn { }
}

@Composable
internal fun RhythmPage(state: InsightsRecapUiState.Ready, steps: RecapSteps, formats: DakilioFormats) {
    val overview = state.recap.overview
    PageColumn {
        Kicker(stringResource(R.string.insights_recap_rhythm))
        overview.rhythm.bestWeekday?.let { day ->
            Big(formats.weekday(day))
            Text(stringResource(R.string.insights_hl_best_weekday), style = MaterialTheme.typography.titleMedium, modifier = Modifier.recapEntrance(2))
        }
        overview.rhythm.busiestDayPart?.let { part ->
            Text(
                stringResource(R.string.insights_recap_mostly_written, dayPartLabel(part).lowercase()),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.recapEntrance(3).padding(top = Spacing.l),
            )
        }
    }
}
