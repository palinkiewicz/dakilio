package pl.dakil.dakilio.feature.insights.impl.entries

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.YearMonth
import pl.dakil.dakilio.core.designsystem.theme.SegmentedShapes
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.PostPhoto
import pl.dakil.dakilio.core.ui.component.PhotoViewer
import pl.dakil.dakilio.core.ui.component.PostCard
import pl.dakil.dakilio.core.ui.rememberFormats
import pl.dakil.dakilio.core.ui.sharedContainer
import pl.dakil.dakilio.feature.entry.api.EntryEditorKey
import pl.dakil.dakilio.feature.insights.impl.InsightsDetailScaffold
import pl.dakil.dakilio.feature.insights.impl.R
import pl.dakil.dakilio.feature.insights.impl.overview.CardNote
import pl.dakil.dakilio.feature.insights.impl.overview.dayPartLabel

/**
 * The entries behind a number: the period's entries that match the filter, newest day first under
 * month headings, as the same cards as on the timeline (so holding one peeks at it).
 */
@Composable
internal fun InsightsEntriesScreen(viewModel: InsightsEntriesViewModel, onBack: () -> Unit, onOpenPost: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formats = rememberFormats()
    val ready = state as? InsightsEntriesUiState.Ready
    var viewer by remember { mutableStateOf<Pair<List<PostPhoto>, Int>?>(null) }

    // What the list is narrowed to, as its title: "Walk · Morning", or just "Entries".
    val parts = if (ready == null) {
        emptyList()
    } else {
        listOfNotNull(
            ready.tag?.label,
            ready.mood?.label,
            ready.filter.weekday?.let(formats::weekday),
            ready.filter.dayPart?.let { dayPartLabel(it) },
        )
    }
    InsightsDetailScaffold(
        title = parts.joinToString(" · ").ifEmpty { stringResource(R.string.insights_entries) },
        subtitle = ready?.let { pluralStringResource(R.plurals.insights_heat_entries, it.count, it.count) },
        header = ready?.header,
        moods = ready?.moods.orEmpty(),
        firstDay = ready?.firstDay ?: java.time.DayOfWeek.MONDAY,
        viewModel = viewModel,
        onBack = onBack,
        itemSpacing = 0.dp, // a day's cards touch as one segmented group
    ) {
        val current = ready ?: return@InsightsDetailScaffold
        if (current.days.isEmpty()) {
            item(key = "none") { CardNote(stringResource(R.string.insights_entries_none), Modifier.padding(top = Spacing.xl, start = Spacing.xs)) }
            return@InsightsDetailScaffold
        }
        val manyMonths = YearMonth.from(current.days.first().date) != YearMonth.from(current.days.last().date)
        var month: YearMonth? = null
        current.days.forEach { day ->
            val dayMonth = YearMonth.from(day.date)
            if (manyMonths && dayMonth != month) {
                month = dayMonth
                item(key = "month-$dayMonth") {
                    Row(
                        Modifier.animateItem().fillMaxWidth().padding(top = Spacing.l).semantics { heading() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            formats.monthYear(dayMonth.atDay(1)),
                            style = MaterialTheme.typography.titleSmallEmphasized,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        HorizontalDivider(Modifier.padding(start = Spacing.m).weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            item(key = "day-${day.date}") {
                Text(
                    formats.fullDate(day.date, current.today),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.animateItem().padding(start = Spacing.xs, top = Spacing.l, bottom = Spacing.s).semantics { heading() },
                )
            }
            itemsIndexed(day.posts, key = { _, post -> post.id }) { index, post ->
                PostCard(
                    post = post,
                    formats = formats,
                    onClick = { onOpenPost(post.id) },
                    index = index,
                    count = day.posts.size,
                    diary = current.diaries[post.diaryId],
                    onPhotoClick = { viewer = post.photos to it },
                    modifier = Modifier
                        .animateItem()
                        .padding(top = if (index > 0) SegmentedShapes.gap else 0.dp)
                        .sharedContainer(EntryEditorKey.postSharedElementKey(post.id), SegmentedShapes.of(index, day.posts.size)),
                )
            }
        }
    }
    viewer?.let { (photos, index) -> PhotoViewer(photos, index, onDismiss = { viewer = null }) }
}
