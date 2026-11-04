package pl.dakil.dakilio.feature.lock.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** More → App lock: the lock's settings. */
@Serializable
data object AppLockKey : NavKey
