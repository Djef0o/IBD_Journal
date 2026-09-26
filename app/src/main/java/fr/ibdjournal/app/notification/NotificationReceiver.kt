package fr.ibdjournal.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.DiaryRepository
import fr.ibdjournal.app.data.model.Stool
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Récepteur déclenché par l'interaction avec la notification de rappel. */
@AndroidEntryPoint
class NotificationReceiver : BroadcastReceiver() {

    @Inject
    lateinit var diaryRepository: DiaryRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_RECORD_STOOL) {
            val bristolType = intent.getIntExtra(EXTRA_BRISTOL_TYPE, 4)
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
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
                } finally {
                    pendingResult.finish()
                }
            }
        } else {
            NotificationHelper.showReminder(context)
        }
    }

    companion object {
        const val ACTION_RECORD_STOOL = "fr.ibdjournal.app.ACTION_RECORD_STOOL"
        const val EXTRA_BRISTOL_TYPE = "extra_bristol_type"
    }
}
