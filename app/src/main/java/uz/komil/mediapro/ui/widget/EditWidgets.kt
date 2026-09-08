package uz.komil.mediapro.ui.widget

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import uz.komil.mediapro.data.ErrorLog
import uz.komil.mediapro.util.Hms
import java.io.File

/** Media source picked from the device, copied into our private cache. */
data class PickedMedia(val path: String, val name: String)

/** Helpers to intake a device file (ACTION_OPEN_DOCUMENT) for editing. */
object MediaPick {

    /** Copy a content:// media file into cacheDir/editor and return its path. */
    fun copyToCache(ctx: Context, uri: Uri): PickedMedia? {
        return try {
            val mime = ctx.contentResolver.getType(uri) ?: ""
            val ext = extFor(mime, uri)
            val disp = displayName(ctx, uri)
            val dir = File(ctx.cacheDir, "editor")
            if (!dir.exists()) dir.mkdirs()
            val out = File(dir, "src_" + System.currentTimeMillis() + "." + ext)
            ctx.contentResolver.openInputStream(uri)?.use { inp ->
                out.outputStream().use { inp.copyTo(it) }
            } ?: return null
            PickedMedia(out.absolutePath, disp)
        } catch (t: Throwable) {
            ErrorLog.e("MediaPick", "copy failed", t)
            null
        }
    }

    fun durationMs(path: String): Long {
        return try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(path)
            val d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            mmr.release()
            d.coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        }
    }

    /** Extension of a local file path, or [def] when unrecognized. */
    fun sourceExtFallback(path: String, def: String): String {
        val e = path.substringAfterLast('.').substringBefore('?').lowercase()
        return if (e.length in 1..5 && e.all { it.isLetterOrDigit() }) e else def
    }

    fun displayName(ctx: Context, uri: Uri): String {
        val q = uri.query
        val name = q?.substringAfter("display_name=", "")?.substringBefore("&")?.let {
            Uri.decode(it)
        }
        if (!name.isNullOrBlank()) return name
        val last = uri.lastPathSegment ?: "media"
        return if (last.contains('.')) last else last + "." + extFor(ctx.contentResolver.getType(uri) ?: "", uri)
    }

    private fun extFor(mime: String, uri: Uri): String = when {
        mime.contains("mp4") || mime.contains("mpeg4") -> "mp4"
        mime.contains("mp3") -> "mp3"
        mime.contains("m4a") || mime == "audio/mp4" -> "m4a"
        mime.contains("aac") -> "aac"
        mime.contains("wav") -> "wav"
        mime.contains("flac") -> "flac"
        mime.contains("ogg") -> "ogg"
        mime.contains("opus") -> "opus"
        mime.contains("mkv") -> "mkv"
        mime.contains("webm") -> "webm"
        mime.contains("mov") || mime.contains("quicktime") -> "mov"
        mime.contains("3gpp") -> "3gp"
        else -> {
            val seg = uri.lastPathSegment ?: ""
            seg.substringAfterLast('.', "mp4").substringBefore('?').takeIf { it.length in 1..5 } ?: "mp4"
        }
    }
}

/**
 * Creates an ExoPlayer for a local media file and exposes it via [playerState]
 * so screens can seek/preview. Released when the path changes or leaves.
 */
@Composable
fun rememberMediaPlayer(path: String): MutableState<ExoPlayer?> {
    val ctx = LocalContext.current
    val state = remember(path) { mutableStateOf<ExoPlayer?>(null) }
    DisposableEffect(path) {
        if (path.isBlank() || !File(path).exists()) {
            state.value = null
            return@DisposableEffect onDispose {}
        }
        val p = try {
            ExoPlayer.Builder(ctx).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
                prepare()
            }
        } catch (t: Throwable) {
            ErrorLog.e("Player", "init failed", t)
            null
        }
        state.value = p
        onDispose {
            try {
                p?.stop()
                p?.release()
            } catch (_: Exception) {}
            state.value = null
        }
    }
    return state
}

/** An [ExoPlayer] wrapped in a [PlayerView] (AndroidView) for Compose. */
@Composable
fun PlayerBox(player: ExoPlayer?, modifier: Modifier = Modifier) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
                setShowFastForwardButton(true)
                setShowRewindButton(true)
                this.player = player
            }
        },
        update = { it.player = player },
        modifier = modifier
    )
}

/**
 * HH:MM:SS field with per-unit numeric cells and step buttons
 * (−1s / −5s / −1m / −5m and their + counterparts). Every press clamps to
 * 0..[maxMs] when provided. `onCurrent` shows a "set = playhead" shortcut
 * when the caller passes a live position.
 */
@Composable
fun HmsStepper(
    label: String,
    value: Hms,
    onSet: (Hms) -> Unit,
    maxMs: Long? = null,
    currentMs: Long? = null,
    getCurrentMs: (() -> Long?)? = null,
    modifier: Modifier = Modifier
) {
    fun clamp(candidate: Hms): Hms {
        val ms = candidate.totalMs.coerceAtLeast(0)
        return if (maxMs != null) Hms.fromMs(ms.coerceAtMost(maxMs)) else Hms.fromMs(ms)
    }

    var menuExpanded by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                var rawText by remember(value) { mutableStateOf(value.toClock()) }
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { input ->
                        rawText = input
                        val parsed = Hms.parse(input)
                        if (parsed != null) {
                            onSet(clamp(parsed))
                        } else {
                            val digitsOnly = input.filter { it.isDigit() }
                            if (digitsOnly.length in 1..6) {
                                val padded = digitsOnly.padStart(6, '0')
                                val h = padded.substring(0, 2).toInt()
                                val m = padded.substring(2, 4).toInt()
                                val s = padded.substring(4, 6).toInt()
                                onSet(clamp(Hms(h, m, s)))
                            }
                        }
                    },
                    readOnly = false,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 18.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = { menuExpanded = !menuExpanded },
                            modifier = Modifier.semantics {
                                contentDescription = "$label vaqtlar spiskasini ochish"
                            }
                        ) {
                            Text(if (menuExpanded) "▲" else "▼", style = MaterialTheme.typography.bodyMedium)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "$label: ${value.toClock()}"
                        }
                )

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.heightIn(max = 280.dp)
                ) {
                    val presets = mutableListOf<Pair<String, Long>>()
                    presets.add("00:00:00 (Boshlanishi)" to 0L)
                    if (maxMs != null && maxMs > 0) {
                        presets.add("25% (${Hms.fromMs((maxMs * 0.25).toLong()).toClock()})" to (maxMs * 0.25).toLong())
                        presets.add("50% Yarmi (${Hms.fromMs((maxMs * 0.50).toLong()).toClock()})" to (maxMs * 0.50).toLong())
                        presets.add("75% (${Hms.fromMs((maxMs * 0.75).toLong()).toClock()})" to (maxMs * 0.75).toLong())
                        presets.add("Oxiri (${Hms.fromMs(maxMs).toClock()})" to maxMs)
                    }
                    val fixedOffsets = listOf(
                        "10 soniya" to 10_000L,
                        "30 soniya" to 30_000L,
                        "1 daqiqa" to 60_000L,
                        "2 daqiqa" to 120_000L,
                        "5 daqiqa" to 300_000L,
                        "10 daqiqa" to 600_000L,
                        "15 daqiqa" to 900_000L,
                        "30 daqiqa" to 1800_000L,
                        "1 soat" to 3600_000L
                    )
                    fixedOffsets.forEach { (tLabel, ms) ->
                        if (maxMs == null || ms <= maxMs) {
                            presets.add("$tLabel (${Hms.fromMs(ms).toClock()})" to ms)
                        }
                    }

                    presets.forEach { (pLabel, pMs) ->
                        val isSelected = value.totalMs == pMs
                        DropdownMenuItem(
                            text = {
                                Text(
                                    pLabel,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onSet(clamp(Hms.fromMs(pMs)))
                                menuExpanded = false
                            },
                            modifier = Modifier.semantics {
                                contentDescription = pLabel
                            }
                        )
                    }
                }
            }

            if (currentMs != null || getCurrentMs != null) {
                OutlinedButton(
                    onClick = {
                        val live = getCurrentMs?.invoke() ?: currentMs ?: 0L
                        onSet(clamp(Hms.fromMs(live)))
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Pleerning joriy vaqtini o'rnatish"
                    }
                ) {
                    Text("↺")
                }
            }
        }

        // Tezkor pozitsiyalar (Boshi, 25%, 50%, 75%, Oxiri)
        if (maxMs != null && maxMs > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                val chips = listOf(
                    "Boshi" to 0L,
                    "25%" to (maxMs * 0.25).toLong(),
                    "50% (Yarmi)" to (maxMs * 0.50).toLong(),
                    "75%" to (maxMs * 0.75).toLong(),
                    "Oxiri" to maxMs
                )
                chips.forEach { (cLabel, cMs) ->
                    val isNear = kotlin.math.abs(value.totalMs - cMs) < 500L
                    FilterChip(
                        selected = isNear,
                        onClick = { onSet(clamp(Hms.fromMs(cMs))) },
                        label = { Text(cLabel, fontSize = 11.sp) },
                        modifier = Modifier
                            .height(30.dp)
                            .semantics {
                                contentDescription = "$cLabel (${Hms.fromMs(cMs).toClock()})"
                            }
                    )
                }
            }
        }

        val steps = listOf(
            Triple("-5m", -5L * 60_000L, "5 daqiqa kamaytirish"),
            Triple("-1m", -60_000L, "1 daqiqa kamaytirish"),
            Triple("-10s", -10_000L, "10 soniya kamaytirish"),
            Triple("-1s", -1000L, "1 soniya kamaytirish"),
            Triple("+1s", 1000L, "1 soniya oshirish"),
            Triple("+10s", 10_000L, "10 soniya oshirish"),
            Triple("+1m", 60_000L, "1 daqiqa oshirish"),
            Triple("+5m", 5L * 60_000L, "5 daqiqa oshirish")
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            steps.forEach { (lab, delta, desc) ->
                OutlinedButton(
                    onClick = {
                        val base = if (value == Hms.ZERO && delta < 0) Hms.ZERO else clamp(value + Hms.fromMs(delta))
                        onSet(base)
                    },
                    contentPadding = PaddingValues(
                        horizontal = 8.dp, vertical = 0.dp
                    ),
                    modifier = Modifier
                        .height(34.dp)
                        .semantics {
                            contentDescription = desc
                        }
                ) { Text(lab, fontSize = 12.sp) }
            }
        }
    }
}

/** A two-cell HH:MM numeric display used by the interval list header. */
@Composable
fun HmsText(hms: Hms, modifier: Modifier = Modifier) {
    Text(
        hms.toClock(),
        modifier = modifier,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodyMedium
    )
}
