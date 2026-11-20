package fr.ibdjournal.app.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.ibdjournal.app.calendar.CalendarSync
import fr.ibdjournal.app.data.AppPrefs
import fr.ibdjournal.app.notification.PendingActions
import fr.ibdjournal.app.notification.ReminderSource
import fr.ibdjournal.app.notification.TreatmentReminderScheduler
import fr.ibdjournal.app.widget.WidgetSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Forgotten PIN: without it the journal cannot be decrypted, so everything is erased
 * (journal, photos, reminders, pending answers, calendar events, generated reports) and the
 * app starts over at the first launch. Appearance, language and reminder time are kept.
 */
@Singleton
class JournalEraser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: Vault,
    private val reminders: TreatmentReminderScheduler,
    private val reminderSource: ReminderSource,
    private val pendingActions: PendingActions,
    private val calendarSync: CalendarSync,
    private val appPrefs: AppPrefs,
    private val widgetSource: WidgetSource,
) {
    suspend fun eraseEverything() {
        // Reminders and calendar events are found through the copy, readable while locked.
        runCatching { reminders.cancelAll() }
        runCatching { calendarSync.removeAll() }
        reminderSource.clear()
        widgetSource.clear()
        pendingActions.clear()
        vault.erase()
        appPrefs.resetJournal()
        withContext(Dispatchers.IO) { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
    }
}
