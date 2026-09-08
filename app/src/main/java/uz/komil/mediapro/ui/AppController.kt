package uz.komil.mediapro.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Tiny process-wide bridge so MainActivity (a non-Compose caller) can ask the
 * running composition to open a URL in the built-in browser. MainActivity reads
 * it from onNewIntent; MediaProRoot observes it and hands the value to
 * BrowserScreen, then clears it.
 */
object AppController {
    var pendingExternalUrl by mutableStateOf<String?>(null)

    fun openExternalUrl(url: String?) {
        pendingExternalUrl = url
    }
}
