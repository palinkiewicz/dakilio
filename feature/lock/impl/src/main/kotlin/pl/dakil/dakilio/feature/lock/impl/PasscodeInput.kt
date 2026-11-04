package pl.dakil.dakilio.feature.lock.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import pl.dakil.dakilio.core.designsystem.component.MorphShape
import pl.dakil.dakilio.core.designsystem.component.pressScale
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing

private val dotMorph by lazy { Morph(MaterialShapes.Circle, MaterialShapes.Cookie6Sided) }

/**
 * One dot per digit. A typed digit's dot grows and morphs from a circle into a cookie. [slots] is the
 * PIN's length when it's known (unlocking); while choosing one, the row grows as digits are typed.
 */
@Composable
internal fun PinDots(filled: Int, slots: Int, modifier: Modifier = Modifier) {
    val description = pluralStringResource(R.plurals.lock_pin_entered, filled, filled)
    Row(
        modifier = modifier.height(24.dp).semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(maxOf(slots, filled)) { index ->
            val on = index < filled
            val motion = MaterialTheme.motionScheme
            val progress by animateFloatAsState(if (on) 1f else 0f, motion.fastSpatialSpec(), label = "pinDot")
            val color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Box(
                Modifier
                    .size(12.dp + 8.dp * progress)
                    .graphicsLayer { rotationZ = 60f * progress }
                    .background(color, MorphShape(dotMorph, progress)),
            )
        }
    }
}

/**
 * Shakes its content sideways once each time [trigger] changes (a wrong PIN): a single springy
 * settle, never a loop.
 */
@Composable
internal fun Modifier.shakeOn(trigger: Int): Modifier {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        offset.snapTo(0f)
        offset.animateTo(0f, spring(dampingRatio = 0.2f, stiffness = 1_200f), initialVelocity = 1_600f)
    }
    return graphicsLayer { translationX = offset.value }
}

/**
 * The PIN keypad: 1–9, then [leading] (biometrics, or nothing), 0 and backspace. Each key is a large
 * pill that squares off and shrinks a little while pressed. With [shuffle], the digits are in a
 * random order that stays put for as long as [shuffleKey] doesn't change.
 */
@Composable
internal fun PinKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shuffle: Boolean = false,
    shuffleKey: Any? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val digits = remember(shuffle, shuffleKey) {
        val all = "1234567890".toList()
        if (shuffle) all.shuffled() else all
    }
    val haptics = rememberHaptics()
    val dim by animateFloatAsState(if (enabled) 1f else 0.4f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "keypadDim")
    Column(
        modifier = modifier.widthIn(max = 320.dp).fillMaxWidth().graphicsLayer { alpha = dim },
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        for (row in 0 until 4) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                for (col in 0 until 3) {
                    val cell = Modifier.weight(1f)
                    when {
                        row < 3 -> {
                            val digit = digits[row * 3 + col]
                            DigitKey(digit, enabled, cell) {
                                haptics.tick()
                                onDigit(digit)
                            }
                        }
                        col == 0 -> Box(cell.aspectRatio(KEY_RATIO), contentAlignment = Alignment.Center) { leading?.invoke() }
                        col == 1 -> DigitKey(digits[9], enabled, cell) {
                            haptics.tick()
                            onDigit(digits[9])
                        }
                        else -> Box(cell.aspectRatio(KEY_RATIO), contentAlignment = Alignment.Center) {
                            IconButton(
                                onClick = {
                                    haptics.tick()
                                    onBackspace()
                                },
                                enabled = enabled,
                                modifier = Modifier.size(64.dp),
                            ) {
                                DakilioIcon(DakilioSymbols.Backspace, contentDescription = stringResource(R.string.lock_backspace))
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val KEY_RATIO = 1.45f

@Composable
private fun DigitKey(digit: Char, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateIntAsState(if (pressed) 28 else 50, MaterialTheme.motionScheme.fastSpatialSpec(), label = "keyCorner")
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(percent = corner),
        color = if (pressed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (pressed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        interactionSource = interaction,
        modifier = modifier.aspectRatio(KEY_RATIO).pressScale(interaction, pressedScale = 0.92f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(digit.toString(), style = MaterialTheme.typography.headlineMediumEmphasized)
        }
    }
}

/**
 * A password field with a show/hide toggle that submits on the keyboard's Done key. Use [readOnly]
 * while checking: a disabled field would drop focus and close the keyboard.
 */
@Composable
internal fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    autoFocus: Boolean = true,
    focusKey: Any? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    // Focused at first and again whenever [focusKey] changes (after a wrong try), keyboard and all.
    if (autoFocus) LaunchedEffect(focusKey, enabled) { if (enabled) runCatching { focus.requestFocus() } }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                DakilioIcon(
                    if (visible) DakilioSymbols.VisibilityOff else DakilioSymbols.Visibility,
                    contentDescription = stringResource(if (visible) R.string.lock_password_hide else R.string.lock_password_show),
                )
            }
        },
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth().focusRequester(focus),
    )
}
