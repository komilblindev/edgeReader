package uz.komil.mediapro.sched

enum class ScheduleRepeatType {
    DAILY_7,       // 7 kunlik haftalik reja (har kuni)
    WORKDAYS_5,    // 5 kun: Dushanba - Juma (ish kunlari)
    ONCE_DATE      // Aniq sana (masalan 21-09-2026)
}

data class SchedulePlan(
    val id: String,
    val title: String,
    val url: String,
    val format: String = "mp4", // mp4, mkv, mp3
    val repeatType: ScheduleRepeatType = ScheduleRepeatType.DAILY_7,
    val specificDate: String = "", // "21-09-2026"
    val timeOfDay: String = "20:00", // "HH:mm"
    val durationMin: Int = 60,
    val enabled: Boolean = true,
    val requestCode: Int = 0
)
