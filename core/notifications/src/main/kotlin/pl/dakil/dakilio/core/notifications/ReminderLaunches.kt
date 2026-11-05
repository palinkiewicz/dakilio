package pl.dakil.dakilio.core.notifications

import android.content.Context
import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a tapped reminder asks for: a new entry in [diaryId], or one with the habit's tag [tagId] ticked. */
data class ReminderLaunch(val diaryId: Long? = null, val tagId: Long? = null)

/**
 * A tapped reminder notification opens MainActivity, which drops its request here; the app shell
 * then opens the new-entry wizard pre-filled and [consume]s it.
 */
@Singleton
class ReminderLaunches @Inject constructor() {
    private val _pending = MutableStateFlow<ReminderLaunch?>(null)
    val pending: StateFlow<ReminderLaunch?> = _pending.asStateFlow()

    /** Returns true if [intent] came from a reminder. */
    fun deliver(intent: Intent?): Boolean {
        intent ?: return false
        val launch = when {
            intent.hasExtra(EXTRA_DIARY) -> ReminderLaunch(diaryId = intent.getLongExtra(EXTRA_DIARY, 0))
            intent.hasExtra(EXTRA_TAG) -> ReminderLaunch(tagId = intent.getLongExtra(EXTRA_TAG, 0))
            else -> return false
        }
        _pending.value = launch
        return true
    }

    fun consume(launch: ReminderLaunch) {
        _pending.compareAndSet(launch, null)
    }

    internal companion object {
        private const val EXTRA_DIARY = "pl.dakil.dakilio.extra.REMINDER_DIARY"
        private const val EXTRA_TAG = "pl.dakil.dakilio.extra.REMINDER_TAG"

        fun diaryIntent(context: Context, diaryId: Long): Intent? = launchIntent(context)?.putExtra(EXTRA_DIARY, diaryId)

        fun habitIntent(context: Context, tagId: Long): Intent? = launchIntent(context)?.putExtra(EXTRA_TAG, tagId)

        private fun launchIntent(context: Context): Intent? =
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
