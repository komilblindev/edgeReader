package uz.komil.mediapro.dl

import uz.komil.mediapro.data.MediaRecord
import uz.komil.mediapro.data.OpKind
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns a MediaRecord + its op-params JSON into an FFmpeg command that
 * Dl.process executes. Each editor op has its own param shape (see the
 * `xxxParams` builders) which the UI stores on [MediaRecord.params].
 *
 * Op semantics:
 *  - TRIMMED   keep [fromMs, toMs]. Remux (stream-copy) when the target keeps
 *              the source container, re-encode only when audio is targeted.
 *  - EDITED    drop up to 10 intervals [s,e], keep the complement. Re-encodes
 *              the kept pieces and concatenates them. On the ffmpeg-kit "audio"
 *              flavour there is no x264 encoder, so a kept VIDEO track is
 *              re-encoded to the built-in MPEG-4 encoder (plays fine in
 *              ExoPlayer/system players, bigger file) — pure-audio sources are
 *              untouched by this caveat.
 *  - MIXED     put a second audio file onto the base media. `mode=replace`
 *              swaps the base's audio for the added track; `mode=mix` blends
 *              them. Dual volume 0–200%.
 *  - CONVERTED change the container / extract audio.
 *
 * All durations are milliseconds in the params; FFmpeg gets seconds. The
 * output file path is always the last argument (Dl relies on this).
 */
object EditPlan {

    /** Decoded op settings; `args` is built lazily with source+output known. */
    data class Plan(
        val defaultExt: String,
        val estimateSec: Double?, // null => unknown/live => wall-clock progress
        val args: (source: String, out: File) -> List<String>
    )

    fun forOp(rec: MediaRecord): Plan {
        val raw = try {
            if (rec.params.isBlank()) JSONObject() else JSONObject(rec.params)
        } catch (_: Exception) {
            JSONObject()
        }
        return when (rec.kind) {
            OpKind.TRIMMED -> trimPlan(raw)
            OpKind.EDITED -> removePlan(raw)
            OpKind.MIXED -> mixPlan(raw)
            else -> convertPlan(raw)
        }
    }

    // ---- param builders (used by the editor screens) ----

    fun trimParams(fromMs: Long, toMs: Long, ext: String): String =
        JSONObject().put("fromMs", fromMs).put("toMs", toMs).put("ext", ext).toString()

    /** remove = list of (startMs,endMs) intervals to drop, ≤ 10, in range. */
    fun removeParams(totalMs: Long, remove: List<LongArray>, ext: String): String =
        JSONObject().apply {
            put("totalMs", totalMs)
            val a = JSONArray()
            remove.forEach { r -> a.put(JSONObject().put("s", r[0]).put("e", r[1])) }
            put("remove", a)
            put("ext", ext)
        }.toString()

    /** vol* are 0..200 (percent). mode = "replace" | "mix". */
    fun mixParams(addPath: String, volBase: Int, volAdd: Int, mode: String, ext: String): String =
        JSONObject().apply {
            put("addPath", addPath)
            put("volBase", volBase)
            put("volAdd", volAdd)
            put("mode", mode)
            put("ext", ext)
        }.toString()

    fun convertParams(ext: String): String =
        JSONObject().put("ext", ext).toString()

    // ---- per-op planners ----

    private fun trimPlan(p: JSONObject): Plan {
        val from = p.optLong("fromMs") / 1000.0
        val to = p.optLong("toMs") / 1000.0
        val dur = (to - from).coerceAtLeast(0.01)
        return Plan(
            defaultExt = p.optString("ext").ifBlank { "mp4" },
            estimateSec = dur
        ) { source, out ->
            trimArgs(source, out, from, dur)
        }
    }

    private fun removePlan(p: JSONObject): Plan {
        val total = p.optLong("totalMs")
        val ra = p.optJSONArray("remove") ?: JSONArray()
        val rem = ArrayList<LongArray>()
        for (i in 0 until ra.length().coerceAtMost(10)) {
            val o = ra.optJSONObject(i) ?: continue
            val s = (o.optLong("s") / 1000.0)
            val e = (o.optLong("e") / 1000.0)
            if (e > s) rem.add(longArrayOf((s * 1000).toLong(), (e * 1000).toLong()))
        }
        // Sort by start and compute the complement (the parts we KEEP).
        rem.sortBy { it[0] }
        val keeps = ArrayList<DoubleArray>()
        var cur = 0.0
        for (r in rem) {
            val s = r[0] / 1000.0
            val e = r[1] / 1000.0
            if (s > cur) keeps.add(doubleArrayOf(cur, s)) // keep [cur, s)
            cur = e.coerceAtLeast(cur)
        }
        if (total > 0 && cur < total / 1000.0) keeps.add(doubleArrayOf(cur, total / 1000.0))
        val keepSec = keeps.sumOf { it[1] - it[0] }
        val ext = p.optString("ext").ifBlank { "mp4" }

        if (keeps.isEmpty()) {
            // No valid keep pieces (empty/invalid intervals) — fall back to a
            // straight copy of the whole input so the job still completes.
            return Plan(ext, null) { source, out ->
                listOf("-y", "-i", source, "-c", "copy", "-avoid_negative_ts", "make_zero", out.absolutePath)
            }
        }
        return Plan(ext, keepSec) { source, out -> removeArgs(source, out, keeps, ext) }
    }

    private fun mixPlan(p: JSONObject): Plan {
        val add = p.optString("addPath")
        val volBase = p.optDouble("volBase", 100.0) / 100.0
        val volAdd = p.optDouble("volAdd", 100.0) / 100.0
        val replace = p.optString("mode", "mix") == "replace"
        val ext = p.optString("ext").ifBlank { "mp4" }
        return Plan(ext, null) { source, out -> mixArgs(source, add, out, ext, volBase, volAdd, replace) }
    }

    private fun convertPlan(p: JSONObject): Plan {
        val ext = p.optString("ext").ifBlank { "mp3" }
        return Plan(ext, null) { source, out -> convertArgs(source, out, ext) }
    }

    // ---- shared helpers ----

    private fun trimArgs(source: String, out: File, fromSec: Double, durSec: Double): List<String> {
        val srcExt = extOf(source)
        val outExt = extOf(out.name)
        val a = ArrayList<String>()
        a += listOf("-y", "-hide_banner", "-loglevel", "info")
        a += listOf("-ss", f(fromSec), "-i", source)
        a += listOf("-t", f(durSec))
        if (isAudio(outExt) || !isVideo(srcExt)) {
            // Audio-only output (or an audio source): target codec == source codec
            // can stream-copy; otherwise decode/encode to the requested codec.
            if (isAudio(srcExt) && srcExt == outExt) {
                a += listOf("-c", "copy", "-avoid_negative_ts", "make_zero")
            } else {
                a += listOf("-vn", "-acodec", MediaEngine.audioCodecFor(outExt))
            }
        } else {
            // Video container kept: remux (fast). Start lands on the keyframe
            // nearest `fromSec` — frame-exactness would need a re-encode pass.
            a += listOf("-map", "0:v:0?", "-map", "0:a:0?")
            if (srcExt == outExt || (srcExt in setOf("mp4", "m4v", "mov") && outExt in setOf("mp4", "mkv"))) {
                a += listOf("-c", "copy", "-avoid_negative_ts", "make_zero")
            } else {
                a += listOf("-c:v", "mpeg4", "-q:v", "5", "-c:a", "aac")
            }
        }
        if (outExt in setOf("mp4", "m4a", "mov", "3gp")) a += listOf("-movflags", "+faststart")
        a += out.absolutePath
        return a
    }

    fun hasAudioStream(source: String): Boolean = try {
        val info = com.arthenica.ffmpegkit.FFprobeKit.getMediaInformation(source)?.mediaInformation
        val streams = info?.streams
        if (streams.isNullOrEmpty()) true else streams.any { it.type == "audio" }
    } catch (_: Throwable) {
        true
    }

    private fun removeArgs(source: String, out: File, keeps: List<DoubleArray>, outExt: String): List<String> {
        val srcExt = extOf(source)
        val hasAudio = hasAudioStream(source)
        val wantAudioOnly = isAudio(outExt) || !isVideo(srcExt)
        val a = ArrayList<String>()
        a += listOf("-y", "-hide_banner", "-loglevel", "info", "-i", source)

        // Per-piece trim into labelled streams.
        val parts = ArrayList<String>()
        keeps.forEachIndexed { i, k ->
            if (wantAudioOnly) {
                if (hasAudio) {
                    val trim = if (k[1] > k[0]) "atrim=start=${f(k[0])}:end=${f(k[1])}" else "atrim=start=${f(k[0])}"
                    parts += "[0:a]$trim,asetpts=PTS-STARTPTS[a$i]"
                }
            } else {
                val vs = if (k[1] > k[0]) "trim=start=${f(k[0])}:end=${f(k[1])}" else "trim=start=${f(k[0])}"
                parts += "[0:v]$vs,setpts=PTS-STARTPTS[v$i]"
                if (hasAudio) {
                    val as_ = if (k[1] > k[0]) "atrim=start=${f(k[0])}:end=${f(k[1])}" else "atrim=start=${f(k[0])}"
                    parts += "[0:a]$as_,asetpts=PTS-STARTPTS[a$i]"
                }
            }
        }

        if (wantAudioOnly) {
            if (!hasAudio) {
                a += listOf("-f", "lavfi", "-i", "anullsrc=r=44100:cl=stereo", "-t", "0.1", "-acodec", MediaEngine.audioCodecFor(outExt))
            } else {
                val mapLabel = if (keeps.size == 1) {
                    a += listOf("-filter_complex", parts[0])
                    "[a0]"
                } else {
                    val inLabels = keeps.indices.joinToString("") { "[a$it]" }
                    val concat = "${inLabels}concat=n=${keeps.size}:v=0:a=1[aout]"
                    a += listOf("-filter_complex", (parts + concat).joinToString(";"))
                    "[aout]"
                }
                a += listOf("-map", mapLabel, "-acodec", MediaEngine.audioCodecFor(outExt))
            }
        } else {
            if (!hasAudio) {
                val map = if (keeps.size == 1) {
                    a += listOf("-filter_complex", parts[0])
                    listOf("-map", "[v0]")
                } else {
                    val inLabels = keeps.indices.joinToString("") { "[v$it]" }
                    val concat = "${inLabels}concat=n=${keeps.size}:v=1:a=0[vo]"
                    a += listOf("-filter_complex", (parts + concat).joinToString(";"))
                    listOf("-map", "[vo]")
                }
                a += map
                a += listOf("-c:v", "mpeg4", "-q:v", "5", "-an")
            } else {
                val map = if (keeps.size == 1) {
                    a += listOf("-filter_complex", parts.joinToString(";"))
                    listOf("-map", "[v0]", "-map", "[a0]")
                } else {
                    val inLabels = keeps.indices.flatMap { listOf("[v$it]", "[a$it]") }.joinToString("")
                    val concat = "${inLabels}concat=n=${keeps.size}:v=1:a=1[vo][ao]"
                    a += listOf("-filter_complex", parts.joinToString(";") + ";" + concat)
                    listOf("-map", "[vo]", "-map", "[ao]")
                }
                a += map
                a += listOf("-c:v", "mpeg4", "-q:v", "5", "-c:a", "aac")
            }
        }
        if (outExt in setOf("mp4", "m4a", "mov", "3gp")) a += listOf("-movflags", "+faststart")
        a += out.absolutePath
        return a
    }

    private fun mixArgs(
        source: String,
        add: String,
        out: File,
        outExt: String,
        volBase: Double,
        volAdd: Double,
        replace: Boolean
    ): List<String> {
        val srcExt = extOf(source)
        val hasBaseAudio = hasAudioStream(source)
        val keepVideo = isVideo(srcExt) && !isAudio(outExt)
        val a = ArrayList<String>()
        a += listOf("-y", "-hide_banner", "-loglevel", "info")
        a += listOf("-i", source)
        a += listOf("-i", add)

        val fc = if (replace || !hasBaseAudio) {
            // Keep the base's video, take only the added audio (scaled).
            "[1:a]volume=${f(volAdd)}[aout]"
        } else {
            "[0:a]volume=${f(volBase)}[ab];[1:a]volume=${f(volAdd)}[aa];" +
                "[ab][aa]amix=inputs=2:duration=longest:normalize=0[aout]"
        }
        a += listOf("-filter_complex", fc)
        a += listOf("-map", "[aout]")
        if (keepVideo) {
            // The base's original video continues under the new audio.
            a += listOf("-map", "0:v:0")
            a += listOf("-c:v", "copy")
        }
        a += listOf("-acodec", MediaEngine.audioCodecFor(outExt))
        if (outExt in setOf("mp4", "m4a", "mov", "3gp")) a += listOf("-movflags", "+faststart")
        a += out.absolutePath
        return a
    }

    private fun convertArgs(source: String, out: File, outExt: String): List<String> {
        val srcExt = extOf(source)
        val a = ArrayList<String>()
        a += listOf("-y", "-hide_banner", "-loglevel", "info", "-i", source)
        if (isAudio(outExt)) {
            val hasAudio = hasAudioStream(source)
            if (!hasAudio) {
                a += listOf("-f", "lavfi", "-t", "0.1", "-i", "anullsrc=r=44100:cl=stereo", "-acodec", MediaEngine.audioCodecFor(outExt))
            } else if (isAudio(srcExt) && srcExt == outExt) {
                a += listOf("-vn", "-c", "copy")
            } else {
                a += listOf("-vn", "-acodec", MediaEngine.audioCodecFor(outExt))
            }
        } else if (isVideo(srcExt)) {
            // Container-to-container: same codecs assumed, remux if compatible.
            if (srcExt == outExt || (srcExt in setOf("mp4", "m4v", "mov") && outExt in setOf("mp4", "mkv"))) {
                a += listOf("-c", "copy", "-avoid_negative_ts", "make_zero")
            } else {
                a += listOf("-c:v", "mpeg4", "-q:v", "5", "-c:a", "aac")
            }
        } else {
            // Audio source into a video container: encode to aac.
            a += listOf("-acodec", "aac")
        }
        if (outExt in setOf("mp4", "m4a", "mov", "3gp")) a += listOf("-movflags", "+faststart")
        a += out.absolutePath
        return a
    }

    // ---- helpers ----

    private fun extOf(path: String): String =
        path.substringAfterLast('.').substringBefore('?').lowercase().ifBlank { "mp4" }

    private val AUDIO = setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav", "wma", "amr", "m4b")
    private val VIDEO = setOf("mp4", "mkv", "webm", "mov", "avi", "3gp", "ts", "flv", "m4v", "mpg", "mpeg")

    fun isAudio(ext: String): Boolean = ext.lowercase() in AUDIO
    fun isVideo(ext: String): Boolean = ext.lowercase() in VIDEO

    /** Seconds with 3 decimals, trimmed of trailing zeros where possible. */
    private fun f(v: Double): String {
        val s = String.format(java.util.Locale.US, "%.3f", v)
        return s.trimEnd('0').trimEnd('.')
    }
}
