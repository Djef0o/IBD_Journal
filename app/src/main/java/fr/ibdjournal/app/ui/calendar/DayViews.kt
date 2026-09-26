package fr.ibdjournal.app.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DinnerDining
import androidx.compose.material.icons.outlined.FreeBreakfast
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.Meal
import fr.ibdjournal.app.data.model.MealType
import fr.ibdjournal.app.data.model.Stool
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.theme.IBDJournalTheme
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

// --- Petites aides visuelles ---

fun mealIcon(type: MealType): ImageVector = when (type) {
    MealType.BREAKFAST -> Icons.Outlined.FreeBreakfast
    MealType.LUNCH -> Icons.Outlined.Restaurant
    MealType.DINNER -> Icons.Outlined.DinnerDining
}

@Composable
fun mealColor(type: MealType): Color = when (type) {
    MealType.BREAKFAST -> MaterialTheme.colorScheme.primary
    MealType.LUNCH -> MaterialTheme.colorScheme.secondary
    MealType.DINNER -> MaterialTheme.colorScheme.tertiary
}

/** Petit point coloré par type d'entrée (petit-déj, déjeuner, dîner, selle). */
@Composable
fun MealDots(meals: List<Meal>, stoolCount: Int) {
    val colors = buildList {
        if (meals.any { it.type == MealType.BREAKFAST }) add(MaterialTheme.colorScheme.primary)
        if (meals.any { it.type == MealType.LUNCH }) add(MaterialTheme.colorScheme.secondary)
        if (meals.any { it.type == MealType.DINNER }) add(MaterialTheme.colorScheme.tertiary)
        if (stoolCount > 0) add(MaterialTheme.colorScheme.error)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        colors.forEach { color ->
            Box(Modifier.size(5.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    meals: List<Meal>,
    stools: List<Stool>,
    showWeekday: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bristolColor = when {
        stools.isEmpty() -> Color.Transparent
        stools.any { it.bristolType in 5..7 } -> Color(0xFFEF6C00) // Orange
        stools.any { it.bristolType in 3..4 } -> Color(0xFF2E7D32) // Vert
        else -> Color(0xFF3E2723) // Marron foncé (1, 2)
    }

    val background = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        bristolColor
    }

    val numberColor = when {
        isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
        background == Color.Transparent -> MaterialTheme.colorScheme.onSurface
        else -> Color.White // Texte blanc sur les couleurs Bristol
    }

    Column(
        modifier = modifier.clickable(onClick = onSelect).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showWeekday) {
            Text(
                text = DateFormats.WEEKDAYS[date.dayOfWeek.value - 1],
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .padding(vertical = 2.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(background)
                .then(
                    if (isToday) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else if (isSelected) {
                         // On ajoute une petite bordure pour bien voir la sélection si elle se confond
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = numberColor,
            )
        }
        MealDots(meals, stools.size)
    }
}

/** Grille mensuelle (lundi en premier, comme en France). */
@Composable
fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    mealsByDay: Map<LocalDate, List<Meal>>,
    stoolsByDay: Map<LocalDate, List<Stool>>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = month.atDay(1)
    val leadingBlanks = first.dayOfWeek.value - 1 // lundi = 1
    val daysInMonth = month.lengthOfMonth()
    val totalCells = ((leadingBlanks + daysInMonth + 6) / 7) * 7
    val today = LocalDate.now()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            DateFormats.WEEKDAYS.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        repeat(totalCells / 7) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val dayNumber = row * 7 + col - leadingBlanks + 1
                    if (dayNumber in 1..daysInMonth) {
                        val date = month.atDay(dayNumber)
                        DayCell(
                            date = date,
                            isSelected = date == selected,
                            isToday = date == today,
                            meals = mealsByDay[date].orEmpty(),
                            stools = stoolsByDay[date].orEmpty(),
                            showWeekday = false,
                            onSelect = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Rangée des 7 jours de la semaine affichée. */
@Composable
fun WeekRow(
    weekStart: LocalDate,
    selected: LocalDate,
    mealsByDay: Map<LocalDate, List<Meal>>,
    stoolsByDay: Map<LocalDate, List<Stool>>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        (0..6).forEach { i ->
            val date = weekStart.plusDays(i.toLong())
            DayCell(
                date = date,
                isSelected = date == selected,
                isToday = date == today,
                meals = mealsByDay[date].orEmpty(),
                stools = stoolsByDay[date].orEmpty(),
                showWeekday = true,
                onSelect = { onSelect(date) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Carte d'un repas (vide ou rempli) pour la vue du jour. */
@Composable
fun MealCard(
    type: MealType,
    meal: Meal?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val photoFile = remember(meal?.photoUri) {
        meal?.photoUri?.let { File(context.filesDir, it) }
    }
    ElevatedCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = mealIcon(type),
                    contentDescription = null,
                    tint = mealColor(type),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(type.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                if (meal != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (meal == null) {
                Text(
                    text = stringResource(R.string.add_meal_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (meal.description.isNotBlank()) {
                    Text(
                        text = meal.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (photoFile != null || meal.product?.name != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (photoFile != null) {
                            AsyncImage(
                                model = photoFile,
                                contentDescription = stringResource(R.string.photo),
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        if (meal.product?.name != null) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = meal.product.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Section selles avec liste et commentaires. */
@Composable
fun StoolSection(
    stools: List<Stool>,
    onAdd: () -> Unit,
    onEdit: (Stool) -> Unit,
    onDelete: (Stool) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onAdd
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.WaterDrop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.stools),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    text = stringResource(R.string.add_stool_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (stools.isNotEmpty()) {
            stools.forEach { stool ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onEdit(stool) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BristolIcon(type = stool.bristolType, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Type ${stool.bristolType}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = DateFormats.time(stool.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row {
                                if (stool.bloodLevel > 0) {
                                    Text(
                                        text = "🩸 ${stool.bloodLevel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFD32F2F)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                if (stool.mucusLevel > 0) {
                                    Text(
                                        text = "✨ ${stool.mucusLevel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFFBC02D)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                if (stool.painLevel > 0) {
                                    Text(
                                        text = "⚡ ${stool.painLevel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF7B1FA2)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                if (stool.fatigueLevel > 0) {
                                    Text(
                                        text = "💤 ${stool.fatigueLevel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF388E3C)
                                    )
                                }
                            }
                            if (stool.comment.isNotBlank()) {
                                Text(
                                    text = stool.comment,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(onClick = { onDelete(stool) }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Vue du jour : le détail complet (repas + selles). */
@Composable
fun DayPage(
    date: LocalDate,
    mealsByDay: Map<LocalDate, List<Meal>>,
    stoolsByDay: Map<LocalDate, List<Stool>>,
    onOpenMeal: (MealType, Meal?) -> Unit,
    onOpenStoolSheet: (Stool?) -> Unit,
    onDeleteMeal: (Meal) -> Unit,
    onDeleteStool: (Stool) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(
                text = DateFormats.weekday(date),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = DateFormats.fullDate(date),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MealType.entries.forEach { type ->
            val meal = mealsByDay[date]?.firstOrNull { it.type == type }
            MealCard(
                type = type,
                meal = meal,
                onClick = { onOpenMeal(type, meal) },
                onDelete = { meal?.let(onDeleteMeal) },
            )
        }
        HorizontalDivider()
        StoolSection(
            stools = stoolsByDay[date].orEmpty(),
            onAdd = { onOpenStoolSheet(null) },
            onEdit = { onOpenStoolSheet(it) },
            onDelete = onDeleteStool,
        )
        Spacer(Modifier.height(72.dp)) // laisse la place au FAB
    }
}

/** Résumé compact de la journée sélectionnée, affiché en bas en vues mois/semaine. */
@Composable
fun DaySummaryCard(
    date: LocalDate,
    meals: List<Meal>,
    stools: List<Stool>,
    onOpenMeal: (MealType, Meal?) -> Unit,
    onOpenStoolSheet: (Stool?) -> Unit,
    onOpenDay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = DateFormats.fullDate(date),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    val stoolCount = stools.size
                    Text(
                        text = if (stoolCount == 0) {
                            stringResource(R.string.empty_day)
                        } else {
                            stringResource(R.string.stool_count, stoolCount)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onOpenDay) { Text(stringResource(R.string.open_day)) }
            }
            MealType.entries.forEach { type ->
                val meal = meals.firstOrNull { it.type == type }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenMeal(type, meal) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = mealIcon(type),
                        contentDescription = null,
                        tint = mealColor(type),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = meal?.description?.ifBlank { "—" } ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (meal != null) {
                        Text(
                            text = stringResource(R.string.edit),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { onOpenStoolSheet(stools.firstOrNull()) })
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.WaterDrop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (stools.isEmpty()) {
                        stringResource(R.string.no_stools)
                    } else {
                        val stool = stools.first()
                        "Type ${stool.bristolType}" + (if (stool.bloodLevel > 0) " 🩸" else "") + (if (stool.comment.isNotBlank()) ": ${stool.comment}" else "")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (stools.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.edit),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MonthGridPreview() {
    val today = LocalDate.now()
    IBDJournalTheme {
        Surface {
            MonthGrid(
                month = YearMonth.now(),
                selected = today,
                mealsByDay = mapOf(
                    today to listOf(
                        Meal(date = today, type = MealType.BREAKFAST, description = "Café et tartines")
                    )
                ),
                stoolsByDay = mapOf(
                    today to listOf(Stool(date = today, comment = "Normal"))
                ),
                onSelect = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DayPagePreview() {
    val today = LocalDate.now()
    IBDJournalTheme {
        Surface {
            DayPage(
                date = today,
                mealsByDay = mapOf(
                    today to listOf(
                        Meal(date = today, type = MealType.BREAKFAST, description = "Omelette"),
                        Meal(
                            date = today,
                            type = MealType.LUNCH,
                            description = "Salade César",
                            product = fr.ibdjournal.app.data.model.Product(
                                barcode = "123",
                                name = "Salade Monoprix",
                                brand = null,
                                nutriscore = null,
                                imageUrl = null,
                                ingredients = null
                            )
                        )
                    )
                ),
                stoolsByDay = mapOf(
                    today to listOf(Stool(date = today, comment = "Un peu dur"))
                ),
                onOpenMeal = { _, _ -> },
                onOpenStoolSheet = {},
                onDeleteMeal = {},
                onDeleteStool = {}
            )
        }
    }
}
