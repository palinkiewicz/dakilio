package pl.dakil.dakilio.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.data.repository.ReminderRepository
import pl.dakil.dakilio.core.model.NextAlarm
import pl.dakil.dakilio.core.model.ReminderSnapshot
import pl.dakil.dakilio.core.model.Reminders

/**
 * Keeps the single reminder alarm pointing at the next reminder (see `Reminders` in core:model).
 *
 * There's no service and no WorkManager: one `AlarmManager` alarm wakes [ReminderAlarmReceiver],
 * which shows what's due and sets the next one. The alarm is exact when the user allows it ("Alarms &
 * reminders", Android 12+; always below), otherwise inexact: that needs no permission and still goes
 * off in Doze, but can come up to an hour late.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminders: ReminderRepository,
    private val notifier: ReminderNotifier,
    private val clock: Clock,
    @Dispatcher(DakilioDispatchers.IO) private val io: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)

    /** Plans the next alarm from now, after something the schedule depends on changed. */
    suspend fun reschedule() = withContext(io) {
        mutex.withLock {
            val snapshot = reminders.snapshot() ?: return@withLock
            plan(snapshot, Reminders.next(now(), snapshot))
        }
    }

    /** The alarm set for [at] went off: shows what's still due then and plans the next one. */
    suspend fun onAlarm(at: LocalDateTime?) = withContext(io) {
        mutex.withLock {
            val today = reminders.snapshot() ?: return@withLock
            val now = now()
            // What the alarm's own day knew: an alarm that slipped past midnight is checked against
            // that evening's entries. One that came too late shows nothing (`Reminders.stillDue`).
            val ofAlarm = when {
                at == null || !Reminders.stillDue(at, now) -> null
                at.toLocalDate() == today.today -> today
                else -> reminders.snapshot(at.toLocalDate())
            }
            if (at != null && ofAlarm != null) notifier.show(Reminders.due(at, ofAlarm), ofAlarm)
            // A reminder a little later that's already past (the alarm came late) still comes, at once.
            val missed = if (at != null && ofAlarm != null && at <= now) Reminders.missed(at, now, ofAlarm) else null
            plan(today, missed ?: Reminders.next(now, today))
        }
    }

    /** Sets the alarm for [next] (or none), with [snapshot] read for today. */
    private fun plan(snapshot: ReminderSnapshot, next: NextAlarm?) {
        if (next == null) {
            existingAlarm()?.let(alarmManager::cancel)
        } else {
            setAlarm(next.at)
        }
        setRescheduleReceiverEnabled(!snapshot.isEmpty)
        notifier.dismissStale(snapshot)
    }

    private fun setAlarm(at: LocalDateTime) {
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val operation = alarm(at)
        if (canScheduleExact()) {
            // The permission can be taken away between the check and the call.
            val set = runCatching { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation) }
            if (set.isSuccess) return
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
    }

    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    // The zone is read each time: a time zone change keeps the injected clock's old one.
    private fun now() = LocalDateTime.now(clock.withZone(ZoneId.systemDefault()))

    private fun alarmIntent() = Intent(context, ReminderAlarmReceiver::class.java)

    // One request code: setting a new alarm replaces the old one.
    private fun alarm(at: LocalDateTime) = PendingIntent.getBroadcast(
        context,
        ALARM_REQUEST,
        alarmIntent().putExtra(ReminderAlarmReceiver.EXTRA_AT, at.toString()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun existingAlarm(): PendingIntent? = PendingIntent.getBroadcast(
        context,
        ALARM_REQUEST,
        alarmIntent(),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    /** With no reminder on, a reboot or a clock change shouldn't even start the app. */
    private fun setRescheduleReceiverEnabled(enabled: Boolean) {
        val component = ComponentName(context, ReminderRescheduleReceiver::class.java)
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        if (context.packageManager.getComponentEnabledSetting(component) != state) {
            context.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }

    private companion object {
        const val ALARM_REQUEST = 1
    }
}
