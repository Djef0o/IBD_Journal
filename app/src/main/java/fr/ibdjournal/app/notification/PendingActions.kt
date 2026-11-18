package fr.ibdjournal.app.notification

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.ibdjournal.app.data.AppPrefs
import fr.ibdjournal.app.data.DailyCheckIn
import fr.ibdjournal.app.data.DiaryRepository
import fr.ibdjournal.app.data.ProfileRepository
import fr.ibdjournal.app.data.model.DoseStatus
import fr.ibdjournal.app.data.model.Stool
import fr.ibdjournal.app.security.KeystoreBox
import fr.ibdjournal.app.security.Vault
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Answers given from the notifications (dose taken or missed, evening check-in). Written to
 * the journal right away when it is unlocked; otherwise kept in a file sealed by the Keystore
 * device key, and written at the next unlock.
 */
@Singleton
class PendingActions @Inject constructor(
    @ApplicationContext context: Context,
    private val vault: Vault,
    private val profileRepository: ProfileRepository,
    private val diaryRepository: DiaryRepository,
    private val appPrefs: AppPrefs,
) {
    private val file = File(context.noBackupFilesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    @Serializable
    private data class Dose(val medicationId: Long, val scheduledAt: Long, val status: DoseStatus)

    /** Answers to a day's evening notification, cumulative: the last one replaces the previous. */
    @Serializable
    private data class CheckIn(
        val epochDay: Long,
        val stoolId: Long? = null,
        val bristolType: Int? = null,
        val painLevel: Int? = null,
        val fatigueLevel: Int? = null,
    )

    @Serializable
    private data class Queue(val doses: List<Dose> = emptyList(), val checkIns: List<CheckIn> = emptyList())

    suspend fun logDose(medicationId: Long, scheduledAt: Long, status: DoseStatus) = mutex.withLock {
        if (vault.database.value != null) {
            profileRepository.logDose(medicationId, scheduledAt, status)
        } else {
            val queue = load()
            save(queue.copy(doses = queue.doses + Dose(medicationId, scheduledAt, status)))
        }
    }

    /** [checkIn]: answers already saved in the preferences, to carry over to the stool. */
    suspend fun recordCheckIn(checkIn: DailyCheckIn) = mutex.withLock {
        val pending = CheckIn(checkIn.epochDay, checkIn.stoolId, checkIn.bristolType, checkIn.painLevel, checkIn.fatigueLevel)
        if (vault.database.value != null) {
            apply(pending)
        } else {
            val queue = load()
            save(queue.copy(checkIns = queue.checkIns.filterNot { it.epochDay == pending.epochDay } + pending))
        }
    }

    /** Writes the waiting answers at every unlock. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            vault.database.filterNotNull().collect {
                runCatching { flush() }
            }
        }
    }

    suspend fun clear() = mutex.withLock { withContext(Dispatchers.IO) { file.delete() } }

    private suspend fun flush() = mutex.withLock {
        val queue = load()
        if (queue.doses.isEmpty() && queue.checkIns.isEmpty()) return@withLock
        queue.doses.forEach { profileRepository.logDose(it.medicationId, it.scheduledAt, it.status) }
        queue.checkIns.forEach { apply(it) }
        withContext(Dispatchers.IO) { file.delete() }
    }

    /**
     * One stool per evening reminder: created with the first stool type given, then corrected
     * with the following answers. Pain and fatigue alone wait for the stool type.
     */
    private suspend fun apply(checkIn: CheckIn) {
        val linkedId = checkIn.stoolId ?: appPrefs.dailyCheckIn(checkIn.epochDay).stoolId
        val existing = linkedId?.let { diaryRepository.getStool(it) }
        val stool = existing ?: Stool(
            date = LocalDate.fromEpochDays(checkIn.epochDay),
            bristolType = checkIn.bristolType ?: return,
            painLevel = checkIn.painLevel ?: 0,
            fatigueLevel = checkIn.fatigueLevel ?: 0,
        )
        val updated = stool.copy(
            bristolType = checkIn.bristolType ?: stool.bristolType,
            painLevel = checkIn.painLevel ?: stool.painLevel,
            fatigueLevel = checkIn.fatigueLevel ?: stool.fatigueLevel,
        )
        if (updated == existing) return
        appPrefs.setCheckInStool(checkIn.epochDay, diaryRepository.saveStool(updated))
    }

    private suspend fun load(): Queue = withContext(Dispatchers.IO) {
        if (!file.exists()) Queue()
        else json.decodeFromString(Queue.serializer(), KeystoreBox.open(file.readBytes()).decodeToString())
    }

    private suspend fun save(queue: Queue) = withContext(Dispatchers.IO) {
        val temp = File(file.path + ".tmp")
        temp.writeBytes(KeystoreBox.seal(json.encodeToString(Queue.serializer(), queue).encodeToByteArray()))
        check(temp.renameTo(file)) { "Could not save the pending answers" }
    }

    private companion object {
        const val FILE_NAME = "pending_answers.bin"
    }
}
