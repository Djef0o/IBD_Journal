package fr.ibdjournal.app.backup

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Google Drive through Google Identity Services' AuthorizationClient (no refresh token on the
 * device: every upload re-runs a silent `authorize`, which returns a cached or fresh access
 * token as long as the grant exists). Scope `drive.file`: the app only sees files it created.
 *
 * The OAuth client is the *Android* client registered in Google Cloud Console for this
 * package name + signing SHA-1, so no client id appears in code.
 */
@Singleton
class GoogleDriveBackup @Inject constructor(
    @ApplicationContext context: Context,
    okHttpClient: OkHttpClient,
    private val json: Json,
    private val store: CloudBackupStore,
) : CloudBackupTarget {

    override val provider = CloudProvider.GOOGLE_DRIVE

    private val authorizationClient = Identity.getAuthorizationClient(context)
    private val http = okHttpClient.forUploads()

    sealed interface ConnectStep {
        /** Account picker / consent screen to show with an IntentSender launcher. */
        data class NeedsUserConsent(val pendingIntent: PendingIntent) : ConnectStep
        data object Connected : ConnectStep
    }

    /** Starts authorization; no account is preset so the user can pick one. */
    suspend fun beginConnect(): ConnectStep {
        val result = authorize(accountEmail = null)
        val pendingIntent = result.pendingIntent
        if (result.hasResolution() && pendingIntent != null) {
            return ConnectStep.NeedsUserConsent(pendingIntent)
        }
        finishConnect(result)
        return ConnectStep.Connected
    }

    /**
     * Completes authorization with the data returned by the consent screen.
     * @return false if the user cancelled.
     */
    suspend fun completeConnect(data: Intent?): Boolean {
        val result = try {
            authorizationClient.getAuthorizationResultFromIntent(data)
        } catch (e: ApiException) {
            if (e.statusCode == CommonStatusCodes.CANCELED) return false
            throw e.toCloudException()
        }
        finishConnect(result)
        return true
    }

    override suspend fun upload(content: ByteArray) {
        val token = accessToken()
        try {
            uploadWith(token, content)
        } catch (e: CloudBackupException) {
            if (e.error != CloudError.AUTH_REQUIRED) throw e
            // The cached token may have been invalidated server-side: drop it, retry once.
            bestEffort {
                authorizationClient
                    .clearToken(ClearTokenRequest.builder().setToken(token).build())
                    .await()
            }
            uploadWith(accessToken(), content)
        }
    }

    override suspend fun disconnect() {
        store.provider(provider).accountLabel?.let { email ->
            bestEffort {
                authorizationClient.revokeAccess(
                    RevokeAccessRequest.builder()
                        .setAccount(Account(email, GOOGLE_ACCOUNT_TYPE))
                        .setScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
                        .build()
                ).await()
            }
        }
        store.clearProvider(provider)
    }

    private suspend fun finishConnect(result: AuthorizationResult) {
        val token = result.accessToken
            ?: throw CloudBackupException(CloudError.AUTH_REQUIRED, "No access token granted")
        // Also proves that the Drive API is enabled for the Cloud project.
        val email = fetchAccountEmail(token)
        store.setConnected(provider, accountLabel = email)
    }

    /** Silent authorization for the connected account; fails if user interaction is needed. */
    private suspend fun accessToken(): String {
        val result = authorize(store.provider(provider).accountLabel)
        if (result.hasResolution()) {
            throw CloudBackupException(CloudError.AUTH_REQUIRED, "Google consent required")
        }
        return result.accessToken
            ?: throw CloudBackupException(CloudError.AUTH_REQUIRED, "No access token granted")
    }

    private suspend fun authorize(accountEmail: String?): AuthorizationResult {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
        if (accountEmail != null) builder.setAccount(Account(accountEmail, GOOGLE_ACCOUNT_TYPE))
        return try {
            authorizationClient.authorize(builder.build()).await()
        } catch (e: ApiException) {
            throw e.toCloudException()
        }
    }

    private suspend fun fetchAccountEmail(token: String): String? {
        val result = http.send(
            Request.Builder()
                .url("$API_URL/about?fields=user(emailAddress)")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
        )
        if (!result.isSuccessful) throw driveFailure(result)
        return json.decodeFromString(DriveAbout.serializer(), result.body).user?.emailAddress
    }

    private suspend fun uploadWith(token: String, content: ByteArray) {
        store.googleFileId()?.let { fileId ->
            val result = http.send(
                Request.Builder()
                    .url("$UPLOAD_URL/$fileId?uploadType=media&fields=id,trashed")
                    .header("Authorization", "Bearer $token")
                    .patch(content.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            )
            when {
                // A file moved to the trash by the user still accepts updates: recreate it.
                result.isSuccessful ->
                    if (!json.decodeFromString(DriveFile.serializer(), result.body).trashed) return
                // Permanently deleted by the user: create it again.
                result.code == 404 -> Unit
                else -> throw driveFailure(result)
            }
        }

        val metadata = json.encodeToString(
            DriveFileMetadata.serializer(),
            DriveFileMetadata(name = CLOUD_BACKUP_FILE_NAME, mimeType = "application/json"),
        )
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toRequestBody(JSON_MEDIA_TYPE))
            .addPart(content.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val result = http.send(
            Request.Builder()
                .url("$UPLOAD_URL?uploadType=multipart&fields=id")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
        )
        if (!result.isSuccessful) throw driveFailure(result)
        store.setGoogleFileId(json.decodeFromString(DriveFile.serializer(), result.body).id)
    }

    private fun driveFailure(result: HttpResult): CloudBackupException {
        val error = when {
            // Drive API not enabled in the Cloud project.
            "accessNotConfigured" in result.body || "SERVICE_DISABLED" in result.body ->
                CloudError.NOT_CONFIGURED
            "storageQuotaExceeded" in result.body -> CloudError.STORAGE_FULL
            "RateLimitExceeded" in result.body || "rateLimitExceeded" in result.body ->
                CloudError.SERVER
            else -> return httpFailure(result)
        }
        return CloudBackupException(error, "HTTP ${result.code}")
    }

    private fun ApiException.toCloudException(): CloudBackupException {
        val error = when (statusCode) {
            // Missing/mismatching Android OAuth client (package name or SHA-1). The consent
            // screen reports UNREGISTERED_ON_API_CONSOLE as INTERNAL_ERROR.
            CommonStatusCodes.DEVELOPER_ERROR, CommonStatusCodes.INTERNAL_ERROR ->
                CloudError.NOT_CONFIGURED
            CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT -> CloudError.NETWORK
            CommonStatusCodes.SIGN_IN_REQUIRED, CommonStatusCodes.RESOLUTION_REQUIRED ->
                CloudError.AUTH_REQUIRED
            else -> CloudError.UNKNOWN
        }
        return CloudBackupException(error, "Google authorization failed (status $statusCode)", this)
    }

    @Serializable
    private data class DriveFile(val id: String, val trashed: Boolean = false)

    @Serializable
    private data class DriveFileMetadata(val name: String, val mimeType: String)

    @Serializable
    private data class DriveAbout(val user: DriveUser? = null)

    @Serializable
    private data class DriveUser(val emailAddress: String? = null)

    private companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
        const val API_URL = "https://www.googleapis.com/drive/v3"
        const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
    }
}
