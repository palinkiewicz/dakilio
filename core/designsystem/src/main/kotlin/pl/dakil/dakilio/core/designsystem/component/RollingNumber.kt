package pl.dakil.dakilio.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import pl.dakil.dakilio.core.designsystem.theme.LocalMotionPreferences

/**
 * Text whose digits roll vertically when they change, odometer-style: up when a digit grows, down
 * when it shrinks. Characters are keyed from the end, so the last digits stay put while the number
 * grows a digit. With animated numbers off in Settings → Feel it's plain text that simply changes.
 */
@Composable
fun RollingNumber(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    // Tabular figures, so a rolling digit never changes the number's width.
    val tabular = style.copy(fontFeatureSettings = "tnum")
    if (!LocalMotionPreferences.current.animatedNumbers) {
        Text(text, modifier = modifier, style = tabular, color = color, maxLines = 1)
        return
    }
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Row(modifier) {
        text.forEachIndexed { index, char ->
            key(text.length - index) {
                if (!char.isDigit()) {
                    Text(char.toString(), style = tabular, color = color, maxLines = 1)
                } else {
                    AnimatedContent(
                        targetState = char,
                        transitionSpec = {
                            val up = targetState > initialState
                            (slideInVertically(spatial) { if (up) it else -it } + fadeIn(effects)) togetherWith
                                (slideOutVertically(spatial) { if (up) -it else it } + fadeOut(effects)) using
                                SizeTransform(clip = true)
                        },
                        modifier = Modifier.clipToBounds(),
                        label = "rollingDigit",
                    ) { digit ->
                        Text(digit.toString(), style = tabular, color = color, maxLines = 1)
                    }
                }
            }
        }
    }
}
