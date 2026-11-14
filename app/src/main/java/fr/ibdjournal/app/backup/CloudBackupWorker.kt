package fr.ibdjournal.app.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Access to Hilt singletons from classes Android instantiates itself (worker, activity). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CloudBackupEntryPoint {
    fun cloudBackupManager(): CloudBackupManager
}

/** Periodic automatic backup; created by WorkManager's default factory (no hilt-work). */
class CloudBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val manager = EntryPointAccessors
            .fromApplication(applicationContext, CloudBackupEntryPoint::class.java)
            .cloudBackupManager()
        val outcome = manager.runAutomaticBackup()
        // Only transient failures are retried; auth/config errors stay visible in settings.
        return if (outcome.hasRetryableFailure && runAttemptCount < MAX_RETRIES) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}

@Singleton
class CloudBackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Enqueues, updates or cancels the periodic backup to match [settings]. */
    fun apply(settings: AutoBackupSettings, hasTargets: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (settings.frequency == BackupFrequency.OFF || !hasTargets) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val intervalDays = if (settings.frequency == BackupFrequency.WEEKLY) 7L else 1L
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<CloudBackupWorker>(intervalDays, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        // UPDATE keeps the current period's timing when only constraints/interval change.
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private companion object {
        const val WORK_NAME = "cloud_backup_auto"
    }
}
