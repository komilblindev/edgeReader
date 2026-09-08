package uz.komil.mediapro.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uz.komil.mediapro.R
import uz.komil.mediapro.data.MediaRecord
import uz.komil.mediapro.data.OpKind
import uz.komil.mediapro.dl.EditPlan
import uz.komil.mediapro.dl.JobManager
import uz.komil.mediapro.lic.LicenseManager
import uz.komil.mediapro.storage.MediaFolder
import uz.komil.mediapro.ui.widget.HmsStepper
import uz.komil.mediapro.ui.widget.MediaPick
import uz.komil.mediapro.ui.widget.PlayerBox
import uz.komil.mediapro.ui.widget.rememberMediaPlayer
import uz.komil.mediapro.util.Hms
import uz.komil.mediapro.util.TimeFmt

/** One removable time interval [sMs, eMs). */
data class Intv(val s: Long, val e: Long)

private val IntvSaver = listSaver<ArrayList<Intv>, Long>(
    save = { it.flatMap { listOf(it.s, it.e) } },
    restore = { list ->
        val out = ArrayList<Intv>()
        list.chunked(2).forEach { p -> if (p.size == 2) out.add(Intv(p[0], p[1])) }
        out
    }
)

private const val HUB = 0
private const val SCREEN_TRIM = 1
private const val SCREEN_REMOVE = 2
private const val SCREEN_CONVERT = 3
private const val SCREEN_MIX = 4

/**
 * Editor hub → op screens. A device file is picked once (ACTION_OPEN_DOCUMENT),
 * copied to private cache, and every op (trim / interval-remove / audio
 * replace-mix / convert) runs through JobManager → FFmpeg with a live
 * ExoPlayer preview and HH:MM:SS time masks with step buttons.
 */
@Composable
fun EditorScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(HUB) }
    var src by rememberSaveable { mutableStateOf("") }
    var durMs by rememberSaveable { mutableLongStateOf(0L) }
    var srcName by rememberSaveable { mutableStateOf("") }
    var pickNonce by rememberSaveable { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val picked = MediaPick.copyToCache(context, uri)
            if (picked != null) {
                src = picked.path
                srcName = picked.name
                durMs = MediaPick.durationMs(picked.path)
            } else {
                Toast.makeText(context, context.getString(R.string.ed_pick_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }
    // Nonce-driven re-pick from any op screen.
    LaunchedEffect(pickNonce) {
        if (pickNonce > 0) launcher.launch(arrayOf("*/*"))
    }

    // A Box bounds each op screen so its own verticalScroll actually scrolls
    // (a non-scrolling Column would hand them unbounded height).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 12.dp)
    ) {
        when (step) {
            SCREEN_TRIM -> TrimScreen(
                src, srcName, durMs,
                onBack = { step = HUB },
                onChangeMedia = { pickNonce++ }
            )
            SCREEN_REMOVE -> RemoveScreen(
                src, srcName, durMs,
                onBack = { step = HUB },
                onChangeMedia = { pickNonce++ }
            )
            SCREEN_CONVERT -> ConvertScreen(
                src, srcName,
                onBack = { step = HUB },
                onChangeMedia = { pickNonce++ }
            )
            SCREEN_MIX -> MixScreen(
                src, srcName, durMs,
                onBack = { step = HUB },
                onChangeMedia = { pickNonce++ }
            )
            else -> EditorHub(
                src, srcName, durMs,
                onPick = { launcher.launch(arrayOf("*/*")) },
                onOp = { op -> step = op }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Hub
// ---------------------------------------------------------------------------

@Composable
private fun EditorHub(
    src: String,
    srcName: String,
    durMs: Long,
    onPick: () -> Unit,
    onOp: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Text(stringResource(R.string.tab_editor), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.ed_intro),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
        )

        if (src.isEmpty()) {
            Button(onClick = onPick, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(stringResource(R.string.ed_pick_file))
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(srcName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.ed_duration) + "  " + TimeFmt.compact(durMs),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onPick) { Text(stringResource(R.string.ed_change_file)) }
                    }
                }
            }
        }

        val enabled = src.isNotEmpty()
        val ops = listOf(
            Triple(SCREEN_TRIM, R.string.ed_op_trim, R.string.ed_op_trim_desc),
            Triple(SCREEN_REMOVE, R.string.ed_op_remove, R.string.ed_op_remove_desc),
            Triple(SCREEN_MIX, R.string.ed_op_mix, R.string.ed_op_mix_desc),
            Triple(SCREEN_CONVERT, R.string.ed_op_convert, R.string.ed_op_convert_desc)
        )
        ops.forEach { (code, title, desc) ->
            Card(
                onClick = { if (enabled) onOp(code) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (enabled) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(desc), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (!enabled) {
            Text(
                stringResource(R.string.ed_need_file),
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Trim
// ---------------------------------------------------------------------------

@Composable
private fun TrimScreen(
    src: String,
    srcName: String,
    durMs: Long,
    onBack: () -> Unit,
    onChangeMedia: () -> Unit
) {
    val ctx = LocalContext.current
    val player by rememberMediaPlayer(src)
    var fromMs by rememberSaveable(src) { mutableLongStateOf(0L) }
    var toMs by rememberSaveable(src) { mutableLongStateOf(durMs) }
    var ext by rememberSaveable { mutableStateOf("") } // "" => keep container

    fun submit() {
        if (toMs <= fromMs) {
            Toast.makeText(ctx, ctx.getString(R.string.ed_bad_range), Toast.LENGTH_SHORT).show()
            return
        }
        player?.pause()
        val outExt = ext.ifBlank { MediaPick.sourceExtFallback(src, "mp4") }
        JobManager.start(
            kind = OpKind.TRIMMED,
            title = srcName + "_trim",
            sourceUrl = src,
            folder = MediaFolder.TRIMMED,
            outExt = outExt,
            onBlocked = { blockedToast(ctx) },
            params = EditPlan.trimParams(fromMs, toMs, outExt)
        )
        startToast(ctx)
    }

    EditorScaffold(
        title = stringResource(R.string.ed_op_trim),
        subtitle = srcName,
        player = player,
        onBack = onBack,
        onChangeMedia = onChangeMedia,
        content = {
            FormatChips(ext, onPick = { ext = it })
            HmsStepper(
                stringResource(R.string.ed_from),
                Hms.fromMs(fromMs), { fromMs = it.totalMs; player?.seekTo(it.totalMs) },
                maxMs = durMs, getCurrentMs = { player?.currentPosition }
            )
            HmsStepper(
                stringResource(R.string.ed_to),
                Hms.fromMs(toMs), { toMs = it.totalMs; player?.seekTo(it.totalMs) },
                maxMs = durMs, getCurrentMs = { player?.currentPosition }
            )
        },
        actionText = stringResource(R.string.ed_run_trim),
        onAction = ::submit
    )
}

// ---------------------------------------------------------------------------
// Interval removal (≤10)
// ---------------------------------------------------------------------------

@Composable
private fun RemoveScreen(
    src: String,
    srcName: String,
    durMs: Long,
    onBack: () -> Unit,
    onChangeMedia: () -> Unit
) {
    val ctx = LocalContext.current
    val player by rememberMediaPlayer(src)
    var intervals by rememberSaveable(src, stateSaver = IntvSaver) { mutableStateOf(ArrayList<Intv>()) }
    var fromMs by rememberSaveable(src) { mutableLongStateOf(0L) }
    var toMs by rememberSaveable(src) { mutableLongStateOf(durMs.coerceAtMost(5000L)) }
    var ext by rememberSaveable { mutableStateOf("") }

    fun addInterval() {
        if (toMs <= fromMs) {
            Toast.makeText(ctx, ctx.getString(R.string.ed_bad_range), Toast.LENGTH_SHORT).show()
            return
        }
        if (intervals.size >= 10) {
            Toast.makeText(ctx, ctx.getString(R.string.ed_max_10), Toast.LENGTH_SHORT).show()
            return
        }
        intervals = ArrayList(intervals).apply { add(Intv(fromMs, toMs)) }
    }

    fun submit() {
        if (intervals.isEmpty()) {
            Toast.makeText(ctx, ctx.getString(R.string.ed_no_intervals), Toast.LENGTH_SHORT).show()
            return
        }
        player?.pause()
        val outExt = ext.ifBlank { MediaPick.sourceExtFallback(src, "mp4") }
        val remList = intervals.map { longArrayOf(it.s, it.e) }
        JobManager.start(
            kind = OpKind.EDITED,
            title = srcName + "_cut",
            sourceUrl = src,
            folder = MediaFolder.EDITED,
            outExt = outExt,
            onBlocked = { blockedToast(ctx) },
            params = EditPlan.removeParams(durMs, remList, outExt)
        )
        startToast(ctx)
    }

    EditorScaffold(
        title = stringResource(R.string.ed_op_remove),
        subtitle = srcName,
        player = player,
        onBack = onBack,
        onChangeMedia = onChangeMedia,
        content = {
            FormatChips(ext, onPick = { ext = it })
            Text(stringResource(R.string.ed_new_interval), style = MaterialTheme.typography.labelLarge)
            HmsStepper(
                stringResource(R.string.ed_from),
                Hms.fromMs(fromMs), { fromMs = it.totalMs; player?.seekTo(it.totalMs) },
                maxMs = durMs, getCurrentMs = { player?.currentPosition }
            )
            HmsStepper(
                stringResource(R.string.ed_to),
                Hms.fromMs(toMs), { toMs = it.totalMs; player?.seekTo(it.totalMs) },
                maxMs = durMs, getCurrentMs = { player?.currentPosition }
            )
            OutlinedButton(onClick = ::addInterval) {
                Text(stringResource(R.string.ed_add_interval))
            }
            if (intervals.isNotEmpty()) {
                Text(
                    stringResource(R.string.ed_intervals) + "  (${intervals.size}/10)",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
                intervals.forEachIndexed { i, iv ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "${i + 1})  ${Hms.fromMs(iv.s).toClock()} → ${Hms.fromMs(iv.e).toClock()}",
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { intervals = ArrayList(intervals).apply { removeAt(i) } }) {
                            Text("✕")
                        }
                    }
                }
            }
        },
        actionText = stringResource(R.string.ed_run_remove),
        onAction = ::submit
    )
}

// ---------------------------------------------------------------------------
// Convert (format change / audio extract)
// ---------------------------------------------------------------------------

@Composable
private fun ConvertScreen(
    src: String,
    srcName: String,
    onBack: () -> Unit,
    onChangeMedia: () -> Unit
) {
    val ctx = LocalContext.current
    val player by rememberMediaPlayer(src)
    val def = if (MediaPick.sourceExtFallback(src, "mp4") in setOf("mp3", "m4a", "aac", "ogg", "flac", "wav")) "mp3" else "mp4"
    var ext by rememberSaveable { mutableStateOf(def) }

    fun submit() {
        player?.pause()
        JobManager.start(
            kind = OpKind.CONVERTED,
            title = srcName + "_conv",
            sourceUrl = src,
            folder = MediaFolder.AUDIO,
            outExt = ext,
            onBlocked = { blockedToast(ctx) },
            params = EditPlan.convertParams(ext)
        )
        startToast(ctx)
    }

    EditorScaffold(
        title = stringResource(R.string.ed_op_convert),
        subtitle = srcName,
        player = player,
        onBack = onBack,
        onChangeMedia = onChangeMedia,
        content = {
            Text(stringResource(R.string.ed_format), style = MaterialTheme.typography.labelLarge)
            FormatChips(ext, onPick = { ext = it }, audioFirst = true)
        },
        actionText = stringResource(R.string.ed_run_convert),
        onAction = ::submit
    )
}

// ---------------------------------------------------------------------------
// Shared scaffolding for the four op screens
// ---------------------------------------------------------------------------

@Composable
private fun EditorScaffold(
    title: String,
    subtitle: String,
    player: androidx.media3.exoplayer.ExoPlayer?,
    onBack: () -> Unit,
    onChangeMedia: () -> Unit,
    content: @Composable () -> Unit,
    actionText: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    player?.pause()
                    onBack()
                },
                modifier = Modifier.semantics {
                    contentDescription = "Orqaga / Back"
                }
            ) { Text("←") }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        if (player != null) {
            PlayerBox(
                player,
                modifier = Modifier.fillMaxWidth().height(210.dp).padding(top = 8.dp)
            )
        }
        OutlinedButton(
            onClick = {
                player?.pause()
                onChangeMedia()
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.ed_change_file))
        }
        Column(Modifier.padding(top = 8.dp)) { content() }
        Button(onClick = onAction, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Text(actionText)
        }
    }
}

/** Output-format chips. `audioFirst` orders audio targets before containers. */
@Composable
fun FormatChips(selected: String, onPick: (String) -> Unit, audioFirst: Boolean = false) {
    val audio = listOf("mp3", "m4a", "aac", "ogg", "flac", "wav")
    val video = listOf("mp4", "mkv")
    val all = if (audioFirst) audio + video else audio
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        all.forEach { e ->
            FilterChip(
                selected = selected == e,
                onClick = { onPick(e) },
                label = { Text(e) }
            )
        }
    }
}

private fun startToast(ctx: android.content.Context) {
    Toast.makeText(ctx, ctx.getString(R.string.ed_started), Toast.LENGTH_SHORT).show()
}

private fun blockedToast(ctx: android.content.Context) {
    Toast.makeText(ctx, ctx.getString(R.string.ed_trial_blocked), Toast.LENGTH_SHORT).show()
}
