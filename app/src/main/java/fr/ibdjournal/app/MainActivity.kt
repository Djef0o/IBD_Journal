package fr.ibdjournal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.ExistingPeriodicWorkPolicy
import dagger.hilt.android.AndroidEntryPoint
import fr.ibdjournal.app.data.AppPrefs
import fr.ibdjournal.app.notification.NotificationHelper
import fr.ibdjournal.app.ui.calendar.CalendarScreen
import fr.ibdjournal.app.ui.settings.SettingsScreen
import fr.ibdjournal.app.ui.theme.IBDJournalTheme
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var appPrefs: AppPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        // Programme ou annule le rappel quotidien selon les préférences de l'utilisateur.
        lifecycleScope.launch {
            val settings = appPrefs.notificationSettings.first()
            if (settings.enabled) {
                notificationHelper.scheduleDailyReminder(
                    settings.hour,
                    settings.minute,
                    ExistingPeriodicWorkPolicy.KEEP,
                )
            } else {
                notificationHelper.cancelDailyReminder()
            }
        }

        setContent {
            IBDJournalTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "calendar") {
                    composable("calendar") {
                        CalendarScreen(onOpenSettings = { navController.navigate("settings") })
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
