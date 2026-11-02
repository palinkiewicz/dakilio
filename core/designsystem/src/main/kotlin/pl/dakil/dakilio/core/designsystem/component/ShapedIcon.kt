package pl.dakil.dakilio.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol

/**
 * A symbol sitting inside an expressive MaterialShapes polygon (diaries, settings rows, headers).
 * [containerRotation] turns only the polygon; the symbol stays upright.
 */
@Composable
fun ShapedIcon(
    symbol: DakilioSymbol,
    modifier: Modifier = Modifier,
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
    size: Dp = 40.dp,
    iconSize: Dp = size * 0.5f,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    filled: Boolean = true,
    contentDescription: String? = null,
    containerRotation: Float = 0f,
) {
    val shape = remember(polygon) { PolygonShape(polygon) }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().graphicsLayer { rotationZ = containerRotation }.background(containerColor, shape))
        DakilioIcon(
            symbol = symbol,
            contentDescription = contentDescription,
            filled = filled,
            tint = contentColor,
            modifier = Modifier.size(iconSize),
        )
    }
}
