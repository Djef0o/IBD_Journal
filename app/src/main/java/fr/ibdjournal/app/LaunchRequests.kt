package fr.ibdjournal.app

import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the app was asked to open from outside (the home-screen button): kept until the screen
 * that handles it has consumed it, so that it also works after the PIN screen.
 */
@Singleton
class LaunchRequests @Inject constructor() {

    private val _quickStool = MutableStateFlow(false)

    /** True while a request to open the quick stool entry is waiting. */
    val quickStool: StateFlow<Boolean> = _quickStool.asStateFlow()

    fun handle(intent: Intent?) {
        if (intent?.action == ACTION_QUICK_STOOL) {
            _quickStool.value = true
            // A recreated activity would otherwise replay the request.
            intent.action = null
        }
    }

    fun consumeQuickStool() {
        _quickStool.value = false
    }

    companion object {
        const val ACTION_QUICK_STOOL = "fr.ibdjournal.app.ACTION_QUICK_STOOL"
    }
}
