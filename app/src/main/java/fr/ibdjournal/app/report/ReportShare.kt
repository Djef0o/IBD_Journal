package fr.ibdjournal.app.report

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import fr.ibdjournal.app.R

/** Opens the Android share sheet for the PDF report [uri] (FileProvider URI). */
fun shareReport(context: Context, uri: Uri, subject: String = context.getString(R.string.report_title)) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        // The ClipData carries the read permission to the app chosen in the chooser.
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.report_share_title)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.report_no_share_app, Toast.LENGTH_SHORT).show()
    }
}
