package uz.komil.mediapro

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import uz.komil.mediapro.data.AppPrefs
import uz.komil.mediapro.data.ErrorLog
import uz.komil.mediapro.dl.JobManager
import uz.komil.mediapro.lic.LicenseManager
import uz.komil.mediapro.util.Lang

/**
 * Application entry.
 *  1. Error journal (errors.txt, same pattern as Edge TTS Reader).
 *  2. Restore the saved UI language so the first activity attaches with it.
 *  3. Start the license/trial manager and the job manager (serialized
 *     downloads / recordings / edits), each on the shared app IO scope.
 */
class MediaProApp : Application() {

    companion object {
        lateinit var instance: MediaProApp
            private set

        /** One IO scope for long-running platform work, lives with the process. */
        val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        ErrorLog.init(this)
        // One synchronous read at boot so the first Activity is attached in the
        // saved language. DataStore reads are local & fast; a failure falls back
        // to "follow system".
        runCatching {
            Lang.tag = runBlocking { AppPrefs.currentLanguage(this@MediaProApp) }
        }
        LicenseManager.init(this, io)
        JobManager.init(this, io)
        uz.komil.mediapro.data.BrowserHistory.init(this)
        uz.komil.mediapro.sched.ScheduleManager.init(this)
    }
}
