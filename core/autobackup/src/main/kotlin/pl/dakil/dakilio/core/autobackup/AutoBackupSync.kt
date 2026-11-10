package pl.dakil.dakilio.core.autobackup

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
import pl.dakil.dakilio.core.data.repository.AutoBackupRepository

/**
 * Keeps the job in step with the settings while the app runs. The settings only change in the app,
 * and the job is persisted, so nothing else has to watch them.
 */
@Singleton
class AutoBackupSync @Inject constructor(
    private val repository: AutoBackupRepository,
    private val scheduler: AutoBackupScheduler,
    @ApplicationScope private val scope: CoroutineScope,
    @Dispatcher(DakilioDispatchers.IO) private val io: CoroutineDispatcher,
) {
    private val started = AtomicBoolean(false)

    /** Starts watching, once per process. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch(io) {
            // Only what the job depends on: a new status or password doesn't touch it.
            repository.config
                .map { it.copy(folderName = null, hasPassword = false, enabledAt = null) }
                .distinctUntilChanged()
                .collect { runCatching { scheduler.apply(it) } }
        }
    }
}
