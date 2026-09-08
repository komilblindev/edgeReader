package uz.komil.mediapro.dl

import uz.komil.mediapro.net.Http

/**
 * Minimal, dependency-free HLS (.m3u8) parser covering the non-DRM cases the
 * app promises: master playlists with quality variants, media playlists with
 * EXTINF segments, and AES-128 encryption (EXT-X-KEY METHOD=AES-128 with an
 * explicit key + optional IV). DRM methods (SAMPLE-AES, Widevine, FairPlay)
 * are intentionally rejected.
 */
object Hls {

    enum class PlaylistKind { MASTER, MEDIA }

    data class Variant(
        val url: String,
        val bandwidth: Long,
        val resolution: String, // "1920x1080" or ""
        val codecs: String,
        val audioGroup: String?
    )

    data class MediaInfo(
        val url: String,
        val isLive: Boolean,
        val targetDurationSec: Double,
        val segments: List<Segment>,
        val totalDurationSec: Double
    )

    data class Segment(
        val url: String,
        val durationSec: Double,
        val keyUrl: String?,   // AES-128 key, resolved
        val ivHex: String?     // 16-byte IV hex if the playlist forces one
    )

    data class Parsed(
        val kind: PlaylistKind,
        val variants: List<Variant> = emptyList(),
        val media: MediaInfo? = null
    )

    /** Loads and parses a playlist; decides master vs media. */
    fun parse(url: String): Parsed {
        val text = Http.getString(url)
        val lines = text.replace("\r", "").split("\n")

        val isMaster = lines.any { it.startsWith("#EXT-X-STREAM-INF") }
        if (isMaster) {
            val variants = ArrayList<Variant>()
            var i = 0
            while (i < lines.size) {
                val ln = lines[i]
                if (ln.startsWith("#EXT-X-STREAM-INF")) {
                    val attrs = parseAttrs(ln.removePrefix("#EXT-X-STREAM-INF:"))
                    // next non-empty line is the URI
                    val segUrl = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                    if (segUrl != null) {
                        variants.add(
                            Variant(
                                url = Http.resolve(url, segUrl),
                                bandwidth = attrs["BANDWIDTH"]?.toLongOrNull() ?: 0L,
                                resolution = attrs["RESOLUTION"] ?: "",
                                codecs = attrs["CODECS"] ?: "",
                                audioGroup = attrs["AUDIO"]
                            )
                        )
                    }
                }
                i++
            }
            variants.sortByDescending { it.bandwidth }
            return Parsed(PlaylistKind.MASTER, variants = variants)
        }
        return Parsed(PlaylistKind.MEDIA, media = parseMedia(url, lines))
    }

    fun parseMedia(url: String, lines: List<String>): MediaInfo {
        var isLive = lines.any { it.startsWith("#EXT-X-ENDLIST") }.not()
        // A playlist that just ended is VOD-ish; without ENDLIST but with
        // sliding window tags it is live.
        val hasSliding = lines.any { it.startsWith("#EXT-X-TWITCH-") } ||
            lines.any { it.startsWith("#EXT-X-MEDIA-SEQUENCE") }
        if (hasSliding) isLive = true

        var targetDuration = 6.0
        lines.firstOrNull { it.startsWith("#EXT-X-TARGETDURATION") }
            ?.removePrefix("#EXT-X-TARGETDURATION:")?.trim()
            ?.toDoubleOrNull()?.let { targetDuration = it }

        val segments = ArrayList<Segment>()
        var curDuration = 0.0
        var keyUrl: String? = null
        var ivHex: String? = null
        var i = 0
        while (i < lines.size) {
            val ln = lines[i]
            when {
                ln.startsWith("#EXT-X-KEY") -> {
                    val attrs = parseAttrs(ln.removePrefix("#EXT-X-KEY:"))
                    val method = attrs["METHOD"] ?: "NONE"
                    if (method == "AES-128") {
                        keyUrl = attrs["URI"]?.let { Http.resolve(url, unquote(it)) }
                        ivHex = attrs["IV"]?.removePrefix("0x")
                    } else if (method != "NONE") {
                        // DRM / SAMPLE-AES unsupported
                        keyUrl = null
                    }
                }
                ln.startsWith("#EXTINF") -> {
                    val meta = ln.removePrefix("#EXTINF:").trim()
                    curDuration = meta.substringBefore(",").toDoubleOrNull() ?: 0.0
                    var next = i + 1
                    while (next < lines.size && (lines[next].isBlank() || lines[next].startsWith("#"))) next++
                    if (next < lines.size) {
                        segments.add(
                            Segment(
                                url = Http.resolve(url, lines[next].trim()),
                                durationSec = curDuration,
                                keyUrl = keyUrl,
                                ivHex = ivHex
                            )
                        )
                        i = next
                        continue
                    }
                }
            }
            i++
        }
        return MediaInfo(
            url = url,
            isLive = isLive,
            targetDurationSec = targetDuration,
            segments = segments,
            totalDurationSec = segments.sumOf { it.durationSec }
        )
    }

    private fun parseAttrs(s: String): Map<String, String> {
        // Split on commas that are not inside quotes.
        val out = HashMap<String, String>()
        val parts = s.split(Regex(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"))
        for (p in parts) {
            val kv = p.trim()
            val eq = kv.indexOf('=')
            if (eq > 0) out[kv.substring(0, eq).trim()] = kv.substring(eq + 1).trim()
        }
        return out
    }

    private fun unquote(s: String): String {
        val t = s.trim()
        return if (t.length >= 2 && t.first() == '"' && t.last() == '"') t.substring(1, t.length - 1)
        else t
    }
}
