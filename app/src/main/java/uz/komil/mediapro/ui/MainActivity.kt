package uz.komil.mediapro.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import uz.komil.mediapro.MediaProApp
import uz.komil.mediapro.data.AppPrefs
import uz.komil.mediapro.data.ErrorLog
import uz.komil.mediapro.sched.ScheduleManager
import uz.komil.mediapro.sched.ScheduleReceiver
import uz.komil.mediapro.ui.theme.MediaProTheme
import uz.komil.mediapro.util.Lang

/**
 * Single activity for the whole app. Two extra entry points are accepted:
 *  - ACTION_SEND text/plain  → a shared URL to open in the built-in browser
 *  - ACTION_VIEW http/https  → "open in MediaPro" from the system browser
 * The URL is forwarded to [AppController], which the root composable observes.
 */
class MainActivity : ComponentActivity() {

    companion object {
        @Volatile var current: MainActivity? = null
            private set

        /** Switch UI language: persist async, recreate so new resources apply. */
        fun changeLanguage(tag: String) {
            val act = current ?: return
            Lang.tag = tag
            MediaProApp.io.launch {
                runCatching { AppPrefs.setLanguage(act.applicationContext, tag) }
            }
            act.recreate()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        current = this

        // Foreground jobs show a notification; ask once on 13+ (harmless if denied).
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val incomingUrl = extractIncomingUrl(intent)
        runScheduledRetry(intent)
        setContent {
            MediaProTheme {
                MediaProRoot(initialUrl = incomingUrl)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url = extractIncomingUrl(intent)
        if (url != null) {
            AppController.openExternalUrl(url)
        }
        runScheduledRetry(intent)
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    /**
     * A scheduled recording whose background FGS start was blocked posts a
     * "tap to record" notification that opens this activity with
     * ACTION_RECORD_RETRY. Now in the foreground, start the missed recording.
     */
    private fun runScheduledRetry(intent: Intent?) {
        if (intent?.action != ScheduleReceiver.ACTION_RECORD_RETRY) return
        val planId = intent.getStringExtra(ScheduleReceiver.EXTRA_PLAN_ID) ?: return
        val plan = ScheduleManager.plans.value.firstOrNull { it.id == planId } ?: return
        ScheduleReceiver.startRecordingNow(this, plan)
    }

    private fun extractIncomingUrl(intent: Intent?): String? {
        intent ?: return null
        return try {
            when (intent.action) {
                Intent.ACTION_SEND ->
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let { extractHttp(it) }
                Intent.ACTION_VIEW ->
                    intent.data?.toString()
                else -> null
            }
        } catch (t: Throwable) {
            ErrorLog.log("MainActivity", "Failed to parse incoming intent", t)
            null
        }
    }

    private fun extractHttp(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed.split(Regex("\\s+")).first()
        }
        val m = Regex("https?://\\S+").find(text)
        return m?.value
    }
}
