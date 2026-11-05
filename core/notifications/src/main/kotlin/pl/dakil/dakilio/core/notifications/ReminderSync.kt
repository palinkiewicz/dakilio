package pl.dakil.dakilio.core.notifications

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.common.ApplicationScope
import pl.dakil.dakilio.core.data.repository.ReminderRepository

/**
 * Keeps the reminder alarm in step with the data while the app runs: any change to diaries, habits,
 * tags or entries (an editor, an entry saved or deleted, an import or restore) or to the first day of
 * the week plans it again. That covers every write path without hooking each one.
 */
@Singleton
class ReminderSync @Inject constructor(
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    /** Starts watching, once per process. Call only once the database may exist (after the intro). */
    @OptIn(FlowPreview::class)
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            reminders.changes.debounce(DEBOUNCE_MS).collectLatest { scheduler.reschedule() }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 1_000L
    }
}
