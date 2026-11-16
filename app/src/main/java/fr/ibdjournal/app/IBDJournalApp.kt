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
import fr.ibdjournal.app.notification.TreatmentReminderScheduler
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

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // WorkManager : seulement pour annuler les travaux des anciennes versions (ancien rappel,
    // sauvegarde Google Drive retirée). Initialisation à la demande (voir le manifeste).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()

        // Replanifie à chaque démarrage du processus : couvre aussi le cas où l'appareil a
        // été redémarré ou l'application forcée à s'arrêter (les alarmes sont alors perdues).
        // Cela supprime aussi l'ancien rappel WorkManager qui notifiait à l'ancien horaire.
        applicationScope.launch {
            notificationHelper.rescheduleFromPreferences(appPrefs)
        }
        applicationScope.launch {
            treatmentReminders.rescheduleAll()
        }
        applicationScope.launch { removeLegacyCloudBackup() }

        // ImageLoader par défaut pour Coil 3 : chargement local + réseau (Open Food Facts).
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components { add(OkHttpNetworkFetcherFactory()) }
                .crossfade(true)
                .build()
        }
    }

    /**
     * Les versions précédentes pouvaient sauvegarder sur Google Drive. Toutes les données restent
     * désormais sur l'appareil : on annule la sauvegarde automatique encore planifiée et on efface
     * l'état de connexion conservé localement (compte, dernière sauvegarde).
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
