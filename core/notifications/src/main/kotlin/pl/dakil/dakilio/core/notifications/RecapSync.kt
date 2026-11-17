package pl.dakil.dakilio.core.notifications

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.common.ApplicationScope
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.data.repository.SettingsRepository

/**
 * Keeps the recap job in step with Settings → Recaps while the app runs. The job is persisted and
 * reschedules itself, so nothing else has to watch.
 */
@Singleton
class RecapSync @Inject constructor(
    private val settings: SettingsRepository,
    private val scheduler: RecapScheduler,
    private val notifier: RecapNotifier,
    @ApplicationScope private val scope: CoroutineScope,
    @Dispatcher(DakilioDispatchers.IO) private val io: CoroutineDispatcher,
) {
    private val started = AtomicBoolean(false)

    /** Starts watching, once per process. Never during the intro: the database mustn't be created early. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch(io) {
            settings.settings
                .map { it.recaps && it.recapNotifications }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) runCatching { notifier.ensureChannel() }
                    runCatching { scheduler.apply(enabled) }
                }
        }
    }
}
