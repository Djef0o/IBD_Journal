package fr.ibdjournal.app.labimport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.ibdjournal.app.domain.TextLine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Lit le texte d'un compte rendu (PDF ou image) avec sa position, entièrement sur l'appareil :
 * le PDF est rendu page par page puis passé à la reconnaissance de texte ML Kit, comme une
 * photo. Une seule chaîne pour les deux formats, et rien n'est envoyé.
 */
@Singleton
class LabReportReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun read(uri: Uri): List<TextLine> = withContext(Dispatchers.IO) {
        if (context.contentResolver.getType(uri) == PDF_MIME) readPdf(uri) else readImage(uri)
    }

    /** Nom du fichier fourni par le sélecteur de documents ; null s'il est inconnu. */
    suspend fun displayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun readImage(uri: Uri): List<TextLine> =
        // fromFilePath tient compte de l'orientation EXIF des photos.
        recognize(InputImage.fromFilePath(context, uri), page = 0)

    private suspend fun readPdf(uri: Uri): List<TextLine> {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
        // close() explicites : PdfRenderer n'est pas AutoCloseable sur toutes les versions visées.
        val renderer = PdfRenderer(descriptor)
        try {
            return (0 until minOf(renderer.pageCount, MAX_PAGES)).flatMap { index ->
                val page = renderer.openPage(index)
                // ~216 ppp : assez net pour l'OCR des petits caractères des tableaux.
                val bitmap = Bitmap.createBitmap(page.width * SCALE, page.height * SCALE, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE) // fond transparent par défaut : illisible pour l'OCR
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    recognize(InputImage.fromBitmap(bitmap, 0), page = index)
                } finally {
                    bitmap.recycle()
                }
            }
        } finally {
            renderer.close()
            descriptor.close()
        }
    }

    private suspend fun recognize(image: InputImage, page: Int): List<TextLine> {
        val text = recognizer.process(image).await()
        return text.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                TextLine(
                    text = line.text,
                    left = box.left.toFloat(),
                    top = box.top.toFloat(),
                    right = box.right.toFloat(),
                    bottom = box.bottom.toFloat(),
                    page = page,
                )
            }
        }
    }

    companion object {
        const val PDF_MIME = "application/pdf"
        private const val SCALE = 3
        /** Les résultats sont en tête ; au-delà, ce sont mentions légales et commentaires. */
        private const val MAX_PAGES = 8
    }
}
