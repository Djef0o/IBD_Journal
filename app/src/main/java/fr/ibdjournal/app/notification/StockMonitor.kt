package fr.ibdjournal.app.notification

import fr.ibdjournal.app.data.local.toMedication
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.domain.crossedLowStock
import fr.ibdjournal.app.security.Vault
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Sends the low-stock notification the moment a counted treatment falls to its threshold, whatever
 * confirmed the dose (the app, a notification button, or answers waiting for the unlock). Only a
 * fall from above the threshold notifies: starting the app or unlocking with a low stock does not.
 */
@Singleton
class StockMonitor @Inject constructor(
    private val vault: Vault,
    private val reminders: TreatmentReminderScheduler,
) {
    private var known: Map<Long, Medication> = emptyMap()

    /** To call once, from the application. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            vault.observe { it.medicationDao().observeAll() }.collect { entities ->
                val current = entities.map { it.toMedication() }
                current.filter { crossedLowStock(known[it.id], it) }.forEach { reminders.showLowStock(it) }
                known = current.associateBy { it.id }
            }
        }
    }
}
