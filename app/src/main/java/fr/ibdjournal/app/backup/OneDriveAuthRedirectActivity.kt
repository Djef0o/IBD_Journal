package fr.ibdjournal.app.backup

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dagger.hilt.android.EntryPointAccessors

/**
 * Receives `<applicationId>://onedrive-auth?code=…&state=…` from the Custom Tab, hands it to
 * [CloudBackupManager] (which validates `state` and redeems the code with the PKCE verifier),
 * then brings the app back to the front, closing the Custom Tab stacked above it.
 */
class OneDriveAuthRedirectActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val redirect = intent?.data
        if (savedInstanceState == null && redirect != null) {
            EntryPointAccessors
                .fromApplication(applicationContext, CloudBackupEntryPoint::class.java)
                .cloudBackupManager()
                .onOneDriveRedirect(redirect)
        }
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(launch)
        }
        finish()
    }
}
