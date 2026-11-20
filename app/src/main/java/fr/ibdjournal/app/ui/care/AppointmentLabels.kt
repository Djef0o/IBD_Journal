package fr.ibdjournal.app.ui.care

import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.AppointmentKind

/** Name of the kind of doctor, in the app's language. */
internal fun AppointmentKind.nameRes(): Int = when (this) {
    AppointmentKind.GASTROENTEROLOGIST -> R.string.appointment_kind_gastroenterologist
    AppointmentKind.DERMATOLOGIST -> R.string.appointment_kind_dermatologist
    AppointmentKind.OPHTHALMOLOGIST -> R.string.appointment_kind_ophthalmologist
    AppointmentKind.RHEUMATOLOGIST -> R.string.appointment_kind_rheumatologist
    AppointmentKind.DIETITIAN -> R.string.appointment_kind_dietitian
    AppointmentKind.GENERAL_PRACTITIONER -> R.string.appointment_kind_general_practitioner
    AppointmentKind.OTHER -> R.string.appointment_kind_other
}
