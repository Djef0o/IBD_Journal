package fr.ibdjournal.app.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Locks the journal [DELAY_MS] after the app leaves the screen: a quick trip to the photo
 * picker or to another app does not ask for the PIN again.
 */
@Singleton
class AutoLock @Inject constructor(private val vault: Vault) : DefaultLifecycleObserver {

    private lateinit var scope: CoroutineScope
    private var pending: Job? = null

    /** To call from the main thread. */
    fun start(scope: CoroutineScope) {
        this.scope = scope
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        pending?.cancel()
        pending = null
    }

    override fun onStop(owner: LifecycleOwner) {
        pending?.cancel()
        pending = scope.launch {
            delay(DELAY_MS)
            vault.lock()
        }
    }

    private companion object {
        const val DELAY_MS = 60_000L
    }
}
