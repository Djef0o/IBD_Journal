package fr.ibdjournal.app.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.ibdjournal.app.data.AppPrefs
import kotlinx.coroutines.flow.first

/** Déclenché par WorkManager : affiche le rappel si l'option est toujours activée. */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val prefs: AppPrefs,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (prefs.notificationSettings.first().enabled) {
            NotificationHelper.showReminder(applicationContext)
        }
        return Result.success()
    }
}
