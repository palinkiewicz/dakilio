package pl.dakil.dakilio.core.ui.mood

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import pl.dakil.dakilio.core.designsystem.component.MorphShape
import pl.dakil.dakilio.core.designsystem.theme.decorativeSpec
import pl.dakil.dakilio.core.designsystem.theme.decorativeTurn
import pl.dakil.dakilio.core.model.Mood

/**
 * A large mood face for a value that changes while it's on screen (a period's average in Insights):
 * when [mood]'s level changes, the shape morphs from the old level's into the new one, the color
 * and the smile follow, and the whole face turns a little. With decorative motion off it just
 * changes.
 */
@Composable
fun AverageMoodFace(mood: Mood, modifier: Modifier = Modifier, size: Dp = 96.dp) {
    var from by remember { mutableStateOf(mood) }
    var to by remember { mutableStateOf(mood) }
    val progress = remember { Animatable(1f) }
    val spec = decorativeSpec(MaterialTheme.motionScheme.slowSpatialSpec<Float>())
    LaunchedEffect(mood) {
        if (mood.level == to.level && mood.color == to.color) {
            to = mood // only the name changed
            return@LaunchedEffect
        }
        from = to
        to = mood
        progress.snapTo(0f)
        progress.animateTo(1f, spec)
    }
    val morph = remember(from.level, to.level) { Morph(moodGlyph(from).active, moodGlyph(to).active) }
    val fromColors = moodColors(from)
    val toColors = moodColors(to)
    val turn = decorativeTurn(if (to.level >= from.level) 45f else -45f)
    val p = progress.value
    val fraction = p.coerceIn(0f, 1f)
    Box(modifier.size(size).semantics { contentDescription = mood.label }, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = (1f - p) * -turn }
                .background(lerp(fromColors.container, toColors.container, fraction), MorphShape(morph, p)),
        )
        Canvas(Modifier.matchParentSize()) {
            val smile = moodGlyph(from).smile + (moodGlyph(to).smile - moodGlyph(from).smile) * p
            drawFace(color = lerp(fromColors.onContainer, toColors.onContainer, fraction), smile = (smile + 0.35f).coerceIn(-1f, 1.2f), excitement = 1f)
        }
    }
}
