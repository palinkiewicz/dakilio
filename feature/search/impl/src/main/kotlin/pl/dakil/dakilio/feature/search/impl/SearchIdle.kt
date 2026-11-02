package pl.dakil.dakilio.feature.search.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File
import pl.dakil.dakilio.core.designsystem.component.ConnectedToggleRow
import pl.dakil.dakilio.core.designsystem.component.PolygonShape
import pl.dakil.dakilio.core.designsystem.component.SectionHeader
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.component.ToggleOption
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.component.segmentedItemColors
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.ui.Hint
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.core.ui.rememberFormats

/** What Search offers before anything is typed or picked: recent searches, lenses and tags. */
@Composable
internal fun SearchIdle(
    state: SearchUiState,
    onRecent: (RecentSearch) -> Unit,
    onRemoveRecent: (RecentSearch) -> Unit,
    onClearRecents: () -> Unit,
    onLens: (Lens) -> Unit,
    onTag: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formats = rememberFormats()
    var forgotten by rememberSaveable { mutableStateOf(false) }
    val showForgotten = forgotten && state.forgottenTags.isNotEmpty()
    val recents = state.recents.take(MAX_SHOWN_RECENTS)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xxl),
    ) {
        if (recents.isNotEmpty()) {
            item(key = "recent-header") {
                Row(Modifier.fillMaxWidth().animateItem(), verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(stringResource(R.string.search_recent), Modifier.weight(1f))
                    TextButton(onClick = onClearRecents, modifier = Modifier.padding(top = Spacing.s)) {
                        Text(stringResource(R.string.search_recent_clear))
                    }
                }
            }
            itemsIndexed(recents, key = { _, recent -> "recent-${recent.raw}" }) { index, recent ->
                SegmentedListItem(
                    onClick = { onRecent(recent) },
                    shapes = ListItemDefaults.segmentedShapes(index, recents.size),
                    colors = segmentedItemColors(),
                    leadingContent = { DakilioIcon(DakilioSymbols.History, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = { onRemoveRecent(recent) }) {
                            DakilioIcon(DakilioSymbols.Close, contentDescription = stringResource(R.string.search_recent_remove))
                        }
                    },
                    modifier = Modifier.animateItem().padding(top = if (index > 0) ListItemDefaults.SegmentedGap else 0.dp),
                ) {
                    Text(searchSummary(recent.filters, state, formats), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (state.lenses.isNotEmpty()) {
            item(key = "lens-header") { SectionHeader(stringResource(R.string.search_look_back), Modifier.animateItem()) }
            state.lenses.chunked(2).forEach { row ->
                item(key = "lenses-${row.first().lens}") {
                    Row(
                        Modifier.animateItem().padding(bottom = Spacing.s),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        row.forEach { LensCard(it, state, onClick = { onLens(it.lens) }, modifier = Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        if (state.topTags.isNotEmpty()) {
            item(key = "tags-header") { SectionHeader(stringResource(R.string.search_your_tags), Modifier.animateItem()) }
            if (state.forgottenTags.isNotEmpty()) {
                item(key = "tags-toggle") {
                    ConnectedToggleRow(
                        options = listOf(
                            ToggleOption(false, stringResource(R.string.search_tags_frequent)),
                            ToggleOption(true, stringResource(R.string.search_tags_forgotten)),
                        ),
                        selected = showForgotten,
                        onSelect = { forgotten = it },
                        modifier = Modifier.animateItem().padding(bottom = Spacing.m),
                    )
                }
            }
            item(key = "tags") {
                // A 40dp touch target instead of 48dp halves the gap between the chip rows.
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
                    FlowRow(
                        Modifier.animateItem(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        (if (showForgotten) state.forgottenTags else state.topTags).forEach { tag ->
                            TagFilterChip(tag, selected = false) { onTag(tag.id) }
                        }
                    }
                }
            }
        }

        item(key = "hint") {
            Hint {
                Text(
                    stringResource(R.string.search_hash_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.animateItem().padding(start = Spacing.xs, top = Spacing.l),
                )
            }
        }
        item(key = "nav-space") { Box(Modifier.navigationBarsPadding()) }
    }
}

/** A lens as a tonal card: an expressive badge, then its name and how many entries it finds. */
@Composable
private fun LensCard(item: LensCount, state: SearchUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = {
            haptics.tick()
            onClick()
        },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        interactionSource = interaction,
        modifier = modifier.pressScale(interaction),
    ) {
        Column(Modifier.padding(Spacing.l)) {
            LensBadge(item.lens, state)
            Text(
                stringResource(
                    when (item.lens) {
                        Lens.OnThisDay -> R.string.search_lens_on_this_day
                        Lens.BrightDays -> R.string.search_lens_bright
                        Lens.ToughDays -> R.string.search_lens_tough
                        Lens.PhotoMemories -> R.string.search_lens_photos
                        Lens.LongReads -> R.string.search_lens_long
                        Lens.Shuffle -> R.string.search_lens_shuffle
                    },
                ),
                style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.m),
            )
            Text(
                if (item.lens == Lens.Shuffle) {
                    stringResource(R.string.search_lens_shuffle_body)
                } else {
                    pluralStringResource(R.plurals.search_results, item.count, item.count)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Mood lenses wear the mood's own face; Photo memories shows the newest photo in a cookie. */
@Composable
private fun LensBadge(lens: Lens, state: SearchUiState) {
    val colors = MaterialTheme.colorScheme
    when (lens) {
        Lens.BrightDays -> state.moods.lastOrNull()?.let { MoodBadge(it, size = BADGE, active = true) }
        Lens.ToughDays -> state.moods.firstOrNull()?.let { MoodBadge(it, size = BADGE) }
        Lens.PhotoMemories -> {
            val shape = remember { PolygonShape(MaterialShapes.Cookie12Sided) }
            val photo = state.coverPhoto
            if (photo != null) {
                AsyncImage(
                    model = File(photo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(BADGE).clip(shape).background(colors.surfaceContainerHighest),
                )
            } else {
                ShapedIcon(DakilioSymbols.PhotoLibrary, polygon = MaterialShapes.Cookie12Sided, size = BADGE)
            }
        }
        Lens.OnThisDay -> ShapedIcon(
            DakilioSymbols.EventRepeat,
            polygon = MaterialShapes.Cookie9Sided,
            size = BADGE,
            containerColor = colors.tertiaryContainer,
            contentColor = colors.onTertiaryContainer,
        )
        Lens.LongReads -> ShapedIcon(
            DakilioSymbols.AutoStories,
            polygon = MaterialShapes.Clover4Leaf,
            size = BADGE,
            containerColor = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
        )
        Lens.Shuffle -> ShapedIcon(DakilioSymbols.Shuffle, polygon = MaterialShapes.SoftBurst, size = BADGE)
    }
}

private val BADGE = 48.dp
private const val MAX_SHOWN_RECENTS = 5
