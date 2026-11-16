package fr.ibdjournal.app.labimport

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.ibdjournal.app.domain.TextLine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lit le texte d'un compte rendu PDF avec sa position, sur l'appareil et sans reconnaissance de
 * caractères : les comptes rendus des laboratoires contiennent leur texte, lu ici tel quel
 * (PDFBox). Un PDF sans texte (document scanné) n'est pas lisible : [NoTextLayerException].
 */
@Singleton
class LabReportReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val pdfBoxReady by lazy { PDFBoxResourceLoader.init(context) }

    suspend fun read(uri: Uri): List<TextLine> = withContext(Dispatchers.IO) {
        pdfBoxReady
        val input = context.contentResolver.openInputStream(uri) ?: throw NoTextLayerException()
        val lines = input.use { stream ->
            PDDocument.load(stream).use { document ->
                val pages = minOf(document.numberOfPages, MAX_PAGES)
                (1..pages).flatMap { page -> PositionedTextStripper(page).linesOf(document) }
            }
        }
        if (lines.isEmpty()) throw NoTextLayerException()
        lines
    }

    /** Nom du fichier fourni par le sélecteur de documents ; null s'il est inconnu. */
    suspend fun displayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    companion object {
        const val PDF_MIME = "application/pdf"

        /** Les résultats sont en tête ; au-delà, ce sont mentions légales et commentaires. */
        private const val MAX_PAGES = 8
    }
}

/** PDF sans texte exploitable : document scanné ou protégé. */
class NoTextLayerException : Exception()

/**
 * Regroupe les caractères d'une page en morceaux de ligne, comme les colonnes d'un tableau :
 * même ligne de base, et un écart horizontal de plus de [COLUMN_GAP] largeurs de caractère
 * ouvre un nouveau morceau (libellé, résultat, unité, références, antériorité).
 */
private class PositionedTextStripper(private val page: Int) : PDFTextStripper() {
    private val glyphs = mutableListOf<TextPosition>()

    init {
        sortByPosition = true
        startPage = page
        endPage = page
    }

    fun linesOf(document: PDDocument): List<TextLine> {
        getText(document)
        // Rangées : caractères dont la ligne de base est à moins de BASELINE_TOLERANCE de la
        // première de la rangée (des seuils fixes couperaient une ligne à cheval sur deux).
        val rows = mutableListOf<MutableList<TextPosition>>()
        for (glyph in glyphs.filter { it.unicode.isNotBlank() }.sortedBy { it.yDirAdj }) {
            val row = rows.lastOrNull()?.takeIf { glyph.yDirAdj - it.first().yDirAdj <= BASELINE_TOLERANCE }
            if (row != null) row += glyph else rows += mutableListOf(glyph)
        }
        return rows.flatMap { row -> chunks(row.sortedBy { it.xDirAdj }) }
    }

    override fun processTextPosition(text: TextPosition) {
        glyphs += text
    }

    private fun chunks(row: List<TextPosition>): List<TextLine> {
        val result = mutableListOf<TextLine>()
        var current = mutableListOf<TextPosition>()
        fun flush() {
            if (current.isNotEmpty()) result += current.toTextLine()
            current = mutableListOf()
        }
        for (glyph in row) {
            val previous = current.lastOrNull()
            if (previous != null) {
                val gap = glyph.xDirAdj - (previous.xDirAdj + previous.widthDirAdj)
                if (gap > COLUMN_GAP * charWidth(previous)) flush()
            }
            current += glyph
        }
        flush()
        return result
    }

    /** Texte du morceau, avec une espace là où l'écart entre deux caractères marque un mot. */
    private fun List<TextPosition>.toTextLine(): TextLine {
        val text = buildString {
            this@toTextLine.forEachIndexed { index, glyph ->
                if (index > 0) {
                    val previous = this@toTextLine[index - 1]
                    val gap = glyph.xDirAdj - (previous.xDirAdj + previous.widthDirAdj)
                    if (gap > WORD_GAP * charWidth(previous)) append(' ')
                }
                append(glyph.unicode)
            }
        }
        val top = minOf { it.yDirAdj - it.heightDir }
        return TextLine(
            text = text,
            left = first().xDirAdj,
            top = top,
            right = last().xDirAdj + last().widthDirAdj,
            bottom = maxOf { it.yDirAdj },
            page = page - 1,
        )
    }

    /** Largeur de référence d'un caractère : la moitié de la taille de police, à défaut sa largeur. */
    private fun charWidth(glyph: TextPosition): Float =
        (glyph.fontSizeInPt * 0.5f).takeIf { it > 0f } ?: glyph.widthDirAdj.coerceAtLeast(1f)

    private companion object {
        /** Lignes de base distantes de moins de 2 points : même ligne. */
        const val BASELINE_TOLERANCE = 2f
        const val WORD_GAP = 0.3f
        const val COLUMN_GAP = 2.0f
    }
}
