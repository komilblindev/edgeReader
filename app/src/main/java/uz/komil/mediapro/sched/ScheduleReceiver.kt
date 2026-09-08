package uz.komil.mediapro.sched

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.launch
import uz.komil.mediapro.MediaProApp
import uz.komil.mediapro.data.ErrorLog
import uz.komil.mediapro.data.OpKind
import uz.komil.mediapro.dl.JobManager
import uz.komil.mediapro.storage.MediaFolder
import uz.komil.mediapro.svc.MediaService
import uz.komil.mediapro.ui.MainActivity

/**
 * Receives two events:
 *  - BOOT_COMPLETED: re-arm all scheduled alarms after a reboot.
 *  - the scheduled-record alarm: start a live recording.
 *
 * Android 12+ forbids starting a foreground service from the background (an
 * alarm firing while the app is not visible throws
 * ForegroundServiceStartNotAllowedException). The receiver therefore asks
 * [MediaService.probeStart] first: if the OS says no, it posts a "tap to
 * record" notification instead — tapping opens [MainActivity], which retries
 * the same plan from the foreground where a FGS start is allowed. This keeps a
 * blocked plan from silently never recording, and the ONCE/repeat bookkeeping
 * only runs once the job was actually accepted.
 */
class ScheduleReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_RECORD = "uz.komil.mediapro.ACTION_SCHEDULED_RECORD"
        const val ACTION_RECORD_RETRY = "uz.komil.mediapro.ACTION_RECORD_RETRY"
        const val EXTRA_PLAN_ID = "extra_plan_id"
        private const val FALLBACK_CHANNEL_ID = "schedule_fallback"
        private const val FALLBACK_NOTIF_ID = 1704

        /**
         * Start a plan's recording now (must be running in the foreground — a
         * caller like [MainActivity], or this receiver after [MediaService.probeStart]
         * confirmed the OS accepts the start). Applies the post-trigger bookkeeping
         * (disable a one-shot plan / re-arm the next occurrence).
         */
        fun startRecordingNow(context: Context, plan: SchedulePlan) {
            if (!plan.enabled) return
            JobManager.start(
                kind = OpKind.RECORDING,
                title = plan.title.ifBlank { "Rejali yozuv" },
                sourceUrl = plan.url,
                folder = MediaFolder.RECORDINGS,
                outExt = plan.format,
                params = (plan.durationMin * 60L).toString()
            )
            MediaProApp.io.launch {
                if (plan.repeatType == ScheduleRepeatType.ONCE_DATE) {
                    ScheduleManager.togglePlan(plan.id)
                } else {
                    // Re-arm next alarm for weekly/daily/workdays.
                    ScheduleManager.scheduleAlarm(context, plan)
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED) {
            ScheduleManager.rescheduleAll()
            return
        }
        if (action == ACTION_RECORD) {
            val planId = intent.getStringExtra(EXTRA_PLAN_ID) ?: return
            val plan = ScheduleManager.plans.value.firstOrNull { it.id == planId }
            if (plan == null || !plan.enabled) {
                ErrorLog.e("ScheduleReceiver", "Plan not found or disabled: $planId")
                return
            }
            val title = plan.title.ifBlank { "Rejali yozuv" }
            if (!MediaService.probeStart(context, title)) {
                // Background FGS start blocked (app not visible). Tell the user so the
                // recording can be started by tapping the notification. A repeat plan
                // is still advanced to its next occurrence so it keeps firing; a
                // one-shot stays enabled so the tap-retry can record it now.
                showFallbackNotification(context, plan)
                if (plan.repeatType != ScheduleRepeatType.ONCE_DATE) {
                    ScheduleManager.scheduleAlarm(context, plan)
                }
                return
            }
            startRecordingNow(context, plan)
        }
    }

    private fun showFallbackNotification(ctx: Context, plan: SchedulePlan) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (nm.getNotificationChannel(FALLBACK_CHANNEL_ID) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(
                            FALLBACK_CHANNEL_ID,
                            "Rejali yozuvlar",
                            NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            description = "Rejali yozuv bildirishnomalari"
                        }
                    )
                }
            }
            // Tapping the notification retries the recording from the foreground.
            val pi = PendingIntent.getActivity(
                ctx,
                plan.id.hashCode(),
                Intent(ctx, MainActivity::class.java).apply {
                    action = ACTION_RECORD_RETRY
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_PLAN_ID, plan.id)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(ctx, FALLBACK_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(plan.title.ifBlank { "Rejali yozuv" })
                    .setContentText("Avtomatik yozuv bloklandi. Bosish bilan davom eting")
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(ctx)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(plan.title.ifBlank { "Rejali yozuv" })
                    .setContentText("Avtomatik yozuv bloklandi. Bosish bilan davom eting")
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()
            }
            nm.notify(FALLBACK_NOTIF_ID, notif)
        } catch (e: Throwable) {
            ErrorLog.e("ScheduleReceiver", "Fallback notification error", e)
        }
    }
}
