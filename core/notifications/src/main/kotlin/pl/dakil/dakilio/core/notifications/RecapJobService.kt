package pl.dakil.dakilio.core.notifications

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.common.ApplicationScope
import pl.dakil.dakilio.core.data.repository.PostRepository
import pl.dakil.dakilio.core.data.repository.SettingsRepository
import pl.dakil.dakilio.core.model.RecapOffer
import pl.dakil.dakilio.core.model.Recaps

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface RecapEntryPoint {
    fun recapSettings(): SettingsRepository

    fun recapPosts(): PostRepository

    fun recapNotifier(): RecapNotifier

    fun recapScheduler(): RecapScheduler

    fun recapClock(): Clock

    @ApplicationScope fun recapScope(): CoroutineScope
}

/** Entries a period needs to earn a recap; the same as Insights' `RECAP_MIN_ENTRIES`. */
private const val MIN_ENTRIES = 5

/**
 * The recap notification job. It checks the date again (a changed clock only reschedules),
 * announces the recaps that have enough entries, then schedules the next run.
 */
class RecapJobService : JobService() {
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, RecapEntryPoint::class.java)
        job = entryPoint.recapScope().launch {
            try {
                val settings = entryPoint.recapSettings().settings.first()
                val enabled = settings.recaps && settings.recapNotifications
                val today = LocalDate.now(entryPoint.recapClock().withZone(ZoneId.systemDefault()))
                // Due on the first; a late run on the second still counts.
                if (enabled && today.dayOfMonth <= 2) {
                    val posts = entryPoint.recapPosts().posts.first()
                    val offers = Recaps.announced(today.withDayOfMonth(1)).filter { offer ->
                        posts.count { post ->
                            when (offer) {
                                is RecapOffer.Month -> YearMonth.from(post.date) == offer.month
                                is RecapOffer.Year -> post.date.year == offer.year
                            }
                        } >= MIN_ENTRIES
                    }
                    entryPoint.recapNotifier().show(offers)
                }
                entryPoint.recapScheduler().apply(enabled)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Recap notification failed", e)
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        job?.cancel()
        job = null
        return true
    }

    private companion object {
        const val TAG = "DakilioRecap"
    }
}
