package pl.dakil.dakilio.core.autobackup

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.common.ApplicationScope
import pl.dakil.dakilio.core.data.repository.AutoBackupRepository
import pl.dakil.dakilio.core.model.AutoBackupFailure
import pl.dakil.dakilio.core.model.AutoBackupResult

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AutoBackupEntryPoint {
    fun repository(): AutoBackupRepository

    @ApplicationScope fun scope(): CoroutineScope
}

/**
 * The automatic backup job. Most runs find nothing changed and end after one database read; the
 * rest write one file. When the system stops it (the charger was unplugged, say), the half-written
 * file is deleted and the system runs it again later.
 */
class AutoBackupJobService : JobService() {
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, AutoBackupEntryPoint::class.java)
        job = entryPoint.scope().launch {
            val result = try {
                entryPoint.repository().runScheduled()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Automatic backup crashed", e)
                AutoBackupResult.Failed(AutoBackupFailure.Other)
            }
            // Only an unexpected error is worth another try soon; a missing folder or password waits
            // for the user, and a full disk for the next period.
            val retry = result is AutoBackupResult.Failed && result.reason == AutoBackupFailure.Other
            jobFinished(params, retry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        job?.cancel()
        job = null
        return true
    }

    private companion object {
        const val TAG = "DakilioAutoBackup"
    }
}
