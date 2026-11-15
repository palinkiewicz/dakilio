package pl.dakil.dakilio.feature.insights.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing

/**
 * The period's patterns as plain sentences: the gentle summary that comes before the charts. Shown
 * in the tertiary container so it reads as a note to the user rather than as one more chart.
 */
@Composable
internal fun EchoesCard(echoes: List<Echo>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = colors.tertiaryContainer,
        contentColor = colors.onTertiaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.xl)) {
            Text(stringResource(R.string.insights_echoes), style = MaterialTheme.typography.titleMediumEmphasized)
            if (echoes.isEmpty()) {
                Text(
                    stringResource(R.string.insights_echoes_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Spacing.s),
                )
                return@Column
            }
            Column(Modifier.padding(top = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                echoes.forEach { echo ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShapedIcon(
                            symbol = echo.symbol(),
                            polygon = MaterialShapes.Cookie9Sided,
                            size = 40.dp,
                            containerColor = colors.tertiary,
                            contentColor = colors.onTertiary,
                        )
                        Text(echo.sentence(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.m))
                    }
                }
            }
        }
    }
}

private fun Echo.symbol(): DakilioSymbol = when (this) {
    is Echo.TagLift -> symbolForKey(tag.iconKey)
    is Echo.TagDip -> symbolForKey(tag.iconKey)
    is Echo.BrightWeekday, is Echo.HeavyWeekday, is Echo.BusyWeekday -> DakilioSymbols.CalendarToday
    is Echo.BrightDayPart -> part.symbol
    is Echo.BusyDayPart -> part.symbol
    is Echo.MoodRising -> DakilioSymbols.TrendingUp
    is Echo.MoodFalling -> DakilioSymbols.TrendingDown
}

@Composable
private fun Echo.sentence(): String = when (this) {
    is Echo.TagLift -> stringResource(R.string.insights_echo_tag_lift, tag.label)
    is Echo.TagDip -> stringResource(R.string.insights_echo_tag_dip, tag.label)
    is Echo.BrightWeekday -> stringResource(R.string.insights_echo_bright, onWeekdays(day))
    is Echo.HeavyWeekday -> stringResource(R.string.insights_echo_heavy, onWeekdays(day))
    is Echo.BusyWeekday -> stringResource(R.string.insights_echo_busy, onWeekdays(day))
    is Echo.BrightDayPart -> stringResource(R.string.insights_echo_bright, inDayPart(part))
    is Echo.BusyDayPart -> stringResource(R.string.insights_echo_busy, inDayPart(part))
    is Echo.MoodRising -> stringResource(R.string.insights_echo_rising)
    is Echo.MoodFalling -> stringResource(R.string.insights_echo_falling)
}

/** "On Mondays": whole phrases per language, Monday first, since weekday names decline. */
@Composable
private fun onWeekdays(day: DayOfWeek): String = stringArrayResource(R.array.insights_on_weekdays)[day.value - 1]

/** "In the morning": whole phrases per language, in [DayPart] order. */
@Composable
private fun inDayPart(part: DayPart): String = stringArrayResource(R.array.insights_in_day_part)[part.ordinal]

internal val DayPart.symbol: DakilioSymbol
    get() = when (this) {
        DayPart.Morning -> DakilioSymbols.WbTwilight
        DayPart.Afternoon -> DakilioSymbols.Sunny
        DayPart.Evening -> DakilioSymbols.NightsStay
        DayPart.Night -> DakilioSymbols.Bedtime
    }
