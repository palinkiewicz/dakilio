package pl.dakil.dakilio.feature.insights.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.SegmentedShapes
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Diary
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.Post
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.component.LocalEntryPeek
import pl.dakil.dakilio.core.ui.component.PostCard
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.feature.insights.impl.logic.nearestMood
import pl.dakil.dakilio.feature.insights.impl.overview.CardNote

/**
 * One day, opened from a calendar or a chart: its average face and its entries, each of which opens.
 * A day without entries offers to add one for that date.
 *
 * @param diaries by id, to label the cards when the day can mix diaries; empty under a diary filter.
 */
@Composable
internal fun DaySheet(
    date: LocalDate,
    posts: List<Post>,
    moods: List<Mood>,
    diaries: Map<Long, Diary>,
    today: LocalDate,
    formats: DakilioFormats,
    onOpenPost: (Long) -> Unit,
    onAddEntry: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Slide the sheet away before moving on, so the next screen doesn't open under it.
    fun leave(then: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            then()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter).padding(bottom = Spacing.xl),
        ) {
            val average = posts.mapNotNull { it.mood?.level }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
            val mood = average?.let { nearestMood(moods, it) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Spacing.xs)) {
                Column(Modifier.weight(1f)) {
                    Text(formats.fullDate(date, today), style = MaterialTheme.typography.titleLargeEmphasized)
                    if (posts.isNotEmpty()) {
                        val entries = pluralStringResource(R.plurals.insights_heat_entries, posts.size, posts.size)
                        Text(
                            if (mood != null && average != null) stringResource(R.string.insights_point, stringResource(R.string.insights_point_mood, mood.label, formats.decimal(average)), entries) else entries,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                mood?.let { MoodBadge(it, size = 48.dp, active = true) }
            }
            if (posts.isEmpty()) {
                CardNote(stringResource(R.string.insights_day_empty), Modifier.padding(top = Spacing.l, start = Spacing.xs))
                FilledTonalButton(onClick = { leave { onAddEntry(date) } }, modifier = Modifier.padding(top = Spacing.l)) {
                    DakilioIcon(DakilioSymbols.Add, contentDescription = null)
                    Text(stringResource(R.string.insights_day_add), Modifier.padding(start = Spacing.s))
                }
                return@Column
            }
            // The sheet is a window of its own: a peek would open in the app's window, behind it.
            CompositionLocalProvider(LocalEntryPeek provides null) {
                Column(Modifier.padding(top = Spacing.l), verticalArrangement = Arrangement.spacedBy(SegmentedShapes.gap)) {
                    posts.forEachIndexed { index, post ->
                        PostCard(
                            post = post,
                            formats = formats,
                            onClick = { leave { onOpenPost(post.id) } },
                            index = index,
                            count = posts.size,
                            diary = diaries[post.diaryId],
                            onPhotoClick = { leave { onOpenPost(post.id) } },
                        )
                    }
                }
            }
        }
    }
}
