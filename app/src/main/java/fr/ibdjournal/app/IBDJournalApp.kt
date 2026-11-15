package fr.ibdjournal.app

import android.app.Application
import androidx.work.Configuration
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

    // WorkManager : sauvegarde cloud automatique (CloudBackupWorker, fabrique par défaut,
    // dépendances via un EntryPoint Hilt) et annulation de l'ancien rappel. Initialisation
    // à la demande (voir le manifeste) plutôt qu'au démarrage de chaque processus.
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

        // ImageLoader par défaut pour Coil 3 : chargement local + réseau (Open Food Facts).
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components { add(OkHttpNetworkFetcherFactory()) }
                .crossfade(true)
                .build()
        }
    }
}
