package uz.komil.mediapro.ui.screen

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.webkit.CookieManager
import uz.komil.mediapro.R
import uz.komil.mediapro.data.AppPrefs
import uz.komil.mediapro.data.BrowserHistory
import uz.komil.mediapro.data.ErrorLog
import uz.komil.mediapro.data.HistoryEntry
import uz.komil.mediapro.data.JobStatus
import uz.komil.mediapro.data.OpKind
import uz.komil.mediapro.dl.Dash
import uz.komil.mediapro.dl.Hls
import uz.komil.mediapro.dl.JobManager
import uz.komil.mediapro.dl.MediaEngine
import uz.komil.mediapro.dl.WorkDir
import uz.komil.mediapro.net.AdBlocker
import uz.komil.mediapro.net.Http
import uz.komil.mediapro.storage.MediaFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Built-in browser with a passive media sniffer.
 *
 * The WebView renders normal pages; every video/audio request it makes
 * (manifests and media files) is recorded into the "found" panel below. A URL
 * that is typed or shared and is itself a media link (HLS / DASH / direct
 * mp4·webm·mp3…) becomes a "direct media" card instead of a page load.
 *
 * Download of an HLS master or a DASH manifest opens a quality picker; live
 * manifests also offer "Record live". All work runs through [JobManager] →
 * Downloads hub, where it can be stopped and its progress watched.
 */
@Composable
fun BrowserScreen(
    contentPadding: PaddingValues,
    externalUrl: String?,
    onExternalUrlConsumed: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var urlText by remember { mutableStateOf("") }
    var wv by remember { mutableStateOf<WebView?>(null) }
    var toLoad by remember { mutableStateOf<String?>(null) }
    var canBack by remember { mutableStateOf(false) }
    var canFwd by remember { mutableStateOf(false) }

    var direct by remember { mutableStateOf<FoundMedia?>(null) }
    var found by remember { mutableStateOf<List<FoundMedia>>(emptyList()) }
    val seen = remember { mutableStateOf(mutableSetOf<String>()) }

    var dlTarget by remember { mutableStateOf<FoundMedia?>(null) }
    var dlExt by remember { mutableStateOf("") }
    var qTarget by remember { mutableStateOf<FoundMedia?>(null) }
    var qItems by remember { mutableStateOf<List<QualityItem>>(emptyList()) }
    var qBusy by remember { mutableStateOf(true) }
    var recTarget by remember { mutableStateOf<FoundMedia?>(null) }
    val maxResults by AppPrefs.maxResultsFlow(ctx).collectAsState(initial = 0)
    val allJobs by JobManager.jobs.collectAsState()
    val activeRecordings = allJobs.filter {
        it.record.kind == OpKind.RECORDING && it.isLive && it.record.status == JobStatus.RUNNING
    }
    var showHistory by remember { mutableStateOf(false) }
    var adBlockEnabled by remember { mutableStateOf(true) }
    val historyEntries by BrowserHistory.entries.collectAsState()

    // ---- actions -------------------------------------------------------
    fun msg(text: String) = shortToast(ctx, text)

    /** Record one sniffed media URL into the found panel (any thread). */
    fun sniffInto(url: String) {
        val kind = sniffKind(url, "") ?: return
        if (seen.value.add(url)) {
            found = (found + FoundMedia(url, kind)).takeLast(40)
        }
    }

    fun go(raw: String) {
        val u = normalizeUrl(raw) ?: return
        urlText = u
        val kind = MediaEngine.classify(u)
        if (kind == MediaEngine.Kind.UNKNOWN) {
            direct = null
            found = emptyList()
            seen.value.clear()
            toLoad = u
        } else {
            // A media link: stop the page, offer the media card instead.
            wv?.loadUrl("about:blank")
            direct = FoundMedia(u, kind)
        }
    }

    fun startDownload(m: FoundMedia, ext: String, title: String) {
        JobManager.start(
            kind = OpKind.DOWNLOAD,
            title = title.ifBlank { baseName(m.url) },
            sourceUrl = m.url,
            folder = MediaFolder.DOWNLOADS,
            outExt = ext
        )
        msg(ctx.getString(R.string.ed_started))
    }

    fun startRecord(m: FoundMedia, fileName: String, ext: String, durationSec: Long) {
        JobManager.start(
            kind = OpKind.RECORDING,
            title = fileName.ifBlank { baseName(m.url) },
            sourceUrl = m.url,
            folder = MediaFolder.RECORDINGS,
            outExt = ext.ifBlank { "mp4" },
            params = if (durationSec > 0L) durationSec.toString() else ""
        )
        msg(ctx.getString(R.string.ed_started))
    }

    // External Share/VIEW intake. Consumed once; media opens the card.
    LaunchedEffect(externalUrl) {
        val u = externalUrl ?: return@LaunchedEffect
        onExternalUrlConsumed()
        go(u)
    }

    // Deferred page load: fires once the WebView exists and a URL is queued.
    LaunchedEffect(toLoad, wv) {
        val t = toLoad
        val v = wv
        if (t != null && v != null) {
            toLoad = null
            v.loadUrl(t)
        }
    }

    // ---- chrome --------------------------------------------------------
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        Surface(tonalElevation = 2.dp) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                TextButton(
                    onClick = { wv?.goBack() },
                    enabled = canBack,
                    modifier = Modifier.semantics {
                        contentDescription = ctx.getString(R.string.brw_back)
                    }
                ) {
                    Text("←", fontSize = MaterialTheme.typography.titleMedium.fontSize)
                }
                TextButton(
                    onClick = { wv?.goForward() },
                    enabled = canFwd,
                    modifier = Modifier.semantics {
                        contentDescription = ctx.getString(R.string.brw_forward)
                    }
                ) {
                    Text("→", fontSize = MaterialTheme.typography.titleMedium.fontSize)
                }
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    placeholder = { Text(stringResource(R.string.brw_hint)) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = ctx.getString(R.string.brw_hint)
                        }
                )
                TextButton(
                    onClick = { go(urlText) },
                    modifier = Modifier.semantics {
                        contentDescription = ctx.getString(R.string.brw_open)
                    }
                ) { Text(stringResource(R.string.brw_open)) }

                TextButton(
                    onClick = {
                        adBlockEnabled = !adBlockEnabled
                        msg(if (adBlockEnabled) ctx.getString(R.string.brw_adblock_on) else ctx.getString(R.string.brw_adblock_off))
                    },
                    modifier = Modifier.semantics {
                        contentDescription = if (adBlockEnabled) ctx.getString(R.string.brw_adblock_on) else ctx.getString(R.string.brw_adblock_off)
                    }
                ) {
                    Text(if (adBlockEnabled) "🛡️" else "⚪")
                }

                TextButton(
                    onClick = { showHistory = true },
                    modifier = Modifier.semantics {
                        contentDescription = ctx.getString(R.string.brw_history_title)
                    }
                ) {
                    Text("🕒")
                }
            }
        }

        // ---- WebView (collapsed while a direct-media card is shown) -----
        if (direct == null) {
            AndroidView(
                factory = { c ->
                    buildWebView(
                        c = c,
                        onNew = { v -> wv = v },
                        onSniff = { sniffInto(it) },
                        onNav = { b, f ->
                            canBack = b
                            canFwd = f
                        },
                        onUrl = { u -> if (toLoad == null) urlText = u },
                        isAdBlockOn = { adBlockEnabled },
                        onPageVisited = { title, u ->
                            scope.launch { BrowserHistory.add(title, u) }
                        }
                    )
                },
                update = { v -> wv = v },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }

        if (activeRecordings.isNotEmpty()) {
            ActiveRecordingsSection(
                recordings = activeRecordings,
                onStop = { id -> JobManager.cancel(id) }
            )
        }

        // ---- media panel ----
        val shown: List<FoundMedia> = if (direct != null) {
            listOf(direct!!)
        } else {
            if (maxResults in 1..99) found.take(maxResults) else found
        }
        if (shown.isNotEmpty()) {
            if (direct == null) {
                Text(
                    stringResource(R.string.brw_media_count, shown.size),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 14.dp, top = 4.dp)
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 230.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp)
            ) {
                shown.forEach { m ->
                    val recJob = activeRecordings.firstOrNull { it.record.sourceUrl == m.url }
                    MediaCard(
                        m = m,
                        recordingJob = recJob,
                        onDownload = { dlTarget = m; dlExt = defaultOutExt(m) },
                        onQuality = {
                            qTarget = m
                            qBusy = true
                            scope.launch {
                                qItems = loadQualities(m.url)
                                qBusy = false
                            }
                        },
                        onRecord = { recTarget = m },
                        onStopRecord = { recJob?.let { JobManager.cancel(it.record.id) } }
                    )
                }
            }
        }
    }

    // ---- dialogs -------------------------------------------------------
    val tgt = dlTarget
    if (tgt != null) {
        ExtDialog(
            ctx = ctx,
            m = tgt,
            selected = dlExt,
            onPick = { dlExt = it },
            onDismiss = { dlTarget = null },
            onConfirm = {
                dlTarget = null
                startDownload(tgt, dlExt, "")
            }
        )
    }

    val qt = qTarget
    if (qt != null) {
        QualityDialog(
            ctx = ctx,
            target = qt,
            items = qItems,
            busy = qBusy,
            onDismiss = { qTarget = null },
            onPickBest = {
                qTarget = null
                startDownload(qt, defaultOutExt(qt), "")
            },
            onPick = { item ->
                qTarget = null
                when {
                    item.url.startsWith("hls:") -> startDownload(
                        qt.copy(url = item.url.removePrefix("hls:")),
                        defaultOutExt(qt), item.label
                    )
                    item.url.startsWith("dash:") -> {
                        val idx = item.url.removePrefix("dash:").toIntOrNull()
                        if (idx != null) {
                            scope.launch {
                                val path = buildDashFiltered(ctx, qt.url, idx)
                                if (path != null) {
                                    startDownload(qt.copy(url = path), "mp4", item.label)
                                } else {
                                    msg(ctx.getString(R.string.brw_err_parse))
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    val rt = recTarget
    if (rt != null) {
        RecordDialog(
            ctx = ctx,
            target = rt,
            onDismiss = { recTarget = null },
            onConfirm = { fileName, ext, durationSec ->
                recTarget = null
                startRecord(rt, fileName, ext, durationSec)
            }
        )
    }

    if (showHistory) {
        HistoryDialog(
            ctx = ctx,
            entries = historyEntries,
            onOpen = { pickedUrl ->
                showHistory = false
                go(pickedUrl)
            },
            onDelete = { id ->
                scope.launch { BrowserHistory.remove(id) }
            },
            onClear = {
                scope.launch {
                    BrowserHistory.clear()
                    msg(ctx.getString(R.string.brw_history_cleared))
                }
            },
            onDismiss = { showHistory = false }
        )
    }
}

// ---------------------------------------------------------------------------
// Sniffer helpers
// ---------------------------------------------------------------------------

/** One sniffed / typed media target. */
data class FoundMedia(val url: String, val kind: MediaEngine.Kind)

private val EXTRA_KIND = mapOf(
    "m2ts" to MediaEngine.Kind.VIDEO_DIRECT,
    "oga" to MediaEngine.Kind.AUDIO_DIRECT
)

/** Decide whether a request URL is media — extension first, then content-type. */
private fun sniffKind(url: String, type: String): MediaEngine.Kind? {
    val k = MediaEngine.classify(url)
    if (k != MediaEngine.Kind.UNKNOWN) return k
    MediaEngine.extOf(url).let { e -> EXTRA_KIND[e]?.let { return it } }
    val ct = type.substringBefore(';').lowercase()
    return when {
        ct.isEmpty() -> null
        ct.contains("mpegurl") || ct.contains("vnd.apple") || ct.contains("hls") -> MediaEngine.Kind.HLS
        ct.contains("dash+xml") || ct.contains("mpd") -> MediaEngine.Kind.DASH
        ct.contains("video/") || ct.contains("mp2t") -> MediaEngine.Kind.VIDEO_DIRECT
        ct.contains("audio/") || ct.contains("mpeg") || ct.contains("ogg") -> MediaEngine.Kind.AUDIO_DIRECT
        else -> null
    }
}

// ---------------------------------------------------------------------------
// WebView plumbing
// ---------------------------------------------------------------------------

@SuppressLint("SetJavaScriptEnabled")
private fun buildWebView(
    c: android.content.Context,
    onNew: (WebView) -> Unit,
    onSniff: (String) -> Unit,
    onNav: (Boolean, Boolean) -> Unit,
    onUrl: (String) -> Unit,
    isAdBlockOn: () -> Boolean,
    onPageVisited: (String, String) -> Unit
): WebView {
    val wv = WebView(c)
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(wv, true)
    }
    return wv.apply {

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            saveFormData = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowFileAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun onMediaFound(url: String?) {
                if (!url.isNullOrBlank()) {
                    post { onSniff(url) }
                }
            }
        }, "MediaProBridge")

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url
                val uStr = url.toString()

                if (isAdBlockOn() && AdBlocker.isAd(uStr)) {
                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                }

                if (url.scheme == "http" || url.scheme == "https") {
                    onSniff(uStr)
                }
                return null
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (url != null && !url.startsWith("data:")) {
                    onNav(view.canGoBack(), view.canGoForward())
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                url?.let(onUrl)
                onNav(view.canGoBack(), view.canGoForward())

                CookieManager.getInstance().flush()

                if (!url.isNullOrBlank() && !url.startsWith("data:") && url != "about:blank") {
                    val title = view.title?.ifBlank { url } ?: url
                    onPageVisited(title, url)
                }

                if (isAdBlockOn()) {
                    view.evaluateJavascript(AdBlocker.JS_AD_BLOCK, null)
                }

                val js = """
                    (function() {
                        function scan() {
                            var tags = document.querySelectorAll('video, audio, source');
                            for (var i = 0; i < tags.length; i++) {
                                var s = tags[i].src || tags[i].currentSrc;
                                if (s && (s.indexOf('http://') === 0 || s.indexOf('https://') === 0)) {
                                    if (window.MediaProBridge) window.MediaProBridge.onMediaFound(s);
                                }
                            }
                        }
                        scan();
                        if (!window.__mvdObserver) {
                            window.__mvdObserver = new MutationObserver(function() { scan(); });
                            window.__mvdObserver.observe(document.documentElement || document.body, { childList: true, subtree: true });
                        }
                    })();
                """.trimIndent()
                view.evaluateJavascript(js, null)
            }
        }
        onNew(this)
    }
}

// ---------------------------------------------------------------------------
// Media card + dialogs
// ---------------------------------------------------------------------------

@Composable
private fun MediaCard(
    m: FoundMedia,
    recordingJob: JobManager.Ui?,
    onDownload: () -> Unit,
    onQuality: () -> Unit,
    onRecord: () -> Unit,
    onStopRecord: () -> Unit
) {
    val ctx = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        stringResource(kindLabel(m.kind)),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    m.url,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp).weight(1f)
                )
                // Copy the link without downloading it.
                val copyStr = stringResource(R.string.brw_copy)
                TextButton(
                    onClick = {
                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("media-url", m.url))
                        shortToast(ctx, ctx.getString(R.string.dl_copied))
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "$copyStr: ${m.url}"
                    }
                ) {
                    Text(copyStr, maxLines = 1)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                val dlStr = stringResource(R.string.brw_download)
                val qualityStr = stringResource(R.string.brw_quality)
                val stopStr = stringResource(R.string.rec_stop_desc)
                val recStr = stringResource(R.string.brw_record)
                Button(
                    onClick = onDownload,
                    modifier = Modifier.semantics {
                        contentDescription = "$dlStr: ${m.url}"
                    }
                ) {
                    Text(dlStr, maxLines = 1)
                }
                if (m.kind == MediaEngine.Kind.HLS || m.kind == MediaEngine.Kind.DASH) {
                    TextButton(
                        onClick = onQuality,
                        modifier = Modifier.semantics {
                            contentDescription = "$qualityStr: ${m.url}"
                        }
                    ) { Text(qualityStr) }

                    if (recordingJob != null) {
                        Button(
                            onClick = onStopRecord,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.semantics {
                                contentDescription = "$stopStr: ${m.url}"
                            }
                        ) {
                            Text(stringResource(R.string.rec_btn_stop), maxLines = 1)
                        }
                    } else {
                        TextButton(
                            onClick = onRecord,
                            modifier = Modifier.semantics {
                                contentDescription = "$recStr: ${m.url}"
                            }
                        ) { Text(recStr) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtDialog(
    ctx: android.content.Context,
    m: FoundMedia,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val audioEx = mutableListOf<Pair<String, String>>()
    audioEx.add(ctx.getString(R.string.brw_ext_original) to defaultOutExt(m))
    audioEx.add("MP3" to "mp3")
    audioEx.add("M4A" to "m4a")
    val choices = if (m.kind == MediaEngine.Kind.HLS || m.kind == MediaEngine.Kind.DASH) {
        listOf("MP4" to "mp4", "TS" to "ts", "MKV" to "mkv", "MP3" to "mp3", "M4A" to "m4a")
    } else audioEx

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(m.url, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text(stringResource(R.string.ed_format), style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    choices.forEach { (label, ext) ->
                        FilterChip(selected = selected == ext, onClick = { onPick(ext) }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.brw_download)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.brw_rec_confirm_no)) }
        }
    )
}

/** A quality choice: [label] shown to the user, [url] carries how to start it. */
private data class QualityItem(val label: String, val url: String)

@Composable
private fun QualityDialog(
    ctx: android.content.Context,
    target: FoundMedia,
    items: List<QualityItem>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onPickBest: () -> Unit,
    onPick: (QualityItem) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.brw_quality_title)) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                TextButton(onClick = onPickBest, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.brw_best))
                }
                if (busy) {
                    Text(
                        stringResource(R.string.brw_err_parse),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                items.forEach { itm ->
                    TextButton(onClick = { onPick(itm) }, modifier = Modifier.fillMaxWidth()) {
                        Text(itm.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!busy && items.isEmpty()) {
                    Text(
                        stringResource(R.string.brw_err_parse),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.brw_rec_confirm_no)) }
        }
    )
}

// ---------------------------------------------------------------------------
// Quality parsing
// ---------------------------------------------------------------------------

/**
 * Read the quality ladder of a manifest.
 *  - HLS master → one item per variant ("hls:<resolved media playlist URL>")
 *  - DASH      → one item per video representation ("dash:<index>"); the actual
 *                filtered MPD is built at pick time ([buildDashFiltered]).
 * Empty when the URL is a media playlist or parsing failed — UI keeps "best".
 */
private suspend fun loadQualities(masterUrl: String): List<QualityItem> = withContext(Dispatchers.IO) {
    try {
        when (MediaEngine.classify(masterUrl)) {
            MediaEngine.Kind.HLS -> {
                val p = Hls.parse(masterUrl)
                if (p.kind != Hls.PlaylistKind.MASTER) emptyList()
                else p.variants.mapIndexed { _, v ->
                    QualityItem(
                        label = streamLabel(v.resolution, v.bandwidth),
                        url = "hls:" + resolve(masterUrl, v.url)
                    )
                }
            }
            MediaEngine.Kind.DASH -> {
                val mpd = Dash.parse(masterUrl)
                mpd.video.mapIndexed { i, r ->
                    val h = if (r.height > 0) "${r.width}x${r.height}" else "video"
                    QualityItem(label = streamLabel(h, r.bandwidth), url = "dash:$i")
                }
            }
            else -> emptyList()
        }
    } catch (t: Throwable) {
        ErrorLog.e("Browser", "quality parse failed", t)
        emptyList()
    }
}

private fun streamLabel(resolution: String, bw: Long): String {
    val kb = if (bw > 0) " · ${bw / 1000} kbps" else ""
    return (resolution.ifBlank { "audio" }) + kb
}

private fun resolve(base: String, rel: String): String = try {
    URL(URL(base), rel).toString()
} catch (t: Throwable) {
    if (rel.startsWith("http")) rel else base
}

/**
 * Produce a DASH MPD restricted to one video representation + the best audio
 * representation, written to a cache file FFmpeg can ingest. Returns the file
 * path or null on failure.
 */
private suspend fun buildDashFiltered(
    c: android.content.Context,
    masterUrl: String,
    videoIndex: Int
): String? = withContext(Dispatchers.IO) {
    try {
        val mpd = Dash.parse(masterUrl)
        val video = mpd.video.getOrNull(videoIndex) ?: return@withContext null
        val audio = mpd.audio.maxByOrNull { it.bandwidth }
        val text = Http.getString(masterUrl)
        val filtered = Dash.filterMpd(text, video, audio, masterUrl)
        if (filtered.isBlank()) return@withContext null
        val out = File(c.cacheDir, "mvdwork").apply { if (!exists()) mkdirs() }
        val target = File(out, "dash_${System.currentTimeMillis()}.mpd")
        target.writeText(filtered)
        target.absolutePath
    } catch (t: Throwable) {
        ErrorLog.e("Browser", "dash filter failed", t)
        null
    }
}

// ---------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------

private fun shortToast(c: android.content.Context, text: String) {
    android.widget.Toast.makeText(c, text, android.widget.Toast.LENGTH_SHORT).show()
}

private fun normalizeUrl(raw: String): String? {
    var u = raw.trim()
    if (u.isEmpty()) return null
    if (u.startsWith("http://") || u.startsWith("https://")) return u
    if (u.contains(" ") || !u.contains(".")) return null
    return "https://$u"
}

/** Human-readable stem used as the job/file title. */
private fun baseName(url: String): String = try {
    val u = URL(url)
    val last = u.path.substringAfterLast('/', "").substringBeforeLast('.')
    last.ifBlank { u.host.ifBlank { "media" } }
} catch (t: Throwable) {
    "media"
}

private fun kindLabel(kind: MediaEngine.Kind): Int = when (kind) {
    MediaEngine.Kind.HLS -> R.string.brw_kind_hls
    MediaEngine.Kind.DASH -> R.string.brw_kind_dash
    MediaEngine.Kind.VIDEO_DIRECT -> R.string.brw_kind_video
    MediaEngine.Kind.AUDIO_DIRECT -> R.string.brw_kind_audio
    MediaEngine.Kind.UNKNOWN -> R.string.brw_direct
}

/** Lossless-by-copy extension when no output choice is forced. */
private fun defaultOutExt(m: FoundMedia): String = when (m.kind) {
    MediaEngine.Kind.AUDIO_DIRECT -> MediaEngine.extOf(m.url).ifEmpty { "mp3" }
    MediaEngine.Kind.VIDEO_DIRECT -> MediaEngine.extOf(m.url).ifEmpty { "mp4" }
    else -> "mp4"
}

// ---------------------------------------------------------------------------
// Active live recording control
// ---------------------------------------------------------------------------

@Composable
private fun ActiveRecordingsSection(
    recordings: List<JobManager.Ui>,
    onStop: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        recordings.forEach { job ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.rec_active_banner),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            job.record.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val stopRecordStr = stringResource(R.string.rec_stop_desc)
                    Button(
                        onClick = { onStop(job.record.id) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .semantics {
                                contentDescription = "$stopRecordStr: ${job.record.title}"
                            }
                    ) {
                        Text(stringResource(R.string.rec_btn_stop), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Record dialog with time input (HH:MM:SS / min), presets and format picker
// ---------------------------------------------------------------------------

@Composable
private fun RecordDialog(
    ctx: android.content.Context,
    target: FoundMedia,
    onDismiss: () -> Unit,
    onConfirm: (fileName: String, ext: String, durationSec: Long) -> Unit
) {
    var fileName by remember { mutableStateOf(baseName(target.url)) }
    var selectedExt by remember { mutableStateOf("mp4") }
    val formats = listOf("MP4" to "mp4", "TS" to "ts", "MKV" to "mkv", "MP3 (Audio)" to "mp3")

    var selectedDurationSec by remember { mutableStateOf(0L) }
    var timeText by remember { mutableStateOf("") }

    val presets = listOf(
        0L to ctx.getString(R.string.rec_time_unlimited),
        900L to "15 daq",
        1800L to "30 daq",
        3600L to "1 soat",
        7200L to "2 soat"
    )

    fun parseDuration(text: String): Long {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return 0L
        if (trimmed.contains(":")) {
            val parts = trimmed.split(":").mapNotNull { it.toLongOrNull() }
            return when (parts.size) {
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                2 -> parts[0] * 60 + parts[1]
                else -> 0L
            }
        }
        val num = trimmed.toLongOrNull() ?: 0L
        return if (num > 0L) num * 60L else 0L
    }

    fun fmtSec(sec: Long): String {
        if (sec <= 0L) return ""
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rec_dialog_title), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Bo'lim 1: Fayl nomi
                Text(stringResource(R.string.rec_filename), style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Bo'lim 2: Format
                Text(stringResource(R.string.ed_format), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    formats.forEach { (label, ext) ->
                        FilterChip(
                            selected = selectedExt == ext,
                            onClick = { selectedExt = ext },
                            label = { Text(label) }
                        )
                    }
                }

                // Bo'lim 3: Vaqt va Davomiylik (Taymer)
                Text(stringResource(R.string.rec_time_title), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(R.string.rec_time_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                // Preset chips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        presets.take(3).forEach { (sec, label) ->
                            FilterChip(
                                selected = selectedDurationSec == sec,
                                onClick = {
                                    selectedDurationSec = sec
                                    timeText = if (sec > 0L) fmtSec(sec) else ""
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        presets.drop(3).forEach { (sec, label) ->
                            FilterChip(
                                selected = selectedDurationSec == sec,
                                onClick = {
                                    selectedDurationSec = sec
                                    timeText = if (sec > 0L) fmtSec(sec) else ""
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                // Vaqt yozadigan joy
                OutlinedTextField(
                    value = timeText,
                    onValueChange = {
                        timeText = it
                        selectedDurationSec = parseDuration(it)
                    },
                    label = { Text(stringResource(R.string.rec_time_hint)) },
                    placeholder = { Text("00:30:00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // Stepper tugmalari (-5m, +5m, +15m)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            val newSec = (selectedDurationSec - 300L).coerceAtLeast(0L)
                            selectedDurationSec = newSec
                            timeText = if (newSec > 0L) fmtSec(newSec) else ""
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("-5 daq") }
                    OutlinedButton(
                        onClick = {
                            val newSec = selectedDurationSec + 300L
                            selectedDurationSec = newSec
                            timeText = fmtSec(newSec)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("+5 daq") }
                    OutlinedButton(
                        onClick = {
                            val newSec = selectedDurationSec + 900L
                            selectedDurationSec = newSec
                            timeText = fmtSec(newSec)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("+15 daq") }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val dur = if (timeText.isNotBlank()) parseDuration(timeText) else selectedDurationSec
                    onConfirm(fileName, selectedExt, dur)
                }
            ) {
                Text(stringResource(R.string.rec_btn_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.brw_rec_confirm_no))
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Browser history dialog
// ---------------------------------------------------------------------------

@Composable
private fun HistoryDialog(
    ctx: android.content.Context,
    entries: List<HistoryEntry>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.brw_history_title), style = MaterialTheme.typography.titleMedium)
                if (entries.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.brw_history_clear), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.brw_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    entries.forEach { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onOpen(item.url) }
                                ) {
                                    Text(
                                        item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        item.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.timestamp > 0L) {
                                        Text(
                                            fmt.format(Date(item.timestamp)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = { onDelete(item.id) },
                                    modifier = Modifier.semantics {
                                        contentDescription = "${ctx.getString(R.string.brw_delete_item)}: ${item.title}"
                                    }
                                ) {
                                    Text("✕")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}

