package pl.dakil.dakilio.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import pl.dakil.dakilio.core.designsystem.R

// Google Sans Flex (OFL-1.1, bundled subset; see tools/subset-font.sh). Display and headline styles
// use the fully rounded ROND axis for the expressive, friendly "big type"; everything else stays crisp.
// The width axis (wdth 75–100) narrows labels that don't fit (`FittingText`).
private fun googleSansFlex(weight: Int, roundness: Float, width: Float) = Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.width(width),
        FontVariation.Setting("ROND", roundness),
    ),
)

private fun googleSansFlexFamily(roundness: Float, width: Float) =
    FontFamily((300..900 step 100).map { googleSansFlex(it, roundness, width) })

val RoundedDisplayFamily: FontFamily = googleSansFlexFamily(roundness = 100f, width = FULL_WIDTH)
val TextFamily: FontFamily = googleSansFlexFamily(roundness = 0f, width = FULL_WIDTH)

/** The normal width, and the narrowest the bundled font goes. */
const val FULL_WIDTH = 100f
const val NARROWEST_WIDTH = 75f

private val narrowedFamilies = mutableMapOf<Pair<Boolean, Float>, FontFamily>()

/**
 * [family] ([TextFamily] or [RoundedDisplayFamily]) at [width] (75–100) on the font's width axis.
 * Any other family comes back as it is, since it has no such axis.
 */
fun narrowed(family: FontFamily?, width: Float): FontFamily? {
    val rounded = when (family) {
        TextFamily -> false
        RoundedDisplayFamily -> true
        else -> return family
    }
    if (width >= FULL_WIDTH) return family
    val clamped = width.coerceAtLeast(NARROWEST_WIDTH)
    return synchronized(narrowedFamilies) {
        narrowedFamilies.getOrPut(rounded to clamped) { googleSansFlexFamily(if (rounded) 100f else 0f, clamped) }
    }
}

private fun TextStyle.rounded() = copy(fontFamily = RoundedDisplayFamily)
private fun TextStyle.text() = copy(fontFamily = TextFamily)

private val base = Typography()

internal val DakilioTypography = Typography(
    displayLarge = base.displayLarge.rounded(),
    displayMedium = base.displayMedium.rounded(),
    displaySmall = base.displaySmall.rounded(),
    headlineLarge = base.headlineLarge.rounded(),
    headlineMedium = base.headlineMedium.rounded(),
    headlineSmall = base.headlineSmall.rounded(),
    titleLarge = base.titleLarge.text(),
    titleMedium = base.titleMedium.text(),
    titleSmall = base.titleSmall.text(),
    bodyLarge = base.bodyLarge.text(),
    bodyMedium = base.bodyMedium.text(),
    bodySmall = base.bodySmall.text(),
    labelLarge = base.labelLarge.text(),
    labelMedium = base.labelMedium.text(),
    labelSmall = base.labelSmall.text(),
    displayLargeEmphasized = base.displayLargeEmphasized.rounded().copy(fontWeight = FontWeight.SemiBold),
    displayMediumEmphasized = base.displayMediumEmphasized.rounded().copy(fontWeight = FontWeight.SemiBold),
    displaySmallEmphasized = base.displaySmallEmphasized.rounded().copy(fontWeight = FontWeight.SemiBold),
    headlineLargeEmphasized = base.headlineLargeEmphasized.rounded().copy(fontWeight = FontWeight.SemiBold),
    headlineMediumEmphasized = base.headlineMediumEmphasized.rounded().copy(fontWeight = FontWeight.SemiBold),
    headlineSmallEmphasized = base.headlineSmallEmphasized.rounded().copy(fontWeight = FontWeight.SemiBold),
    titleLargeEmphasized = base.titleLargeEmphasized.text(),
    titleMediumEmphasized = base.titleMediumEmphasized.text(),
    titleSmallEmphasized = base.titleSmallEmphasized.text(),
    bodyLargeEmphasized = base.bodyLargeEmphasized.text(),
    bodyMediumEmphasized = base.bodyMediumEmphasized.text(),
    bodySmallEmphasized = base.bodySmallEmphasized.text(),
    labelLargeEmphasized = base.labelLargeEmphasized.text(),
    labelMediumEmphasized = base.labelMediumEmphasized.text(),
    labelSmallEmphasized = base.labelSmallEmphasized.text(),
)
