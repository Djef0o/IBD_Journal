package fr.ibdjournal.app.notification

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.ibdjournal.app.data.local.AppDatabase
import fr.ibdjournal.app.data.local.toCheckup
import fr.ibdjournal.app.data.local.toExam
import fr.ibdjournal.app.data.local.toMedication
import fr.ibdjournal.app.data.model.Checkup
import fr.ibdjournal.app.data.model.Exam
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.security.EncryptedDatabase
import fr.ibdjournal.app.security.KeystoreBox
import fr.ibdjournal.app.security.Vault
import fr.ibdjournal.app.security.VaultState
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What reminders need: treatments, check-ups and exams. */
@Serializable
data class ReminderData(
    val medications: List<Medication> = emptyList(),
    val checkups: List<Checkup> = emptyList(),
    val exams: List<Exam> = emptyList(),
)

/**
 * Reminders keep ringing while the app is locked and after a reboot: they read a copy of
 * the treatments, check-ups and exams, sealed by the Keystore device key and refreshed
 * from the database every time it changes while unlocked.
 */
@Singleton
class ReminderSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: Vault,
) {
    private val file = File(context.noBackupFilesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    /**
     * Database while unlocked (the copy is refreshed on the way), copy otherwise. Before the
     * first PIN of an update, the database is still in clear and read as before.
     */
    suspend fun current(): ReminderData = withContext(Dispatchers.IO) {
        vault.database.value?.let { return@withContext read(it).also { data -> save(data) } }
        if (vault.state.value == VaultState.NEEDS_SETUP) {
            EncryptedDatabase.openPlain(context)?.let { plain ->
                return@withContext try { read(plain) } finally { plain.close() }
            }
        }
        load()
    }

    /** Keeps the copy up to date for as long as the process lives. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            vault.observe { db ->
                combine(db.medicationDao().observeAll(), db.checkupDao().observeAll(), db.examDao().observeAll()) { m, c, e ->
                    ReminderData(m.map { it.toMedication() }, c.map { it.toCheckup() }, e.map { it.toExam() })
                }
            }.collect { withContext(Dispatchers.IO) { save(it) } }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) { mutex.withLock { file.delete() } }

    private suspend fun read(db: AppDatabase) = ReminderData(
        medications = db.medicationDao().getAll().map { it.toMedication() },
        checkups = db.checkupDao().getAll().map { it.toCheckup() },
        exams = db.examDao().getAll().map { it.toExam() },
    )

    private suspend fun load(): ReminderData = mutex.withLock {
        if (!file.exists()) return ReminderData()
        json.decodeFromString(ReminderData.serializer(), KeystoreBox.open(file.readBytes()).decodeToString())
    }

    private suspend fun save(data: ReminderData) = mutex.withLock {
        val temp = File(file.path + ".tmp")
        temp.writeBytes(KeystoreBox.seal(json.encodeToString(ReminderData.serializer(), data).encodeToByteArray()))
        temp.renameTo(file)
    }

    private companion object {
        const val FILE_NAME = "reminders.bin"
    }
}
