package pl.dakil.dakilio.core.ui.mood

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.component.MorphShape
import pl.dakil.dakilio.core.designsystem.component.PolygonShape
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.model.Mood

/**
 * A mood's face: its expressive shape filled with the mood color and a simple drawn face. When
 * [active], the shape springs into its livelier variant, turns a little, and the face smiles wider.
 */
@Composable
fun MoodBadge(
    mood: Mood,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    active: Boolean = false,
    /** 0..1 how muted the badge should look (unselected options while another is picked). */
    dimmed: Float = 0f,
) {
    val glyph = remember(mood.iconKey, mood.value) { moodGlyph(mood) }
    val colors = moodColors(mood)
    val motion = MaterialTheme.motionScheme
    val progress by animateFloatAsState(if (active) 1f else 0f, motion.slowSpatialSpec(), label = "moodMorph")
    val rotation by animateFloatAsState(if (active) 30f else 0f, motion.slowSpatialSpec(), label = "moodSpin")
    val dim by animateFloatAsState(dimmed, motion.defaultEffectsSpec(), label = "moodDim")
    val container = lerpColor(colors.container, MaterialTheme.colorScheme.surfaceContainerHighest, dim * 0.7f)
    val face = lerpColor(colors.onContainer, MaterialTheme.colorScheme.onSurfaceVariant, dim * 0.7f)

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = mood.label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = rotation }
                .background(container, MorphShape(glyph.morph, progress)),
        )
        Canvas(Modifier.matchParentSize()) {
            drawFace(color = face, smile = (glyph.smile + progress * 0.35f).coerceIn(-1f, 1.2f), excitement = progress)
        }
    }
}

/** Badge for entries without a mood: a calm neutral cookie with a note glyph. */
@Composable
fun NoMoodBadge(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val shape = remember { PolygonShape(MaterialShapes.Cookie4Sided) }
    Box(
        modifier = modifier.size(size).background(MaterialTheme.colorScheme.surfaceContainerHighest, shape),
        contentAlignment = Alignment.Center,
    ) {
        DakilioIcon(
            DakilioSymbols.EditNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

private fun DrawScope.drawFace(color: Color, smile: Float, excitement: Float) {
    val w = size.width
    val h = size.height
    val eyeY = h * 0.42f
    val eyeRadius = w * (0.055f + 0.01f * excitement)
    drawCircle(color, eyeRadius, Offset(w * 0.37f, eyeY))
    drawCircle(color, eyeRadius, Offset(w * 0.63f, eyeY))

    val mouthY = h * 0.62f
    val halfWidth = w * (0.15f + 0.03f * excitement)
    val mouth = Path().apply {
        moveTo(w / 2 - halfWidth, mouthY - smile * h * 0.02f)
        quadraticTo(w / 2, mouthY + smile * h * 0.15f, w / 2 + halfWidth, mouthY - smile * h * 0.02f)
    }
    drawPath(mouth, color, style = Stroke(width = w * 0.068f, cap = StrokeCap.Round))
}

private fun lerpColor(from: Color, to: Color, fraction: Float): Color =
    androidx.compose.ui.graphics.lerp(from, to, fraction.coerceIn(0f, 1f))
