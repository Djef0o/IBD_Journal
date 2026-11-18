package fr.ibdjournal.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import fr.ibdjournal.app.ui.analysis.AnalysisScreen
import fr.ibdjournal.app.ui.day.DayScreen
import fr.ibdjournal.app.ui.history.HistoryScreen
import fr.ibdjournal.app.ui.care.LabsScreen
import fr.ibdjournal.app.ui.home.HomeScreen
import fr.ibdjournal.app.ui.onboarding.OnboardingScreen
import fr.ibdjournal.app.ui.care.CareScreen
import fr.ibdjournal.app.ui.settings.SettingsScreen
import fr.ibdjournal.app.data.Appearance
import fr.ibdjournal.app.ui.calendar.CalendarViewModel
import fr.ibdjournal.app.ui.theme.IBDJournalTheme
import fr.ibdjournal.app.ui.theme.isDark
import fr.ibdjournal.app.security.VaultState
import fr.ibdjournal.app.ui.security.LockScreen
import fr.ibdjournal.app.ui.security.SecurityUpgradeScreen
import fr.ibdjournal.app.ui.security.SecurityViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * AppCompatActivity rather than ComponentActivity: it is what applies the language
 * chosen in the app (AppCompatDelegate.setApplicationLocales) before Android 13.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val security: SecurityViewModel = hiltViewModel()
            val loadedAppearance by viewModel.appearance.collectAsStateWithLifecycle()
            val onboardingDone by viewModel.onboardingDone.collectAsStateWithLifecycle()
            val vaultState by security.vaultState.collectAsStateWithLifecycle()
            val setupPhase by security.setupPhase.collectAsStateWithLifecycle()
            val appearance = loadedAppearance ?: Appearance()
            val dark = appearance.isDark()

            // System bar icons light or dark depending on the chosen theme,
            // even when it differs from the phone's mode.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }

            IBDJournalTheme(appearance) {
                val locked = vaultState == VaultState.LOCKED
                Box(Modifier.fillMaxSize()) {
                    // Locked: the screens stay composed below (drafts kept) but hidden.
                    Box(Modifier.fillMaxSize().then(if (locked) Modifier.clearAndSetSemantics {} else Modifier)) {
                        when {
                            loadedAppearance == null || onboardingDone == null -> Surface(Modifier.fillMaxSize()) {}
                            onboardingDone == false -> OnboardingScreen(onFinish = viewModel::finishOnboarding, security = security)
                            // Update from a version without encryption: PIN before anything else.
                            security.setupVisible(vaultState, setupPhase) -> SecurityUpgradeScreen(security)
                            else -> MainScaffold()
                        }
                    }
                    if (locked) LockScreen(security)
                }
            }
        }
    }
}

private data class Tab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val tabs = listOf(
    Tab("home", R.string.nav_home, Icons.Outlined.Home),
    Tab("history", R.string.nav_history, Icons.Outlined.CalendarMonth),
    Tab("analysis", R.string.nav_analysis, Icons.Outlined.BarChart),
    Tab("care", R.string.nav_care, Icons.Outlined.MedicalServices),
    Tab("settings", R.string.nav_settings, Icons.Outlined.Settings),
)

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = tabs.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            // A single line, shrunk if needed: with a large system font,
                            // "Today" wrapped onto two lines and shifted its tab.
                            label = {
                                Text(
                                    stringResource(tab.labelRes),
                                    maxLines = 1,
                                    softWrap = false,
                                    autoSize = TextAutoSize.StepBased(
                                        minFontSize = 8.sp,
                                        maxFontSize = MaterialTheme.typography.labelMedium.fontSize,
                                    ),
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") {
                HomeScreen(
                    onOpenSettings = {
                        navController.navigate("settings") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable("history") {
                HistoryScreen(
                    onOpenDay = { date -> navController.navigate("day/${date.toEpochDays()}") },
                )
            }
            composable("analysis") { AnalysisScreen() }
            composable("care") { CareScreen(onOpenLabs = { navController.navigate("care/labs") }) }
            composable("care/labs") { LabsScreen(onBack = { navController.popBackStack() }) }
            composable("settings") { SettingsScreen(onBack = null) }
            composable("day/{${CalendarViewModel.ARG_DATE}}") {
                DayScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
