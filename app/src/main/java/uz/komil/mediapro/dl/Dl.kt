package uz.komil.mediapro.dl

import android.content.Context
import android.os.SystemClock
import uz.komil.mediapro.data.ErrorLog
import uz.komil.mediapro.data.MediaRecord
import uz.komil.mediapro.data.OpKind
import uz.komil.mediapro.net.Http
import uz.komil.mediapro.storage.MediaFolder
import uz.komil.mediapro.storage.MediaSaver
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Job implementations. Each returns a [DlResult]; the caller (JobManager)
 * persists it into history + the public Downloads folders.
 */
object Dl {

    sealed class DlResult {
        data class Ok(val uri: String, val fileName: String, val sizeBytes: Long) : DlResult()
        object Cancelled : DlResult()
        data class Err(val message: String) : DlResult()
    }

    // Per-job cancel flags. A job registers a flag, MediaEngine's poller reads it.
    private val cancelFlags = ConcurrentHashMap<String, AtomicBoolean>()

    /** Register the flag; the returned lambda is what JobManager calls to cancel. */
    fun register(id: String): () -> Unit {
        val f = AtomicBoolean(false)
        cancelFlags[id] = f
        return { f.set(true) }
    }

    fun unregister(id: String) {
        cancelFlags.remove(id)
    }

    private fun shouldCancel(id: String): Boolean = cancelFlags[id]?.get() ?: false

    /**
     * Download a link. `live=true` is a recording: the stream keeps flowing
     * until the user stops the job; a stop is a SUCCESS (the partial file is
     * kept and saved).
     */
    suspend fun download(
        c: Context,
        sourceUrl: String,
        rec: MediaRecord,
        outExt: String,
        live: Boolean,
        onUpdate: (MediaRecord) -> Unit
    ): DlResult {
        return try {
            val ext = outExt.lowercase()
            val kind = MediaEngine.classify(sourceUrl)
            // Plain byte-copy is only lossless when the output container equals
            // the source container (direct video/audio link, same extension).
            // Audio extraction, manifest demux (HLS/DASH), opaque URLs and any
            // container change all need FFmpeg — byte-copying e.g. a .webm into
            // a .mp4 would silently corrupt the file.
            val origExt = MediaEngine.extOf(sourceUrl)
            val directSame = (kind == MediaEngine.Kind.VIDEO_DIRECT ||
                kind == MediaEngine.Kind.AUDIO_DIRECT) && origExt.isNotEmpty() && ext == origExt
            when {
                !directSame -> {
                    // Let FFmpeg demux: it resolves quality, AES keys, segments
                    // and redirects natively.
                    viaFfmpeg(c, sourceUrl, rec, outExt, live, onUpdate)
                }
                else -> {
                    plainFile(c, sourceUrl, rec, outExt, onUpdate)
                }
            }
        } catch (t: Throwable) {
            ErrorLog.e("Dl.download", "error: ${t.message}", t)
            DlResult.Err(t.message ?: "download error")
        }
    }

    /** Trim/cut/mix/convert — all expressed as an [EditPlan]. */
    suspend fun process(
        c: Context,
        rec: MediaRecord,
        outExt: String?,
        onUpdate: (MediaRecord) -> Unit
    ): DlResult {
        val plan = EditPlan.forOp(rec)
        val ext = outExt ?: plan.defaultExt
        val out = WorkDir.fresh(c, "op_${rec.id}.$ext")
        return try {
            onUpdate(rec.copy(progress = 1))
            val estimated = plan.estimateSec ?: 0.0
            val res = MediaEngine.run(
                plan.args(rec.sourceUrl, out),
                estimated,
                { shouldCancel(rec.id) },
                { f -> onUpdate(rec.copy(progress = (f * 100).toInt().coerceIn(0, 99))) }
            )
            when (res) {
                is MediaEngine.Out.Done -> saveDone(c, rec, res.outFile)
                is MediaEngine.Out.Cancelled -> {
                    WorkDir.clean(c, out, res.partial)
                    DlResult.Cancelled
                }
                is MediaEngine.Out.Failed -> {
                    WorkDir.clean(c, out, res.partial)
                    ErrorLog.e("Dl.process", "${rec.kind}: ${res.message}\n${res.output.takeLast(1500)}")
                    DlResult.Err(res.message)
                }
            }
        } catch (t: Throwable) {
            ErrorLog.e("Dl.process", "ffmpeg error", t)
            DlResult.Err(t.message ?: "process error")
        }
    }

    // ---- shared download internals ----

    private suspend fun viaFfmpeg(
        c: Context,
        sourceUrl: String,
        rec: MediaRecord,
        outExt: String,
        live: Boolean,
        onUpdate: (MediaRecord) -> Unit
    ): DlResult {
        val ext = outExt.ifBlank { "mp4" }
        // Audio-only target => drop the video stream and re-encode to the target.
        val wantAudio = AUDIO_EXTS.contains(ext.lowercase())
        val out = WorkDir.fresh(c, "dl_${rec.id}.$ext")

        val durationSec = if (live) {
            rec.params.toLongOrNull() ?: 0L
        } else 0L

        val estimated = if (durationSec > 0L) {
            durationSec.toDouble()
        } else {
            probeDurationSec(c, sourceUrl)
        }
        val startedWall = SystemClock.elapsedRealtime()
        val res = MediaEngine.run(
            MediaEngine.buildDownloadArgs(sourceUrl, out, wantAudio, ext, durationSec),
            estimated,
            { shouldCancel(rec.id) },
            { f ->
                val p = if (durationSec > 0L) {
                    (f * 100).toInt().coerceIn(0, 99)
                } else {
                    val sec = (SystemClock.elapsedRealtime() - startedWall) / 1000L
                    (sec % 100).toInt()
                }
                onUpdate(rec.copy(progress = p))
            }
        )
        return when (res) {
            is MediaEngine.Out.Done -> saveDone(c, rec, res.outFile)
            is MediaEngine.Out.Cancelled -> {
                // For a live recording a stop keeps whatever was captured.
                if (live && res.partial != null && res.partial.exists() && res.partial.length() > 0) {
                    saveDone(c, rec, res.partial!!)
                } else {
                    WorkDir.clean(c, out, res.partial)
                    DlResult.Cancelled
                }
            }
            is MediaEngine.Out.Failed -> {
                if (live && res.partial != null && res.partial.exists() && res.partial.length() > 0) {
                    saveDone(c, rec, res.partial!!)
                } else {
                    WorkDir.clean(c, out, res.partial)
                    ErrorLog.e("Dl.viaFfmpeg", "failed: ${res.message} ${res.output.takeLast(800)}")
                    DlResult.Err(res.message)
                }
            }
        }
    }

    private val AUDIO_EXTS = setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav")

    private suspend fun saveDone(c: Context, rec: MediaRecord, f: File): DlResult {
        return try {
            val folder = folderFor(rec.kind)
            // Human-readable file name from the job title, kept unique on disk.
            val finalFile = destine(f, (rec.fileName.ifBlank { rec.title }).substringBeforeLast('.'))
            val uri = MediaSaver.save(c, finalFile, finalFile.name, folder)
            WorkDir.clean(c, f, finalFile)
            DlResult.Ok(uri, finalFile.name, finalFile.length())
        } catch (t: Throwable) {
            WorkDir.clean(c, f)
            ErrorLog.e("Dl.saveDone", "save failed", t)
            DlResult.Err(t.message ?: "save failed")
        }
    }

    /** Rename a finished work file to baseName.ext, uniquified if taken. */
    private fun destine(f: File, baseName: String): File {
        val clean = baseName.replace(Regex("[^\\p{L}\\p{N} _\\-]"), "").trim().take(60)
            .ifBlank { "media" }
        val parent = f.parentFile ?: return f
        var cand = File(parent, "$clean.${f.extension}")
        var i = 2
        while (cand.exists() && cand != f) {
            cand = File(parent, "$clean($i).${f.extension}")
            i++
        }
        if (cand != f && !cand.exists()) runCatching { f.renameTo(cand) }
        return if (cand.exists()) cand else f
    }

    private suspend fun plainFile(
        c: Context,
        sourceUrl: String,
        rec: MediaRecord,
        outExt: String,
        onUpdate: (MediaRecord) -> Unit
    ): DlResult {
        var out: java.io.File? = null
        return try {
            val ext = if (outExt.isNotBlank()) outExt.lowercase()
            else sourceUrl.substringAfterLast('.').substringBefore('?').ifBlank { "mp4" }
            val outFile = WorkDir.fresh(c, "dl_${rec.id}.$ext")
            out = outFile
            val resp = Http.get(sourceUrl)
            resp.use { r ->
                if (!r.isSuccessful) return DlResult.Err("HTTP ${r.code}")
                val total = r.body?.contentLength() ?: 0L
                val body = r.body!!.byteStream()
                val fos = FileOutputStream(outFile)
                val buf = ByteArray(128 * 1024)
                var written = 0L
                try {
                    while (true) {
                        val n = body.read(buf)
                        if (n <= 0) break
                        fos.write(buf, 0, n)
                        written += n
                        if (total > 0 && !shouldCancel(rec.id)) {
                            onUpdate(rec.copy(progress = ((written.toDouble() / total) * 100).toInt().coerceIn(0, 99)))
                        }
                        if (shouldCancel(rec.id)) {
                            try { fos.close() } catch (_: Throwable) {}
                            WorkDir.clean(c, outFile)
                            return DlResult.Cancelled
                        }
                    }
                } finally {
                    try { fos.close() } catch (_: Throwable) {}
                }
            }
            saveDone(c, rec, outFile)
        } catch (t: Throwable) {
            out?.let { WorkDir.clean(c, it) }
            ErrorLog.e("Dl.plainFile", "download error", t)
            DlResult.Err(t.message ?: "download error")
        }
    }

    private fun probeDurationSec(c: Context, url: String): Double {
        return try {
            val info = com.arthenica.ffmpegkit.FFprobeKit.getMediaInformation(url)
            val d = info?.mediaInformation?.duration?.toDoubleOrNull() ?: 0.0
            if (d > 0 && d < 3600 * 24) d else 0.0
        } catch (_: Exception) { 0.0 }
    }

    fun folderFor(kind: OpKind): MediaFolder = when (kind) {
        OpKind.DOWNLOAD -> MediaFolder.DOWNLOADS
        OpKind.RECORDING -> MediaFolder.RECORDINGS
        OpKind.TRIMMED -> MediaFolder.TRIMMED
        OpKind.EDITED -> MediaFolder.EDITED
        OpKind.MIXED -> MediaFolder.MIXED
        OpKind.CONVERTED -> MediaFolder.AUDIO
    }
}
