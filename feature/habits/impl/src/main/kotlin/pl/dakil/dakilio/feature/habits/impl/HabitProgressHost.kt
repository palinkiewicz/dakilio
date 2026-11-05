package pl.dakil.dakilio.feature.habits.impl

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.graphics.shapes.Morph
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.designsystem.component.MorphShape
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.HabitBump
import pl.dakil.dakilio.core.model.Habits
import pl.dakil.dakilio.core.model.Tag
import pl.dakil.dakilio.core.ui.color.tagColors
import pl.dakil.dakilio.core.ui.LocalFirstDayOfWeek
import pl.dakil.dakilio.core.ui.rememberFormats

/** Lets the editor's container transform settle before the popup rises. */
private const val ENTER_DELAY_MS = 380L

/** How long the old count shows before it ticks up. */
private const val BUMP_DELAY_MS = 450L

/** Between the rows' ticks, when several habits gained a day. */
private const val ROW_STAGGER_MS = 160L

private const val SHOW_MS = 3_600L
private const val MAX_ROWS = 3

private val iconMorph by lazy { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }

/**
 * The habit progress popup: after an entry is saved, a card rises from the bottom for each habit
 * that gained a day, shows the old weekly count for a moment and ticks it up by one. It leaves on
 * its own after a few seconds; a tap or a swipe down dismisses it sooner.
 *
 * Placed once by the app shell, over every screen.
 *
 * @param bottomPadding what covers the bottom edge (the nav bar); the system bar inset is the floor.
 */
@Composable
fun HabitProgressHost(bottomPadding: Dp, modifier: Modifier = Modifier) {
    val viewModel: HabitProgressViewModel = hiltViewModel()
    val bumps by viewModel.bumps.collectAsStateWithLifecycle()
    var shown by remember { mutableStateOf(emptyList<HabitBump>()) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(bumps) {
        if (bumps.isEmpty()) {
            visible = false
            return@LaunchedEffect
        }
        if (visible) {
            visible = false
            delay(200)
        }
        delay(ENTER_DELAY_MS)
        shown = bumps
        visible = true
        delay(SHOW_MS + ROW_STAGGER_MS * (minOf(bumps.size, MAX_ROWS) - 1))
        viewModel.consume()
    }

    val motion = MaterialTheme.motionScheme
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier
            .padding(bottom = max(bottomPadding, systemBottom) + Spacing.m)
            .padding(horizontal = Spacing.gutter)
            .widthIn(max = 480.dp),
        enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
        exit = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
    ) {
        HabitProgressCard(shown, onDismiss = viewModel::consume)
    }
}

@Composable
private fun HabitProgressCard(bumps: List<HabitBump>, onDismiss: () -> Unit) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val settle = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    Surface(
        onClick = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = offset.value
                alpha = 1f - (offset.value / size.height).coerceIn(0f, 1f) * 0.6f
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (offset.value > size.height * 0.3f) onDismiss() else scope.launch { offset.animateTo(0f, settle) }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, settle) } },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch { offset.snapTo((offset.value + dragAmount).coerceAtLeast(0f)) }
                    },
                )
            }
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            bumps.take(MAX_ROWS).forEachIndexed { index, bump -> BumpRow(bump, index) }
            val more = bumps.size - MAX_ROWS
            if (more > 0) {
                Text(
                    pluralStringResource(R.plurals.habits_progress_more, more, more),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun BumpRow(bump: HabitBump, index: Int) {
    val haptics = rememberHaptics()
    val formats = rememberFormats()
    var bumped by remember(bump) { mutableStateOf(false) }
    LaunchedEffect(bump) {
        delay(BUMP_DELAY_MS + ROW_STAGGER_MS * index)
        bumped = true
        if (index == 0) haptics.confirm()
    }
    val target = bump.habit.daysPerWeek
    val count = if (bumped) bump.after else bump.before
    val goal = bumped && bump.before < target && bump.after >= target
    val week = if (bump.weekStart == Habits.weekStart(LocalDate.now(), LocalFirstDayOfWeek.current)) {
        stringResource(R.string.habits_progress_this_week)
    } else {
        stringResource(R.string.habits_progress_week_of, formats.dayMonth(bump.weekStart))
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        BumpIcon(bump.habit.tag, goal)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                bump.habit.tag.label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AnimatedContent(goal, label = "habitWeek") { reached ->
                Text(
                    if (reached) stringResource(R.string.habits_progress_goal) else week,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
            SegmentBar(target = target, count = count, modifier = Modifier.padding(top = Spacing.xs))
        }
        Counter(count = count, target = target, bumped = bumped)
    }
}

/** The tag's symbol in a circle that morphs into a cookie with a check once the week's goal is reached. */
@Composable
private fun BumpIcon(tag: Tag, goal: Boolean) {
    val colors = tagColors(tag.color)
    val progress by animateFloatAsState(if (goal) 1f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "habitIcon")
    Box(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer { rotationZ = progress * 30f }
            .background(colors?.container ?: MaterialTheme.colorScheme.primary, MorphShape(iconMorph, progress)),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(goal, label = "habitGlyph", modifier = Modifier.graphicsLayer { rotationZ = -progress * 30f }) { reached ->
            DakilioIcon(
                if (reached) DakilioSymbols.Check else symbolForKey(tag.iconKey),
                contentDescription = null,
                filled = true,
                tint = colors?.onContainer ?: MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** One segment per target day; the newly counted one fills with a spring. Over target, all are full. */
@Composable
private fun SegmentBar(target: Int, count: Int, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f)
    val fill = MaterialTheme.colorScheme.primary
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    Row(modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(target) { i ->
            val fraction by animateFloatAsState(if (i < count) 1f else 0f, spec, label = "habitSegment")
            Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(track)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(CircleShape).background(fill))
            }
        }
    }
}

/** "2/5" rolling up to "3/5", with a "+1" that pops in beside it. */
@Composable
private fun Counter(count: Int, target: Int, bumped: Boolean) {
    val motion = MaterialTheme.motionScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        AnimatedVisibility(
            visible = bumped,
            enter = scaleIn(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        ) {
            Text(
                stringResource(R.string.habits_progress_plus_one),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(horizontal = Spacing.s, vertical = Spacing.xxs),
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    (slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec())) togetherWith
                        (slideOutVertically(motion.defaultSpatialSpec()) { -it } + fadeOut(motion.fastEffectsSpec())) using
                        SizeTransform(clip = false)
                },
                label = "habitCount",
            ) { value ->
                Text(value.toString(), style = MaterialTheme.typography.headlineSmallEmphasized)
            }
            Text(
                "/$target",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}
