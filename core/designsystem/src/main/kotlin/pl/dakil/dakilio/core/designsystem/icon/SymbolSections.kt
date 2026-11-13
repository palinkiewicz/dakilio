package pl.dakil.dakilio.core.designsystem.icon

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import pl.dakil.dakilio.core.designsystem.R

/** A titled run of symbols the icon picker offers. [id] is unique among all sections. */
@Immutable
data class SymbolSection(val id: String, @StringRes val title: Int, val symbols: List<DakilioSymbol>)

/** The bundled Material Symbols offered for tags and diaries, one section per category in display order. */
val iconSections: List<SymbolSection> by lazy {
    DakilioSymbols.all
        .filter { it.category != SymbolCategory.Ui }
        .groupBy { it.category }
        .toList()
        .sortedBy { (category, _) -> category.ordinal }
        .map { (category, symbols) -> SymbolSection("icons-${category.name}", category.label, symbols) }
}

/**
 * The letters offered for tags and diaries: the alphabet of the app's language, upper case then
 * lower case. Both come from string resources, so a translation brings its own letters (Polish
 * adds ą, ł, ż, …) and decides how each one pairs up, which a case conversion can't for every
 * language.
 */
@Composable
fun letterSections(): List<SymbolSection> {
    val upper = stringResource(R.string.symbol_letters_upper)
    val lower = stringResource(R.string.symbol_letters_lower)
    return remember(upper, lower) {
        listOf(
            SymbolSection("letters-upper", R.string.symbol_category_uppercase, GlyphSymbol.each(upper)),
            SymbolSection("letters-lower", R.string.symbol_category_lowercase, GlyphSymbol.each(lower)),
        )
    }
}
