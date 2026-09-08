package uz.komil.mediapro.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uz.komil.mediapro.R
import uz.komil.mediapro.data.OpKind
import uz.komil.mediapro.dl.EditPlan
import uz.komil.mediapro.dl.JobManager
import uz.komil.mediapro.storage.MediaFolder
import uz.komil.mediapro.ui.widget.MediaPick
import uz.komil.mediapro.ui.widget.PlayerBox
import uz.komil.mediapro.ui.widget.rememberMediaPlayer

/**
 * Audio replace / mix. Picks a second audio file and blends it over the base
 * media with two independent 0–200% volume sliders.
 *  - replace → the base's original audio is swapped for the added track
 *  - mix     → base audio and added track are mixed together
 */
@Composable
fun MixScreen(
    src: String,
    srcName: String,
    durMs: Long,
    onBack: () -> Unit,
    onChangeMedia: () -> Unit
) {
    val ctx = LocalContext.current
    val player by rememberMediaPlayer(src)
    var addPath by rememberSaveable { mutableStateOf("") }
    var volBase by rememberSaveable { mutableIntStateOf(100) }
    var volAdd by rememberSaveable { mutableIntStateOf(100) }
    var replace by rememberSaveable { mutableStateOf(false) }
    var ext by rememberSaveable {
        mutableStateOf(MediaPick.sourceExtFallback(src, "mp4").takeIf { it in setOf("mp4", "mkv", "m4a") } ?: "mp4")
    }

    val addLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val picked = MediaPick.copyToCache(ctx, uri)
            if (picked != null) addPath = picked.path
            else Toast.makeText(ctx, ctx.getString(R.string.ed_pick_failed), Toast.LENGTH_SHORT).show()
        }
    }

    androidx.compose.runtime.LaunchedEffect(volBase, replace) {
        player?.volume = if (replace) 0f else (volBase / 100f).coerceIn(0f, 1f)
    }

    fun submit() {
        if (addPath.isEmpty()) {
            Toast.makeText(ctx, ctx.getString(R.string.ed_need_audio2), Toast.LENGTH_SHORT).show()
            return
        }
        player?.pause()
        JobManager.start(
            kind = OpKind.MIXED,
            title = srcName + "_mix",
            sourceUrl = src,
            folder = MediaFolder.MIXED,
            outExt = ext,
            onBlocked = { Toast.makeText(ctx, ctx.getString(R.string.ed_trial_blocked), Toast.LENGTH_SHORT).show() },
            params = EditPlan.mixParams(addPath, volBase, volAdd, if (replace) "replace" else "mix", ext)
        )
        Toast.makeText(ctx, ctx.getString(R.string.ed_started), Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
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
            Text(stringResource(R.string.ed_op_mix), style = MaterialTheme.typography.titleLarge)
        }
        if (player != null) {
            PlayerBox(player, modifier = Modifier.fillMaxWidth().height(200.dp).padding(top = 8.dp))
        }
        Text(srcName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

        // Second (overlay) audio.
        if (addPath.isEmpty()) {
            OutlinedButton(onClick = { addLauncher.launch(arrayOf("audio/*")) }, modifier = Modifier.padding(top = 10.dp)) {
                Text(stringResource(R.string.ed_second_audio))
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Text(stringResource(R.string.ed_audio2_loaded), modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { addLauncher.launch(arrayOf("audio/*")) }) {
                    Text(stringResource(R.string.ed_change_file))
                }
            }
        }

        // Mode.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            FilterChipRow(
                selectedReplace = replace,
                onPick = { replace = it }
            )
        }

        // Dual volume sliders 0–200%.
        VolSlider(stringResource(R.string.ed_volume_base), volBase) { volBase = it }
        VolSlider(stringResource(R.string.ed_volume_add), volAdd) { volAdd = it }

        // Output container.
        Text(stringResource(R.string.ed_format), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("mp4", "mkv", "m4a").forEach { e ->
                androidx.compose.material3.FilterChip(
                    selected = ext == e,
                    onClick = { ext = e },
                    label = { Text(e) }
                )
            }
        }

        Button(onClick = ::submit, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Text(stringResource(R.string.ed_run_mix))
        }
        OutlinedButton(onClick = onChangeMedia, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(stringResource(R.string.ed_change_file))
        }
    }
}

@Composable
private fun VolSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("$label: $value%", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..200f,
            steps = 19,
            modifier = Modifier.semantics {
                contentDescription = "$label: $value foiz"
                stateDescription = "$value%"
            }
        )
    }
}

@Composable
private fun FilterChipRow(selectedReplace: Boolean, onPick: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.material3.FilterChip(
            selected = !selectedReplace,
            onClick = { onPick(false) },
            label = { Text(stringResource(R.string.ed_mode_mix)) }
        )
        androidx.compose.material3.FilterChip(
            selected = selectedReplace,
            onClick = { onPick(true) },
            label = { Text(stringResource(R.string.ed_mode_replace)) }
        )
    }
}
