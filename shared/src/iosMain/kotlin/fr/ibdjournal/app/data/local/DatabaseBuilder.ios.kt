package fr.ibdjournal.app.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * iOS app database, in the Documents folder. An iOS install starts from scratch (version 3),
 * hence no migrations here: they only concern older Android versions.
 */
@OptIn(ExperimentalForeignApi::class)
fun createDatabase(): AppDatabase {
    val documents = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    val path = requireNotNull(documents?.path) { "Dossier Documents introuvable" } + "/" + AppDatabase.NAME
    return Room.databaseBuilder<AppDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
