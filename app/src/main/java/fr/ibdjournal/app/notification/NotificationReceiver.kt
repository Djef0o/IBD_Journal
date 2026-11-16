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
import fr.ibdjournal.app.data.ProfileRepository
import fr.ibdjournal.app.data.model.DoseStatus
import fr.ibdjournal.app.domain.today
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

    @Inject
    lateinit var treatmentReminders: TreatmentReminderScheduler

    @Inject
    lateinit var profileRepository: ProfileRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_RECORD_STOOL -> recordStool(context, intent)
                    ACTION_RECORD_LEVEL -> recordLevel(context, intent)
                    NotificationHelper.ACTION_ALARM_REMINDER -> onReminderAlarm(context, intent)
                    TreatmentReminderScheduler.ACTION_TREATMENT_REMINDER -> treatmentReminders.onAlarm(intent)
                    TreatmentReminderScheduler.ACTION_DOSE_TAKEN -> logDose(intent, DoseStatus.TAKEN)
                    TreatmentReminderScheduler.ACTION_DOSE_MISSED -> logDose(intent, DoseStatus.MISSED)
                }
            } catch (_: Throwable) {
                // Ne jamais laisser une erreur (base, préférences…) casser la chaîne du rappel.
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Réponse « Pris » / « Oublié » depuis la notification : on journalise puis on la retire. */
    private suspend fun logDose(intent: Intent, status: DoseStatus) {
        val medicationId = intent.getLongExtra(TreatmentReminderScheduler.EXTRA_MEDICATION_ID, -1L)
        val scheduledAt = intent.getLongExtra(TreatmentReminderScheduler.EXTRA_SCHEDULED_AT, -1L)
        if (medicationId < 0 || scheduledAt < 0) return
        treatmentReminders.cancelMedicationNotification(medicationId)
        profileRepository.logDose(medicationId, scheduledAt, status)
    }

    /**
     * Type de selle choisi dans la notification : une seule selle par rappel, corrigée si
     * l'utilisateur change d'avis, avec la douleur et la fatigue déjà données.
     */
    private suspend fun recordStool(context: Context, intent: Intent) {
        val bristolType = intent.getIntExtra(EXTRA_BRISTOL_TYPE, 4)
        val checkIn = appPrefs.dailyCheckIn(today().toEpochDays())
        val existing = checkIn.stoolId?.let { diaryRepository.getStool(it) }
        val stoolId = diaryRepository.saveStool(
            existing?.copy(bristolType = bristolType)
                ?: Stool(
                    date = today(),
                    bristolType = bristolType,
                    painLevel = checkIn.painLevel ?: 0,
                    fatigueLevel = checkIn.fatigueLevel ?: 0,
                )
        )
        val updated = checkIn.copy(stoolId = stoolId, bristolType = bristolType)
        appPrefs.saveDailyCheckIn(updated)
        notificationHelper.showDailyCheckIn(updated)
        toast(
            context,
            if (bristolType == 0) context.getString(R.string.stool_recorded_type_0)
            else context.getString(R.string.stool_recorded_format, bristolType),
        )
    }

    /** Douleur ou fatigue choisie dans la notification : portée par la selle du rappel, ou en attente d'elle. */
    private suspend fun recordLevel(context: Context, intent: Intent) {
        val scale = intent.getStringExtra(EXTRA_SCALE)
            ?.let { name -> CheckInScale.entries.firstOrNull { it.name == name } } ?: return
        val level = intent.getIntExtra(EXTRA_LEVEL, 0)
        val checkIn = appPrefs.dailyCheckIn(today().toEpochDays())
        checkIn.stoolId?.let { diaryRepository.getStool(it) }?.let { stool ->
            diaryRepository.saveStool(scale.applyTo(stool, level))
        }
        val updated = scale.answered(checkIn, level)
        appPrefs.saveDailyCheckIn(updated)
        notificationHelper.showDailyCheckIn(updated)
        toast(context, context.getString(scale.recordedFormat, level))
    }

    private suspend fun toast(context: Context, message: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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

        notificationHelper.startDailyCheckIn()
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            runCatching { appPrefs.markReminderShown() }
        }
    }

    companion object {
        const val ACTION_RECORD_STOOL = "fr.ibdjournal.app.ACTION_RECORD_STOOL"
        const val EXTRA_BRISTOL_TYPE = "extra_bristol_type"
        const val ACTION_RECORD_LEVEL = "fr.ibdjournal.app.ACTION_RECORD_LEVEL"
        const val EXTRA_SCALE = "extra_scale"
        const val EXTRA_LEVEL = "extra_level"
        private const val DEFAULT_HOUR = 19
        private const val DEFAULT_MINUTE = 0
    }
}
