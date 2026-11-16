package pl.dakil.dakilio.feature.insights.impl.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import java.time.LocalDate
import pl.dakil.dakilio.core.designsystem.component.DakilioLinearProgress
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.DateRange
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.feature.insights.impl.R
import pl.dakil.dakilio.feature.insights.impl.chart.SingleBar
import pl.dakil.dakilio.feature.insights.impl.logic.DiaryStat
import pl.dakil.dakilio.feature.insights.impl.logic.Gathering
import pl.dakil.dakilio.feature.insights.impl.logic.Records
import pl.dakil.dakilio.feature.insights.impl.logic.WritingStats
import pl.dakil.dakilio.feature.insights.impl.logic.nearestMood
import pl.dakil.dakilio.feature.insights.impl.rangeLabel

/** What was written: words, words per entry, photos, and the longest entry (a tap opens it). */
@Composable
internal fun WritingCard(writing: WritingStats, today: LocalDate, formats: DakilioFormats, onOpenPost: (Long) -> Unit) {
    InsightCard {
        CardTitle(stringResource(R.string.insights_writing))
        val tile = RoundedCornerShape(16.dp)
        val tileColor = MaterialTheme.colorScheme.surfaceContainerHigh
        Row(Modifier.padding(top = Spacing.l).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            StatTile(stringResource(R.string.insights_words), formats.count(writing.words), tile, Modifier.weight(1.2f), color = tileColor)
            StatTile(stringResource(R.string.insights_words_per_entry), formats.count(writing.averageWords), tile, Modifier.weight(1f), color = tileColor)
            StatTile(stringResource(R.string.insights_photos), formats.count(writing.photos), tile, Modifier.weight(1f), color = tileColor)
        }
        writing.longest?.let { post ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = Spacing.s).fillMaxWidth().cardRowClick { onOpenPost(post.id) },
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.insights_longest_entry), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(
                            R.string.insights_point,
                            pluralStringResource(R.plurals.insights_words_count, writing.longestWords, formats.count(writing.longestWords)),
                            formats.fullDate(post.date, today),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DakilioIcon(DakilioSymbols.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** How the period's entries split over the diaries, busiest first; a tap filters Insights to that diary. */
@Composable
internal fun DiariesCard(diaries: List<DiaryStat>, moods: List<Mood>, onDiary: (Long) -> Unit) {
    val max = diaries.maxOf { it.entries }.toFloat()
    InsightCard {
        CardTitle(stringResource(R.string.insights_diaries))
        Column(Modifier.padding(top = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            diaries.forEach { stat ->
                val entries = pluralStringResource(R.plurals.insights_heat_entries, stat.entries, stat.entries)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .cardRowClick { onDiary(stat.diary.id) }
                        .semantics(mergeDescendants = true) { contentDescription = "${stat.diary.name}, $entries" },
                ) {
                    ShapedIcon(symbolForKey(stat.diary.iconKey), size = 36.dp)
                    Column(Modifier.padding(start = Spacing.m).weight(1f)) {
                        Text(stat.diary.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.xs)) {
                            SingleBar(stat.entries / max, Modifier.weight(1f))
                            Text(
                                stat.entries.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                modifier = Modifier.padding(start = Spacing.s).widthIn(min = 24.dp),
                            )
                        }
                    }
                    stat.averageMood?.let { average -> nearestMood(moods, average) }?.let { MoodBadge(it, Modifier.padding(start = Spacing.m), size = 28.dp) }
                }
            }
        }
    }
}

/** The standout moments of a long stretch. */
@Composable
internal fun RecordsCard(records: Records, moods: List<Mood>, today: LocalDate, formats: DakilioFormats) {
    InsightCard {
        CardTitle(stringResource(R.string.insights_records))
        Column(Modifier.padding(top = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            records.firstEntry?.let {
                RecordRow(DakilioSymbols.Flag, MaterialShapes.Cookie9Sided, stringResource(R.string.insights_record_first), formats.dayMonthYear(it))
            }
            records.bestMonth?.let { (month, average) ->
                RecordRow(
                    DakilioSymbols.Star,
                    MaterialShapes.Sunny,
                    stringResource(R.string.insights_record_best_month),
                    stringResource(R.string.insights_point, formats.monthYear(month.atDay(1)), moodValueLabel(average, moods, formats)),
                )
            }
            records.busiestMonth?.let { (month, entries) ->
                RecordRow(
                    DakilioSymbols.CalendarMonth,
                    MaterialShapes.Cookie6Sided,
                    stringResource(R.string.insights_record_busiest_month),
                    stringResource(R.string.insights_point, formats.monthYear(month.atDay(1)), pluralStringResource(R.plurals.insights_heat_entries, entries, entries)),
                )
            }
            records.longestStreak?.let { RecordRow(DakilioSymbols.LocalFireDepartment, MaterialShapes.Clover4Leaf, stringResource(R.string.insights_record_streak), runLabel(it, today, formats)) }
            records.brightRun?.let { RecordRow(DakilioSymbols.AutoAwesome, MaterialShapes.Flower, stringResource(R.string.insights_record_bright), runLabel(it, today, formats)) }
            records.busiestDay?.let { (date, entries) ->
                RecordRow(
                    DakilioSymbols.EditNote,
                    MaterialShapes.Cookie12Sided,
                    stringResource(R.string.insights_record_busiest_day),
                    stringResource(R.string.insights_point, formats.fullDate(date, today), pluralStringResource(R.plurals.insights_heat_entries, entries, entries)),
                )
            }
        }
    }
}

/** "23 days · 1 – 23 Oct". */
@Composable
private fun runLabel(run: DateRange, today: LocalDate, formats: DakilioFormats): String =
    stringResource(R.string.insights_point, pluralStringResource(R.plurals.insights_days_count, run.days, run.days), rangeLabel(run, today, formats))

@Composable
private fun RecordRow(symbol: DakilioSymbol, polygon: RoundedPolygon, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }) {
        ShapedIcon(symbol, polygon = polygon, size = 40.dp)
        Column(Modifier.padding(start = Spacing.m)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** What isn't shown yet for a thin period, with how far along each is, instead of a wall of empty charts. */
@Composable
internal fun GatheringCard(items: List<Gathering>) {
    InsightCard {
        CardTitle(stringResource(R.string.insights_gathering), stringResource(R.string.insights_gathering_body))
        Column(Modifier.padding(top = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            items.forEach { item ->
                Column(Modifier.semantics(mergeDescendants = true) { }) {
                    Text(
                        stringResource(
                            when (item.kind) {
                                Gathering.Kind.MoodFlow -> R.string.insights_gathering_flow
                                Gathering.Kind.TagLinks -> R.string.insights_gathering_links
                                Gathering.Kind.BestDay -> R.string.insights_gathering_best_day
                            },
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    DakilioLinearProgress(Modifier.fillMaxWidth().padding(top = Spacing.s), progress = { item.progress })
                }
            }
        }
    }
}

/** A period with no entries, inside a journal that has some. */
@Composable
internal fun EmptyPeriodCard(hint: String?) {
    InsightCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShapedIcon(DakilioSymbols.CalendarToday, polygon = MaterialShapes.Cookie9Sided, size = 48.dp)
            Column(Modifier.padding(start = Spacing.l)) {
                Text(stringResource(R.string.insights_period_empty_title), style = MaterialTheme.typography.titleMediumEmphasized)
                hint?.let { CardNote(it, Modifier.padding(top = Spacing.xxs)) }
            }
        }
    }
}
