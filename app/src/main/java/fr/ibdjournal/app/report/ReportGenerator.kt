package fr.ibdjournal.app.report

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.ibdjournal.app.data.DiaryRepository
import fr.ibdjournal.app.data.ProfileRepository
import fr.ibdjournal.app.domain.AnalysisPeriod
import fr.ibdjournal.app.domain.now
import fr.ibdjournal.app.domain.startFrom
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/** Cache folder shared by the FileProvider (see res/xml/report_paths.xml). */
private const val REPORTS_DIR = "reports"

/**
 * Generates the PDF report for a period in the cache and returns the shareable URI
 * (FileProvider) of the file.
 */
@Singleton
class ReportGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diaryRepository: DiaryRepository,
    private val profileRepository: ProfileRepository,
) {

    suspend fun generate(period: AnalysisPeriod): Uri {
        val now = now()
        val mealsFrom = period.startFrom(now.date) ?: LocalDate.fromEpochDays(0)
        val data = reportData(
            period = period,
            now = now,
            profile = profileRepository.profile.first(),
            stools = diaryRepository.observeAllStools().first(),
            meals = diaryRepository.observeMeals(mealsFrom, now.date).first(),
            medications = profileRepository.medications.first(),
            doseLogs = profileRepository.doseLogs.first(),
            labResults = profileRepository.labResults.first(),
            checkups = profileRepository.checkups.first(),
        )
        return withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, REPORTS_DIR).apply { mkdirs() }
            // One report at a time: previous ones have already been shared.
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "ibd-journal-report-${data.to}.pdf")
            file.outputStream().use { ReportPdfBuilder(localizedContext()).write(data, it) }
            FileProvider.getUriForFile(context, fileProviderAuthority(context), file)
        }
    }

    /**
     * The application context does not follow the language chosen in the app before Android 13
     * (AppCompat only applies it to activities): we impose it on it for the PDF texts.
     */
    private fun localizedContext(): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return context
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
        }
        return context.createConfigurationContext(configuration)
    }
}

/** Authority of the FileProvider declared in the manifest. */
private fun fileProviderAuthority(context: Context): String = "${context.packageName}.fileprovider"
