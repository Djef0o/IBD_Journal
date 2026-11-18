package fr.ibdjournal.app.security

import android.content.Context
import androidx.room.Room
import fr.ibdjournal.app.data.local.AppDatabase
import fr.ibdjournal.app.data.local.Migrations
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Journal database encrypted by SQLCipher with the 256-bit key held by [Vault].
 * Raw key (`x'…'`): SQLCipher uses it as is, without its own password derivation.
 */
internal object EncryptedDatabase {

    init {
        System.loadLibrary("sqlcipher")
    }

    /** Header of a plain SQLite file: an encrypted file starts with random bytes. */
    private val PLAIN_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /**
     * Opens the database, after encrypting it if it is still a plain file (versions up to 1.4).
     * The first access validates the key and runs the Room migrations right away.
     */
    fun open(context: Context, key: ByteArray): AppDatabase {
        val file = context.getDatabasePath(AppDatabase.NAME)
        if (isPlain(file)) encryptInPlace(file, key)
        val database = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .openHelperFactory(SupportOpenHelperFactory(rawKey(key)))
            .addMigrations(*Migrations.ALL)
            .fallbackToDestructiveMigration()
            .build()
        try {
            database.openHelper.writableDatabase
        } catch (error: Throwable) {
            database.close()
            throw error
        }
        return database
    }

    /**
     * Database of a version up to 1.4 not yet encrypted (PIN not chosen yet), so reminders keep
     * working until then; null once encrypted. Close after use.
     */
    fun openPlain(context: Context): AppDatabase? {
        if (!isPlain(context.getDatabasePath(AppDatabase.NAME))) return null
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .openHelperFactory(SupportOpenHelperFactory(ByteArray(0)))
            .addMigrations(*Migrations.ALL)
            .build()
    }

    fun delete(context: Context) {
        val file = context.getDatabasePath(AppDatabase.NAME)
        listOf(file, File("${file.path}-wal"), File("${file.path}-shm"), File("${file.path}-journal"), tempFile(file))
            .forEach { it.delete() }
    }

    private fun isPlain(file: File): Boolean {
        if (!file.exists() || file.length() < PLAIN_HEADER.size) return false
        val header = ByteArray(PLAIN_HEADER.size)
        file.inputStream().use { input -> if (input.read(header) != header.size) return false }
        return header.contentEquals(PLAIN_HEADER)
    }

    /**
     * Copies the plain database into an encrypted file (sqlcipher_export), then replaces it.
     * Run again on the next opening if interrupted: the plain file stays in place until the
     * rename, and the key is already saved by [Vault].
     */
    private fun encryptInPlace(file: File, key: ByteArray) {
        val encrypted = tempFile(file).apply { delete() }
        // CREATE_IF_NECESSARY: the attached encrypted file inherits these flags and does not exist yet.
        val flags = SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY
        val plain = SQLiteDatabase.openDatabase(file.path, "", null, flags, null, null)
        try {
            // sqlcipher_export copies tables and indexes, not the schema version read by Room.
            val version = plain.version
            plain.execSQL("ATTACH DATABASE ? AS encrypted KEY \"${String(rawKey(key), Charsets.US_ASCII)}\"", arrayOf(encrypted.path))
            plain.rawQuery("SELECT sqlcipher_export('encrypted')", null).use { it.moveToFirst() }
            plain.execSQL("PRAGMA encrypted.user_version = $version")
            plain.execSQL("DETACH DATABASE encrypted")
        } finally {
            plain.close()
        }
        File("${file.path}-wal").delete()
        File("${file.path}-shm").delete()
        File("${file.path}-journal").delete()
        check(encrypted.renameTo(file)) { "Could not replace the database with its encrypted copy" }
    }

    private fun tempFile(file: File) = File("${file.path}.encrypting")

    private fun rawKey(key: ByteArray): ByteArray =
        ("x'" + key.joinToString("") { "%02x".format(it) } + "'").toByteArray(Charsets.US_ASCII)
}
