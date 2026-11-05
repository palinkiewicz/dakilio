package pl.dakil.dakilio.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.common.ApplicationScope

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ReminderEntryPoint {
    fun scheduler(): ReminderScheduler

    @ApplicationScope fun scope(): CoroutineScope
}

/** Runs [block] off the main thread, keeping the receiver alive until it's done. */
private fun BroadcastReceiver.work(context: Context, block: suspend (ReminderScheduler) -> Unit) {
    val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)
    val result = goAsync()
    entryPoint.scope().launch {
        try {
            block(entryPoint.scheduler())
        } finally {
            result.finish()
        }
    }
}

/** The reminder alarm went off. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val at = intent.getStringExtra(EXTRA_AT)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        work(context) { it.onAlarm(at) }
    }

    internal companion object {
        /** The local date-time the alarm was set for (ISO). */
        const val EXTRA_AT = "pl.dakil.dakilio.extra.REMINDER_AT"
    }
}

/** A reboot, an app update or a clock change: the alarm is gone or points at the wrong moment. */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED ->
                work(context) { it.reschedule() }
        }
    }
}
