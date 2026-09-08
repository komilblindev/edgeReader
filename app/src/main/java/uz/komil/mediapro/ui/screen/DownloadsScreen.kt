package uz.komil.mediapro.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import uz.komil.mediapro.R
import uz.komil.mediapro.data.ErrorLog
import uz.komil.mediapro.data.JobStatus
import uz.komil.mediapro.data.OpKind
import uz.komil.mediapro.dl.JobManager
import uz.komil.mediapro.storage.MediaFolders
import uz.komil.mediapro.util.TimeFmt
import java.io.File

/**
 * Downloads hub: every job (link download, live recording, trim/cut/mix/
 * convert) is listed here with live progress + cancel, and finished items give
 * open / share / copy-link actions.
 */
@Composable
fun DownloadsScreen(contentPadding: PaddingValues) {
    val ctx = LocalContext.current
    val items by JobManager.jobs.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        Text(
            stringResource(R.string.tab_downloads),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.dl_empty),
                    color = MaterialTheme.colorScheme.outline
                )
            }
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            items(items, key = { it.record.id }) { itm ->
                DownloadRow(
                    context = ctx,
                    title = itm.record.title,
                    sub = folderLabel(ctx, itm.record),
                    fileName = itm.record.fileName,
                    status = itm.record.status,
                    isLive = itm.isLive,
                    isRecording = itm.record.kind == OpKind.RECORDING,
                    progress = itm.record.progress,
                    uri = itm.record.savedUri,
                    sourceUrl = itm.record.sourceUrl,
                    sizeBytes = itm.record.sizeBytes,
                    error = itm.record.error,
                    onCancel = { JobManager.cancel(itm.record.id) }
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(
    context: Context,
    title: String,
    sub: String,
    fileName: String,
    status: JobStatus,
    isLive: Boolean,
    isRecording: Boolean,
    progress: Int,
    uri: String,
    sourceUrl: String,
    sizeBytes: Long,
    error: String,
    onCancel: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$sub · ${fileName.ifBlank { "-" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            when {
                isLive && status == JobStatus.RUNNING -> {
                    val progressLabel = "$progress%"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (isRecording) stringResource(R.string.rec_active_banner) else stringResource(R.string.dl_running)) + " $progressLabel",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.semantics {
                                stateDescription = progressLabel
                            }
                        )
                        if (isRecording) {
                            Button(
                                onClick = onCancel,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .semantics {
                                        contentDescription = "${context.getString(R.string.rec_stop_desc)}: $title"
                                    }
                            ) {
                                Text(stringResource(R.string.rec_btn_stop), style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            TextButton(
                                onClick = onCancel,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .semantics {
                                        contentDescription = "${context.getString(R.string.dl_cancel)}: $title"
                                    }
                            ) {
                                Text(stringResource(R.string.dl_cancel))
                            }
                        }
                    }
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                progressBarRangeInfo = ProgressBarRangeInfo(progress / 100f, 0f..1f)
                                stateDescription = progressLabel
                                contentDescription = "$title: $progressLabel"
                            }
                    )
                }
                status == JobStatus.DONE -> {
                    Text(
                        stringResource(R.string.dl_done) + "  " + bytesLabel(sizeBytes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (uri.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                            TextButton(
                                onClick = { openUri(context, uri, fileName) },
                                modifier = Modifier.semantics {
                                    contentDescription = "${context.getString(R.string.dl_open)}: $fileName"
                                }
                            ) {
                                Text(stringResource(R.string.dl_open))
                            }
                            TextButton(
                                onClick = { shareUri(context, uri, fileName, title) },
                                modifier = Modifier.semantics {
                                    contentDescription = "${context.getString(R.string.dl_share)}: $fileName"
                                }
                            ) {
                                Text(stringResource(R.string.dl_share))
                            }
                            if (sourceUrl.startsWith("http")) {
                                TextButton(
                                    onClick = { copyLink(context, sourceUrl) },
                                    modifier = Modifier.semantics {
                                        contentDescription = "${context.getString(R.string.dl_copy)}: $title"
                                    }
                                ) {
                                    Text(stringResource(R.string.dl_copy))
                                }
                            }
                        }
                    }
                }
                status == JobStatus.FAILED -> {
                    Text(
                        stringResource(R.string.dl_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                    if (error.isNotBlank()) {
                        Text(error.take(160), style = MaterialTheme.typography.bodySmall, maxLines = 3)
                    }
                }
                status == JobStatus.CANCELED -> {
                    Text(stringResource(R.string.dl_canceled), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun folderLabel(ctx: Context, rec: uz.komil.mediapro.data.MediaRecord): String =
    MediaFolders.displayName(ctx, rec.folder)

private fun bytesLabel(b: Long): String = when {
    b <= 0 -> ""
    b < 1_000_000 -> String.format(java.util.Locale.US, "%.0f KB", b / 1000.0)
    else -> String.format(java.util.Locale.US, "%.1f MB", b / 1_000_000.0)
}

private fun resolveViewUri(ctx: Context, saved: String): Uri {
    if (saved.startsWith("content://")) return Uri.parse(saved)
    // API 28 legacy path.
    return FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", File(saved))
}

private fun openUri(ctx: Context, saved: String, fileName: String) {
    try {
        val u = resolveViewUri(ctx, saved)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(u, uz.komil.mediapro.storage.MediaSaver.mimeFor(fileName))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
    } catch (t: Throwable) {
        ErrorLog.e("Downloads", "open failed", t)
        Toast.makeText(ctx, ctx.getString(R.string.dl_open_fail), Toast.LENGTH_SHORT).show()
    }
}

private fun shareUri(ctx: Context, saved: String, fileName: String, title: String) {
    try {
        val u = resolveViewUri(ctx, saved)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = uz.komil.mediapro.storage.MediaSaver.mimeFor(fileName)
            putExtra(Intent.EXTRA_STREAM, u)
            putExtra(Intent.EXTRA_TITLE, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, ctx.getString(R.string.dl_share)))
    } catch (t: Throwable) {
        ErrorLog.e("Downloads", "share failed", t)
    }
}

private fun copyLink(ctx: Context, url: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("url", url))
    Toast.makeText(ctx, ctx.getString(R.string.dl_copied), Toast.LENGTH_SHORT).show()
}
