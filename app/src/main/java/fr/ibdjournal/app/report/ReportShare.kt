package fr.ibdjournal.app.report

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import fr.ibdjournal.app.R

/** Ouvre la feuille de partage Android pour le rapport PDF [uri] (URI du FileProvider). */
fun shareReport(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.report_title))
        // Le ClipData porte l'autorisation de lecture jusqu'à l'app choisie dans le sélecteur.
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.report_share_title)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.report_no_share_app, Toast.LENGTH_SHORT).show()
    }
}
