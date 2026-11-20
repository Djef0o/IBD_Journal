package fr.ibdjournal.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import dagger.hilt.android.AndroidEntryPoint
import fr.ibdjournal.app.MainActivity
import fr.ibdjournal.app.R
import fr.ibdjournal.app.domain.today
import fr.ibdjournal.app.domain.weekView
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.datetime.toJavaLocalDate

/** "Last 7 days": stool count per day, blood marks, total and average Bristol type. */
@AndroidEntryPoint
class WeekWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var source: WidgetSource

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = render(context)
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    private fun render(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_week)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.week_root, open)

        val stats = source.load()
        views.setViewVisibility(R.id.week_empty, if (stats == null) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.week_columns, if (stats == null) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.week_summary, if (stats == null) View.GONE else View.VISIBLE)
        if (stats == null) return views

        val week = weekView(stats, today())
        val locale = Locale.getDefault()
        week.days.forEachIndexed { i, day ->
            views.setTextViewText(LABELS[i], day.date.toJavaLocalDate().dayOfWeek.getDisplayName(TextStyle.NARROW, locale))
            views.setTextViewText(COUNTS[i], if (day.stools == 0) "\u2013" else day.stools.toString())
            views.setTextViewText(BLOODS[i], if (day.blood) "\u25CF" else "")
        }
        val stoolsText = "${week.stools} " + context.resources.getQuantityString(R.plurals.stools_label, week.stools)
        views.setTextViewText(
            R.id.week_summary,
            week.averageBristol?.let { average ->
                context.getString(R.string.widget_week_summary, stoolsText, String.format(locale, "%.1f", average))
            } ?: context.getString(R.string.widget_week_summary_none),
        )
        return views
    }

    companion object {
        private val LABELS = intArrayOf(
            R.id.week_label_0, R.id.week_label_1, R.id.week_label_2, R.id.week_label_3,
            R.id.week_label_4, R.id.week_label_5, R.id.week_label_6,
        )
        private val COUNTS = intArrayOf(
            R.id.week_count_0, R.id.week_count_1, R.id.week_count_2, R.id.week_count_3,
            R.id.week_count_4, R.id.week_count_5, R.id.week_count_6,
        )
        private val BLOODS = intArrayOf(
            R.id.week_blood_0, R.id.week_blood_1, R.id.week_blood_2, R.id.week_blood_3,
            R.id.week_blood_4, R.id.week_blood_5, R.id.week_blood_6,
        )

        /** Asks every placed "last 7 days" widget to redraw. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, WeekWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setComponent(component)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }
    }
}
