package pl.dakil.dakilio.feature.insights.impl.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.component.PolygonShape
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.SegmentedShapes
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.model.RecapOffer
import pl.dakil.dakilio.core.ui.DakilioFormats
import pl.dakil.dakilio.core.ui.mood.MoodBadge
import pl.dakil.dakilio.feature.insights.impl.OfferedRecap
import pl.dakil.dakilio.feature.insights.impl.R
import pl.dakil.dakilio.feature.insights.impl.logic.nearestMood

/**
 * The recaps ready to play, as one segmented group: the year first, then the month. Each can be
 * played, or put aside for good with "Not now".
 */
@Composable
internal fun RecapOffersCard(
    offers: List<OfferedRecap>,
    moods: List<Mood>,
    formats: DakilioFormats,
    onPlay: (OfferedRecap) -> Unit,
    onDismiss: (OfferedRecap) -> Unit,
    onArchive: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SegmentedShapes.gap)) {
        offers.forEachIndexed { index, item ->
            RecapOfferRow(item, moods, formats, SegmentedShapes.of(index, offers.size), { onPlay(item) }, { onDismiss(item) }, onArchive.takeIf { index == offers.lastIndex })
        }
    }
}

@Composable
private fun RecapOfferRow(
    item: OfferedRecap,
    moods: List<Mood>,
    formats: DakilioFormats,
    shape: androidx.compose.ui.graphics.Shape,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
    onArchive: (() -> Unit)?,
) {
    val haptics = rememberHaptics()
    val colors = MaterialTheme.colorScheme
    val name = when (val offer = item.offer) {
        is RecapOffer.Month -> formats.monthYear(offer.month.atDay(1))
        is RecapOffer.Year -> offer.year.toString()
    }
    val decoration = remember { PolygonShape(MaterialShapes.Cookie12Sided) }
    Surface(shape = shape, color = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.clipToBounds()) {
            // A quiet shape in the corner, a shade off the card.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 48.dp, y = (-56).dp)
                    .size(180.dp)
                    .background(lerp(colors.tertiaryContainer, colors.onTertiaryContainer, 0.06f), decoration),
            )
            Column(Modifier.padding(Spacing.xl)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.insights_offer_kicker), style = MaterialTheme.typography.labelLarge)
                        Text(
                            name,
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            modifier = Modifier.padding(top = Spacing.xxs),
                        )
                    }
                    item.average?.let { nearestMood(moods, it) }?.let { MoodBadge(it, size = 52.dp, active = true) }
                }
                Row(Modifier.padding(top = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            haptics.confirm()
                            onPlay()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.tertiary, contentColor = colors.onTertiary),
                    ) {
                        DakilioIcon(DakilioSymbols.PlayArrow, contentDescription = null, filled = true, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(R.string.insights_offer_play), Modifier.padding(start = Spacing.s))
                    }
                    TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = colors.onTertiaryContainer)) {
                        Text(stringResource(R.string.insights_offer_dismiss))
                    }
                    if (onArchive != null) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                            TextButton(onClick = onArchive, colors = ButtonDefaults.textButtonColors(contentColor = colors.onTertiaryContainer)) {
                                Text(stringResource(R.string.insights_offer_all))
                            }
                        }
                    }
                }
            }
        }
    }
}
