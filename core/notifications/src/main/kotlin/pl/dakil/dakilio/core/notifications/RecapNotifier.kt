package pl.dakil.dakilio.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import pl.dakil.dakilio.core.data.repository.ReminderChannels
import pl.dakil.dakilio.core.model.RecapOffer
import pl.dakil.dakilio.core.designsystem.R as DesignR

/**
 * Says a recap is ready: one notification, naming the year too on 1 January. It carries no journal
 * content, only the period's name, so it needs no private version.
 */
@Singleton
class RecapNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    /** Creates the channel (or renames it to the app's language), so it's in the system settings early. */
    fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(ReminderChannels.RECAP, context.getString(R.string.recap_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.recap_channel_body) },
        )
    }

    /** Announces [offers] (the year first, then the month); the notification opens the first one. */
    fun show(offers: List<RecapOffer>) {
        if (offers.isEmpty()) return
        ensureChannel()
        if (!canNotify()) return
        val locale = Locale.forLanguageTag(context.getString(R.string.recap_format_language))
        fun name(offer: RecapOffer) = when (offer) {
            is RecapOffer.Year -> offer.year.toString()
            is RecapOffer.Month -> monthName(offer.month, locale)
        }
        val first = offers.first()
        val title = context.getString(R.string.recap_ready_title, name(first))
        val text = if (offers.size > 1) {
            context.getString(R.string.recap_ready_both, name(offers[1]))
        } else {
            context.getString(R.string.recap_ready_text)
        }
        val launch = RecapLaunches.intent(context, encode(first))
        val notification = NotificationCompat.Builder(context, ReminderChannels.RECAP)
            .setSmallIcon(DesignR.drawable.sym_history)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .apply {
                if (launch != null) {
                    setContentIntent(PendingIntent.getActivity(context, REQUEST_CODE, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }
            }
            .build()
        manager.notify(TAG, 0, notification)
    }

    /** The permission is granted, notifications are on for the app, and the recap channel isn't off. */
    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            manager.getNotificationChannel(ReminderChannels.RECAP)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private companion object {
        const val TAG = "recap"
        const val REQUEST_CODE = 4202

        /** "October 2026", standalone and capitalized, in the app's language. */
        fun monthName(month: YearMonth, locale: Locale): String =
            month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } + " " + month.year

        /** The period as Insights reads it back (see `InsightsPeriod.encode`). */
        fun encode(offer: RecapOffer): String = when (offer) {
            is RecapOffer.Year -> "year:${offer.year}"
            is RecapOffer.Month -> "month:${offer.month.atDay(1).toEpochDay()}"
        }
    }
}
