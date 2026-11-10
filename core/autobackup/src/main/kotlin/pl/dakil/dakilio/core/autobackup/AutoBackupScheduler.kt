package pl.dakil.dakilio.core.autobackup

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import pl.dakil.dakilio.core.model.AutoBackupConfig
import pl.dakil.dakilio.core.model.AutoBackupFrequency

/**
 * Keeps the one automatic backup job in step with the settings.
 *
 * It's a periodic `JobScheduler` job, so the system runs it when it suits the device, batched with
 * other work: never on low battery or low storage, and by default only while charging. There's no
 * alarm, no wakelock and no foreground service, and a missed period doesn't pile up.
 */
@Singleton
class AutoBackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val jobScheduler get() = context.getSystemService(JobScheduler::class.java)

    /**
     * Schedules, changes or cancels the job for [config]. A job that already matches is left alone,
     * since scheduling it again would restart its period.
     */
    fun apply(config: AutoBackupConfig) {
        val pending = jobScheduler.getPendingJob(JOB_ID)
        if (!config.isOn) {
            if (pending != null) jobScheduler.cancel(JOB_ID)
            return
        }
        val wanted = jobInfo(config)
        if (pending != null && pending.sameAs(wanted)) return
        jobScheduler.schedule(wanted)
    }

    private fun jobInfo(config: AutoBackupConfig): JobInfo {
        val (interval, flex) = when (config.frequency) {
            AutoBackupFrequency.Daily -> TimeUnit.HOURS.toMillis(24) to TimeUnit.HOURS.toMillis(8)
            AutoBackupFrequency.Weekly -> TimeUnit.DAYS.toMillis(7) to TimeUnit.DAYS.toMillis(1)
        }
        return JobInfo.Builder(JOB_ID, ComponentName(context, AutoBackupJobService::class.java))
            .setPeriodic(interval, flex)
            .setRequiresCharging(config.onlyWhileCharging)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .setPersisted(true)
            // A failed run (an I/O error) tries again gently, not within seconds.
            .setBackoffCriteria(TimeUnit.MINUTES.toMillis(30), JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()
    }

    private fun JobInfo.sameAs(other: JobInfo): Boolean =
        service == other.service &&
            intervalMillis == other.intervalMillis &&
            flexMillis == other.flexMillis &&
            isRequireCharging == other.isRequireCharging &&
            isRequireBatteryNotLow == other.isRequireBatteryNotLow &&
            isRequireStorageNotLow == other.isRequireStorageNotLow &&
            isPersisted == other.isPersisted &&
            initialBackoffMillis == other.initialBackoffMillis

    internal companion object {
        /** Never change it: a persisted job under the old id would keep running. */
        const val JOB_ID = 4201
    }
}
