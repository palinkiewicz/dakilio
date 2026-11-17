package pl.dakil.dakilio.core.notifications

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import pl.dakil.dakilio.core.model.Recaps

/**
 * Keeps one persisted, one-shot `JobScheduler` job waiting for the next recap notification (9:00 on
 * the first of a month). There's no alarm and no boot receiver: the system keeps the job across
 * reboots and runs it within a few hours of the time, batched with other work. Each run schedules
 * the next one.
 */
@Singleton
class RecapScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    private val jobScheduler get() = context.getSystemService(JobScheduler::class.java)

    /** Schedules the job when [enabled], cancels it otherwise. A job already aimed at the right time is left alone. */
    fun apply(enabled: Boolean) {
        val pending = jobScheduler.getPendingJob(JOB_ID)
        if (!enabled) {
            if (pending != null) jobScheduler.cancel(JOB_ID)
            return
        }
        // Read the zone fresh: the injected clock keeps the zone it was made with.
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(clock.withZone(zone))
        val target = Recaps.nextNotification(now)
        val targetMillis = target.atZone(zone).toInstant().toEpochMilli()
        if (pending != null && pending.extras.getLong(EXTRA_TARGET) == targetMillis) return
        val delay = (targetMillis - clock.millis()).coerceAtLeast(0)
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, RecapJobService::class.java))
            .setMinimumLatency(delay)
            .setOverrideDeadline(delay + DEADLINE_SLACK)
            .setPersisted(true)
            .setExtras(PersistableBundle().apply { putLong(EXTRA_TARGET, targetMillis) })
            .build()
        jobScheduler.schedule(job)
    }

    internal companion object {
        /** Never change it: a persisted job under the old id would keep running. Next to the backup job's 4201. */
        const val JOB_ID = 4202
        private const val EXTRA_TARGET = "target"
        private val DEADLINE_SLACK = TimeUnit.HOURS.toMillis(3)
    }
}
