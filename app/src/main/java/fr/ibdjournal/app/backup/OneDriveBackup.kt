package fr.ibdjournal.app.backup

import android.net.Uri
import android.util.Base64
import fr.ibdjournal.app.BuildConfig
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OneDrive through the Microsoft identity platform v2 (tenant `common`: personal, work and
 * school accounts), authorization code + PKCE in a Custom Tab, public client (no secret).
 * Files go to the app's dedicated folder (`Apps/<app name>`), scope Files.ReadWrite.AppFolder.
 */
@Singleton
class OneDriveBackup @Inject constructor(
    okHttpClient: OkHttpClient,
    private val json: Json,
    private val store: CloudBackupStore,
) : CloudBackupTarget {

    override val provider = CloudProvider.ONEDRIVE
    override val isConfigured = CLIENT_ID.isNotBlank()

    private val http = okHttpClient.forUploads()
    private val random = SecureRandom()

    /** In-memory access token; refreshed (and the rotated refresh token saved) on demand. */
    private val tokenMutex = Mutex()
    private var cachedAccessToken: String? = null
    private var cachedAccessTokenExpiry = 0L

    /** Builds the authorize URL and remembers its `state` + PKCE verifier for the redirect. */
    suspend fun createAuthorizationUri(): Uri {
        val state = randomUrlSafe(24)
        val codeVerifier = randomUrlSafe(48)
        val codeChallenge = base64Url(
            MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        )
        store.setOneDrivePending(state, codeVerifier)

        return Uri.parse(AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_mode", "query")
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("prompt", "select_account")
            .build()
    }

    /**
     * Handles the redirect received by [OneDriveAuthRedirectActivity].
     * @return true when an account got connected, false if the user cancelled.
     */
    suspend fun completeAuthorization(redirect: Uri): Boolean {
        val (expectedState, codeVerifier) = store.takeOneDrivePending()
            ?: throw CloudBackupException(CloudError.UNKNOWN, "No OneDrive authorization pending")
        val state = redirect.getQueryParameter("state")
        if (state == null ||
            !MessageDigest.isEqual(state.toByteArray(), expectedState.toByteArray())
        ) {
            throw CloudBackupException(CloudError.UNKNOWN, "OneDrive state mismatch")
        }

        redirect.getQueryParameter("error")?.let { error ->
            if (error == "access_denied") return false // The user cancelled or declined.
            throw CloudBackupException(
                if (error in CONFIGURATION_ERRORS) CloudError.NOT_CONFIGURED else CloudError.UNKNOWN,
                "OneDrive authorization error: $error",
            )
        }
        val code = redirect.getQueryParameter("code")
            ?: throw CloudBackupException(CloudError.UNKNOWN, "OneDrive redirect without code")

        val tokens = requestTokens(
            FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("code_verifier", codeVerifier)
                .add("scope", SCOPES)
                .build()
        )
        val refreshToken = tokens.refreshToken
            ?: throw CloudBackupException(CloudError.UNKNOWN, "No refresh token returned")

        val label = try {
            fetchAccountLabel(tokens.accessToken)
        } catch (_: CloudBackupException) {
            null // The label is cosmetic; the connection itself succeeded.
        }
        tokenMutex.withLock { cache(tokens) }
        store.setConnected(provider, accountLabel = label, refreshToken = refreshToken)
        return true
    }

    override suspend fun upload(content: ByteArray) {
        val first = put(accessToken(), content)
        val result = if (first.code == 401) {
            tokenMutex.withLock { cachedAccessToken = null }
            put(accessToken(), content)
        } else {
            first
        }
        if (!result.isSuccessful) throw httpFailure(result)
    }

    /**
     * Microsoft offers no per-token revocation for public clients (only "revoke all sessions"
     * of the user, far too broad): disconnecting forgets the tokens on this device.
     */
    override suspend fun disconnect() {
        tokenMutex.withLock {
            cachedAccessToken = null
            cachedAccessTokenExpiry = 0L
        }
        store.clearProvider(provider)
    }

    private suspend fun put(token: String, content: ByteArray): HttpResult = http.send(
        Request.Builder()
            .url(UPLOAD_URL)
            .header("Authorization", "Bearer $token")
            .put(content.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    )

    private suspend fun accessToken(): String = tokenMutex.withLock {
        val cached = cachedAccessToken
        if (cached != null && System.currentTimeMillis() < cachedAccessTokenExpiry) {
            cached
        } else {
            refreshAccessToken()
        }
    }

    /** Must be called with [tokenMutex] held. */
    private suspend fun refreshAccessToken(): String {
        val refreshToken = store.refreshToken(provider)
            ?: throw CloudBackupException(CloudError.AUTH_REQUIRED, "No OneDrive refresh token")
        val tokens = requestTokens(
            FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("scope", SCOPES)
                .build()
        )
        // Refresh tokens rotate: always keep the newest one.
        tokens.refreshToken?.let { store.updateRefreshToken(provider, it) }
        return cache(tokens)
    }

    /** Must be called with [tokenMutex] held. */
    private fun cache(tokens: TokenResponse): String {
        cachedAccessToken = tokens.accessToken
        // One minute of margin against clock skew and request duration.
        cachedAccessTokenExpiry = System.currentTimeMillis() + (tokens.expiresIn - 60) * 1000
        return tokens.accessToken
    }

    private suspend fun requestTokens(form: FormBody): TokenResponse {
        val result = http.send(Request.Builder().url(TOKEN_URL).post(form).build())
        if (result.isSuccessful) return json.decodeFromString(TokenResponse.serializer(), result.body)

        val error = try {
            json.decodeFromString(TokenError.serializer(), result.body).error
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        throw when (error) {
            "invalid_grant", "interaction_required", "consent_required" ->
                CloudBackupException(CloudError.AUTH_REQUIRED, "OneDrive token error: $error")
            in CONFIGURATION_ERRORS ->
                CloudBackupException(CloudError.NOT_CONFIGURED, "OneDrive token error: $error")
            else -> httpFailure(result)
        }
    }

    private suspend fun fetchAccountLabel(token: String): String? {
        val result = http.send(
            Request.Builder()
                .url(ME_URL)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
        )
        if (!result.isSuccessful) throw httpFailure(result)
        val user = json.decodeFromString(GraphUser.serializer(), result.body)
        return user.mail ?: user.userPrincipalName ?: user.displayName
    }

    private fun randomUrlSafe(byteCount: Int): String =
        base64Url(ByteArray(byteCount).also { random.nextBytes(it) })

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long = 3600,
    )

    @Serializable
    private data class TokenError(val error: String? = null)

    @Serializable
    private data class GraphUser(
        val displayName: String? = null,
        val mail: String? = null,
        val userPrincipalName: String? = null,
    )

    companion object {
        private val CLIENT_ID: String = BuildConfig.ONEDRIVE_CLIENT_ID

        /** Must match the manifest intent-filter and the URI registered in Azure. */
        private val REDIRECT_URI = "${BuildConfig.APPLICATION_ID}://onedrive-auth"

        private const val SCOPES = "Files.ReadWrite.AppFolder offline_access User.Read"
        private const val AUTHORIZE_URL =
            "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
        private const val TOKEN_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/token"
        private const val ME_URL =
            "https://graph.microsoft.com/v1.0/me?\$select=displayName,mail,userPrincipalName"
        private const val UPLOAD_URL =
            "https://graph.microsoft.com/v1.0/me/drive/special/approot:/IBD%20Journal%20backup.json:/content"

        /** OAuth errors that point at the app registration rather than the user. */
        private val CONFIGURATION_ERRORS = setOf(
            "invalid_client",
            "unauthorized_client",
            "invalid_request",
            "invalid_scope",
            "unsupported_response_type",
        )
    }
}
