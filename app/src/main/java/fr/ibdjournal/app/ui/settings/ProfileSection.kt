package fr.ibdjournal.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.ProfileRepository
import fr.ibdjournal.app.data.model.Profile
import fr.ibdjournal.app.data.model.Sex
import fr.ibdjournal.app.domain.today
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.labelRes
import fr.ibdjournal.app.ui.care.DatePickerDialogFor
import fr.ibdjournal.app.ui.care.FieldRow
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.yearsUntil

@HiltViewModel
class ProfileSettingsViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {

    val profile: StateFlow<Profile> = repository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Profile())

    fun save(profile: Profile) {
        viewModelScope.launch { repository.saveProfile(profile) }
    }
}

/** Identité du patient (nom, date de naissance), reprise en tête du rapport médecin. */
@Composable
fun ProfileSection(modifier: Modifier = Modifier) {
    val viewModel: ProfileSettingsViewModel = hiltViewModel()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    val fullName = listOf(profile.firstName, profile.lastName).filter { it.isNotBlank() }.joinToString(" ")

    Column(modifier) {
        Text(
            text = stringResource(R.string.profile_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        ListItem(
            leadingContent = { Icon(Icons.Outlined.Person, contentDescription = null) },
            headlineContent = { Text(fullName.ifEmpty { stringResource(R.string.profile_empty_name) }) },
            supportingContent = {
                val birth = profile.birthDate?.let { birthDate ->
                    val age = birthDate.yearsUntil(today())
                    stringResource(
                        R.string.profile_born_on,
                        DateFormats.dayMonthYear(birthDate),
                        pluralStringResource(R.plurals.profile_age, age, age),
                    )
                } ?: stringResource(R.string.profile_birth_date_missing)
                val sex = profile.sex?.let { stringResource(it.labelRes) }
                    ?: stringResource(R.string.profile_sex_missing)
                Text("$birth\n$sex")
            },
            trailingContent = {
                TextButton(onClick = { editing = true }) { Text(stringResource(R.string.modify)) }
            },
            modifier = Modifier.clickable { editing = true },
            colors = settingsRowColors(),
        )
    }

    if (editing) {
        ProfileEditDialog(profile = profile, onDismiss = { editing = false }, onSave = viewModel::save)
    }
}

@Composable
private fun ProfileEditDialog(
    profile: Profile,
    onDismiss: () -> Unit,
    onSave: (Profile) -> Unit,
) {
    var firstName by remember { mutableStateOf(profile.firstName) }
    var lastName by remember { mutableStateOf(profile.lastName) }
    var birthDate by remember { mutableStateOf(profile.birthDate) }
    var sex by remember { mutableStateOf(profile.sex) }
    var pickDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text(stringResource(R.string.profile_first_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text(stringResource(R.string.profile_last_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                FieldRow(
                    label = stringResource(R.string.profile_birth_date),
                    value = birthDate?.let(DateFormats::dayMonthYear) ?: stringResource(R.string.profile_not_set),
                ) {
                    TextButton(onClick = { pickDate = true }) { Text(stringResource(R.string.modify)) }
                }
                SexSelector(sex = sex, onChange = { sex = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(Profile(firstName.trim(), lastName.trim(), birthDate, sex))
                    onDismiss()
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (pickDate) {
        DatePickerDialogFor(
            initial = birthDate ?: today().minus(30, DateTimeUnit.YEAR),
            maxDate = today(),
            onDismiss = { pickDate = false },
            onPick = { birthDate = it },
        )
    }
}

/** Femme / Homme : donnée d'identité, reprise dans le rapport. */
@Composable
fun SexSelector(sex: Sex?, onChange: (Sex) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.profile_sex), style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Sex.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = sex == option,
                    onClick = { onChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, Sex.entries.size),
                ) { Text(stringResource(option.labelRes)) }
            }
        }
    }
}
