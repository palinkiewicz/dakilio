package pl.dakil.dakilio.feature.search.impl

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.SegmentedShapes
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.PostPhoto
import pl.dakil.dakilio.core.model.Tag
import pl.dakil.dakilio.core.ui.component.EmptyState
import pl.dakil.dakilio.core.ui.component.PhotoViewer
import pl.dakil.dakilio.core.ui.component.PostCard
import pl.dakil.dakilio.core.ui.relativeDayLabel
import pl.dakil.dakilio.core.ui.rememberFormats
import pl.dakil.dakilio.core.ui.sharedContainer
import pl.dakil.dakilio.feature.search.api.SearchKey

@Composable
internal fun SearchScreen(viewModel: SearchViewModel, onBack: () -> Unit, onOpenPost: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formats = rememberFormats()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var viewer by remember { mutableStateOf<Pair<List<PostPhoto>, Int>?>(null) }
    var openFacet by rememberSaveable { mutableStateOf<Facet?>(null) }

    // Back first clears the search, then leaves.
    BackHandler(enabled = state.filters.isActive || viewModel.query.text.isNotEmpty(), onBack = viewModel::clearAll)

    LaunchedEffect(Unit) {
        delay(250) // let the container transform settle before the keyboard slides in
        focus.requestFocus()
    }

    Surface(
        modifier = Modifier.fillMaxSize().sharedContainer(SearchKey.SHARED_ELEMENT_KEY, CircleShape),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .widthIn(max = Spacing.maxContentWidth)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.s)
                    .heightIn(min = 56.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = Spacing.xs)) {
                        DakilioIcon(DakilioSymbols.ArrowBack, contentDescription = stringResource(R.string.search_back))
                    }
                    TextField(
                        state = viewModel.query,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        onKeyboardAction = {
                            keyboard?.hide()
                            viewModel.recordSearch()
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                    AnimatedVisibility(viewModel.query.text.isNotEmpty(), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                        IconButton(onClick = { viewModel.query.clearText() }, modifier = Modifier.padding(end = Spacing.xs)) {
                            DakilioIcon(DakilioSymbols.Close, contentDescription = stringResource(R.string.search_clear))
                        }
                    }
                }
            }

            // While a #tag is being typed, its suggestions take the facet bar's place.
            val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            AnimatedContent(
                targetState = state.tagSuggestions != null,
                transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
                label = "facetsOrSuggestions",
                modifier = Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxWidth(),
            ) { suggesting ->
                val rowModifier = Modifier.fillMaxWidth()
                if (suggesting) {
                    TagSuggestions(state, onPick = viewModel::pickTagSuggestion, modifier = rowModifier)
                } else {
                    FacetBar(
                        state = state,
                        onOpen = {
                            keyboard?.hide()
                            openFacet = it
                        },
                        onClear = viewModel::clearFacet,
                        modifier = rowModifier,
                    )
                }
            }

            Box(Modifier.weight(1f).widthIn(max = Spacing.maxContentWidth).fillMaxWidth()) {
                when {
                    !state.loaded -> Unit
                    !state.hasEntries -> EmptyState(
                        symbol = DakilioSymbols.Search,
                        title = stringResource(R.string.search_empty_title),
                        body = stringResource(R.string.search_empty_body),
                        modifier = Modifier.align(Alignment.Center),
                    )
                    !state.filters.isActive -> SearchIdle(
                        state = state,
                        onRecent = {
                            keyboard?.hide()
                            viewModel.applyRecent(it)
                        },
                        onRemoveRecent = viewModel::removeRecent,
                        onClearRecents = viewModel::clearRecents,
                        onLens = { lens ->
                            keyboard?.hide()
                            if (lens == Lens.Shuffle) viewModel.shuffle()?.let(onOpenPost) else viewModel.applyLens(lens)
                        },
                        onTag = {
                            keyboard?.hide()
                            viewModel.toggleTag(it)
                        },
                    )
                    state.days.isEmpty() -> EmptyState(
                        symbol = DakilioSymbols.TravelExplore,
                        title = stringResource(R.string.search_none_title),
                        body = stringResource(R.string.search_none_body),
                        modifier = Modifier.align(Alignment.Center),
                        action = if (state.filters.facetCount > 0) {
                            { TextButton(onClick = viewModel::clearAll) { Text(stringResource(R.string.search_clear_all)) } }
                        } else {
                            null
                        },
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xxl),
                    ) {
                        item(key = "count") {
                            Row(Modifier.fillMaxWidth().animateItem().padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    pluralStringResource(R.plurals.search_results, state.resultCount, state.resultCount),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = Spacing.xs).weight(1f),
                                )
                                val filterCount = state.filters.facetCount + if (state.filters.words.isNotEmpty()) 1 else 0
                                if (filterCount >= 2) {
                                    TextButton(onClick = viewModel::clearAll) { Text(stringResource(R.string.search_clear_all)) }
                                } else {
                                    Spacer(Modifier.heightIn(min = 40.dp))
                                }
                            }
                        }
                        if (state.refine.isNotEmpty()) {
                            item(key = "refine") {
                                Column(Modifier.animateItem()) {
                                    Text(
                                        stringResource(R.string.search_narrow_down),
                                        style = MaterialTheme.typography.labelLargeEmphasized,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xs),
                                    )
                                    Row(
                                        Modifier
                                            .bleed(Spacing.gutter)
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = Spacing.gutter),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        state.refine.forEach { (tag, count) ->
                                            TagFilterChip(tag, selected = false, trailing = count.toString()) { viewModel.toggleTag(tag.id) }
                                        }
                                    }
                                }
                            }
                        }
                        state.days.forEach { day ->
                            item(key = "day-${day.date}") {
                                Text(
                                    relativeDayLabel(day.date, state.today, formats),
                                    style = MaterialTheme.typography.titleMediumEmphasized,
                                    modifier = Modifier.animateItem().padding(start = Spacing.xs, top = Spacing.l, bottom = Spacing.s),
                                )
                            }
                            itemsIndexed(day.posts, key = { _, post -> post.id }) { index, post ->
                                PostCard(
                                    post = post,
                                    formats = formats,
                                    onClick = {
                                        viewModel.recordSearch()
                                        onOpenPost(post.id)
                                    },
                                    index = index,
                                    count = day.posts.size,
                                    highlight = state.highlight,
                                    onPhotoClick = { viewer = post.photos to it },
                                    modifier = Modifier.animateItem().padding(top = if (index > 0) SegmentedShapes.gap else 0.dp),
                                )
                            }
                        }
                        item(key = "nav-space") { Box(Modifier.navigationBarsPadding()) }
                    }
                }
            }
        }
    }

    openFacet?.let { facet -> FilterSheet(facet, state, viewModel, onDismiss = { openFacet = null }) }
    viewer?.let { (photos, index) -> PhotoViewer(photos, index, onDismiss = { viewer = null }) }
}

/** Tags matching the `#…` being typed; picking one turns the word into a tag filter. */
@Composable
private fun TagSuggestions(state: SearchUiState, onPick: (Tag) -> Unit, modifier: Modifier = Modifier) {
    val suggestions = state.tagSuggestions.orEmpty()
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (suggestions.isEmpty()) {
            Text(
                stringResource(R.string.search_no_tag_matches, hashFragment(state.filters.query).orEmpty()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.heightIn(min = 32.dp).padding(start = Spacing.xs, top = Spacing.s, bottom = Spacing.s),
            )
        }
        suggestions.forEach { tag -> TagFilterChip(tag, selected = false) { onPick(tag) } }
    }
}

/** Widens the element by [horizontal] on both sides, past the list's content padding, so a scrolling row reaches the edges. */
private fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}
