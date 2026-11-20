package fr.ibdjournal.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import fr.ibdjournal.app.LaunchRequests
import fr.ibdjournal.app.MainActivity
import fr.ibdjournal.app.R

/**
 * One button: opens the app on the quick stool entry (after the PIN if the journal is locked).
 * It shows no data.
 */
class QuickStoolWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val open = Intent(context, MainActivity::class.java).apply {
            action = LaunchRequests.ACTION_QUICK_STOOL
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            context, REQUEST_CODE, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val views = RemoteViews(context.packageName, R.layout.widget_quick_stool)
            .apply { setOnClickPendingIntent(R.id.widget_quick_root, pending) }
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    private companion object {
        const val REQUEST_CODE = 1
    }
}
