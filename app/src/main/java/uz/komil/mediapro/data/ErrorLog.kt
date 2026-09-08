package uz.komil.mediapro.data

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Append-only error journal on disk: <filesDir>/errors.txt (private). Every
 * failure across downloads, live recording, editing and conversion is written
 * here with a timestamp so the owner can see what went wrong and share the
 * file from Settings → Errors. Same idea as Edge TTS Reader.
 *
 * Thread-safe (writes are serialized) and never throws: logging must not be
 * able to crash the app.
 */
object ErrorLog {

    private const val FILE_NAME = "errors.txt"

    private val tsFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private var logDir: File? = null

    /** Must be called once, from Application.onCreate. */
    fun init(context: Context) {
        if (logDir == null) {
            logDir = context.filesDir
        }
    }

    /** Convenience shorthand for [log]. */
    @JvmStatic
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(tag, message, throwable)

    /** Append an error line. [detail] may be null; a Throwable adds its stack. */
    @JvmStatic
    fun log(tag: String, message: String, throwable: Throwable? = null) {
        val dir = logDir ?: return
        val sb = StringBuilder()
        synchronized(this) {
            sb.append(tsFmt.format(Date()))
                .append(" [").append(tag).append("] ")
                .append(message)
                .append('\n')
            if (throwable != null) {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                sb.append(sw).append('\n')
            }
        }
        try {
            val file = File(dir, FILE_NAME)
            FileOutputStream(file, true).use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
        } catch (_: Exception) {
            // Never let logging break the app.
        }
    }

    /** Full journal text for viewing/sharing, newest last. */
    fun read(): String {
        val dir = logDir ?: return ""
        return try {
            File(dir, FILE_NAME).readText(Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    fun sizeBytes(): Long {
        val dir = logDir ?: return 0L
        return try {
            File(dir, FILE_NAME).length()
        } catch (_: Exception) {
            0L
        }
    }

    fun clear() {
        val dir = logDir ?: return
        try {
            val f = File(dir, FILE_NAME)
            if (f.exists()) f.delete()
        } catch (_: Exception) {
        }
    }
}
