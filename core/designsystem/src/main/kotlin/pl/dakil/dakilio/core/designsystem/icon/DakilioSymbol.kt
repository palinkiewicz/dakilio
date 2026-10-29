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

/** A bundled Material Symbol (Rounded) with its outlined and filled variants. */
@Immutable
data class DakilioSymbol(
    val key: String,
    @DrawableRes val outlined: Int,
    @DrawableRes val filled: Int,
    val category: SymbolCategory,
)

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

private val symbolsByKey: Map<String, DakilioSymbol> by lazy { DakilioSymbols.all.associateBy { it.key } }

/** Resolves a stored icon key (tags, diaries) to a symbol; unknown keys fall back to a label glyph. */
fun symbolForKey(key: String?): DakilioSymbol = key?.let(symbolsByKey::get) ?: DakilioSymbols.Label

/** Symbols offered in the icon picker, grouped by category in display order. */
val pickerSymbols: List<Pair<SymbolCategory, List<DakilioSymbol>>> by lazy {
    DakilioSymbols.all
        .filter { it.category != SymbolCategory.Ui }
        .groupBy { it.category }
        .toList()
        .sortedBy { it.first.ordinal }
}

@Composable
fun DakilioIcon(
    symbol: DakilioSymbol,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(if (filled) symbol.filled else symbol.outlined),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}
