package uz.komil.mediapro.dl

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import uz.komil.mediapro.net.Http
import java.io.StringReader

/**
 * DASH (.mpd) manifest parser. It does not download segments itself — FFmpeg
 * is the download engine. Its job here is only to ENUMERATE video/audio
 * representations (so the UI can offer qualities) and to produce a *filtered*
 * MPD that keeps exactly the representation the user picked, which FFmpeg is
 * then pointed at. Keeps the templates/base-urls intact so relative segment
 * URLs still resolve.
 */
object Dash {

    data class Rep(
        val kind: String, // "video" | "audio"
        val id: String,
        val mime: String,
        val codecs: String,
        val bandwidth: Long,
        val width: Int,
        val height: Int,
        val lang: String,
        val frameRate: String
    )

    data class Mpd(
        val video: List<Rep> = emptyList(),
        val audio: List<Rep> = emptyList()
    )

    fun parse(mpdUrl: String): Mpd = parseText(Http.getString(mpdUrl))

    fun parseText(text: String): Mpd {
        val v = ArrayList<Rep>()
        val a = ArrayList<Rep>()
        try {
            val f = XmlPullParserFactory.newInstance()
            f.isNamespaceAware = false
            val xpp = f.newPullParser()
            xpp.setInput(StringReader(text))
            var inAdaptVideo = false
            var inAdaptAudio = false
            var adaptMime = ""
            var adaptLang = ""
            var event = xpp.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (xpp.name) {
                        "AdaptationSet" -> {
                            val ct = xpp.getAttributeValue(null, "contentType") ?: ""
                            adaptMime = xpp.getAttributeValue(null, "mimeType") ?: ""
                            adaptLang = xpp.getAttributeValue(null, "lang") ?: ""
                            val mimeLike = if (adaptMime.startsWith("audio")) "audio"
                            else if (adaptMime.startsWith("video")) "video" else ""
                            inAdaptVideo = ct == "video" || (mimeLike == "video")
                            inAdaptAudio = ct == "audio" || (mimeLike == "audio")
                        }
                        "Representation" -> {
                            val id = xpp.getAttributeValue(null, "id") ?: ""
                            val mime = xpp.getAttributeValue(null, "mimeType") ?: adaptMime
                            val codecs = xpp.getAttributeValue(null, "codecs") ?: ""
                            val bw = xpp.getAttributeValue(null, "bandwidth")?.toLongOrNull() ?: 0L
                            val w = xpp.getAttributeValue(null, "width")?.toIntOrNull() ?: 0
                            val h = xpp.getAttributeValue(null, "height")?.toIntOrNull() ?: 0
                            val fr = xpp.getAttributeValue(null, "frameRate") ?: ""
                            val kind = when {
                                mime.startsWith("audio") || inAdaptAudio -> "audio"
                                else -> "video"
                            }
                            val rep = Rep(kind, id, mime, codecs, bw, w, h, adaptLang, fr)
                            if (kind == "audio") a.add(rep) else v.add(rep)
                        }
                    }
                }
                event = xpp.next()
            }
        } catch (_: Exception) {
        }
        v.sortByDescending { it.height * 100_000L + it.bandwidth }
        a.sortByDescending { it.bandwidth }
        return Mpd(v, a)
    }

    /**
     * Rewrite an MPD so that it keeps ONLY the chosen [Rep]s. When no choice
     * is given the original text is returned unchanged (FFmpeg picks its own
     * best track). Keeps every SegmentTemplate/SegmentList/BaseURL element.
     */
    /**
     * Rewrite [original] MPD text retaining only the chosen video and audio
     * Representation elements. Also injects an absolute [BaseURL] if [masterUrl]
     * is provided so that relative URLs work when the MPD is saved locally.
     */
    fun filterMpd(original: String, keepVideo: Rep?, keepAudio: Rep?, masterUrl: String? = null): String {
        if (keepVideo == null && keepAudio == null && masterUrl == null) return original
        val keepVideoId = keepVideo?.id
        val keepAudioId = keepAudio?.id

        val out = StringBuilder()
        try {
            val f = XmlPullParserFactory.newInstance()
            f.isNamespaceAware = false
            val xpp = f.newPullParser()
            xpp.setInput(StringReader(original))
            var dropping = 0
            var event = xpp.eventType
            var inAudioSet = false
            val baseUrlToInject = masterUrl?.substringBeforeLast('/', "")?.let { if (it.isNotEmpty()) "$it/" else null }

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val name = xpp.name
                        if (name == "AdaptationSet") {
                            val ct = xpp.getAttributeValue(null, "contentType") ?: ""
                            val mime = xpp.getAttributeValue(null, "mimeType") ?: ""
                            inAudioSet = ct == "audio" || mime.startsWith("audio")
                        }

                        val keep = when {
                            name == "Representation" -> {
                                val repId = xpp.getAttributeValue(null, "id") ?: ""
                                if (inAudioSet) {
                                    keepAudioId == null || repId == keepAudioId
                                } else {
                                    keepVideoId == null || repId == keepVideoId
                                }
                            }
                            else -> true
                        }

                        if (!keep || dropping > 0) {
                            dropping++
                        } else {
                            out.append('<').append(name)
                            for (i in 0 until xpp.attributeCount) {
                                out.append(' ').append(xpp.getAttributeName(i))
                                    .append("=\"").append(xmlEsc(xpp.getAttributeValue(i))).append('"')
                            }
                            if (xpp.isEmptyElementTag) {
                                out.append("/>")
                            } else {
                                out.append('>')
                                if (name == "MPD" && baseUrlToInject != null) {
                                    out.append("<BaseURL>").append(xmlEsc(baseUrlToInject)).append("</BaseURL>")
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (dropping == 0) out.append(xmlEsc(xpp.text ?: ""))
                    }
                    XmlPullParser.END_TAG -> {
                        if (dropping > 0) {
                            dropping--
                        } else {
                            out.append("</").append(xpp.name).append('>')
                        }
                    }
                }
                event = xpp.next()
            }
        } catch (_: Exception) {
            return original
        }
        return out.toString()
    }

    private fun xmlEsc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    /** Durations like "PT1H2M3S" / "PT30.5S" -> seconds. */
    fun durationToSec(v: String): Double {
        if (v.isBlank()) return 0.0
        if (!v.startsWith("P")) return v.toDoubleOrNull() ?: 0.0
        var total = 0.0
        val num = StringBuilder()
        val inTime = v.startsWith("PT")
        var s = if (inTime) v.substring(2) else v.substring(1)
        for (c in s) {
            when (c) {
                'H' -> { total += (num.toString().toDoubleOrNull() ?: 0.0) * 3600; num.clear() }
                'M' -> { total += (num.toString().toDoubleOrNull() ?: 0.0) * 60; num.clear() }
                'S' -> { total += num.toString().toDoubleOrNull() ?: 0.0; num.clear() }
                'D', 'T' -> {}
                else -> if (c.isDigit() || c == '.') num.append(c)
            }
        }
        return total
    }
}
