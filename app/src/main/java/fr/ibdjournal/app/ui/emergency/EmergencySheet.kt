package fr.ibdjournal.app.ui.emergency

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.EmergencyInfo
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.data.model.Profile
import fr.ibdjournal.app.domain.today
import fr.ibdjournal.app.notification.ReminderData
import fr.ibdjournal.app.ui.DateFormats
import kotlinx.datetime.yearsUntil

/** What the emergency sheet displays: profile identity and diagnosis, emergency fields, treatments. */
data class EmergencyData(
    val profile: Profile = Profile(),
    val info: EmergencyInfo = EmergencyInfo(),
    val medications: List<Medication> = emptyList(),
)

/** Sealed copy read while locked: null unless the user allowed the sheet on the lock screen. */
fun ReminderData.toEmergencyData(): EmergencyData? =
    emergency?.let { EmergencyData(profile ?: Profile(), it, medications) }

/** Emergency information for a first responder: large text, phone numbers open the dialer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencySheet(data: EmergencyData, onDismiss: () -> Unit) {
    val profile = data.profile
    val info = data.info
    val name = listOf(profile.firstName, profile.lastName).filter { it.isNotBlank() }.joinToString(" ")
    val treatments = data.medications.map { listOf(it.name, it.dosage).filter(String::isNotBlank).joinToString(" · ") }
    val born = profile.birthDate?.let { birth ->
        val age = birth.yearsUntil(today())
        stringResource(
            R.string.profile_born_on,
            DateFormats.dayMonthYear(birth),
            pluralStringResource(R.plurals.profile_age, age, age),
        )
    }
    val diagnosis = profile.diagnosis?.let { stringResource(it.labelRes) }
    val empty = listOf(name, diagnosis.orEmpty(), info.allergies, info.doctorName, info.doctorPhone,
        info.contactName, info.contactPhone, info.notes).all { it.isBlank() } &&
        born == null && treatments.isEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = stringResource(R.string.emergency_sheet_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            if (empty) {
                Text(stringResource(R.string.emergency_sheet_empty), style = MaterialTheme.typography.titleMedium)
            }
            Field(R.string.emergency_field_name, name)
            Field(R.string.emergency_field_born, born.orEmpty())
            Field(R.string.diagnosis_title, diagnosis.orEmpty())
            Field(R.string.emergency_field_allergies, info.allergies)
            Field(R.string.emergency_field_treatments, treatments.joinToString("\n"))
            Field(R.string.emergency_field_doctor, info.doctorName, info.doctorPhone)
            Field(R.string.emergency_field_contact, info.contactName, info.contactPhone)
            Field(R.string.emergency_field_notes, info.notes)
        }
    }
}

/** Label, big value and, if given, a call button; nothing at all when everything is blank. */
@Composable
private fun Field(labelRes: Int, value: String, phone: String = "") {
    if (value.isBlank() && phone.isBlank()) return
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(labelRes).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (value.isNotBlank()) {
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
        if (phone.isNotBlank()) {
            FilledTonalButton(onClick = { dial(context, phone) }) {
                Icon(Icons.Outlined.Call, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(phone, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Opens the dialer with the number filled in: never places the call. */
private fun dial(context: android.content.Context, phone: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone.trim(), null)))
    } catch (_: ActivityNotFoundException) {
        // No dialer on this device.
    }
}
