package fr.ibdjournal.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import fr.ibdjournal.app.data.model.Checkup
import fr.ibdjournal.app.data.model.DoseLog
import fr.ibdjournal.app.data.model.DoseStatus
import fr.ibdjournal.app.data.model.LabResult
import fr.ibdjournal.app.data.model.LabQualifier
import fr.ibdjournal.app.data.model.Sex
import fr.ibdjournal.app.data.model.LabTest
import fr.ibdjournal.app.data.model.CheckupType
import fr.ibdjournal.app.data.model.Meal
import fr.ibdjournal.app.data.model.MealType
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.data.model.Product
import fr.ibdjournal.app.data.model.Profile
import fr.ibdjournal.app.data.model.ReminderSchedule
import fr.ibdjournal.app.data.model.RepeatUnit
import fr.ibdjournal.app.data.model.Stool
import fr.ibdjournal.app.data.model.nowEpochMillis
import kotlinx.datetime.LocalDate

@Entity(tableName = "meals", indices = [Index("dateEpochDay")])
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val mealType: MealType,
    val description: String = "",
    val photoPath: String? = null,
    val productBarcode: String? = null,
    val productName: String? = null,
    val createdAt: Long = nowEpochMillis(),
)

@Entity(tableName = "stools", indices = [Index("dateEpochDay")])
data class StoolEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val bristolType: Int,
    val bloodLevel: Int,
    val mucusLevel: Int,
    val painLevel: Int,
    val fatigueLevel: Int,
    val comment: String = "",
    val createdAt: Long = nowEpochMillis(),
    /** Ajoutées en version 5 : défaut SQL identique à celui de la migration 4 → 5. */
    @ColumnInfo(defaultValue = "0") val urgency: Boolean = false,
    @ColumnInfo(defaultValue = "0") val nocturnal: Boolean = false,
)

/** Cache des produits Open Food Facts, par code-barres. */
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val barcode: String,
    val name: String?,
    val brand: String?,
    val nutriscore: String?,
    val imageUrl: String?,
    val ingredients: String?,
    val fetchedAt: Long = nowEpochMillis(),
)

/**
 * Repas avec la fiche produit complète du cache Open Food Facts. Le repas ne stocke
 * que code-barres et nom ; image, marque et Nutri-Score viennent de `products`.
 */
data class MealWithProduct(
    @Embedded val meal: MealEntity,
    @Relation(parentColumn = "productBarcode", entityColumn = "barcode")
    val product: ProductEntity?,
)

// --- Mappeurs entité <-> modèle de domaine ---

fun MealWithProduct.toMeal(): Meal {
    val base = meal.toMeal()
    return if (product != null) base.copy(product = product.toProduct()) else base
}

fun MealEntity.toMeal() = Meal(
    id = id,
    date = LocalDate.fromEpochDays(dateEpochDay),
    type = mealType,
    description = description,
    photoUri = photoPath,
    product = productBarcode?.let {
        Product(
            barcode = it,
            name = productName,
            brand = null,
            nutriscore = null,
            imageUrl = null,
            ingredients = null
        )
    },
)

fun Meal.toMealEntity() = MealEntity(
    id = id,
    dateEpochDay = date.toEpochDays(),
    mealType = type,
    description = description,
    photoPath = photoUri,
    productBarcode = product?.barcode,
    productName = product?.name,
)

fun StoolEntity.toStool() = Stool(
    id = id,
    date = LocalDate.fromEpochDays(dateEpochDay),
    bristolType = bristolType,
    bloodLevel = bloodLevel,
    mucusLevel = mucusLevel,
    painLevel = painLevel,
    fatigueLevel = fatigueLevel,
    comment = comment,
    createdAt = createdAt,
    urgency = urgency,
    nocturnal = nocturnal,
)

fun Stool.toStoolEntity() = StoolEntity(
    id = id,
    dateEpochDay = date.toEpochDays(),
    bristolType = bristolType,
    bloodLevel = bloodLevel,
    mucusLevel = mucusLevel,
    painLevel = painLevel,
    fatigueLevel = fatigueLevel,
    comment = comment,
    createdAt = createdAt,
    urgency = urgency,
    nocturnal = nocturnal,
)

/** Résultats d'analyses (version 5 ; seuil et valeurs de référence en version 6). */
@Entity(tableName = "lab_results", indices = [Index("test", "dateEpochDay")])
data class LabResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val test: LabTest,
    val value: Double,
    val dateEpochDay: Long,
    val note: String,
    /** null = résultat exact : colonne nullable, ajoutée sans valeur par défaut. */
    val qualifier: LabQualifier? = null,
    val referenceLow: Double? = null,
    val referenceHigh: Double? = null,
)

/** Prises confirmées ou oubliées (version 5) : une ligne par traitement et par occurrence. */
@Entity(
    tableName = "dose_logs",
    indices = [Index(value = ["medicationId", "scheduledAt"], unique = true)],
)
data class DoseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val scheduledAt: Long,
    val status: DoseStatus,
    val loggedAt: Long,
)

fun LabResultEntity.toLabResult() = LabResult(
    id = id,
    test = test,
    value = value,
    date = LocalDate.fromEpochDays(dateEpochDay),
    note = note,
    qualifier = qualifier ?: LabQualifier.EXACT,
    referenceLow = referenceLow,
    referenceHigh = referenceHigh,
)

fun LabResult.toLabResultEntity() = LabResultEntity(
    id = id,
    test = test,
    value = value,
    dateEpochDay = date.toEpochDays(),
    note = note,
    qualifier = qualifier.takeIf { it != LabQualifier.EXACT },
    referenceLow = referenceLow,
    referenceHigh = referenceHigh,
)

fun DoseLogEntity.toDoseLog() = DoseLog(
    id = id,
    medicationId = medicationId,
    scheduledAt = scheduledAt,
    status = status,
    loggedAt = loggedAt,
)

fun DoseLog.toDoseLogEntity() = DoseLogEntity(
    id = id,
    medicationId = medicationId,
    scheduledAt = scheduledAt,
    status = status,
    loggedAt = loggedAt,
)

fun ProductEntity.toProduct() = Product(
    barcode = barcode,
    name = name,
    brand = brand,
    nutriscore = nutriscore,
    imageUrl = imageUrl,
    ingredients = ingredients,
)

fun Product.toProductEntity() = ProductEntity(
    barcode = barcode,
    name = name,
    brand = brand,
    nutriscore = nutriscore,
    imageUrl = imageUrl,
    ingredients = ingredients,
)

/** Une seule ligne (id = [ProfileEntity.SINGLE_ID]) : l'app ne gère qu'un profil. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = SINGLE_ID,
    val firstName: String,
    val lastName: String,
    val birthDateEpochDay: Long?,
    /** Ajouté en version 6. */
    val sex: Sex? = null,
) {
    companion object {
        const val SINGLE_ID = 1
    }
}

/** Colonnes de planification partagées par traitements et contrôles. */
data class ScheduleColumns(
    val reminderEnabled: Boolean,
    val startDateEpochDay: Long,
    val hour: Int,
    val minute: Int,
    val repeatEvery: Int,
    val repeatUnit: RepeatUnit,
)

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosage: String,
    @Embedded val schedule: ScheduleColumns,
    val createdAt: Long = nowEpochMillis(),
    /** Ajoutées en version 4 : défaut SQL identique à celui de la migration 3 → 4. */
    @ColumnInfo(defaultValue = "0") val refillAlert: Boolean = false,
    val stockEndEpochDay: Long? = null,
)

@Entity(tableName = "checkups")
data class CheckupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: CheckupType,
    val note: String,
    @Embedded val schedule: ScheduleColumns,
    val createdAt: Long = nowEpochMillis(),
)

fun ProfileEntity.toProfile() = Profile(
    firstName = firstName,
    lastName = lastName,
    birthDate = birthDateEpochDay?.let { LocalDate.fromEpochDays(it) },
    sex = sex,
)

fun Profile.toProfileEntity() = ProfileEntity(
    firstName = firstName,
    lastName = lastName,
    birthDateEpochDay = birthDate?.toEpochDays(),
    sex = sex,
)

fun ScheduleColumns.toSchedule() = ReminderSchedule(
    enabled = reminderEnabled,
    startDate = LocalDate.fromEpochDays(startDateEpochDay),
    hour = hour,
    minute = minute,
    repeatEvery = repeatEvery,
    repeatUnit = repeatUnit,
)

fun ReminderSchedule.toColumns() = ScheduleColumns(
    reminderEnabled = enabled,
    startDateEpochDay = startDate.toEpochDays(),
    hour = hour,
    minute = minute,
    repeatEvery = repeatEvery,
    repeatUnit = repeatUnit,
)

fun MedicationEntity.toMedication() = Medication(
    id = id,
    name = name,
    dosage = dosage,
    schedule = schedule.toSchedule(),
    refillAlert = refillAlert,
    stockEndDate = stockEndEpochDay?.let { LocalDate.fromEpochDays(it) },
)

fun Medication.toMedicationEntity() = MedicationEntity(
    id = id,
    name = name,
    dosage = dosage,
    schedule = schedule.toColumns(),
    refillAlert = refillAlert,
    stockEndEpochDay = stockEndDate?.toEpochDays(),
)

fun CheckupEntity.toCheckup() = Checkup(
    id = id,
    type = type,
    note = note,
    schedule = schedule.toSchedule(),
)

fun Checkup.toCheckupEntity() = CheckupEntity(
    id = id,
    type = type,
    note = note,
    schedule = schedule.toColumns(),
)
