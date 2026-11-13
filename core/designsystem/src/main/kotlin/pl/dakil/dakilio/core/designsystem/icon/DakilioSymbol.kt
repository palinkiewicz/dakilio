package pl.dakil.dakilio.core.designsystem.icon

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import pl.dakil.dakilio.core.designsystem.R

/**
 * Anything [DakilioIcon] can draw. Tags and diaries store a symbol's [key], which [symbolForKey]
 * turns back into the symbol, so a key must never change once it has shipped.
 *
 * Each kind of symbol owns its part of the key space: a [VectorSymbol]'s key is its Material Symbol
 * name (`[a-z0-9_]+`), and every other kind uses a `kind:` prefix ([GlyphSymbol]'s is `glyph:`),
 * which no Material Symbol name can contain. A new kind needs its own prefix, a branch in
 * [symbolForKey] and one in [DakilioIcon].
 */
@Immutable
sealed interface DakilioSymbol {
    val key: String
}

/** A bundled Material Symbol (Rounded) with its outlined and filled variants. */
@Immutable
data class VectorSymbol(
    override val key: String,
    @DrawableRes val outlined: Int,
    @DrawableRes val filled: Int,
    val category: SymbolCategory,
) : DakilioSymbol

enum class SymbolCategory(@StringRes val label: Int) {
    Ui(R.string.symbol_category_ui),
    Emotions(R.string.symbol_category_emotions),
    People(R.string.symbol_category_people),
    Activities(R.string.symbol_category_activities),
    Health(R.string.symbol_category_health),
    Nature(R.string.symbol_category_nature),
    Weather(R.string.symbol_category_weather),
    Places(R.string.symbol_category_places),
    Objects(R.string.symbol_category_objects),
}

private val vectorsByKey: Map<String, VectorSymbol> by lazy { DakilioSymbols.all.associateBy { it.key } }

/** Resolves a stored icon key (tags, diaries) to a symbol; unknown keys fall back to a label glyph. */
fun symbolForKey(key: String?): DakilioSymbol {
    if (key == null) return DakilioSymbols.Label
    return vectorsByKey[key] ?: GlyphSymbol.fromKey(key) ?: DakilioSymbols.Label
}

/**
 * Draws [symbol] like a Material icon: 24 dp unless [modifier] sets a size, tinted with [tint].
 * [filled] is the selected look: the filled drawable, or a bolder letter.
 */
@Composable
fun DakilioIcon(
    symbol: DakilioSymbol,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tint: Color = LocalContentColor.current,
) {
    when (symbol) {
        is VectorSymbol -> Icon(
            painter = painterResource(if (filled) symbol.filled else symbol.outlined),
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint,
        )
        is GlyphSymbol -> GlyphIcon(
            text = symbol.text,
            contentDescription = contentDescription,
            modifier = modifier,
            emphasized = filled,
            tint = tint,
        )
    }
}
