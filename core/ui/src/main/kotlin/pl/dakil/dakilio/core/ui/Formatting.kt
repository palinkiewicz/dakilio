package pl.dakil.dakilio.core.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Locale- and 12/24h-aware formatters, built once per configuration. */
@Immutable
class DakilioFormats(private val locale: Locale, is24Hour: Boolean) {
    private val time = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm"), locale)
    private val weekday = DateTimeFormatter.ofPattern("EEEE", locale)
    private val weekdayShort = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEE"), locale)
    private val dayMonth = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMMM"), locale)
    private val dayMonthShort = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale)
    private val dayMonthYear = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMMyyyy"), locale)
    private val monthYear = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMMyyyy"), locale)
    private val fullDate = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
    private val fullDateYear = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMMyyyy"), locale)

    fun time(time: LocalTime): String = this.time.format(time)
    fun weekday(date: LocalDate): String = weekday.format(date).replaceFirstChar { it.titlecase() }
    fun weekdayShort(date: LocalDate): String = weekdayShort.format(date)
    fun dayMonth(date: LocalDate): String = dayMonth.format(date)
    fun dayMonthShort(date: LocalDate): String = dayMonthShort.format(date)
    fun weekdayNarrow(day: DayOfWeek): String = day.getDisplayName(TextStyle.NARROW, locale)
    fun dayMonthYear(date: LocalDate): String = dayMonthYear.format(date)

    fun monthYear(date: LocalDate): String = monthYear.format(date).replaceFirstChar { it.titlecase() }

    fun fullDate(date: LocalDate, today: LocalDate): String =
        (if (date.year == today.year) fullDate else fullDateYear).format(date).replaceFirstChar { it.titlecase() }
}

@Composable
fun rememberFormats(): DakilioFormats {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    // Dates speak the app's language (not a mix of translated UI and device-language dates),
    // while keeping the device's region conventions.
    val language = stringResource(R.string.ui_format_language)
    val locale = Locale.Builder().setLanguage(language).setRegion(configuration.locales[0].country).build()
    val is24 = DateFormat.is24HourFormat(context)
    return remember(locale, is24) { DakilioFormats(locale, is24) }
}

/** Day-section title: "Today", "Yesterday", otherwise the weekday (the date sits beside it). */
@Composable
fun dayTitle(date: LocalDate, today: LocalDate, formats: DakilioFormats = rememberFormats()): String =
    when (ChronoUnit.DAYS.between(date, today)) {
        0L -> stringResource(R.string.ui_today)
        1L -> stringResource(R.string.ui_yesterday)
        else -> formats.weekday(date)
    }

/** "Today", "Yesterday", the weekday within the last week, otherwise the date. */
@Composable
fun relativeDayLabel(date: LocalDate, today: LocalDate, formats: DakilioFormats = rememberFormats()): String {
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        days == 0L -> stringResource(R.string.ui_today)
        days == 1L -> stringResource(R.string.ui_yesterday)
        days in 2..6 -> formats.weekday(date)
        else -> formats.fullDate(date, today)
    }
}
