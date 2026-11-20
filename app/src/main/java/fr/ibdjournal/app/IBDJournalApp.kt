package fr.ibdjournal.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import java.io.File
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import fr.ibdjournal.app.data.AppPrefs
import fr.ibdjournal.app.notification.NotificationHelper
import fr.ibdjournal.app.notification.PendingActions
import fr.ibdjournal.app.notification.ReminderSource
import fr.ibdjournal.app.notification.StockMonitor
import fr.ibdjournal.app.notification.TreatmentReminderScheduler
import fr.ibdjournal.app.security.AutoLock
import fr.ibdjournal.app.widget.WidgetSource
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class IBDJournalApp : Application(), Configuration.Provider {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var appPrefs: AppPrefs

    @Inject
    lateinit var treatmentReminders: TreatmentReminderScheduler

    @Inject
    lateinit var reminderSource: ReminderSource

    @Inject
    lateinit var widgetSource: WidgetSource

    @Inject
    lateinit var pendingActions: PendingActions

    @Inject
    lateinit var stockMonitor: StockMonitor

    @Inject
    lateinit var autoLock: AutoLock

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // WorkManager: only to cancel work from older versions (old reminder,
    // removed Google Drive backup). On-demand initialization (see the manifest).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()

        // Encrypted journal: reminder copy and pending notification answers follow the
        // unlocks; the journal locks itself a minute after leaving the screen.
        reminderSource.start(applicationScope)
        pendingActions.start(applicationScope)
        widgetSource.start(applicationScope)
        stockMonitor.start(applicationScope)
        autoLock.start(applicationScope)

        // Reschedules on every process start: also covers the case where the device was
        // rebooted or the app was force-stopped (alarms are then lost).
        // This also removes the old WorkManager reminder that notified at the old time.
        applicationScope.launch {
            notificationHelper.rescheduleFromPreferences(appPrefs)
        }
        applicationScope.launch {
            runCatching { treatmentReminders.rescheduleAll() }
        }
        applicationScope.launch { removeLegacyCloudBackup() }

        // Default ImageLoader for Coil 3: local + network loading (Open Food Facts).
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components { add(OkHttpNetworkFetcherFactory()) }
                .crossfade(true)
                .build()
        }
    }

    /**
     * Previous versions could back up to Google Drive. All data now stays on the device:
     * we cancel the automatic backup that is still scheduled and clear the locally kept
     * sign-in state (account, last backup).
     */
    private fun removeLegacyCloudBackup() {
        runCatching { WorkManager.getInstance(this).cancelUniqueWork(LEGACY_CLOUD_BACKUP_WORK) }
        File(filesDir, "datastore/$LEGACY_CLOUD_BACKUP_STORE").delete()
    }

    private companion object {
        const val LEGACY_CLOUD_BACKUP_WORK = "cloud_backup_auto"
        const val LEGACY_CLOUD_BACKUP_STORE = "cloud_backup.preferences_pb"
    }
}
