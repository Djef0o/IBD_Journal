package fr.ibdjournal.app.report

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Same cache folder as the report, shared by the FileProvider (see res/xml/report_paths.xml). */
private const val TRAVEL_DIR = "reports"

/** Writes the travel pack PDF in the cache and returns its shareable URI. */
@Singleton
class TravelPackGenerator @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun generate(data: TravelPackData): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, TRAVEL_DIR).apply { mkdirs() }
        // One document at a time: previous ones have already been shared.
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "ibd-journal-travel-${data.departure}.pdf")
        file.outputStream().use { TravelPdfBuilder(context).write(data, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
