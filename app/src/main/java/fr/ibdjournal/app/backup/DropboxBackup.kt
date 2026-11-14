package fr.ibdjournal.app.backup

import android.app.Activity
import com.dropbox.core.DbxException
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.InvalidAccessTokenException
import com.dropbox.core.NetworkIOException
import com.dropbox.core.RateLimitException
import com.dropbox.core.ServerException
import com.dropbox.core.android.Auth
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.oauth.DbxOAuthError
import com.dropbox.core.oauth.DbxOAuthException
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.UploadErrorException
import com.dropbox.core.v2.files.WriteMode
import fr.ibdjournal.app.BuildConfig
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dropbox through the official SDK's PKCE flow ("App folder" access, offline refresh token).
 * The SDK's AuthActivity receives the redirect on the `db-<APP_KEY>` scheme declared in the
 * manifest; the credential is then read back when the app resumes.
 */
@Singleton
class DropboxBackup @Inject constructor(
    private val store: CloudBackupStore,
) : CloudBackupTarget {

    override val provider = CloudProvider.DROPBOX
    override val isConfigured = APP_KEY.isNotBlank()

    private val requestConfig = DbxRequestConfig.newBuilder("IBDJournal-Android/${BuildConfig.VERSION_NAME}")
        .withAutoRetryEnabled(2)
        .build()

    /** Must be called (and awaited) before [launchAuthorization]. */
    suspend fun markAuthorizationPending() {
        store.setDropboxAuthPending(true)
    }

    /** Opens the Dropbox app or the browser. Throws if the manifest entry is missing. */
    fun launchAuthorization(activity: Activity) {
        Auth.startOAuth2PKCE(activity, APP_KEY, requestConfig, SCOPES)
    }

    /**
     * Collects the credential produced by AuthActivity, if an authorization was started.
     * @return true when an account got connected.
     */
    suspend fun completeAuthorizationIfPending(): Boolean {
        if (!store.isDropboxAuthPending()) return false
        // Static result of the last flow; null when the user backed out.
        val credential: DbxCredential? = Auth.getDbxCredential()
        store.setDropboxAuthPending(false)
        if (credential == null) return false
        val refreshToken = credential.refreshToken
            ?: throw CloudBackupException(CloudError.UNKNOWN, "No refresh token returned")

        val email = withContext(Dispatchers.IO) {
            try {
                DbxClientV2(requestConfig, credential).users().getCurrentAccount().email
            } catch (_: DbxException) {
                null // The label is cosmetic; the connection itself succeeded.
            }
        }
        store.setConnected(provider, accountLabel = email, refreshToken = refreshToken)
        return true
    }

    override suspend fun upload(content: ByteArray) {
        val client = client()
        withContext(Dispatchers.IO) {
            try {
                client.files().uploadBuilder("/$CLOUD_BACKUP_FILE_NAME")
                    .withMode(WriteMode.OVERWRITE)
                    .withMute(true)
                    .uploadAndFinish(ByteArrayInputStream(content))
            } catch (e: UploadErrorException) {
                val error = e.errorValue
                val insufficientSpace = error.isPath && error.pathValue.reason.isInsufficientSpace
                throw CloudBackupException(
                    if (insufficientSpace) CloudError.STORAGE_FULL else CloudError.UNKNOWN,
                    "Dropbox upload rejected",
                    e,
                )
            } catch (e: DbxException) {
                throw e.toCloudException()
            } catch (e: IOException) {
                throw CloudBackupException(CloudError.NETWORK, "Dropbox I/O failure", e)
            }
        }
    }

    override suspend fun disconnect() {
        bestEffort {
            val client = client()
            withContext(Dispatchers.IO) { client.auth().tokenRevoke() }
        }
        store.clearProvider(provider)
    }

    /** Client with a freshly refreshed short-lived access token. */
    private suspend fun client(): DbxClientV2 {
        val refreshToken = store.refreshToken(provider)
            ?: throw CloudBackupException(CloudError.AUTH_REQUIRED, "No Dropbox refresh token")
        // Placeholder access token already expired: refresh() replaces it before any call.
        val credential = DbxCredential("", 0L, refreshToken, APP_KEY)
        withContext(Dispatchers.IO) {
            try {
                credential.refresh(requestConfig)
            } catch (e: DbxException) {
                throw e.toCloudException()
            }
        }
        return DbxClientV2(requestConfig, credential)
    }

    private fun DbxException.toCloudException(): CloudBackupException {
        val error = when (this) {
            is InvalidAccessTokenException -> CloudError.AUTH_REQUIRED
            is DbxOAuthException -> when (dbxOAuthError.error) {
                DbxOAuthError.INVALID_GRANT -> CloudError.AUTH_REQUIRED
                DbxOAuthError.INVALID_REQUEST -> CloudError.NOT_CONFIGURED
                else -> CloudError.UNKNOWN
            }
            is NetworkIOException -> CloudError.NETWORK
            is RateLimitException, is ServerException -> CloudError.SERVER
            else -> CloudError.UNKNOWN
        }
        return CloudBackupException(error, "Dropbox request failed", this)
    }

    private companion object {
        val APP_KEY: String = BuildConfig.DROPBOX_APP_KEY

        /** Must match the scopes ticked in the Dropbox App Console "Permissions" tab. */
        val SCOPES = listOf("account_info.read", "files.content.write")
    }
}
