package pl.dakil.dakilio.core.ui

import androidx.compose.runtime.compositionLocalOf
import java.time.DayOfWeek

/**
 * The first day of the week, provided at the app root from `UserSettings.weekStart` (the region
 * default already resolved). Rows of weekdays and week views start here.
 */
val LocalFirstDayOfWeek = compositionLocalOf { DayOfWeek.MONDAY }

/** The seven days of the week, starting at [first]. */
fun weekdaysFrom(first: DayOfWeek): List<DayOfWeek> = List(7) { first.plus(it.toLong()) }
