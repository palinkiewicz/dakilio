package pl.dakil.dakilio.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.component.PolygonShape
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol

/** A calm cluster of expressive shapes with a message underneath. Static on purpose: see CLAUDE.md. */
@Composable
fun EmptyState(
    symbol: DakilioSymbol,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val big = remember { PolygonShape(MaterialShapes.Cookie12Sided) }
    val small = remember { PolygonShape(MaterialShapes.Sunny) }
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(132.dp).background(colors.primaryContainer, big),
            )
            Box(
                Modifier
                    .size(52.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp)
                    .background(colors.tertiaryContainer, small),
            )
            DakilioIcon(symbol, contentDescription = null, filled = true, tint = colors.onPrimaryContainer, modifier = Modifier.size(48.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center)
        body?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, textAlign = TextAlign.Center) }
        action?.invoke()
    }
}
