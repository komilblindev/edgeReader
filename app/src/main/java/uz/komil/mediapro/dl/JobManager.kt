package uz.komil.mediapro.dl

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uz.komil.mediapro.data.ErrorLog
import uz.komil.mediapro.data.MediaHistory
import uz.komil.mediapro.data.MediaRecord
import uz.komil.mediapro.data.OpKind
import uz.komil.mediapro.data.JobStatus
import uz.komil.mediapro.lic.LicenseManager
import uz.komil.mediapro.svc.MediaService
import uz.komil.mediapro.storage.MediaFolder
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The single place that executes long-running media work and publishes its
 * state to the UI. All jobs (link downloads, live recordings, editor/convert
 * operations) funnel through here so that:
 *  - jobs are serialized (FFmpeg is one heavy engine),
 *  - progress is observable via [jobs] (Compose collects it),
 *  - finished/failed items persist into history + public Downloads folders,
 *  - the foreground service keeps the process alive while a job runs.
 */
object JobManager {

    /** A history item or a live in-progress job, for the UI list. */
    data class Ui(
        val record: MediaRecord,
        val isLive: Boolean = false,
        val progress: Float = 1f
    )

    private val _jobs = MutableStateFlow<List<Ui>>(emptyList())
    val jobs: StateFlow<List<Ui>> = _jobs

    private val cancellers = ConcurrentHashMap<String, () -> Unit>()
    private val runningStarts = ConcurrentHashMap<String, Long>()

    private var scope: CoroutineScope? = null
    private var appCtx: Context? = null
    private val mutex = Mutex()
    private var _history: List<MediaRecord> = emptyList()

    fun init(ctx: Context, io: CoroutineScope) {
        if (appCtx != null) return
        appCtx = ctx.applicationContext
        scope = io
        _history = MediaHistory.load(ctx)
        _jobs.value = _history.map { Ui(it) }
        io.launch { WorkDir.cleanOld(ctx) }
    }

    private fun ctx(): Context =
        appCtx ?: throw IllegalStateException("JobManager.init(ctx) first")

    /**
     * Queue a new job. Gates are enforced at start: recordings cap the 15-min
     * free budget by wall time; downloads and edits decrement their counters.
     * @param params editor op settings (trim/cut/mix/convert JSON, see
     *        EditPlan); empty for downloads/recordings.
     */
    fun start(
        kind: OpKind,
        title: String,
        sourceUrl: String,
        folder: MediaFolder,
        outExt: String?,
        onBlocked: (() -> Unit)? = null,
        params: String = ""
    ) {
        val c = ctx()
        val io = scope ?: return
        io.launch {
            val gatePass = when (kind) {
                OpKind.RECORDING -> LicenseManager.canRecordAdditional(c, 1)
                OpKind.DOWNLOAD -> LicenseManager.canDownload(c)
                else -> LicenseManager.canEdit(c)
            }
            if (!gatePass) {
                onBlocked?.invoke()
                return@launch
            }
            mutex.withLock {
                val id = UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                val cancelFn = Dl.register(id)
                cancellers[id] = cancelFn
                runningStarts[id] = SystemClock.elapsedRealtime()

                val rec = MediaRecord(
                    id = id,
                    kind = kind,
                    status = JobStatus.RUNNING,
                    title = title,
                    fileName = title,
                    sourceUrl = sourceUrl,
                    folder = folder,
                    progress = 0,
                    params = params
                )
                _jobs.value = _jobs.value + Ui(rec, isLive = true, progress = 0f)
                if (kind == OpKind.DOWNLOAD) LicenseManager.consumeDownload(c)
                if (kind != OpKind.RECORDING && kind != OpKind.DOWNLOAD) LicenseManager.consumeEdit(c)

                runJob(c, rec, outExt)
            }
        }
    }

    private suspend fun runJob(c: Context, rec: MediaRecord, outExt: String?) {
        try {
            // Without a live foreground service Android may kill this process mid-job.
            // If the platform refuses the FGS start (background call on 12+), fail the
            // job visibly instead of letting it appear to run and silently die.
            if (!MediaService.startFor(c, rec)) {
                ErrorLog.e("JobManager", "foreground service blocked for ${rec.kind}; job ${rec.id} not started")
                finish(c, rec.copy(status = JobStatus.FAILED, error = "Foreground service blocked"), keep = true)
                return
            }
            val result = when (rec.kind) {
                OpKind.DOWNLOAD,
                OpKind.RECORDING -> Dl.download(
                    c, rec.sourceUrl, rec,
                    outExt ?: defaultExt(rec.sourceUrl),
                    live = rec.kind == OpKind.RECORDING
                ) { publish(it) }
                else -> Dl.process(c, rec, outExt) { publish(it) }
            }
            when (result) {
                is Dl.DlResult.Ok -> finishDone(c, rec, result)
                is Dl.DlResult.Cancelled -> finish(c, rec.copy(status = JobStatus.CANCELED), keep = false)
                is Dl.DlResult.Err -> {
                    ErrorLog.e("JobManager", "${rec.kind}: ${result.message}")
                    finish(c, rec.copy(status = JobStatus.FAILED, error = result.message), keep = true)
                }
            }
        } catch (t: Throwable) {
            ErrorLog.e("JobManager", "unhandled job error", t)
            finish(c, rec.copy(status = JobStatus.FAILED, error = t.message ?: "error"), keep = true)
        } finally {
            cleanup(rec.id)
        }
    }

    private fun publish(r: MediaRecord) {
        _jobs.value = _jobs.value.map {
            if (it.record.id == r.id) Ui(r, isLive = true, progress = r.progress / 100f) else it
        }
        MediaService.publishProgress(r.id, r.progress / 100f, r.title)
    }

    private suspend fun finishDone(c: Context, rec: MediaRecord, ok: Dl.DlResult.Ok) {
        // Recording consumes wall-clock seconds against the 15-min budget.
        if (rec.kind == OpKind.RECORDING) {
            val started = runningStarts[rec.id] ?: 0L
            val secs = (SystemClock.elapsedRealtime() - started) / 1000L
            if (secs > 0) LicenseManager.consumeRecording(c, secs)
        }
        finish(
            c,
            rec.copy(
                status = JobStatus.DONE,
                savedUri = ok.uri,
                fileName = ok.fileName,
                sizeBytes = ok.sizeBytes,
                durationSec = ok.durationSec,
                progress = 100
            ),
            keep = true
        )
    }

    private fun finish(c: Context, rec: MediaRecord, keep: Boolean) {
        _jobs.value = _jobs.value.filterNot { it.record.id == rec.id }
        if (keep) {
            _history = (listOf(rec) + _history).take(200)
            MediaHistory.save(c, _history)
            _jobs.value = _history.map { Ui(it) } + _jobs.value.filter { it.isLive }
        }
    }

    private fun cleanup(id: String) {
        cancellers.remove(id)
        runningStarts.remove(id)
        Dl.unregister(id)
        MediaService.stop()
    }

    fun cancel(id: String) {
        cancellers[id]?.invoke()
    }

    fun uiSnapshot(): List<Ui> = _jobs.value

    private fun defaultExt(url: String): String = when (MediaEngine.classify(url)) {
        MediaEngine.Kind.AUDIO_DIRECT -> "mp3"
        else -> "mp4"
    }
}

/** Scratch location for ffmpeg temp files (cacheDir — auto-cleared by OS). */
object WorkDir {
    fun root(c: Context): File {
        val d = File(c.cacheDir, "mvdwork")
        if (!d.exists()) d.mkdirs()
        return d
    }
    fun fresh(c: Context, name: String): File {
        val f = File(root(c), name)
        if (f.exists()) f.delete()
        return f
    }
    fun clean(c: Context, vararg files: File?) {
        files.forEach { it?.delete() }
    }

    /** Total bytes occupied by scratch/cache files. */
    fun cacheSizeBytes(c: Context): Long {
        var total = 0L
        listOf(root(c), File(c.cacheDir, "editor")).forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { total += it.length() }
            }
        }
        return total
    }

    /** Clean all scratch/editor cache files. */
    fun clearAll(c: Context) {
        listOf(root(c), File(c.cacheDir, "editor")).forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { it.delete() }
            }
        }
    }

    /** Clean any stale scratch/editor files older than 30 minutes or on app boot. */
    fun cleanOld(c: Context, maxAgeMs: Long = 30 * 60 * 1000L) {
        val now = System.currentTimeMillis()
        listOf(root(c), File(c.cacheDir, "editor")).forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (now - file.lastModified() > maxAgeMs) {
                        file.delete()
                    }
                }
            }
        }
    }
}
