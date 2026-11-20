package fr.ibdjournal.app.domain

import fr.ibdjournal.app.data.model.DoseStatus
import fr.ibdjournal.app.data.model.Medication

/**
 * Change of the intakes left when a dose goes from [previous] to [new] (null: no confirmation):
 * -1 when it becomes taken, +1 when a taken dose is undone or changed to missed, 0 otherwise.
 * A scheduled dose nobody confirmed never counts as taken.
 */
fun stockDelta(previous: DoseStatus?, new: DoseStatus?): Int =
    (if (previous == DoseStatus.TAKEN) 1 else 0) - (if (new == DoseStatus.TAKEN) 1 else 0)

/** True when [after] has just fallen to its low-stock threshold or below, from above it. */
fun crossedLowStock(before: Medication?, after: Medication): Boolean =
    after.isLowStock && before?.stockDoses != null && !before.isLowStock
