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
 * Reschedules the reminder after events that clear AlarmManager alarms:
 * device reboot, app update, time or time zone change.
 * Without this receiver, the reminder silently stops until the app is next opened.
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
