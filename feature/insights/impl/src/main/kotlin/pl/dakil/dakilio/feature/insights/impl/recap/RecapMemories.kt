package pl.dakil.dakilio.feature.insights.impl.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Tag
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.component.TagPill
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.feature.insights.impl.R

@Composable
internal fun BrightestDayPage(state: InsightsRecapUiState.Ready, formats: DakilioFormats, onOpenPost: (Long) -> Unit) {
    val post = state.recap.brightest!!
    PageColumn {
        Kicker(stringResource(R.string.insights_recap_brightest))
        Big(formats.fullDate(post.date, state.today))
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.recapEntrance(2).fillMaxWidth()) {
            Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    post.mood?.let { MoodBadge(it, size = 44.dp, active = true) }
                    Text(
                        post.title ?: post.mood?.label.orEmpty(),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = Spacing.m),
                    )
                }
                if (post.tags.isNotEmpty()) RecapTags(post.tags.take(6))
            }
        }
        Button(onClick = { onOpenPost(post.id) }, modifier = Modifier.recapEntrance(3)) { Text(stringResource(R.string.insights_recap_open_entry)) }
    }
}

@Composable
internal fun PhotosPage(state: InsightsRecapUiState.Ready) {
    PageColumn { }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecapTags(tags: List<Tag>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { tags.forEach { TagPill(it) } }
}
