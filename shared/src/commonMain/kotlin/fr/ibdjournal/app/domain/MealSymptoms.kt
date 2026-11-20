package fr.ibdjournal.app.domain

import fr.ibdjournal.app.data.model.Meal
import fr.ibdjournal.app.data.model.Stool
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * A food that came before many bad days. [occurrences]: days it was eaten, with stools logged
 * that day or the next; [badRate]: share of those followed by symptoms; [baseline]: the same
 * share for all days with meals. An observation on logged data, not a cause.
 */
data class FoodLink(val item: String, val occurrences: Int, val badRate: Double, val baseline: Double)

/** Minimum days a food must have been eaten before it can appear. */
const val MIN_FOOD_OCCURRENCES = 4

/** Minimum gap above the baseline (share of days) to be reported. */
private const val MIN_EXCESS = 0.2

/** Bad day: liquid stool (Bristol 6–7), blood, or pain of 3 and above. */
internal fun Stool.isBad(): Boolean = isRealStool && (bristolType >= 6 || bloodLevel >= 1 || painLevel >= 3)

/** Foods of a meal: product name and description items split on commas, semicolons, "+" and line breaks. */
internal fun mealItems(meal: Meal): Set<String> {
    val items = mutableSetOf<String>()
    meal.product?.name?.let { items += it }
    items += meal.description.split(',', ';', '+', '\n')
    return items.map { it.trim().lowercase().replace(Regex("\\s+"), " ") }.filter { it.length >= 3 }.toSet()
}

/**
 * Foods eaten on days that were followed (same day or next day) by a bad day clearly more often
 * than usual, most striking first, at most [limit].
 */
fun foodSymptomLinks(meals: List<Meal>, stools: List<Stool>, limit: Int = 5): List<FoodLink> {
    val stoolsByDay = stools.groupBy { it.date }
    /** Outcome of the meal day [day]: null if no stool was logged that day or the next. */
    fun outcome(day: LocalDate): Boolean? {
        val window = listOf(day, day.plus(1, DateTimeUnit.DAY)).mapNotNull { stoolsByDay[it] }
        if (window.isEmpty()) return null
        return window.any { dayStools -> dayStools.any { it.isBad() } }
    }

    val mealsByDay = meals.groupBy { it.date }
    val outcomes = mealsByDay.keys.associateWith(::outcome).filterValues { it != null }.mapValues { it.value == true }
    if (outcomes.isEmpty()) return emptyList()
    val baseline = outcomes.values.count { it }.toDouble() / outcomes.size

    val daysByItem = mutableMapOf<String, MutableSet<LocalDate>>()
    for ((day, dayMeals) in mealsByDay) {
        if (day !in outcomes) continue
        dayMeals.flatMap(::mealItems).forEach { daysByItem.getOrPut(it) { mutableSetOf() } += day }
    }
    return daysByItem
        .filterValues { it.size >= MIN_FOOD_OCCURRENCES }
        .map { (item, days) -> FoodLink(item, days.size, days.count { outcomes.getValue(it) }.toDouble() / days.size, baseline) }
        .filter { it.badRate - it.baseline >= MIN_EXCESS }
        .sortedWith(compareByDescending<FoodLink> { it.badRate - it.baseline }.thenByDescending { it.occurrences })
        .take(limit)
}
