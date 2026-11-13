package pl.dakil.dakilio.core.ui.component

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import pl.dakil.dakilio.core.designsystem.component.ConnectedToggleRow
import pl.dakil.dakilio.core.designsystem.component.MorphShape
import pl.dakil.dakilio.core.designsystem.component.ToggleOption
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.GlyphSymbol
import pl.dakil.dakilio.core.designsystem.icon.SymbolSection
import pl.dakil.dakilio.core.designsystem.icon.VectorSymbol
import pl.dakil.dakilio.core.designsystem.icon.iconSections
import pl.dakil.dakilio.core.designsystem.icon.letterSections
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.ui.R

private val pickerMorph by lazy { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }

/** What the picker shows at a time. Each page is a list of [SymbolSection]s, so a new kind of symbol is one more page. */
private enum class PickerPage(@StringRes val label: Int) {
    Icons(R.string.ui_icon_picker_icons),
    Letters(R.string.ui_icon_picker_letters),
}

/**
 * Picks a tag's or diary's symbol: the bundled icons by category, or a letter of the alphabet in
 * either case. It opens on the page and section of [selectedKey]; the chosen cell blooms into a
 * cookie shape.
 */
@Composable
fun IconPicker(
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    val letters = letterSections()
    var page by rememberSaveable {
        mutableStateOf(if (symbolForKey(selectedKey) is GlyphSymbol) PickerPage.Letters else PickerPage.Icons)
    }
    val layoutDirection = LocalLayoutDirection.current
    val start = contentPadding.calculateStartPadding(layoutDirection)
    val end = contentPadding.calculateEndPadding(layoutDirection)
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    Column(modifier) {
        ConnectedToggleRow(
            options = PickerPage.entries.map { ToggleOption(it, stringResource(it.label)) },
            selected = page,
            onSelect = { page = it },
            // Lines up with the cells, which are inset by their own padding.
            modifier = Modifier.padding(start = start + CellPadding, end = end + CellPadding, top = contentPadding.calculateTopPadding()),
        )
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "pickerPage",
        ) { shown ->
            SymbolGrid(
                sections = when (shown) {
                    PickerPage.Icons -> iconSections
                    PickerPage.Letters -> letters
                },
                selectedKey = selectedKey,
                onSelect = onSelect,
                contentPadding = PaddingValues(start = start, end = end, bottom = contentPadding.calculateBottomPadding()),
            )
        }
    }
}

@Composable
private fun SymbolGrid(
    sections: List<SymbolSection>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val haptics = rememberHaptics()
    // Only where it opens: picking another symbol must not move the grid.
    val state = rememberLazyGridState(initialFirstVisibleItemIndex = remember { sectionStartIndex(sections, selectedKey) })
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 52.dp),
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        sections.forEach { section ->
            item(key = "header-${section.id}", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                Text(
                    stringResource(section.title),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp),
                )
            }
            items(section.symbols, key = { it.key }, contentType = { "icon" }) { symbol ->
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

/**
 * The grid index of the header of the section that holds [selectedKey], so the picker opens where
 * the current symbol is. Each section is a header followed by its symbols; an unknown key opens
 * at the top.
 */
internal fun sectionStartIndex(sections: List<SymbolSection>, selectedKey: String): Int {
    var index = 0
    for (section in sections) {
        if (section.symbols.any { it.key == selectedKey }) return index
        index += 1 + section.symbols.size
    }
    return 0
}

/** What a screen reader calls a cell: the letter itself, or the icon's name. */
private val DakilioSymbol.spokenName: String
    get() = when (this) {
        is GlyphSymbol -> text
        is VectorSymbol -> key.replace('_', ' ')
    }

private val CellPadding = 4.dp

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
            .padding(CellPadding)
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
            contentDescription = symbol.spokenName,
            filled = selected,
            tint = lerp(colors.onSurfaceVariant, colors.onPrimaryContainer, progress),
            modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = -progress * 20f },
        )
    }
}
