package uz.komil.mediapro.dl

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import uz.komil.mediapro.data.ErrorLog
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * One facade over FFmpegKit for every long media operation (downloads,
 * remux/conversion, trim/cut/mix). FFmpeg natively demuxes HLS (.m3u8),
 * DASH (.mpd) and plain URLs and decrypts AES-128 HLS, so the download engine
 * does not re-implement segment fetching — it classifies the source, chooses a
 * quality variant, builds the command and streams a 0..1 progress by parsing
 * FFmpeg's `time=` logs against a known/estimated duration.
 */
object MediaEngine {

    fun ffmpegVersion(): String {
        return try {
            val s = FFmpegKitConfig.getFFmpegVersion()
            s ?: "ffmpeg"
        } catch (e: Exception) { "ffmpeg" }
    }

    /** Outcome of a run. */
    sealed class Out {
        data class Done(val outFile: File) : Out()
        data class Cancelled(val partial: File?) : Out()
        data class Failed(
            val message: String,
            val output: String,
            val partial: File? = null // any bytes captured before the failure
        ) : Out()
    }

    interface Progress { fun onProgress(fraction: Float) } // 0..1

    /**
     * Run an FFmpeg command array to a destination file.
     * @param totalSec known duration (for %); 0 => progress based on wall time.
     */
    suspend fun run(
        args: List<String>,
        estimatedTotalSec: Double,
        isCancelled: () -> Boolean,
        onProgress: (Float) -> Unit
    ): Out = withContext(Dispatchers.IO) {
        val finished = AtomicBoolean(false)
        var cancelled = false
        val sb = StringBuilder(8192)

        suspendCancellableCoroutine { cont ->
            var session: com.arthenica.ffmpegkit.FFmpegSession? = null
            var poller: Thread? = null

            FFmpegKitConfig.enableLogCallback { log ->
                val msg = log?.message ?: return@enableLogCallback
                sb.append(msg)
                val m = TIME_RE.find(msg) ?: return@enableLogCallback
                try {
                    val hh = m.groupValues[1].toInt()
                    val mm = m.groupValues[2].toInt()
                    val ss = m.groupValues[3].toInt()
                    val ms = m.groupValues[4].ifEmpty { "0" }.let {
                        if (it.length == 1) it + "00" else if (it.length == 2) it + "0" else it
                    }.toInt()
                    val sec = hh * 3600L + mm * 60L + ss + ms / 1000.0
                    val f = if (estimatedTotalSec > 0) {
                        (sec / estimatedTotalSec).toFloat().coerceIn(0f, 0.99f)
                    } else {
                        // No known duration (live/unknown): idle low progress that
                        // still moves, so the bar reads "working".
                        (0.05 + (sec % 20.0) / 400.0).toFloat().coerceIn(0f, 0.35f)
                    }
                    onProgress(f)
                } catch (_: Exception) {}
            }

            val command = commandOf(args)
            session = FFmpegKit.executeAsync(command) { s ->
                finished.set(true)
                try { poller?.interrupt() } catch (_: Exception) {}
                try {
                    if (!cont.isActive) return@executeAsync
                    val rc = s.returnCode
                    s.output?.let { sb.append(it) }
                    val partial = partialFile(args)
                    when {
                        cancelled || rc != null && ReturnCode.isCancel(rc) ->
                            cont.resume(Out.Cancelled(partial))
                        rc != null && ReturnCode.isSuccess(rc) ->
                            cont.resume(Out.Done(File(args.last())))
                        else ->
                            cont.resume(
                                Out.Failed(
                                    if (rc == null) "ffmpeg: no result" else "ffmpeg rc=${rc.value}",
                                    sb.toString().takeLast(2000),
                                    partial
                                )
                            )
                    }
                } catch (t: Throwable) {
                    if (cont.isActive) {
                        cont.resume(
                            Out.Failed(t.message ?: "ffmpeg error", sb.toString(), partialFile(args))
                        )
                    }
                }
            }
            cont.invokeOnCancellation {
                cancelled = true
                finished.set(true)
                try { session?.cancel() } catch (_: Exception) {}
                try { poller?.interrupt() } catch (_: Exception) {}
            }
            // Poll the caller's cancel flag so UI cancel reaches ffmpeg promptly.
            poller = Thread {
                try {
                    while (!cancelled && !finished.get()) {
                        if (isCancelled()) {
                            cancelled = true
                            finished.set(true)
                            try { session?.cancel() } catch (_: Exception) {}
                            break
                        }
                        Thread.sleep(300)
                    }
                } catch (_: InterruptedException) {
                }
            }.apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun partialFile(args: List<String>): File? {
        val last = args.lastOrNull() ?: return null
        return if (last.contains(File.separatorChar)) File(last) else null
    }

    /**
     * ffmpeg-kit's async API takes a single command *string* (tokens joined by
     * spaces) rather than an argument list, so tokens containing whitespace are
     * single-quoted (quotes in a token are dropped — our own file names never
     * contain them). URLs and cache paths we build are already space-free.
     */
    private fun commandOf(args: List<String>): String =
        args.joinToString(" ") { a ->
            if (a.any { it.isWhitespace() }) "'${a.replace("'", "")}'" else a
        }

    private val TIME_RE = Regex("time=(\\d{1,2}):(\\d{1,2}):(\\d{1,2})(?:\\.(\\d{1,3}))?")

    /**
     * Classify a media URL. Returns the type used for command building and for
     * the UI "kind" tag.
     */
    fun classify(url: String): Kind {
        val p = url.substringBefore('?').lowercase()
        return when {
            p.endsWith(".m3u8") || p.endsWith(".m3u") -> Kind.HLS
            p.endsWith(".mpd") -> Kind.DASH
            p.endsWith(".mp3") -> Kind.AUDIO_DIRECT
            p.endsWith(".m4a") || p.endsWith(".aac") || p.endsWith(".wav") ||
                p.endsWith(".ogg") || p.endsWith(".flac") || p.endsWith(".opus") ||
                p.endsWith(".wma") || p.endsWith(".amr") -> Kind.AUDIO_DIRECT
            p.endsWith(".mp4") || p.endsWith(".m4v") || p.endsWith(".mkv") ||
                p.endsWith(".webm") || p.endsWith(".avi") || p.endsWith(".mov") ||
                p.endsWith(".ts") || p.endsWith(".flv") || p.endsWith(".3gp") -> Kind.VIDEO_DIRECT
            else -> Kind.UNKNOWN
        }
    }

    enum class Kind { HLS, DASH, VIDEO_DIRECT, AUDIO_DIRECT, UNKNOWN }

    /** Lowercased file extension of [url] (query stripped), or "" when none. */
    fun extOf(url: String): String {
        val p = url.substringBefore('?').substringAfterLast('/', "")
        val e = p.substringAfterLast('.', "").lowercase()
        return if (e.length in 1..5 && e.all { it.isLetterOrDigit() }) e else ""
    }

    val AUDIO_EXTS = setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav")

    /**
     * Build the FFmpeg argument list to download `url` into `out`.
     * @param wantAudio true => audio-only extraction (used for MP3 output).
     */
    fun buildDownloadArgs(
        url: String,
        outFile: File,
        wantAudio: Boolean,
        ext: String,
        durationSec: Long = 0L
    ): List<String> {
        val a = ArrayList<String>()
        a.add("-y")
        a.add("-hide_banner")
        a.add("-loglevel")
        a.add("info")
        a.add("-threads")
        a.add("2")
        a.add("-user_agent")
        a.add(
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
        )
        a.add("-i")
        a.add(url)
        if (durationSec > 0L) {
            a.add("-t")
            a.add(durationSec.toString())
        }
        val targetExt = ext.lowercase().ifBlank { "mp4" }
        if (wantAudio || AUDIO_EXTS.contains(targetExt)) {
            a.add("-vn")
            a.add("-acodec")
            a.add(audioCodecFor(targetExt))
        } else if (targetExt == "mkv" || targetExt == "ts") {
            a.add("-c")
            a.add("copy")
        } else {
            a.add("-c:v")
            a.add("copy")
            a.add("-c:a")
            a.add("aac")
        }
        if (targetExt in setOf("mp4", "m4a", "mov", "3gp")) {
            a.add("-movflags")
            a.add("+faststart")
        }
        a.add(outFile.absolutePath)
        return a
    }

    fun audioCodecFor(ext: String): String = when (ext.lowercase()) {
        "mp3" -> "libmp3lame"
        "m4a", "mp4" -> "aac"
        "ogg" -> "libvorbis"
        "opus" -> "libopus"
        "flac" -> "flac"
        "wav" -> "pcm_s16le"
        "aac" -> "aac"
        else -> "aac"
    }

    fun outNameFor(url: String, ext: String): String {
        val base = url.substringAfterLast('/').substringBefore('?')
        val stem = base.substringBeforeLast('.', "media")
        val clean = stem.replace(Regex("[^\\p{L}\\p{N} _\\-]"), "").trim().take(60)
        return (if (clean.isBlank()) "media" else clean) + "." + ext
    }
}
