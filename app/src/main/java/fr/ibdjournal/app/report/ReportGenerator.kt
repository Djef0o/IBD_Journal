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

/** Dossier du cache partagé par le FileProvider (voir res/xml/report_paths.xml). */
private const val REPORTS_DIR = "reports"

/**
 * Génère le rapport PDF d'une période dans le cache et renvoie l'URI partageable
 * (FileProvider) du fichier.
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
            // Un seul rapport à la fois : les précédents ont déjà été partagés.
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "ibd-journal-report-${data.to}.pdf")
            file.outputStream().use { ReportPdfBuilder(localizedContext()).write(data, it) }
            FileProvider.getUriForFile(context, fileProviderAuthority(context), file)
        }
    }

    /**
     * Le contexte d'application ne suit pas la langue choisie dans l'app avant Android 13
     * (AppCompat ne l'applique qu'aux activités) : on la lui impose pour les textes du PDF.
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

/** Autorité du FileProvider déclaré dans le manifeste. */
private fun fileProviderAuthority(context: Context): String = "${context.packageName}.fileprovider"
