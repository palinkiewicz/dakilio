package pl.dakil.dakilio.feature.lock.impl

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols

internal val BiometricKind.symbol: DakilioSymbol
    get() = when (this) {
        BiometricKind.Face -> DakilioSymbols.Face
        BiometricKind.Iris -> DakilioSymbols.Visibility
        BiometricKind.Fingerprint, BiometricKind.Generic -> DakilioSymbols.Fingerprint
    }

// Whole phrases per sensor rather than "Unlock with %s": the noun's case changes in Polish.

/** "Use fingerprint", for the keypad's biometric button. */
@Composable
internal fun BiometricKind.useLabel(): String = stringResource(
    pick(R.string.lock_use_fingerprint, R.string.lock_use_face, R.string.lock_use_iris, R.string.lock_use_biometrics),
)

/** "Unlock with fingerprint", for the settings switch. */
@Composable
internal fun BiometricKind.unlockWithLabel(): String = stringResource(
    pick(R.string.lock_unlock_fingerprint, R.string.lock_unlock_face, R.string.lock_unlock_iris, R.string.lock_unlock_biometrics),
)

@StringRes
private fun BiometricKind.pick(@StringRes fingerprint: Int, @StringRes face: Int, @StringRes iris: Int, @StringRes generic: Int): Int =
    when (this) {
        BiometricKind.Fingerprint -> fingerprint
        BiometricKind.Face -> face
        BiometricKind.Iris -> iris
        BiometricKind.Generic -> generic
    }
