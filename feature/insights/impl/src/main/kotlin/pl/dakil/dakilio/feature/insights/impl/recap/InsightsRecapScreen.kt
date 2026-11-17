package pl.dakil.dakilio.feature.insights.impl.recap

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.floor
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.designsystem.component.MorphShape
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.designsystem.theme.decorativeSpec
import pl.dakil.dakilio.core.designsystem.theme.decorativeTurn
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.ui.mood.moodColors
import pl.dakil.dakilio.core.ui.rememberFormats
import pl.dakil.dakilio.feature.insights.impl.R
import pl.dakil.dakilio.feature.insights.impl.logic.Recap
import pl.dakil.dakilio.feature.insights.impl.logic.RecapPage
import pl.dakil.dakilio.feature.insights.impl.logic.hasReveal
import pl.dakil.dakilio.feature.insights.impl.logic.nearestMood
import pl.dakil.dakilio.feature.insights.impl.periodLabel

/** A page's colors: its background and the content on it. */
private data class RecapTone(val background: Color, val content: Color)

/** The backdrop's shapes, one per page in turn, morphing from one to the next. */
private val backdropShapes by lazy {
    listOf(MaterialShapes.Cookie12Sided, MaterialShapes.Sunny, MaterialShapes.Clover8Leaf, MaterialShapes.Flower, MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst)
}

/**
 * A period's recap: full-screen pages to tap through (right side forward, left side back) or swipe.
 * Nothing moves on a timer. A page with a reveal (the top tag, the best weekday) shows it on the
 * first forward tap, then moves on with the next.
 */
@Composable
internal fun InsightsRecapScreen(
    viewModel: InsightsRecapViewModel,
    onClose: () -> Unit,
    onOpenPost: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ready = state as? InsightsRecapUiState.Ready
    if (ready == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) { LoadingIndicator() }
        return
    }
    val formats = rememberFormats()
    var sharing by rememberSaveable { mutableStateOf(false) }
    val recap = ready.recap
    val pages = recap.pages
    val pager = rememberPagerState { pages.size }
    val steps = rememberSaveable(saver = RecapSteps.Saver) { RecapSteps(emptySet(), null) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.drop(1).collect { haptics.tick() } }

    val tones = pageTones(recap, ready.moods)
    val foreground by animateColorAsState(tones[pager.currentPage].content, MaterialTheme.motionScheme.slowEffectsSpec(), label = "recapContent")
    // The background follows the finger: halfway through a swipe it's halfway between two pages' tones.
    val backgroundAt: () -> Color = {
        val position = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, pages.lastIndex.toFloat())
        val from = floor(position).toInt()
        val to = (from + 1).coerceAtMost(pages.lastIndex)
        lerp(tones[from].background, tones[to].background, position - from)
    }
    val periodName = periodLabel(ready.header.period, ready.header.bounds, formats)

    CompositionLocalProvider(LocalContentColor provides foreground) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(backgroundAt()) }
                .pointerInput(pages.size) {
                    detectTapGestures { offset ->
                        scope.launch {
                            val index = pager.currentPage
                            val page = pages[index]
                            when {
                                offset.x < size.width / 3f -> if (index > 0) pager.animateScrollToPage(index - 1)
                                recap.hasReveal(page) && !steps.isRevealed(page) -> {
                                    haptics.confirm()
                                    steps.reveal(page)
                                }
                                index < pages.lastIndex -> pager.animateScrollToPage(index + 1)
                            }
                        }
                    }
                },
        ) {
            Backdrop(pager, foreground, backgroundAt, Modifier.requiredSize(maxWidth * 1.3f).offset(x = maxWidth * 0.45f, y = -maxWidth * 0.35f))
            HorizontalPager(pager, Modifier.fillMaxSize(), key = { pages[it] }) { index ->
                CompositionLocalProvider(LocalRecapPageActive provides (pager.settledPage == index)) {
                    RecapPageContent(
                        page = pages[index],
                        state = ready,
                        steps = steps,
                        periodName = periodName,
                        formats = formats,
                        onOpenPost = onOpenPost,
                        onShare = { sharing = true },
                        onDone = onClose,
                    )
                }
            }
            if (sharing) RecapShareSheet(ready, formats, onDismiss = { sharing = false })
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.gutter, vertical = Spacing.s)) {
                RecapProgress(pager, pages.size, foreground)
                Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOfNotNull(stringResource(R.string.insights_recap), ready.diary?.name).joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { sharing = true }) {
                        DakilioIcon(DakilioSymbols.Share, contentDescription = stringResource(R.string.insights_share))
                    }
                    IconButton(onClick = onClose) {
                        DakilioIcon(DakilioSymbols.Close, contentDescription = stringResource(R.string.insights_recap_close))
                    }
                }
            }
        }
    }
}

/**
 * Each page's tone: the mood pages (how it felt, the days, the brightest day) are tinted with their
 * mood's color, softly enough that faces in the full color still stand out; the others take the
 * theme's containers in turn.
 */
@Composable
private fun pageTones(recap: Recap, moods: List<Mood>): List<RecapTone> {
    val colors = MaterialTheme.colorScheme
    val containers = listOf(
        RecapTone(colors.primaryContainer, colors.onPrimaryContainer),
        RecapTone(colors.tertiaryContainer, colors.onTertiaryContainer),
        RecapTone(colors.secondaryContainer, colors.onSecondaryContainer),
    )
    val average = recap.overview.mood.average?.let { nearestMood(moods, it) }
    val brightest = recap.brightest?.mood
    var plain = 0
    return recap.pages.map { page ->
        val mood = when (page) {
            RecapPage.Mood, RecapPage.Days -> average
            RecapPage.BrightestDay -> brightest
            else -> null
        }
        if (mood != null) {
            RecapTone(lerp(colors.surfaceContainerLow, moodColors(mood).container, 0.3f), colors.onSurface)
        } else {
            containers[plain++ % containers.size]
        }
    }
}

/**
 * One big shape in a slightly stronger tone than the page, morphing into the next page's shape and
 * turning a little with every page (decorative: with it off, the shapes just change).
 */
@Composable
private fun Backdrop(pager: PagerState, foreground: Color, backgroundAt: () -> Color, modifier: Modifier) {
    val page = pager.currentPage
    var from by remember { mutableIntStateOf(0) }
    var to by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(1f) }
    val spec = decorativeSpec(MaterialTheme.motionScheme.slowSpatialSpec<Float>())
    LaunchedEffect(page) {
        val target = page % backdropShapes.size
        if (target == to) return@LaunchedEffect
        from = to
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, spec)
    }
    val morph = remember(from, to) { Morph(backdropShapes[from], backdropShapes[to]) }
    val turn by animateFloatAsState(decorativeTurn(page * 40f), decorativeSpec(MaterialTheme.motionScheme.slowSpatialSpec()), label = "recapTurn")
    Box(
        modifier
            .graphicsLayer { rotationZ = turn }
            .drawBehind {
                val outline = MorphShape(morph, progress.value).createOutline(size, layoutDirection, this)
                drawOutline(outline, lerp(backgroundAt(), foreground, 0.07f))
            },
    )
}

/** A segment per page, filled up to the current one; the current one is a little taller. */
@Composable
private fun RecapProgress(pager: PagerState, count: Int, color: Color) {
    val description = stringResource(R.string.insights_recap_page, pager.currentPage + 1, count)
    Row(
        Modifier.fillMaxWidth().height(6.dp).semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val filled by animateFloatAsState(if (i <= pager.currentPage) 1f else 0.25f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "segment")
            val tall by animateFloatAsState(if (i == pager.currentPage) 6f else 4f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "segmentHeight")
            Box(Modifier.weight(1f).height(tall.dp).background(color.copy(alpha = filled), CircleShape))
        }
    }
}
