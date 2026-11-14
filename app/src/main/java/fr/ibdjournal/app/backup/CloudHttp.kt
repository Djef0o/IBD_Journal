package fr.ibdjournal.app.backup

import kotlin.coroutines.cancellation.CancellationException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request

internal class HttpResult(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

internal val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()

/** Backup uploads may be slower than the app's default API timeouts. */
internal fun OkHttpClient.forUploads(): OkHttpClient = newBuilder()
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()

/** Executes [request] off the main thread; I/O failures become [CloudError.NETWORK]. */
internal suspend fun OkHttpClient.send(request: Request): HttpResult = withContext(Dispatchers.IO) {
    try {
        newCall(request).execute().use { response -> HttpResult(response.code, response.body.string()) }
    } catch (e: IOException) {
        throw CloudBackupException(CloudError.NETWORK, "HTTP I/O failure", e)
    }
}

/** Generic mapping for statuses not handled specifically by a provider. */
internal fun httpFailure(result: HttpResult): CloudBackupException {
    val error = when {
        result.code == 401 || result.code == 403 -> CloudError.AUTH_REQUIRED
        result.code == 507 -> CloudError.STORAGE_FULL
        result.code == 408 || result.code == 429 || result.code >= 500 -> CloudError.SERVER
        else -> CloudError.UNKNOWN
    }
    // Only the status code: bodies may echo request details and must not end up in logs.
    return CloudBackupException(error, "HTTP ${result.code}")
}

/** Runs an optional step (e.g. token revocation) whose failure must not block the caller. */
internal suspend fun bestEffort(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Ignored on purpose: the local outcome (e.g. disconnection) proceeds regardless.
    }
}
