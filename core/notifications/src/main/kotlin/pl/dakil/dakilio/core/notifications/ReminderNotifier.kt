package pl.dakil.dakilio.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import pl.dakil.dakilio.core.model.DueReminders
import pl.dakil.dakilio.core.model.ReminderSnapshot
import pl.dakil.dakilio.core.model.ReminderSubject
import pl.dakil.dakilio.core.model.Reminders
import pl.dakil.dakilio.core.designsystem.R as DesignR

/**
 * Shows reminder notifications: one per diary or habit (however many reminders it has, a later one
 * replaces the earlier notification), with a line picked from that language's
 * own list of texts. On the lock screen they only say "Dakilio" and a generic line, since diary and
 * habit names can be private.
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    fun show(due: DueReminders, snapshot: ReminderSnapshot) {
        if (due.isEmpty || !canNotify()) return
        ensureChannels()
        due.diaries.forEach { show(it, snapshot) }
        due.habits.forEach { show(it, snapshot) }
    }

    /** Takes down notifications that no longer apply: the diary got its entry, the habit met its target. */
    fun dismissStale(snapshot: ReminderSnapshot) {
        val live = snapshot.reminders.map { it.subject }.filter(snapshot::applies)
        val diaries = live.filterIsInstance<ReminderSubject.Diary>().map { it.diaryId.toInt() }.toSet()
        val habits = live.filterIsInstance<ReminderSubject.Habit>().map { it.habitId.toInt() }.toSet()
        manager.activeNotifications.forEach { posted ->
            val stale = when (posted.tag) {
                TAG_DIARY -> posted.id !in diaries
                TAG_HABIT -> posted.id !in habits
                else -> false
            }
            if (stale) manager.cancel(posted.tag, posted.id)
        }
    }

    private fun show(reminder: ReminderSubject.Diary, snapshot: ReminderSnapshot) {
        val texts = context.resources.getStringArray(R.array.reminder_diary_texts)
        post(
            tag = TAG_DIARY,
            id = reminder.diaryId,
            channel = CHANNEL_DIARY,
            icon = DesignR.drawable.sym_edit_note,
            title = context.getString(R.string.reminder_diary_title, reminder.name),
            text = texts[Reminders.textIndex(texts.size, snapshot.today, reminder.diaryId)],
            subText = null,
            publicText = context.getString(R.string.reminder_diary_public),
            launch = ReminderLaunches.diaryIntent(context, reminder.diaryId),
        )
    }

    private fun show(reminder: ReminderSubject.Habit, snapshot: ReminderSnapshot) {
        val texts = context.resources.getStringArray(R.array.reminder_habit_texts)
        post(
            tag = TAG_HABIT,
            id = reminder.habitId,
            channel = CHANNEL_HABIT,
            icon = DesignR.drawable.sym_check_circle,
            title = context.getString(R.string.reminder_habit_title, reminder.label),
            text = texts[Reminders.textIndex(texts.size, snapshot.today, reminder.habitId)],
            subText = context.resources.getQuantityString(
                R.plurals.reminder_habit_progress,
                reminder.daysPerWeek,
                snapshot.weekCount(reminder),
                reminder.daysPerWeek,
            ),
            publicText = context.getString(R.string.reminder_habit_public),
            launch = ReminderLaunches.habitIntent(context, reminder.tagId),
        )
    }

    private fun post(
        tag: String,
        id: Long,
        channel: String,
        icon: Int,
        title: String,
        text: String,
        subText: String?,
        publicText: String,
        launch: Intent?,
    ) {
        val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
        val publicVersion = NotificationCompat.Builder(context, channel)
            .setSmallIcon(icon)
            .setContentTitle(appName)
            .setContentText(publicText)
            .build()
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSubText(subText)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .apply {
                if (launch != null) {
                    val requestCode = (id * 2 + if (tag == TAG_HABIT) 1 else 0).toInt()
                    setContentIntent(
                        PendingIntent.getActivity(context, requestCode, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
                    )
                }
            }
            .build()
        if (canNotify()) manager.notify(tag, id.toInt(), notification)
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannels() {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_DIARY, context.getString(R.string.reminder_channel_diary), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.reminder_channel_diary_body) },
                NotificationChannel(CHANNEL_HABIT, context.getString(R.string.reminder_channel_habit), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.reminder_channel_habit_body) },
            ),
        )
    }

    private companion object {
        const val CHANNEL_DIARY = "diary_reminders"
        const val CHANNEL_HABIT = "habit_reminders"
        const val TAG_DIARY = "diary"
        const val TAG_HABIT = "habit"
    }
}
