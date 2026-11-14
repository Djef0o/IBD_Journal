package fr.ibdjournal.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.AppPrefs
import fr.ibdjournal.app.data.DiaryRepository
import fr.ibdjournal.app.data.model.Stool
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Récepteur déclenché par l'alarme précise ou par l'interaction avec la notification. */
@AndroidEntryPoint
class NotificationReceiver : BroadcastReceiver() {

    @Inject
    lateinit var diaryRepository: DiaryRepository

    @Inject
    lateinit var appPrefs: AppPrefs

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_RECORD_STOOL -> recordStool(context, intent)
                    NotificationHelper.ACTION_ALARM_REMINDER -> onReminderAlarm(context, intent)
                }
            } catch (_: Throwable) {
                // Ne jamais laisser une erreur (base, préférences…) casser la chaîne du rappel.
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun recordStool(context: Context, intent: Intent) {
        val bristolType = intent.getIntExtra(EXTRA_BRISTOL_TYPE, 4)
        diaryRepository.saveStool(
            Stool(
                date = LocalDate.now(),
                bristolType = bristolType,
            )
        )
        withContext(Dispatchers.Main) {
            val message = if (bristolType == 0) {
                context.getString(R.string.stool_recorded_type_0)
            } else {
                context.getString(R.string.stool_recorded_format, bristolType)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(NotificationHelper.NOTIFICATION_ID)
    }

    private suspend fun onReminderAlarm(context: Context, intent: Intent) {
        val settings = runCatching { appPrefs.notificationSettings.first() }.getOrNull()

        if (settings != null && !settings.enabled) {
            notificationHelper.cancelDailyReminder()
            return
        }

        // On replanifie AVANT d'afficher : si l'affichage échoue, le rappel de demain
        // reste programmé. On utilise l'heure enregistrée (source de vérité) et non les
        // extras de l'alarme, qui peuvent dater d'un ancien horaire.
        val hour = settings?.hour
            ?: intent.getIntExtra(NotificationHelper.EXTRA_HOUR, DEFAULT_HOUR)
        val minute = settings?.minute
            ?: intent.getIntExtra(NotificationHelper.EXTRA_MINUTE, DEFAULT_MINUTE)
        notificationHelper.scheduleDailyReminder(hour, minute)

        NotificationHelper.showReminder(context)
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            runCatching { appPrefs.markReminderShown() }
        }
    }

    companion object {
        const val ACTION_RECORD_STOOL = "fr.ibdjournal.app.ACTION_RECORD_STOOL"
        const val EXTRA_BRISTOL_TYPE = "extra_bristol_type"
        private const val DEFAULT_HOUR = 19
        private const val DEFAULT_MINUTE = 0
    }
}
