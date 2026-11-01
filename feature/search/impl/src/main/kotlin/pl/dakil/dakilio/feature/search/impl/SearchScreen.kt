package pl.dakil.dakilio.feature.search.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.SegmentedShapes
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.PostPhoto
import pl.dakil.dakilio.core.ui.component.EmptyState
import pl.dakil.dakilio.core.ui.component.PhotoViewer
import pl.dakil.dakilio.core.ui.component.PostCard
import pl.dakil.dakilio.core.ui.hintOrNull
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.core.ui.relativeDayLabel
import pl.dakil.dakilio.core.ui.rememberFormats
import pl.dakil.dakilio.core.ui.sharedContainer
import pl.dakil.dakilio.feature.search.api.SearchKey

@Composable
internal fun SearchScreen(viewModel: SearchViewModel, onBack: () -> Unit, onOpenPost: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formats = rememberFormats()
    val haptics = rememberHaptics()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var viewer by remember { mutableStateOf<Pair<List<PostPhoto>, Int>?>(null) }

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
                        onKeyboardAction = { keyboard?.hide() },
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

            Row(
                Modifier
                    .widthIn(max = Spacing.maxContentWidth)
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                state.moods.asReversed().forEach { mood ->
                    val selected = mood.level in state.moodLevels
                    FilterChip(
                        selected = selected,
                        onClick = {
                            haptics.toggle(!selected)
                            viewModel.toggleMoodLevel(mood.level)
                        },
                        label = { Text(mood.label) },
                        leadingIcon = { MoodBadge(mood, size = 20.dp, active = selected) },
                        shape = CircleShape,
                    )
                }
                FilterChip(
                    selected = state.withPhotosOnly,
                    onClick = {
                        haptics.toggle(!state.withPhotosOnly)
                        viewModel.togglePhotosOnly()
                    },
                    label = { Text(stringResource(R.string.search_with_photos)) },
                    leadingIcon = {
                        DakilioIcon(DakilioSymbols.Image, contentDescription = null, filled = state.withPhotosOnly, modifier = Modifier.size(FilterChipDefaults.IconSize))
                    },
                    shape = CircleShape,
                )
            }

            Box(Modifier.weight(1f).widthIn(max = Spacing.maxContentWidth).fillMaxWidth()) {
                when {
                    !state.active -> EmptyState(
                        symbol = DakilioSymbols.Search,
                        title = stringResource(R.string.search_start_title),
                        body = hintOrNull(stringResource(R.string.search_start_body)),
                        modifier = Modifier.align(Alignment.Center),
                    )
                    state.days.isEmpty() -> EmptyState(
                        symbol = DakilioSymbols.TravelExplore,
                        title = stringResource(R.string.search_none_title),
                        body = stringResource(R.string.search_none_body),
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xxl),
                    ) {
                        item(key = "count") {
                            Text(
                                pluralStringResource(R.plurals.search_results, state.resultCount, state.resultCount),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = Spacing.xs, top = Spacing.m).animateItem(),
                            )
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
                                    onClick = { onOpenPost(post.id) },
                                    index = index,
                                    count = day.posts.size,
                                    highlight = state.query,
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

    viewer?.let { (photos, index) -> PhotoViewer(photos, index, onDismiss = { viewer = null }) }
}
