package fr.ibdjournal.app.data.local

import androidx.room.deferredTransaction
import androidx.room.immediateTransaction
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import fr.ibdjournal.app.data.model.BackupData

/** Consistent snapshot of all the data to back up (read in a transaction). */
suspend fun AppDatabase.snapshot(): BackupData = useReaderConnection { connection ->
    connection.deferredTransaction {
        BackupData(
            meals = mealDao().getAll().map { it.toMeal() },
            stools = stoolDao().getAll().map { it.toStool() },
            profile = profileDao().get()?.toProfile(),
            medications = medicationDao().getAll().map { it.toMedication() },
            checkups = checkupDao().getAll().map { it.toCheckup() },
            labResults = labResultDao().getAll().map { it.toLabResult() },
            doseLogs = doseLogDao().getAll().map { it.toDoseLog() },
            exams = examDao().getAll().map { it.toExam() },
            weights = weightDao().getAll().map { it.toWeight() },
            flares = flareDao().getAll().map { it.toFlare() },
            activityScores = activityScoreDao().getAll().map { it.toActivityScore() },
            vaccinations = vaccinationDao().getAll().map { it.toVaccination() },
            steroidCourses = steroidCourseDao().getAll().map { it.toSteroidCourse() },
            emergencyInfo = emergencyDao().get()?.toEmergencyInfo(),
            extraSymptoms = extraSymptomDao().getAll().map { it.toExtraSymptomsDay() },
            doctorQuestions = doctorQuestionDao().getAll().map { it.toDoctorQuestion() },
            appointments = appointmentDao().getAll().map { it.toAppointment() },
        )
    }
}

/**
 * Replaces the journal with [backup], in one transaction. Sections missing from earlier
 * backups (profile, treatments, checkups, results, doses, exams, weights) are kept.
 * Treatment ids are kept: confirmed doses stay linked to them.
 */
suspend fun AppDatabase.restore(backup: BackupData) {
    useWriterConnection { connection ->
        connection.immediateTransaction {
            mealDao().deleteAll()
            stoolDao().deleteAll()
            mealDao().insertAll(backup.meals.map { it.toMealEntity() })
            stoolDao().insertAll(backup.stools.map { it.toStoolEntity() })
            backup.profile?.let { profileDao().upsert(it.toProfileEntity()) }
            backup.medications?.let { list ->
                medicationDao().deleteAll()
                medicationDao().insertAll(list.map { it.toMedicationEntity() })
            }
            backup.checkups?.let { list ->
                checkupDao().deleteAll()
                checkupDao().insertAll(list.map { it.toCheckupEntity() })
            }
            backup.labResults?.let { list ->
                labResultDao().deleteAll()
                labResultDao().insertAll(list.map { it.toLabResultEntity() })
            }
            backup.doseLogs?.let { list ->
                doseLogDao().deleteAll()
                doseLogDao().insertAll(list.map { it.toDoseLogEntity() })
            }
            backup.exams?.let { list ->
                examDao().deleteAll()
                examDao().insertAll(list.map { it.toExamEntity() })
            }
            backup.weights?.let { list ->
                weightDao().deleteAll()
                weightDao().insertAll(list.map { it.toWeightEntity() })
            }
            backup.flares?.let { list ->
                flareDao().deleteAll()
                flareDao().insertAll(list.map { it.toFlareEntity() })
            }
            backup.activityScores?.let { list ->
                activityScoreDao().deleteAll()
                activityScoreDao().insertAll(list.map { it.toActivityScoreEntity() })
            }
            backup.vaccinations?.let { list ->
                vaccinationDao().deleteAll()
                vaccinationDao().insertAll(list.map { it.toVaccinationEntity() })
            }
            backup.steroidCourses?.let { list ->
                steroidCourseDao().deleteAll()
                steroidCourseDao().insertAll(list.map { it.toSteroidCourseEntity() })
            }
            backup.emergencyInfo?.let { emergencyDao().upsert(it.toEmergencyEntity()) }
            backup.extraSymptoms?.let { list ->
                extraSymptomDao().deleteAll()
                extraSymptomDao().insertAll(list.map { it.toExtraSymptomEntity() })
            }
            backup.doctorQuestions?.let { list ->
                doctorQuestionDao().deleteAll()
                doctorQuestionDao().insertAll(list.map { it.toDoctorQuestionEntity() })
            }
            backup.appointments?.let { list ->
                appointmentDao().deleteAll()
                appointmentDao().insertAll(list.map { it.toAppointmentEntity() })
            }
        }
    }
}
