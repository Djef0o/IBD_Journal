package fr.ibdjournal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import fr.ibdjournal.app.notification.NotificationHelper
import fr.ibdjournal.app.ui.calendar.CalendarScreen
import fr.ibdjournal.app.ui.settings.SettingsScreen
import fr.ibdjournal.app.ui.theme.IBDJournalTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        // Par défaut, programme le rappel à 19h si ce n'est pas déjà fait (Unique Work).
        notificationHelper.scheduleDailyReminder(19, 0)

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
