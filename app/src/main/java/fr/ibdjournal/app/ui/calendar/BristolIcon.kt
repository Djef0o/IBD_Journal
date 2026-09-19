package fr.ibdjournal.app.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.ibdjournal.app.ui.theme.IBDJournalTheme
import kotlin.math.min

/**
 * Icône dessinée du type de Bristol (0 à 7) : granulés durs, grappe bosselée,
 * boudin fissuré ou lisse, fragments, amas floconneux, liquide.
 */
@Composable
fun BristolIcon(
    type: Int,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        when (type) {
            0 -> {
                // Rien : cercle vide.
                drawCircle(tint.copy(alpha = 0.35f), radius = min(w, h) * 0.3f, center = Offset(cx, cy), style = stroke)
            }
            1 -> {
                // Dur : petits granulés séparés.
                val r = w * 0.09f
                listOf(
                    Offset(cx - w * 0.22f, cy - h * 0.12f),
                    Offset(cx + w * 0.05f, cy - h * 0.2f),
                    Offset(cx + w * 0.24f, cy - h * 0.02f),
                    Offset(cx - w * 0.05f, cy + h * 0.14f),
                    Offset(cx + w * 0.15f, cy + h * 0.2f),
                ).forEach { drawCircle(tint, radius = r, center = it) }
            }
            2 -> {
                // Bosselé : grappe de sphères accolées.
                val r = w * 0.13f
                listOf(
                    Offset(cx - w * 0.2f, cy),
                    Offset(cx - w * 0.05f, cy - h * 0.1f),
                    Offset(cx + w * 0.12f, cy - h * 0.05f),
                    Offset(cx + w * 0.22f, cy + h * 0.08f),
                    Offset(cx, cy + h * 0.12f),
                    Offset(cx - w * 0.12f, cy + h * 0.08f),
                ).forEach { drawCircle(tint, radius = r, center = it) }
            }
            3 -> {
                // Fissuré : boudin avec craquelures.
                drawRoundRect(
                    tint,
                    topLeft = Offset(cx - w * 0.3f, cy - h * 0.18f),
                    size = Size(w * 0.6f, h * 0.36f),
                    cornerRadius = CornerRadius(h * 0.18f),
                )
                drawLine(tint, Offset(cx - w * 0.1f, cy - h * 0.18f), Offset(cx - w * 0.16f, cy + h * 0.18f), strokeWidth = stroke.width)
                drawLine(tint, Offset(cx + w * 0.12f, cy - h * 0.18f), Offset(cx + w * 0.18f, cy + h * 0.18f), strokeWidth = stroke.width)
            }
            4 -> {
                // Lisse : boudin régulier.
                drawRoundRect(
                    tint,
                    topLeft = Offset(cx - w * 0.32f, cy - h * 0.17f),
                    size = Size(w * 0.64f, h * 0.34f),
                    cornerRadius = CornerRadius(h * 0.17f),
                )
            }
            5 -> {
                // Fragments : gros morceaux aux bords nets.
                val r = w * 0.14f
                listOf(
                    Offset(cx - w * 0.18f, cy - h * 0.08f),
                    Offset(cx + w * 0.1f, cy - h * 0.04f),
                    Offset(cx - w * 0.02f, cy + h * 0.16f),
                ).forEach { drawCircle(tint, radius = r, center = it) }
            }
            6 -> {
                // Floconneux : amas flou aux bords déchiquetés.
                val r = w * 0.16f
                val c = tint.copy(alpha = 0.55f)
                listOf(
                    Offset(cx - w * 0.15f, cy),
                    Offset(cx + w * 0.05f, cy - h * 0.08f),
                    Offset(cx + w * 0.18f, cy + h * 0.06f),
                    Offset(cx - w * 0.02f, cy + h * 0.1f),
                ).forEach { drawCircle(c, radius = r, center = it) }
            }
            7 -> {
                // Liquide : vague et flaque.
                val path = Path().apply {
                    moveTo(cx - w * 0.36f, cy - h * 0.02f)
                    cubicTo(
                        cx - w * 0.15f, cy - h * 0.26f,
                        cx - w * 0.05f, cy + h * 0.24f,
                        cx + w * 0.15f, cy,
                    )
                    cubicTo(
                        cx + w * 0.25f, cy + h * 0.12f,
                        cx + w * 0.3f, cy + h * 0.04f,
                        cx + w * 0.38f, cy + h * 0.02f,
                    )
                }
                drawPath(path, tint, style = stroke)
                drawOval(
                    tint.copy(alpha = 0.4f),
                    topLeft = Offset(cx - w * 0.2f, cy + h * 0.16f),
                    size = Size(w * 0.4f, h * 0.16f),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BristolIconPreview() {
    IBDJournalTheme {
        Row(verticalAlignment = Alignment.CenterVertically) {
            (0..7).forEach { type ->
                BristolIcon(type = type, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}
