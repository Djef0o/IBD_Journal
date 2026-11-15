package fr.ibdjournal.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import fr.ibdjournal.app.data.AppPrefs
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Replanifie le rappel après les événements qui effacent les alarmes AlarmManager :
 * redémarrage de l'appareil, mise à jour de l'application, changement d'heure ou de fuseau.
 * Sans ce récepteur, le rappel s'arrête silencieusement jusqu'à la prochaine ouverture de l'app.
 */
@AndroidEntryPoint
class ReminderRescheduleReceiver : BroadcastReceiver() {

    @Inject
    lateinit var appPrefs: AppPrefs

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var treatmentReminders: TreatmentReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching { notificationHelper.rescheduleFromPreferences(appPrefs) }
                runCatching { treatmentReminders.rescheduleAll() }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
