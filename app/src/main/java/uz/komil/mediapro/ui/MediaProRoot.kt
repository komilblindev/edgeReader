package uz.komil.mediapro.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uz.komil.mediapro.R
import uz.komil.mediapro.data.AppPrefs
import uz.komil.mediapro.ui.screen.BrowserScreen
import uz.komil.mediapro.ui.screen.DownloadsScreen
import uz.komil.mediapro.ui.screen.EditorScreen
import uz.komil.mediapro.ui.screen.SettingsScreen

/** Top-level destinations shown as bottom-nav tabs. */
enum class AppTab(val icon: ImageVector, val labelRes: Int) {
    BROWSER(Icons.Filled.Home, R.string.tab_browser),
    DOWNLOADS(Icons.AutoMirrored.Filled.List, R.string.tab_downloads),
    EDITOR(Icons.Filled.Create, R.string.tab_editor),
    SETTINGS(Icons.Filled.Settings, R.string.tab_settings)
}

/**
 * Root composable: holds the selected tab, observes [AppController] for URLs
 * handed in by external intents (Share / VIEW) and routes to the screens.
 */
@Composable
fun MediaProRoot(initialUrl: String?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(AppTab.BROWSER) }

    LaunchedEffect(Unit) {
        if (initialUrl == null) {
            val saved = AppPrefs.lastTabFlow(ctx).first()
            val match = AppTab.entries.firstOrNull { it.name == saved }
            if (match != null) tab = match
        }
    }

    // External (activity-level) URL requests — both a cold start and onNewIntent.
    LaunchedEffect(initialUrl) {
        if (initialUrl != null) {
            tab = AppTab.BROWSER
            AppController.pendingExternalUrl = initialUrl
        }
    }
    // Reading the singleton's mutableStateOf-backed property inside composition
    // subscribes this scope to its changes (a `by` here would be wrong — the
    // right side is the value, not a State).
    val externalUrl = AppController.pendingExternalUrl
    LaunchedEffect(externalUrl) {
        if (externalUrl != null) tab = AppTab.BROWSER
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = {
                            tab = t
                            scope.launch { AppPrefs.setLastTab(ctx, t.name) }
                        },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.labelRes)) }
                    )
                }
            }
        }
    ) { padding ->
        when (tab) {
            AppTab.BROWSER -> BrowserScreen(
                contentPadding = padding,
                externalUrl = externalUrl,
                onExternalUrlConsumed = { AppController.pendingExternalUrl = null }
            )
            AppTab.DOWNLOADS -> DownloadsScreen(contentPadding = padding)
            AppTab.EDITOR -> EditorScreen(contentPadding = padding)
            AppTab.SETTINGS -> SettingsScreen(contentPadding = padding)
        }
    }
}
