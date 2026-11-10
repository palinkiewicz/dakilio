package pl.dakil.dakilio.core.data.repository

/**
 * The notification channels reminders post to. core:notifications creates them; the Reminders screen
 * checks whether the user turned one off. Never rename an id: the system keeps the user's channel
 * settings by it.
 */
object ReminderChannels {
    const val DIARY = "diary_reminders"
    const val HABIT = "habit_reminders"
}
