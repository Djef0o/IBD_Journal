package fr.ibdjournal.app.backup

/** One cloud storage the backup JSON can be written to. */
interface CloudBackupTarget {
    val provider: CloudProvider

    /** False when this build lacks the provider's OAuth client id / app key. */
    val isConfigured: Boolean

    /** Writes [content] as [CLOUD_BACKUP_FILE_NAME], replacing the previous backup. */
    suspend fun upload(content: ByteArray)

    /** Best-effort server-side revocation, then local forgetting of the account. */
    suspend fun disconnect()
}
