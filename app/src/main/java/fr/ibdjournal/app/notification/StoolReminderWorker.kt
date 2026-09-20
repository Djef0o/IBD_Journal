package fr.ibdjournal.app.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.ibdjournal.app.data.AppPrefs
import kotlinx.coroutines.flow.first

@HiltWorker
class StoolReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val appPrefs: AppPrefs,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): ListenableWorker.Result {
        val settings = appPrefs.notificationSettings.first()
        if (settings.enabled) {
            notificationHelper.showStoolReminder()
        }
        return ListenableWorker.Result.success()
    }
}
