package uz.komil.mediapro.data

import uz.komil.mediapro.storage.MediaFolder

/** What produced a file — drives which operation folder it lands in. */
enum class OpKind {
    DOWNLOAD,   // HLS/DASH/direct link download
    RECORDING,  // live stream recording
    TRIMMED,    // editor: trim
    EDITED,     // editor: interval removal
    MIXED,      // editor: audio replace / mix
    CONVERTED   // editor: plain format conversion
}

enum class JobStatus { RUNNING, DONE, FAILED, CANCELED }

/** Outcome of a finished file: where it lives + its source link. */
data class MediaRecord(
    val id: String,
    val kind: OpKind,
    val status: JobStatus,
    val title: String,          // user-facing name
    val fileName: String,       // on-disk name
    val sourceUrl: String = "", // original media/manifest link, or the editor's local source path
    val folder: MediaFolder,
    val savedUri: String = "",  // content:// or file path for API 28
    val mime: String = "",
    val sizeBytes: Long = 0L,
    val durationSec: Long = 0L, // finished file's play time (ffprobe); 0 = unknown/live
    val progress: Int = 0,      // 0..100 while running
    val error: String = "",
    // Editor op settings (trim range / cut intervals / mix volumes / output
    // format) as a small JSON object consumed by dl/EditPlan; empty for
    // downloads/recordings where the URL + chosen extension is enough.
    val params: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

/** Json (de)serialization of the history list — no Room/KSP dependency. */
object MediaHistory {
    private const val FILE = "media_history.json"

    private fun file(ctx: android.content.Context) =
        java.io.File(ctx.filesDir, FILE)

    fun load(ctx: android.content.Context): List<MediaRecord> {
        return try {
            val text = file(ctx).readText()
            parse(text)
        } catch (_: Exception) { emptyList() }
    }

    fun save(ctx: android.content.Context, list: List<MediaRecord>) {
        try {
            file(ctx).writeText(toJson(list))
        } catch (_: Exception) {}
    }

    private fun toJson(list: List<MediaRecord>): String {
        val a = org.json.JSONArray()
        list.forEach { r ->
            a.put(
                org.json.JSONObject().apply {
                    put("id", r.id)
                    put("kind", r.kind.name)
                    put("status", r.status.name)
                    put("title", r.title)
                    put("fileName", r.fileName)
                    put("sourceUrl", r.sourceUrl)
                    put("folder", r.folder.name)
                    put("savedUri", r.savedUri)
                    put("mime", r.mime)
                    put("sizeBytes", r.sizeBytes)
                    put("durationSec", r.durationSec)
                    put("error", r.error)
                    put("params", r.params)
                    put("createdAtMs", r.createdAtMs)
                }
            )
        }
        return a.toString()
    }

    private fun parse(text: String): List<MediaRecord> {
        val arr = org.json.JSONArray(text)
        val out = ArrayList<MediaRecord>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            try {
                out.add(
                    MediaRecord(
                        id = o.getString("id"),
                        kind = enumOf(OpKind.DOWNLOAD, o.optString("kind")),
                        status = enumOf(JobStatus.DONE, o.optString("status")),
                        title = o.optString("title"),
                        fileName = o.optString("fileName"),
                        sourceUrl = o.optString("sourceUrl"),
                        folder = folderOf(o.optString("folder")),
                        savedUri = o.optString("savedUri"),
                        mime = o.optString("mime"),
                        sizeBytes = o.optLong("sizeBytes"),
                        durationSec = o.optLong("durationSec"),
                        error = o.optString("error"),
                        params = o.optString("params"),
                        createdAtMs = o.optLong("createdAtMs", System.currentTimeMillis())
                    )
                )
            } catch (_: Exception) {}
        }
        return out.sortedByDescending { it.createdAtMs }
    }

    private inline fun <reified T : Enum<T>> enumOf(def: T, name: String): T =
        enumValues<T>().firstOrNull { it.name == name } ?: def

    private fun folderOf(name: String): MediaFolder =
        MediaFolder.entries.firstOrNull { it.name == name } ?: MediaFolder.DOWNLOADS
}
