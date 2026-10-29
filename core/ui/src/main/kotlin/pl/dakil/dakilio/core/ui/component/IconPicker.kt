package pl.dakil.dakilio.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import pl.dakil.dakilio.core.designsystem.component.MorphShape
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.pickerSymbols

private val pickerMorph by lazy { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }

/** Categorized grid of bundled symbols; the chosen one blooms into a cookie shape. */
@Composable
fun IconPicker(
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    val haptics = rememberHaptics()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 52.dp),
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        pickerSymbols.forEach { (category, symbols) ->
            item(key = "header-${category.name}", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                Text(
                    stringResource(category.label),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp),
                )
            }
            items(symbols, key = { it.key }, contentType = { "icon" }) { symbol ->
                IconCell(
                    symbol = symbol,
                    selected = symbol.key == selectedKey,
                    onClick = {
                        haptics.tick()
                        onSelect(symbol.key)
                    },
                )
            }
        }
    }
}

@Composable
private fun IconCell(symbol: DakilioSymbol, selected: Boolean, onClick: () -> Unit) {
    val progress by animateFloatAsState(
        if (selected) 1f else 0f,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "iconCell",
    )
    val interaction = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .pressScale(interaction, 0.9f)
            .graphicsLayer { rotationZ = progress * 20f }
            .background(lerp(colors.surfaceContainerHigh, colors.primaryContainer, progress), MorphShape(pickerMorph, progress))
            .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        DakilioIcon(
            symbol = symbol,
            contentDescription = symbol.key.replace('_', ' '),
            filled = selected,
            tint = lerp(colors.onSurfaceVariant, colors.onPrimaryContainer, progress),
            modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = -progress * 20f },
        )
    }
}
