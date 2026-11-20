package fr.ibdjournal.app.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.ibdjournal.app.data.local.toStool
import fr.ibdjournal.app.domain.DayStats
import fr.ibdjournal.app.domain.dailyStats
import fr.ibdjournal.app.domain.today
import fr.ibdjournal.app.security.KeystoreBox
import fr.ibdjournal.app.security.Vault
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * What the "last 7 days" widget shows: stool counts and blood marks per day (no comments,
 * no details), sealed by the Keystore device key and refreshed from the journal each time it
 * changes while unlocked. A widget on the home screen is readable without the PIN: placing it
 * is the user's choice, and it is limited to these counts.
 */
@Singleton
class WidgetSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: Vault,
) {
    private val file = File(context.noBackupFilesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(DayStats.serializer())
    private val lock = Any()

    /** Saved days, or null if the journal was never opened with this version (or was erased). */
    fun load(): List<DayStats>? = synchronized(lock) {
        if (!file.exists()) return null
        runCatching { json.decodeFromString(serializer, KeystoreBox.open(file.readBytes()).decodeToString()) }.getOrNull()
    }

    /** Keeps the copy up to date for as long as the process lives, and redraws the widgets. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            vault.observe { db ->
                val day = today()
                db.stoolDao().observeBetween(
                    day.plus(-KEPT_DAYS, DateTimeUnit.DAY).toEpochDays(),
                    day.plus(1, DateTimeUnit.DAY).toEpochDays(),
                )
            }.collect { stools ->
                withContext(Dispatchers.IO) {
                    save(stools.map { it.toStool() }.dailyStats())
                    WeekWidgetProvider.refresh(context)
                }
            }
        }
    }

    /** Erased journal: the counts go too, and the widgets show their empty state. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(lock) { file.delete() }
        WeekWidgetProvider.refresh(context)
    }

    private fun save(stats: List<DayStats>) = synchronized(lock) {
        val temp = File(file.path + ".tmp")
        temp.writeBytes(KeystoreBox.seal(json.encodeToString(serializer, stats).encodeToByteArray()))
        check(temp.renameTo(file)) { "Could not save the widget data" }
    }

    private companion object {
        const val FILE_NAME = "week_widget.bin"
        const val KEPT_DAYS = 13
    }
}
