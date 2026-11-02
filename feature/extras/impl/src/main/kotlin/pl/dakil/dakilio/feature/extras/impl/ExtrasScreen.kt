package pl.dakil.dakilio.feature.extras.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.component.segmentedItemColors
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.feature.habits.api.HabitsKey
import pl.dakil.dakilio.feature.tagcolors.api.TagColorsKey

/**
 * One extra on the hub.
 *
 * @property status how it's used right now, or `null` when it isn't used yet.
 */
private data class Extra(
    val key: NavKey,
    val symbol: DakilioSymbol,
    val title: String,
    val body: String,
    val status: String?,
)

/**
 * More → Extras: optional tools that build on moods and tags, each with its own screen. They have
 * no on/off switch; one that holds nothing (no colors set, …) is simply unused.
 */
@Composable
internal fun ExtrasScreen(viewModel: ExtrasViewModel, onBack: () -> Unit, onOpen: (NavKey) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val coloredTags = state?.coloredTags ?: 0
    val habits = state?.habits ?: 0

    // To add an extra: give it its own feature module, then list it here.
    val extras = listOf(
        Extra(
            key = TagColorsKey,
            symbol = DakilioSymbols.Palette,
            title = stringResource(R.string.extras_tag_colors),
            body = stringResource(R.string.extras_tag_colors_body),
            status = if (coloredTags > 0) pluralStringResource(R.plurals.extras_tag_colors_status, coloredTags, coloredTags) else null,
        ),
        Extra(
            key = HabitsKey,
            symbol = DakilioSymbols.CheckCircle,
            title = stringResource(R.string.extras_habits),
            body = stringResource(R.string.extras_habits_body),
            status = if (habits > 0) pluralStringResource(R.plurals.extras_habits_status, habits, habits) else null,
        ),
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.extras_title)) },
                subtitle = { Text(stringResource(R.string.extras_subtitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        DakilioIcon(DakilioSymbols.ArrowBack, contentDescription = stringResource(R.string.extras_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.gutter,
                    end = Spacing.gutter,
                    top = Spacing.s,
                    bottom = padding.calculateBottomPadding() + Spacing.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                itemsIndexed(extras, key = { _, extra -> extra.key.toString() }) { index, extra ->
                    ExtraItem(extra, index, extras.size, onClick = { onOpen(extra.key) })
                }
            }
        }
    }
}

@Composable
private fun ExtraItem(extra: Extra, index: Int, count: Int, onClick: () -> Unit) {
    val inUse = extra.status != null
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = segmentedItemColors(),
        leadingContent = {
            ShapedIcon(
                extra.symbol,
                polygon = MaterialShapes.Cookie9Sided,
                size = 48.dp,
                containerColor = if (inUse) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (inUse) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        },
        supportingContent = {
            Column {
                Text(extra.body)
                Text(
                    extra.status ?: stringResource(R.string.extras_not_used),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (inUse) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        },
        trailingContent = { DakilioIcon(DakilioSymbols.ChevronRight, contentDescription = null) },
    ) { Text(extra.title) }
}
