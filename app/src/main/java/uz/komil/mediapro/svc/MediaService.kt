package uz.komil.mediapro.svc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import uz.komil.mediapro.R
import uz.komil.mediapro.data.MediaRecord
import uz.komil.mediapro.ui.MainActivity

/**
 * Foreground service that hosts long-running media jobs (downloads, live
 * recording, FFmpeg edits). Serialized behind JobManager, so at most one job
 * is active and this service shows a single progress notification.
 *
 * Android 14+ requires the foreground-service type both in the manifest AND
 * on the startForeground call — this file and the manifest agree on
 * dataSync|mediaProcessing, so downloads (data sync) and FFmpeg processing
 * (media processing) both start cleanly.
 */
class MediaService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        synchronized(this) { if (running === null) running = this }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelfQuiet(this)
                return START_NOT_STICKY
            }
            else -> {
                intent?.getStringExtra(EXTRA_TITLE)?.let { title = it }
                goForeground(this)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        synchronized(this) { if (running === this) running = null }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "media_jobs"
        const val NOTIF_ID = 1703
        private const val ACTION_START = "uz.komil.mediapro.action.START"
        private const val ACTION_STOP = "uz.komil.mediapro.action.STOP"
        private const val EXTRA_TITLE = "title"

        @Volatile private var running: MediaService? = null
        @Volatile private var title: String = ""
        @Volatile private var pct: Int = -1
        @Volatile private var lastCtx: Context? = null
        @Volatile private var lastNotifAt = 0L

        /** Kick off the foreground service for a job. Safe to call repeatedly. */
        fun startFor(c: Context, rec: MediaRecord): Boolean {
            title = rec.title
            lastCtx = c.applicationContext
            return startIntent(c, rec.title)
        }

        /**
         * Raise the foreground service from the CURRENT state and report whether
         * the platform accepted it. Background callers (an alarm/boot receiver on
         * Android 12+ when the app is not visible) get
         * ForegroundServiceStartNotAllowedException here — returning false lets the
         * caller react (e.g. post a "tap to record" notification) instead of the job
         * silently never starting.
         */
        fun probeStart(c: Context, title: String): Boolean {
            lastCtx = c.applicationContext
            this.title = title
            return startIntent(c, title)
        }

        private fun startIntent(c: Context, jobTitle: String): Boolean {
            val i = Intent(c, MediaService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_TITLE, jobTitle)
            return try {
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i)
                else c.startService(i)
                true
            } catch (t: Throwable) {
                uz.komil.mediapro.data.ErrorLog.e("MediaService", "startForegroundService blocked: ${t.message}", t)
                false
            }
        }

        /** Update the progress notification (throttled to ~4 Hz). */
        fun publishProgress(id: String, frac: Float, jobName: String) {
            title = jobName
            val newPct = (frac * 100).toInt().coerceIn(0, 100)
            val now = SystemClock.elapsedRealtime()
            // Only push at most every 250 ms and only on an actual change.
            if (newPct == pct && now - lastNotifAt < 250) return
            pct = newPct
            lastNotifAt = now
            lastCtx?.let { ctx ->
                try {
                    ensureChannel(ctx)
                    ctx.getSystemService(NotificationManager::class.java)
                        ?.notify(NOTIF_ID, notification(ctx))
                } catch (_: Exception) {}
            }
        }

        /** Stop the service + notification after the active job finishes. */
        fun stop() {
            val s: MediaService?
            synchronized(this) { s = running }
            if (s != null) {
                stopSelfQuiet(s)
            } else {
                lastCtx?.let { ctx ->
                    // Service not live (killed early): just clear the notif.
                    try {
                        ctx.getSystemService(NotificationManager::class.java)
                            ?.cancel(NOTIF_ID)
                    } catch (_: Exception) {}
                }
            }
            pct = -1
        }

        // ---- internals ----

        private fun goForeground(s: MediaService) {
            ensureChannel(s)
            val n = notification(s)
            if (Build.VERSION.SDK_INT >= 29) {
                val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
                s.startForeground(NOTIF_ID, n, type)
            } else {
                s.startForeground(NOTIF_ID, n)
            }
        }

        private fun notification(ctx: Context): Notification {
            val pi = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val text = title.ifBlank { ctx.getString(R.string.notif_running) }
            val content = if (pct in 0..100) "$text · $pct%" else text
            return Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(ctx.getString(R.string.notif_running))
                .setContentText(content)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, pct.coerceAtLeast(0), pct < 0)
                .build()
        }

        private fun ensureChannel(ctx: Context) {
            try {
                val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(
                            CHANNEL_ID, ctx.getString(R.string.notif_channel),
                            NotificationManager.IMPORTANCE_LOW
                        ).apply {
                            description = ctx.getString(R.string.notif_running)
                            setShowBadge(false)
                        }
                    )
                }
            } catch (_: Exception) {}
        }

        private fun stopSelfQuiet(s: Service) {
            try {
                s.stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {}
            s.stopSelf()
            synchronized(s) { if (running === s) running = null }
        }
    }
}
