package fr.ibdjournal.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

private val Context.dataStore by preferencesDataStore(name = "settings")

data class NotificationSettings(
    val enabled: Boolean = true,
    val hour: Int = 19,
    val minute: Int = 0,
    /** Timestamp of the last reminder actually shown (diagnostics). */
    val lastReminderAt: Long? = null,
)

data class CalendarSyncSettings(
    val enabled: Boolean = false,
    /** Chosen calendar; null = the phone's primary calendar. */
    val calendarId: Long? = null,
)

/**
 * Answers to the evening notification for the day [epochDay]: the recorded stool ([stoolId],
 * [bristolType]), pain and fatigue. The notification stays displayed as long as one is missing.
 */
data class DailyCheckIn(
    val epochDay: Long,
    val stoolId: Long? = null,
    val bristolType: Int? = null,
    val fatigueLevel: Int? = null,
    val painLevel: Int? = null,
) {
    val complete: Boolean get() = bristolType != null && painLevel != null && fatigueLevel != null
}

/** [lastBackup]: null if never; [since]: start of the reminder countdown when there is no backup. */
data class BackupSettings(
    val reminderEnabled: Boolean = true,
    val lastBackup: LocalDate? = null,
    val since: LocalDate? = null,
)

data class WeighInSettings(val enabled: Boolean = false, val day: DayOfWeek = DayOfWeek.SUNDAY)

/** Application preferences (Jetpack DataStore). */
@Singleton
class AppPrefs @Inject constructor(@ApplicationContext context: Context) {

    private val dataStore = context.dataStore

    private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    private val KEY_NOTIF_ENABLED = booleanPreferencesKey("notif_enabled")
    private val KEY_NOTIF_HOUR = intPreferencesKey("notif_hour")
    private val KEY_NOTIF_MINUTE = intPreferencesKey("notif_minute")
    private val KEY_LAST_REMINDER = longPreferencesKey("last_reminder_at")
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    private val KEY_THEME_PALETTE = stringPreferencesKey("theme_palette")
    private val KEY_CALENDAR_ENABLED = booleanPreferencesKey("calendar_sync_enabled")
    private val KEY_CALENDAR_ID = longPreferencesKey("calendar_sync_calendar_id")
    private val KEY_CALENDAR_EVENTS = stringSetPreferencesKey("calendar_sync_event_ids")
    private val KEY_CHECKIN_DAY = longPreferencesKey("checkin_day")
    private val KEY_CHECKIN_STOOL_ID = longPreferencesKey("checkin_stool_id")
    private val KEY_CHECKIN_BRISTOL = intPreferencesKey("checkin_bristol")
    private val KEY_CHECKIN_FATIGUE = intPreferencesKey("checkin_fatigue")
    private val KEY_CHECKIN_PAIN = intPreferencesKey("checkin_pain")
    private val KEY_BACKUP_REMINDER = booleanPreferencesKey("backup_reminder_enabled")
    private val KEY_LAST_BACKUP_DAY = longPreferencesKey("last_backup_day")
    private val KEY_BACKUP_SINCE_DAY = longPreferencesKey("backup_since_day")
    private val KEY_WEIGH_IN = booleanPreferencesKey("weigh_in_reminder")
    private val KEY_WEIGH_IN_DAY = intPreferencesKey("weigh_in_day")

    /** true if the user has already seen the welcome screen. */
    val onboardingDone: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_DONE] ?: false
    }

    val notificationSettings: Flow<NotificationSettings> = dataStore.data.map { prefs ->
        NotificationSettings(
            enabled = prefs[KEY_NOTIF_ENABLED] ?: true,
            hour = prefs[KEY_NOTIF_HOUR] ?: 19,
            minute = prefs[KEY_NOTIF_MINUTE] ?: 0,
            lastReminderAt = prefs[KEY_LAST_REMINDER],
        )
    }

    /** Unknown value (old version, removed palette): falls back to the default. */
    val appearance: Flow<Appearance> = dataStore.data.map { prefs ->
        Appearance(
            mode = prefs[KEY_THEME_MODE]
                ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: ThemeMode.SYSTEM,
            palette = prefs[KEY_THEME_PALETTE]
                ?.let { name -> ThemePalette.entries.firstOrNull { it.name == name } }
                ?: ThemePalette.SAGE,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setThemePalette(palette: ThemePalette) {
        dataStore.edit { prefs -> prefs[KEY_THEME_PALETTE] = palette.name }
    }

    suspend fun markOnboardingDone() {
        dataStore.edit { prefs -> prefs[KEY_ONBOARDING_DONE] = true }
    }

    /** Encrypted-backup status and reminder (see `domain/BackupReminders.kt`). */
    val backupSettings: Flow<BackupSettings> = dataStore.data.map { prefs ->
        BackupSettings(
            reminderEnabled = prefs[KEY_BACKUP_REMINDER] ?: true,
            lastBackup = prefs[KEY_LAST_BACKUP_DAY]?.let { LocalDate.fromEpochDays(it) },
            since = prefs[KEY_BACKUP_SINCE_DAY]?.let { LocalDate.fromEpochDays(it) },
        )
    }

    /** The day the encrypted backup was last exported. */
    suspend fun markBackupDone(day: LocalDate) {
        dataStore.edit { prefs -> prefs[KEY_LAST_BACKUP_DAY] = day.toEpochDays() }
    }

    suspend fun setBackupReminderEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_BACKUP_REMINDER] = enabled }
    }

    /** Day the reminder countdown started when no backup exists yet: set once, at first use. */
    suspend fun ensureBackupSince(today: LocalDate): LocalDate {
        var since = today
        dataStore.edit { prefs ->
            val saved = prefs[KEY_BACKUP_SINCE_DAY]
            if (saved == null) prefs[KEY_BACKUP_SINCE_DAY] = today.toEpochDays() else since = LocalDate.fromEpochDays(saved)
        }
        return since
    }

    /** Weekly weigh-in reminder (see `domain/WeekSummary.kt`). */
    val weighIn: Flow<WeighInSettings> = dataStore.data.map { prefs ->
        WeighInSettings(
            enabled = prefs[KEY_WEIGH_IN] ?: false,
            day = DayOfWeek(prefs[KEY_WEIGH_IN_DAY] ?: DayOfWeek.SUNDAY.isoDayNumber),
        )
    }

    suspend fun setWeighIn(enabled: Boolean, day: DayOfWeek) {
        dataStore.edit { prefs ->
            prefs[KEY_WEIGH_IN] = enabled
            prefs[KEY_WEIGH_IN_DAY] = day.isoDayNumber
        }
    }

    /** Records that a reminder was just shown (used for diagnostics in settings). */
    suspend fun markReminderShown() {
        dataStore.edit { prefs -> prefs[KEY_LAST_REMINDER] = System.currentTimeMillis() }
    }

    suspend fun updateNotificationSettings(enabled: Boolean, hour: Int, minute: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_NOTIF_ENABLED] = enabled
            prefs[KEY_NOTIF_HOUR] = hour
            prefs[KEY_NOTIF_MINUTE] = minute
        }
    }

    /**
     * Treatment, refill and check-up reminders handed over to the phone's calendar
     * rather than to the app's notifications.
     */
    val calendarSync: Flow<CalendarSyncSettings> = dataStore.data.map { prefs ->
        CalendarSyncSettings(
            enabled = prefs[KEY_CALENDAR_ENABLED] ?: false,
            calendarId = prefs[KEY_CALENDAR_ID],
        )
    }

    suspend fun setCalendarSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_CALENDAR_ENABLED] = enabled }
    }

    suspend fun setCalendarId(calendarId: Long) {
        dataStore.edit { prefs -> prefs[KEY_CALENDAR_ID] = calendarId }
    }

    /** Events created by the app in the calendar: item key ("dose:12") → event id. */
    val calendarEventIds: Flow<Map<String, Long>> = dataStore.data.map { prefs ->
        prefs[KEY_CALENDAR_EVENTS].orEmpty().mapNotNull { entry ->
            val (key, id) = entry.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            id.toLongOrNull()?.let { key to it }
        }.toMap()
    }

    suspend fun setCalendarEventId(key: String, eventId: Long?) {
        dataStore.edit { prefs ->
            val others = prefs[KEY_CALENDAR_EVENTS].orEmpty().filterNot { it.startsWith("$key=") }
            prefs[KEY_CALENDAR_EVENTS] = if (eventId == null) others.toSet() else (others + "$key=$eventId").toSet()
        }
    }

    /**
     * Today's answers to the evening notification. An answer from another day is ignored:
     * each reminder starts from scratch.
     */
    suspend fun dailyCheckIn(epochDay: Long): DailyCheckIn {
        val prefs = dataStore.data.first()
        if (prefs[KEY_CHECKIN_DAY] != epochDay) return DailyCheckIn(epochDay)
        return DailyCheckIn(
            epochDay = epochDay,
            stoolId = prefs[KEY_CHECKIN_STOOL_ID],
            bristolType = prefs[KEY_CHECKIN_BRISTOL],
            fatigueLevel = prefs[KEY_CHECKIN_FATIGUE],
            painLevel = prefs[KEY_CHECKIN_PAIN],
        )
    }

    suspend fun saveDailyCheckIn(checkIn: DailyCheckIn) {
        dataStore.edit { prefs ->
            prefs[KEY_CHECKIN_DAY] = checkIn.epochDay
            prefs.setOrRemove(KEY_CHECKIN_STOOL_ID, checkIn.stoolId)
            prefs.setOrRemove(KEY_CHECKIN_BRISTOL, checkIn.bristolType)
            prefs.setOrRemove(KEY_CHECKIN_FATIGUE, checkIn.fatigueLevel)
            prefs.setOrRemove(KEY_CHECKIN_PAIN, checkIn.painLevel)
        }
    }

    /** Links the stool recorded for [epochDay]'s answers, unless a later reminder has replaced them. */
    suspend fun setCheckInStool(epochDay: Long, stoolId: Long) {
        dataStore.edit { prefs ->
            if (prefs[KEY_CHECKIN_DAY] == epochDay) prefs[KEY_CHECKIN_STOOL_ID] = stoolId
        }
    }

    /** Journal erased (forgotten PIN): back to the first launch, settings kept. */
    suspend fun resetJournal() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_ONBOARDING_DONE)
            prefs.remove(KEY_CALENDAR_EVENTS)
            listOf(KEY_CHECKIN_DAY, KEY_CHECKIN_STOOL_ID).forEach { prefs.remove(it) }
            listOf(KEY_CHECKIN_BRISTOL, KEY_CHECKIN_FATIGUE, KEY_CHECKIN_PAIN).forEach { prefs.remove(it) }
            listOf(KEY_LAST_BACKUP_DAY, KEY_BACKUP_SINCE_DAY).forEach { prefs.remove(it) }
        }
    }

    private fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }
}

