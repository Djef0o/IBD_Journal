package fr.ibdjournal.app.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.data.model.Profile
import fr.ibdjournal.app.data.model.Sex
import fr.ibdjournal.app.domain.today
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.calendar.BristolIcon
import fr.ibdjournal.app.ui.care.DatePickerDialogFor
import fr.ibdjournal.app.ui.care.MedicationEditorSheet
import fr.ibdjournal.app.ui.care.scheduleStatus
import fr.ibdjournal.app.ui.settings.SexSelector
import fr.ibdjournal.app.ui.settings.settingsRowColors
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** First-launch steps: setup, then a guided tour of the tabs. */
private enum class Step { WELCOME, PROFILE, TREATMENTS, TOUR_TODAY, TOUR_HISTORY, TOUR_TRENDS, TOUR_CARE, NOTIFICATIONS }

/**
 * First launch: introduction, profile, treatments, tab tour and notification permission.
 * Each input step can be skipped; everything stays editable afterwards in the Profile
 * tab.
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var birthEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var sex by rememberSaveable { mutableStateOf<Sex?>(null) }

    val step = Step.entries[stepIndex]
    val isLast = stepIndex == Step.entries.lastIndex
    val profileEmpty = firstName.isBlank() && lastName.isBlank() && birthEpochDay == null && sex == null
    // "Skip" rather than "Next" when the input step was left empty.
    val skipping = (step == Step.PROFILE && profileEmpty) || (step == Step.TREATMENTS && medications.isEmpty())

    BackHandler(enabled = stepIndex > 0) { stepIndex-- }

    val next = {
        if (step == Step.PROFILE && !profileEmpty) {
            viewModel.saveProfile(
                Profile(firstName.trim(), lastName.trim(), birthEpochDay?.let { LocalDate.fromEpochDays(it) }, sex)
            )
        }
        if (isLast) onFinish() else stepIndex += 1
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            LinearProgressIndicator(
                progress = { (stepIndex + 1f) / Step.entries.size },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_step, stepIndex + 1, Step.entries.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.weight(1f),
                label = "onboarding-step",
            ) { current ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (current) {
                        Step.WELCOME -> WelcomeStep()
                        Step.PROFILE -> ProfileStep(
                            firstName = firstName,
                            lastName = lastName,
                            birthDate = birthEpochDay?.let { LocalDate.fromEpochDays(it) },
                            sex = sex,
                            onFirstName = { firstName = it },
                            onLastName = { lastName = it },
                            onBirthDate = { birthEpochDay = it.toEpochDays() },
                            onSex = { sex = it },
                        )
                        Step.TREATMENTS -> TreatmentsStep(
                            medications = medications,
                            onSave = viewModel::saveMedication,
                            onDelete = viewModel::deleteMedication,
                        )
                        Step.TOUR_TODAY -> TourStep(
                            icon = Icons.Outlined.Home,
                            title = R.string.onboarding_tour_today_title,
                            text = R.string.onboarding_tour_today_text,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                (1..7).forEach { BristolIcon(it, Modifier.size(36.dp)) }
                            }
                        }
                        Step.TOUR_HISTORY -> TourStep(
                            icon = Icons.Outlined.CalendarMonth,
                            title = R.string.onboarding_tour_history_title,
                            text = R.string.onboarding_tour_history_text,
                        )
                        Step.TOUR_TRENDS -> TourStep(
                            icon = Icons.Outlined.BarChart,
                            title = R.string.onboarding_tour_trends_title,
                            text = R.string.onboarding_tour_trends_text,
                        )
                        Step.TOUR_CARE -> TourStep(
                            icon = Icons.Outlined.MedicalServices,
                            title = R.string.onboarding_tour_care_title,
                            text = R.string.onboarding_tour_care_text,
                        )
                        Step.NOTIFICATIONS -> NotificationsStep()
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (stepIndex > 0) {
                    TextButton(onClick = { stepIndex-- }) { Text(stringResource(R.string.onboarding_back)) }
                }
                Spacer(Modifier.weight(1f))
                if (skipping) {
                    OutlinedButton(onClick = next) { Text(stringResource(R.string.onboarding_skip)) }
                } else {
                    Button(onClick = next) {
                        Text(stringResource(if (isLast) R.string.start else R.string.onboarding_next))
                    }
                }
            }
        }
    }
}

@Composable
private fun StepHeader(icon: ImageVector, title: String, text: String) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(56.dp),
    )
    Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WelcomeStep() {
    StepHeader(
        icon = Icons.AutoMirrored.Outlined.MenuBook,
        title = stringResource(R.string.onboarding_title),
        text = stringResource(R.string.onboarding_text),
    )
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.onboarding_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun ProfileStep(
    firstName: String,
    lastName: String,
    birthDate: LocalDate?,
    sex: Sex?,
    onFirstName: (String) -> Unit,
    onLastName: (String) -> Unit,
    onBirthDate: (LocalDate) -> Unit,
    onSex: (Sex) -> Unit,
) {
    var pickDate by remember { mutableStateOf(false) }

    StepHeader(
        icon = Icons.Outlined.Person,
        title = stringResource(R.string.onboarding_profile_title),
        text = stringResource(R.string.onboarding_profile_text),
    )
    OutlinedTextField(
        value = firstName,
        onValueChange = onFirstName,
        label = { Text(stringResource(R.string.profile_first_name)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = lastName,
        onValueChange = onLastName,
        label = { Text(stringResource(R.string.profile_last_name)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.profile_birth_date), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = birthDate?.let(DateFormats::dayMonthYear) ?: stringResource(R.string.profile_not_set),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { pickDate = true }) { Text(stringResource(R.string.modify)) }
    }
    SexSelector(sex = sex, onChange = onSex)

    if (pickDate) {
        DatePickerDialogFor(
            initial = birthDate ?: today().minus(30, DateTimeUnit.YEAR),
            maxDate = today(),
            onDismiss = { pickDate = false },
            onPick = onBirthDate,
        )
    }
}

/** Item being edited: null = closed; `item == null` = creation. */
private data class Editing(val item: Medication?)

@Composable
private fun TreatmentsStep(
    medications: List<Medication>,
    onSave: (Medication) -> Unit,
    onDelete: (Medication) -> Unit,
) {
    var editing by remember { mutableStateOf<Editing?>(null) }

    StepHeader(
        icon = Icons.Outlined.Medication,
        title = stringResource(R.string.onboarding_treatments_title),
        text = stringResource(R.string.onboarding_treatments_text),
    )
    if (medications.isEmpty()) {
        Text(
            text = stringResource(R.string.onboarding_treatments_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    medications.forEach { medication ->
        ListItem(
            headlineContent = {
                Text(listOf(medication.name, medication.dosage).filter { it.isNotBlank() }.joinToString(" · "))
            },
            supportingContent = { Text(scheduleStatus(medication.schedule)) },
            leadingContent = { Icon(Icons.Outlined.Medication, contentDescription = null) },
            modifier = Modifier.clickable { editing = Editing(medication) },
            colors = settingsRowColors(),
        )
    }
    OutlinedButton(onClick = { editing = Editing(null) }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.treatment_add))
    }

    editing?.let { current ->
        MedicationEditorSheet(
            initial = current.item,
            onDismiss = { editing = null },
            onSave = onSave,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun TourStep(
    icon: ImageVector,
    title: Int,
    text: Int,
    illustration: @Composable () -> Unit = {},
) {
    StepHeader(icon = icon, title = stringResource(title), text = stringResource(text))
    illustration()
}

@Composable
private fun NotificationsStep() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasNotificationPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }

    StepHeader(
        icon = Icons.Outlined.NotificationsActive,
        title = stringResource(R.string.onboarding_notifications_title),
        text = stringResource(R.string.onboarding_notifications_text),
    )
    if (granted) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.onboarding_notifications_granted),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Button(
            onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_notifications_allow), textAlign = TextAlign.Center)
        }
    }
}

private fun hasNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
