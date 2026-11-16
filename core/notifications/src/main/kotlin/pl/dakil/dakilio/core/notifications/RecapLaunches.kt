package pl.dakil.dakilio.core.notifications

import android.content.Context
import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What a tapped recap notification asks for: the recap of [period], written as Insights writes
 * periods (`month:<epoch day>`, `year:2026`).
 */
data class RecapLaunch(val period: String)

/**
 * A tapped recap notification opens MainActivity, which drops its request here; the app shell then
 * opens the recap in Insights and [consume]s it.
 */
@Singleton
class RecapLaunches @Inject constructor() {
    private val _pending = MutableStateFlow<RecapLaunch?>(null)
    val pending: StateFlow<RecapLaunch?> = _pending.asStateFlow()

    /** Returns true if [intent] came from a recap notification. */
    fun deliver(intent: Intent?): Boolean {
        val period = intent?.getStringExtra(EXTRA_PERIOD) ?: return false
        _pending.value = RecapLaunch(period)
        return true
    }

    fun consume(launch: RecapLaunch) {
        _pending.compareAndSet(launch, null)
    }

    internal companion object {
        private const val EXTRA_PERIOD = "pl.dakil.dakilio.extra.RECAP_PERIOD"

        fun intent(context: Context, period: String): Intent? =
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                ?.putExtra(EXTRA_PERIOD, period)
    }
}
