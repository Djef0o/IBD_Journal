package fr.ibdjournal.app.notification

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.ibdjournal.app.data.local.AppDatabase
import fr.ibdjournal.app.data.local.toCheckup
import fr.ibdjournal.app.data.local.toExam
import fr.ibdjournal.app.data.local.toMedication
import fr.ibdjournal.app.data.local.toEmergencyInfo
import fr.ibdjournal.app.data.local.toProfile
import fr.ibdjournal.app.data.local.toVaccination
import fr.ibdjournal.app.data.model.Checkup
import fr.ibdjournal.app.data.model.EmergencyInfo
import fr.ibdjournal.app.data.model.Exam
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.data.model.Profile
import fr.ibdjournal.app.data.model.Vaccination
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

/**
 * What reminders and the emergency sheet need while the app is locked: treatments, check-ups,
 * exams, vaccinations and, only when the user allows the emergency sheet on the lock screen,
 * [profile] and [emergency].
 */
@Serializable
data class ReminderData(
    val medications: List<Medication> = emptyList(),
    val checkups: List<Checkup> = emptyList(),
    val exams: List<Exam> = emptyList(),
    val profile: Profile? = null,
    val emergency: EmergencyInfo? = null,
    val vaccinations: List<Vaccination> = emptyList(),
) {
    companion object {
        fun of(
            medications: List<Medication>,
            checkups: List<Checkup>,
            exams: List<Exam>,
            profile: Profile?,
            emergency: EmergencyInfo?,
            vaccinations: List<Vaccination> = emptyList(),
        ): ReminderData {
            val shown = emergency?.takeIf { it.showOnLockScreen }
            return ReminderData(medications, checkups, exams, profile.takeIf { shown != null }, shown, vaccinations)
        }
    }
}

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
                combine(
                    combine(
                        db.medicationDao().observeAll(),
                        db.checkupDao().observeAll(),
                        db.examDao().observeAll(),
                        db.profileDao().observe(),
                        db.emergencyDao().observe(),
                    ) { m, c, e, p, em ->
                        ReminderData.of(
                            m.map { it.toMedication() }, c.map { it.toCheckup() }, e.map { it.toExam() },
                            p?.toProfile(), em?.toEmergencyInfo(),
                        )
                    },
                    db.vaccinationDao().observeAll(),
                ) { data, vaccinations -> data.copy(vaccinations = vaccinations.map { it.toVaccination() }) }
            }.collect { withContext(Dispatchers.IO) { save(it) } }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) { mutex.withLock { file.delete() } }

    private suspend fun read(db: AppDatabase) = ReminderData.of(
        medications = db.medicationDao().getAll().map { it.toMedication() },
        checkups = db.checkupDao().getAll().map { it.toCheckup() },
        exams = db.examDao().getAll().map { it.toExam() },
        profile = db.profileDao().get()?.toProfile(),
        emergency = db.emergencyDao().get()?.toEmergencyInfo(),
        vaccinations = db.vaccinationDao().getAll().map { it.toVaccination() },
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
