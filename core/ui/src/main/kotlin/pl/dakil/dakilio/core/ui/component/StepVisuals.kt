package pl.dakil.dakilio.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.theme.Spacing

/** The big shaped icon that heads a step of a multi-step flow (the Daylio import, backups). */
@Composable
fun StepHero(symbol: DakilioSymbol, polygon: RoundedPolygon, error: Boolean = false) {
    Box(Modifier.fillMaxWidth().padding(top = Spacing.xl, bottom = Spacing.s), contentAlignment = Alignment.Center) {
        ShapedIcon(
            symbol = symbol,
            polygon = polygon,
            size = 96.dp,
            iconSize = 48.dp,
            containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** One tile of a segmented block of counts; [shape] gives its corner within the block. */
@Composable
fun StatTile(label: String, value: Int, shape: RoundedCornerShape, modifier: Modifier = Modifier) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxHeight()) {
        Column(Modifier.padding(Spacing.l)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineMediumEmphasized)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

/** A one-shot "passport stamp" for a finished flow: the badge lands with a springy scale and a slight twist. */
@Composable
fun StepDoneStamp() {
    val haptics = rememberHaptics()
    val stamp = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        haptics.confirm()
        stamp.animateTo(1f, spec)
    }
    Box(
        Modifier.fillMaxWidth().padding(top = Spacing.xxl, bottom = Spacing.l).graphicsLayer {
            val t = stamp.value
            scaleX = 1.6f - 0.6f * t
            scaleY = 1.6f - 0.6f * t
            rotationZ = -18f * (1f - t)
            alpha = t.coerceIn(0f, 1f)
        },
        contentAlignment = Alignment.Center,
    ) {
        ShapedIcon(
            symbol = DakilioSymbols.CheckCircle,
            polygon = MaterialShapes.SoftBurst,
            size = 120.dp,
            iconSize = 56.dp,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
