package uz.komil.mediapro.util

import java.util.Locale

/**
 * HH:MM:SS (and optional .mmm) time handling shared by every editor screen
 * and the FFmpeg command builder (spec sections 4 & 13-17). All times in the
 * app are expressed as this one type.
 */
data class Hms(
    val h: Int,
    val m: Int,
    val s: Int,
    val ms: Int = 0 // 0..999
) : Comparable<Hms> {

    val totalMs: Long
        get() = ((h * 3600L + m * 60L + s) * 1000L) + ms

    operator fun plus(other: Hms): Hms = fromMs(totalMs + other.totalMs)
    operator fun minus(other: Hms): Hms = fromMs((totalMs - other.totalMs).coerceAtLeast(0))

    fun toClock(): String = String.format(Locale.US, "%02d:%02d:%02d", h, m, s)

    /** FFmpeg accepts HH:MM:SS[.mmm]. */
    fun toFfmpeg(): String =
        if (ms == 0) toClock()
        else String.format(Locale.US, "%02d:%02d:%02d.%03d", h, m, s, ms)

    override fun compareTo(other: Hms): Int = totalMs.compareTo(other.totalMs)

    companion object {
        val ZERO = Hms(0, 0, 0)

        fun fromMs(ms: Long): Hms {
            var t = ms.coerceAtLeast(0)
            val h = (t / 3_600_000).toInt(); t %= 3_600_000
            val m = (t / 60_000).toInt(); t %= 60_000
            val s = (t / 1000).toInt(); t %= 1000
            return Hms(h, m, s, t.toInt())
        }

        private val RE = Regex("""^\s*(\d+):(\d{1,2}):(\d{1,2})(?:[.,](\d{1,3}))?\s*$""")
        private val RE_MS = Regex("""^\s*(\d{1,3})\s*$""")

        /**
         * Parse "HH:MM:SS[.mmm]" or "MM:SS" or bare seconds. Returns null when
         * the text is not a valid time. Minute/second fields are clamped to
         * 0..59 (typing 99:00:00 minutes is auto-corrected to 1:39:00).
         */
        fun parse(raw: String): Hms? {
            val t = raw.trim()
            if (t.isEmpty()) return null
            RE.find(t)?.let { mt ->
                var h = mt.groupValues[1].toLong()
                var m = mt.groupValues[2].toLong()
                var s = mt.groupValues[3].toLong()
                val frac = mt.groupValues[4].ifEmpty { "0" }.padEnd(3, '0')
                h += m / 60; m %= 60
                h += s / 60; s %= 60
                val ms = frac.toInt()
                return Hms(h.toInt(), m.toInt(), s.toInt(), ms)
            }
            // bare digits = seconds? No: a plain integer field means HH:MM is
            // safer to reject, but mm:ss without hours is common. Accept 5+ as ss
            // only when the user typed a colon-less number and it fits a clock mask.
            return null
        }
    }
}

/** Duration formatting for humans ("1 soat 2 daqiqa" handled at UI layer). */
object TimeFmt {
    fun compact(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.US, "%d:%02d", m, sec)
    }
}
