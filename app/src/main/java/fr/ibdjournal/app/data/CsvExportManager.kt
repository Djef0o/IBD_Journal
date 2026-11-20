package fr.ibdjournal.app.data

import fr.ibdjournal.app.data.local.snapshot
import fr.ibdjournal.app.domain.toCsvFiles
import fr.ibdjournal.app.security.Vault
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod

/**
 * CSV export of the journal in a ZIP archive protected by a password (AES-256, the format opened by
 * 7-Zip, Keka and most archivers). The CSV content comes from the shared module; nothing is written
 * unencrypted: the files go straight into the encrypted archive stream.
 */
@Singleton
class CsvExportManager @Inject constructor(private val vault: Vault) {

    /** Writes the archive to [outputStream], which is closed. */
    suspend fun export(outputStream: OutputStream, password: CharArray) = withContext(Dispatchers.IO) {
        val files = vault.requireDatabase().snapshot().toCsvFiles()
        ZipOutputStream(outputStream.buffered(), password).use { zip ->
            for ((name, content) in files) {
                val parameters = ZipParameters().apply {
                    fileNameInZip = name
                    compressionMethod = CompressionMethod.DEFLATE
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                    aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                }
                zip.putNextEntry(parameters)
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }
}
