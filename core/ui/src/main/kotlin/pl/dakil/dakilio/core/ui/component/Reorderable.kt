package pl.dakil.dakilio.core.ui.component

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.designsystem.component.DakilioHaptics
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics

/*
 * A small drag-to-reorder helper for vertical lists, without a library. Items mark themselves with
 * [reorderableItem]; a [dragHandle] inside an item starts the drag. While held, the item follows the
 * finger and swaps with a neighbour once its leading edge passes the neighbour's middle; the
 * neighbour slides into the freed place. Works for lazy items and for plain Columns alike, since it only reads positions.
 *
 * The caller owns the order: [ReorderState] calls `onMove(from, to)` for each swap (update the list
 * right away) and `onDrop()` when the item is let go (persist then).
 */

/** The scrolling list that dragged items autoscroll near its top and bottom edges. */
@Stable
class ReorderScrollArea internal constructor(internal val list: LazyListState) {
    internal var bounds: Rect? = null
}

@Composable
fun rememberReorderScrollArea(list: LazyListState): ReorderScrollArea = remember(list) { ReorderScrollArea(list) }

/** Put on the scrolling list itself, so dragged items know where its edges are. */
fun Modifier.reorderScrollArea(area: ReorderScrollArea): Modifier =
    onGloballyPositioned { area.bounds = it.rootBounds() }

@Stable
class ReorderState<K : Any> internal constructor(
    private val scope: CoroutineScope,
    private val density: Density,
) {
    internal var keys: List<K> = emptyList()
    internal var onMove: (from: Int, to: Int) -> Unit = { _, _ -> }
    internal var onDrop: () -> Unit = {}
    internal var area: ReorderScrollArea? = null
    internal var clampToItems = false
    internal var spec: FiniteAnimationSpec<Float>? = null
    internal var haptics: DakilioHaptics? = null

    /** The item being held, if any. */
    var draggedKey: K? by mutableStateOf(null)
        private set

    // The item let go and still sliding back into its place.
    private var settlingKey: K? by mutableStateOf(null)
    private var translation by mutableFloatStateOf(0f)
    private val offsets = mutableStateMapOf<K, Float>()
    private val offsetJobs = HashMap<K, Job>()
    private val bounds = HashMap<K, Rect>()
    internal val handleTops = HashMap<K, Float>()
    private var fingerY = 0f
    // Set right after a swap, until the list has been laid out again with the new order.
    private var awaitingLayout = false
    private var framesSinceSwap = 0
    private var settleJob: Job? = null
    private var dragJob: Job? = null

    /** Whether [key] is held, or still settling back after being let go: draw it raised. */
    fun isLifted(key: K): Boolean = draggedKey == key || settlingKey == key

    internal fun offsetOf(key: K): Float = when (key) {
        draggedKey -> clampedTranslation(key)
        settlingKey -> translation
        else -> offsets[key] ?: 0f
    }

    internal fun onPositioned(key: K, rect: Rect) {
        bounds[key] = rect
    }

    internal fun start(key: K, handleY: Float) {
        settleJob?.cancel()
        settlingKey = null
        dragJob?.cancel()
        translation = 0f
        awaitingLayout = false
        fingerY = (handleTops[key] ?: 0f) + handleY
        draggedKey = key
        haptics?.longPress()
        dragJob = scope.launch { whileHeld() }
    }

    internal fun drag(dy: Float) {
        if (draggedKey == null) return
        translation += dy
        fingerY += dy
        checkSwap()
    }

    internal fun end() {
        val key = draggedKey ?: return
        dragJob?.cancel()
        val from = clampedTranslation(key)
        draggedKey = null
        translation = from
        settlingKey = key
        haptics?.confirm()
        onDrop()
        settleJob = scope.launch {
            animate(from, 0f, animationSpec = spec ?: spring()) { value, _ -> translation = value }
            settlingKey = null
        }
    }

    /** Keeps the dragged item from leaving the span of its list (used for tags inside a group card). */
    private fun clampedTranslation(key: K): Float {
        if (!clampToItems) return translation
        val own = bounds[key] ?: return translation
        val first = keys.firstOrNull()?.let { bounds[it] } ?: return translation
        val last = keys.lastOrNull()?.let { bounds[it] } ?: return translation
        val min = first.top - own.top
        return translation.coerceIn(min, maxOf(min, last.bottom - own.bottom))
    }

    private fun checkSwap() {
        val key = draggedKey ?: return
        if (awaitingLayout) return
        val index = keys.indexOf(key)
        if (index < 0) return
        val own = bounds[key] ?: return
        val shift = clampedTranslation(key)
        val next = keys.getOrNull(index + 1)?.let { bounds[it] }
        val previous = keys.getOrNull(index - 1)?.let { bounds[it] }
        when (swapDirection(own.top + shift, own.bottom + shift, previous?.center?.y, next?.center?.y)) {
            1 -> swap(index, index + 1, own, next!!)
            -1 -> swap(index, index - 1, own, previous!!)
        }
    }

    private fun swap(from: Int, to: Int, own: Rect, other: Rect) {
        val otherKey = keys[to]
        val down = to > from
        // The held item's slot moves by this much; shift it back so it stays under the finger.
        translation -= if (down) other.bottom - own.bottom else other.top - own.top
        // The neighbour takes the freed place: start it where it was and let it slide there.
        val otherMoves = if (down) own.top - other.top else own.bottom - other.bottom
        slide(otherKey, -otherMoves)
        awaitingLayout = true
        framesSinceSwap = 0
        haptics?.tick()
        onMove(from, to)
    }

    private fun slide(key: K, start: Float) {
        offsetJobs.remove(key)?.cancel()
        val from = (offsets[key] ?: 0f) + start
        offsets[key] = from
        offsetJobs[key] = scope.launch {
            animate(from, 0f, animationSpec = spec ?: spring()) { value, _ -> offsets[key] = value }
            offsets.remove(key)
        }
    }

    /** Runs only while an item is held: waits out layouts after swaps and autoscrolls near the edges. */
    private suspend fun whileHeld() {
        var last = 0L
        while (true) {
            var seconds = 0f
            withFrameNanos { now ->
                seconds = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
            }
            // Two frame callbacks guarantee that a layout pass with the new order ran in between.
            if (awaitingLayout && ++framesSinceSwap >= 2) awaitingLayout = false
            val step = edgeVelocity() * seconds
            val list = area?.list
            if (step != 0f && list != null) {
                val consumed = list.scrollBy(step)
                // The content moved under a resting finger; keep the item where the finger is.
                translation += consumed
            }
            checkSwap()
        }
    }

    /** Pixels per second to scroll: faster the deeper the finger is in an edge zone. */
    private fun edgeVelocity(): Float {
        if (draggedKey == null) return 0f
        val viewport = area?.bounds ?: return 0f
        val list = area?.list ?: return 0f
        val zone = with(density) { 64.dp.toPx() }
        val max = with(density) { 900.dp.toPx() }
        val intoBottom = (fingerY - (viewport.bottom - zone)) / zone
        val intoTop = ((viewport.top + zone) - fingerY) / zone
        // Items that stay within their list only scroll while the list's end is still out of sight.
        val first = if (clampToItems) keys.firstOrNull()?.let { bounds[it] } else null
        val last = if (clampToItems) keys.lastOrNull()?.let { bounds[it] } else null
        return when {
            intoBottom > 0f && list.canScrollForward && (last == null || last.bottom > viewport.bottom) ->
                max * intoBottom.coerceAtMost(1f)
            intoTop > 0f && list.canScrollBackward && (first == null || first.top < viewport.top) ->
                -max * intoTop.coerceAtMost(1f)
            else -> 0f
        }
    }
}

/**
 * Which way the held item (drawn between [ownTop] and [ownBottom]) swaps: +1 with the next item, −1
 * with the previous one, 0 not at all. It swaps once its leading edge passes the neighbour's middle.
 * Not its center: a clamped item can only go as far as lying exactly over the end item, where the
 * centers are equal, so the swap with the end item could never happen.
 */
internal fun swapDirection(ownTop: Float, ownBottom: Float, previousCenter: Float?, nextCenter: Float?): Int = when {
    nextCenter != null && ownBottom > nextCenter -> 1
    previousCenter != null && ownTop < previousCenter -> -1
    else -> 0
}

/**
 * @param keys the items' keys in their current order.
 * @param onMove move the item at `from` to `to` in the list right away.
 * @param onDrop the held item was let go: persist the order.
 * @param area the list to autoscroll near its edges.
 * @param clampToItems keep the held item within the span of these items (a list inside a card).
 */
@Composable
fun <K : Any> rememberReorderState(
    keys: List<K>,
    onMove: (from: Int, to: Int) -> Unit,
    onDrop: () -> Unit,
    area: ReorderScrollArea? = null,
    clampToItems: Boolean = false,
): ReorderState<K> {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val state = remember(scope, density) { ReorderState<K>(scope, density) }
    // Before layout, so a swap's layout pass already sees the new order.
    SideEffect {
        state.keys = keys
        state.onMove = onMove
        state.onDrop = onDrop
        state.area = area
        state.clampToItems = clampToItems
        state.spec = spec
        state.haptics = haptics
    }
    return state
}

/**
 * Marks a reorderable item. Put it before any other drawing modifiers of the item, so the measured
 * position is the item's place in the list and not where it's currently drawn.
 */
fun <K : Any> Modifier.reorderableItem(state: ReorderState<K>, key: K): Modifier = this
    .onGloballyPositioned { state.onPositioned(key, it.rootBounds()) }
    .zIndex(if (state.isLifted(key)) 1f else 0f)
    .graphicsLayer { translationY = state.offsetOf(key) }

/** Dragging this (no long press needed) moves the item [key] of [state]. */
fun <K : Any> Modifier.dragHandle(state: ReorderState<K>, key: K): Modifier = this
    .onGloballyPositioned { state.handleTops[key] = it.positionInRoot().y }
    .pointerInput(state, key) {
        detectDragGestures(
            onDragStart = { state.start(key, it.y) },
            onDragEnd = { state.end() },
            onDragCancel = { state.end() },
            onDrag = { change, amount ->
                change.consume()
                state.drag(amount.y)
            },
        )
    }

// Not boundsInRoot(): that's clipped by the list, which would shrink items at its edges.
private fun LayoutCoordinates.rootBounds(): Rect = Rect(positionInRoot(), size.toSize())
