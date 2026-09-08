package uz.komil.mediapro.sched

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import uz.komil.mediapro.data.ErrorLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object ScheduleManager {
    private val _plans = MutableStateFlow<List<SchedulePlan>>(emptyList())
    val plans: StateFlow<List<SchedulePlan>> = _plans.asStateFlow()

    private var appFile: File? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        appFile = File(context.filesDir, "scheduled_recordings.json")
        load()
        rescheduleAll()
    }

    private fun load() {
        val f = appFile ?: return
        if (!f.exists()) return
        try {
            val json = f.readText()
            val arr = JSONArray(json)
            val list = mutableListOf<SchedulePlan>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val typeName = o.optString("repeatType", ScheduleRepeatType.DAILY_7.name)
                val repeatType = runCatching { ScheduleRepeatType.valueOf(typeName) }.getOrDefault(ScheduleRepeatType.DAILY_7)
                val id = o.optString("id", i.toString())
                val defaultReq = (id.hashCode() and 0x7FFFFFFF)
                val requestCode = o.optInt("requestCode", defaultReq)
                list.add(
                    SchedulePlan(
                        id = id,
                        title = o.optString("title", ""),
                        url = o.optString("url", ""),
                        format = o.optString("format", "mp4"),
                        repeatType = repeatType,
                        specificDate = o.optString("specificDate", ""),
                        timeOfDay = o.optString("timeOfDay", "20:00"),
                        durationMin = o.optInt("durationMin", 60),
                        enabled = o.optBoolean("enabled", true),
                        requestCode = requestCode
                    )
                )
            }
            _plans.value = list
        } catch (t: Throwable) {
            ErrorLog.e("ScheduleManager", "load plans failed", t)
        }
    }

    suspend fun addPlan(plan: SchedulePlan) = withContext(Dispatchers.IO) {
        val current = _plans.value.filterNot { it.id == plan.id }
        val updated = current + plan
        _plans.value = updated
        save(updated)
        appContext?.let { scheduleAlarm(it, plan) }
    }

    suspend fun deletePlan(id: String) = withContext(Dispatchers.IO) {
        val plan = _plans.value.firstOrNull { it.id == id }
        val updated = _plans.value.filterNot { it.id == id }
        _plans.value = updated
        save(updated)
        if (plan != null && appContext != null) {
            cancelAlarm(appContext!!, plan)
        }
    }

    suspend fun togglePlan(id: String) = withContext(Dispatchers.IO) {
        val updated = _plans.value.map {
            if (it.id == id) it.copy(enabled = !it.enabled) else it
        }
        _plans.value = updated
        save(updated)
        val changed = updated.firstOrNull { it.id == id }
        if (changed != null && appContext != null) {
            if (changed.enabled) {
                scheduleAlarm(appContext!!, changed)
            } else {
                cancelAlarm(appContext!!, changed)
            }
        }
    }

    fun rescheduleAll() {
        val ctx = appContext ?: return
        _plans.value.forEach { plan ->
            if (plan.enabled) {
                scheduleAlarm(ctx, plan)
            } else {
                cancelAlarm(ctx, plan)
            }
        }
    }

    fun calculateNextTriggerEpochMs(plan: SchedulePlan): Long? {
        val now = System.currentTimeMillis()
        val timeParts = plan.timeOfDay.split(":")
        val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 20
        val min = timeParts.getOrNull(1)?.toIntOrNull() ?: 0

        val cal = Calendar.getInstance()
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        when (plan.repeatType) {
            ScheduleRepeatType.DAILY_7 -> {
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, min)
                if (cal.timeInMillis <= now) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                return cal.timeInMillis
            }
            ScheduleRepeatType.WORKDAYS_5 -> {
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, min)
                if (cal.timeInMillis <= now) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                return cal.timeInMillis
            }
            ScheduleRepeatType.ONCE_DATE -> {
                if (plan.specificDate.isBlank()) return null
                val cleanDate = plan.specificDate.trim().replace("/", "-").replace(".", "-")
                val parts = cleanDate.split("-")
                val parsedCal = Calendar.getInstance()
                parsedCal.set(Calendar.SECOND, 0)
                parsedCal.set(Calendar.MILLISECOND, 0)
                if (parts.size == 3) {
                    // Check if format is dd-MM-yyyy or yyyy-MM-dd
                    if (parts[0].length == 4) {
                        parsedCal.set(Calendar.YEAR, parts[0].toIntOrNull() ?: return null)
                        parsedCal.set(Calendar.MONTH, (parts[1].toIntOrNull() ?: 1) - 1)
                        parsedCal.set(Calendar.DAY_OF_MONTH, parts[2].toIntOrNull() ?: return null)
                    } else {
                        parsedCal.set(Calendar.DAY_OF_MONTH, parts[0].toIntOrNull() ?: return null)
                        parsedCal.set(Calendar.MONTH, (parts[1].toIntOrNull() ?: 1) - 1)
                        parsedCal.set(Calendar.YEAR, parts[2].toIntOrNull() ?: return null)
                    }
                    parsedCal.set(Calendar.HOUR_OF_DAY, hour)
                    parsedCal.set(Calendar.MINUTE, min)
                    return if (parsedCal.timeInMillis > now) parsedCal.timeInMillis else null
                }
                return null
            }
        }
    }

    fun scheduleAlarm(context: Context, plan: SchedulePlan) {
        if (!plan.enabled) return
        val triggerEpochMs = calculateNextTriggerEpochMs(plan)
        if (triggerEpochMs == null) {
            // Auto-disable expired single-date plans so they don't linger as phantom active
            if (plan.repeatType == ScheduleRepeatType.ONCE_DATE && plan.enabled) {
                val updated = _plans.value.map { if (it.id == plan.id) it.copy(enabled = false) else it }
                _plans.value = updated
                save(updated)
            }
            return
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ScheduleReceiver.ACTION_RECORD
            putExtra(ScheduleReceiver.EXTRA_PLAN_ID, plan.id)
        }
        val reqCode = if (plan.requestCode != 0) plan.requestCode else (plan.id.hashCode() and 0x7FFFFFFF)
        val pi = PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerEpochMs, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerEpochMs, pi)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerEpochMs, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerEpochMs, pi)
            }
        } catch (t: Throwable) {
            ErrorLog.e("ScheduleManager", "setAlarm error for ${plan.title}", t)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerEpochMs, pi)
                } else {
                    am.set(AlarmManager.RTC_WAKEUP, triggerEpochMs, pi)
                }
            }
        }
    }

    fun cancelAlarm(context: Context, plan: SchedulePlan) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ScheduleReceiver.ACTION_RECORD
            putExtra(ScheduleReceiver.EXTRA_PLAN_ID, plan.id)
        }
        val reqCode = if (plan.requestCode != 0) plan.requestCode else (plan.id.hashCode() and 0x7FFFFFFF)
        val pi = PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
            return am.canScheduleExactAlarms()
        }
        return true
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = android.net.Uri.parse("package:" + context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            runCatching { context.startActivity(intent) }
        }
    }

    private fun save(list: List<SchedulePlan>) {
        val f = appFile ?: return
        try {
            val arr = JSONArray()
            list.forEach { item ->
                val o = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("url", item.url)
                    put("format", item.format)
                    put("repeatType", item.repeatType.name)
                    put("specificDate", item.specificDate)
                    put("timeOfDay", item.timeOfDay)
                    put("durationMin", item.durationMin)
                    put("enabled", item.enabled)
                    put("requestCode", item.requestCode)
                }
                arr.put(o)
            }
            f.writeText(arr.toString())
        } catch (t: Throwable) {
            ErrorLog.e("ScheduleManager", "save plans failed", t)
        }
    }
}

