package pl.dakil.dakilio.feature.insights.impl.recap

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.component.OffscreenCapture
import pl.dakil.dakilio.core.ui.color.tagColors
import pl.dakil.dakilio.core.ui.component.TagPill
import pl.dakil.dakilio.core.ui.component.shareImageIntent
import pl.dakil.dakilio.core.ui.component.writeSharedImage
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.core.ui.mood.NoMoodBadge
import pl.dakil.dakilio.core.ui.mood.moodColors
import pl.dakil.dakilio.feature.insights.impl.R
import pl.dakil.dakilio.feature.insights.impl.chart.BarSegment
import pl.dakil.dakilio.feature.insights.impl.chart.DaysGrid
import pl.dakil.dakilio.feature.insights.impl.chart.SegmentedBar
import pl.dakil.dakilio.feature.insights.impl.logic.InsightsPeriod
import pl.dakil.dakilio.feature.insights.impl.logic.calendarRange
import pl.dakil.dakilio.feature.insights.impl.logic.encode
import pl.dakil.dakilio.feature.insights.impl.logic.nearestMood
import pl.dakil.dakilio.feature.insights.impl.overview.dayCaption
import pl.dakil.dakilio.feature.insights.impl.periodLabel

/**
 * Shares a recap as an image: a preview of exactly what will be shared, a switch for tag names (off
 * leaves only moods and numbers), and Share. Note text is never in it.
 */
@Composable
internal fun RecapShareSheet(state: InsightsRecapUiState.Ready, formats: DakilioFormats, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var includeTags by rememberSaveable { mutableStateOf(false) }
    var capturing by rememberSaveable { mutableStateOf(false) }
    // Through an ActivityResult launcher, so the app lock treats the share sheet like a picker.
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).padding(bottom = Spacing.xl)) {
            Text(stringResource(R.string.insights_share_title), style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(start = Spacing.xs))
            Box(
                Modifier
                    .padding(top = Spacing.l)
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(28.dp))
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(Modifier.widthIn(max = 360.dp)) { RecapShareCard(state, includeTags, formats) }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.m)
                    .clip(RoundedCornerShape(16.dp))
                    .toggleable(value = includeTags, role = Role.Switch) {
                        haptics.toggle(it)
                        includeTags = it
                    }
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(start = Spacing.xs)) {
                    Text(stringResource(R.string.insights_share_tags), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.insights_share_tags_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = includeTags, onCheckedChange = null)
            }
            Button(
                onClick = {
                    haptics.confirm()
                    capturing = true
                },
                enabled = !capturing,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.l),
            ) {
                if (capturing) {
                    LoadingIndicator(Modifier.size(ButtonDefaults.IconSize))
                } else {
                    DakilioIcon(DakilioSymbols.Share, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                }
                Text(stringResource(R.string.insights_share), Modifier.padding(start = Spacing.s))
            }
        }
        if (capturing) {
            OffscreenCapture(
                key = includeTags,
                onCaptured = { image ->
                    scope.launch {
                        val written = withContext(Dispatchers.IO) {
                            runCatching { writeSharedImage(context, image, "recap-" + state.header.period.encode().replace(':', '-'), jpeg = false) }
                        }
                        capturing = false
                        written.onSuccess { (uri, mime) -> runCatching { shareLauncher.launch(shareImageIntent(uri, mime)) } }
                    }
                },
            ) { RecapShareCard(state, includeTags, formats) }
        }
    }
}

/** The recap as one image: the period, its face and numbers, the mood mix, its days and (optionally) tags. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecapShareCard(state: InsightsRecapUiState.Ready, includeTags: Boolean, formats: DakilioFormats) {
    val colors = MaterialTheme.colorScheme
    val overview = state.recap.overview
    val moods = state.moods
    val period = state.header.period
    // An opaque frame all around, so the rounded corners don't turn into transparent pixels.
    Column(Modifier.fillMaxWidth().background(colors.surface).padding(16.dp)) {
        CompositionLocalProvider(LocalContentColor provides colors.onPrimaryContainer) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(colors.primaryContainer).padding(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                Column {
                    Text(listOfNotNull(stringResource(R.string.insights_recap), state.diary?.name).joinToString(" · "), style = MaterialTheme.typography.labelLarge)
                    Text(periodLabel(period, state.header.bounds, formats), style = MaterialTheme.typography.headlineLargeEmphasized)
                }
                val average = overview.mood.average
                val mood = average?.let { nearestMood(moods, it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (mood != null) MoodBadge(mood, size = 72.dp, active = true) else NoMoodBadge(size = 64.dp)
                    Column(Modifier.padding(start = Spacing.l)) {
                        if (mood != null && average != null) {
                            Text(stringResource(R.string.insights_mostly, mood.label), style = MaterialTheme.typography.titleMedium)
                            Text(formats.decimal(average), style = MaterialTheme.typography.displaySmallEmphasized)
                        }
                    }
                }
                val figures = listOfNotNull(
                    formats.count(overview.entries) to stringResource(R.string.insights_entries),
                    formats.count(overview.daysJournaled) to stringResource(R.string.insights_days_journaled),
                    (formats.count(overview.writing.words) to stringResource(R.string.insights_words)).takeIf { overview.writing.words > 0 },
                    (formats.count(overview.streak) to stringResource(R.string.insights_best_streak)).takeIf { overview.streak > 1 },
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    figures.chunked(2).forEach { row ->
                        Row {
                            row.forEach { (value, label) -> Box(Modifier.weight(1f)) { ShareFigure(value, label) } }
                            if (row.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
                if (overview.mood.moodEntries > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        SegmentedBar(
                            segments = overview.mood.distribution.filter { it.count > 0 }.map { BarSegment(it.count, moodColors(it.mood).container) },
                            description = "",
                        )
                        MoodShares(overview.mood.distribution.map { it.mood to it.count })
                    }
                }
                when (period) {
                    is InsightsPeriod.Month -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        DaysGrid(
                            range = period.calendarRange(state.header.bounds),
                            cells = overview.calendar.cells,
                            moods = moods,
                            firstDay = state.firstDay,
                            today = state.today,
                            selected = null,
                            onSelect = {},
                            formats = formats,
                            describe = { dayCaption(it, overview.calendar.cells[it], moods, state.today, formats) },
                            selectable = false,
                        )
                    }
                    is InsightsPeriod.Year -> if (state.recap.months.any { it.second != null }) MonthFaces(state.recap, moods, formats)
                    else -> Unit
                }
                if (includeTags && overview.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        overview.tags.take(5).forEach { stat ->
                            val tones = tagColors(stat.tag.color)
                            TagPill(
                                label = "${stat.tag.label} \u00D7${stat.days}",
                                iconKey = stat.tag.iconKey,
                                containerColor = tones?.soft ?: MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = tones?.accent ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        Text(
            stringResource(pl.dakil.dakilio.core.ui.R.string.ui_share_footer),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}

@Composable
private fun ShareFigure(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLargeEmphasized)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
