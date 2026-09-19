package fr.ibdjournal.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Récepteur déclaré dans le manifest : affiche la notification de rappel. */
class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        NotificationHelper.showReminder(context)
    }
}
