package fr.ibdjournal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import fr.ibdjournal.app.ui.calendar.CalendarScreen
import fr.ibdjournal.app.ui.theme.IBDJournalTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            IBDJournalTheme {
                CalendarScreen()
            }
        }
    }
}
