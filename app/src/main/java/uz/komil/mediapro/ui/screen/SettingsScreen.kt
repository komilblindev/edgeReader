package uz.komil.mediapro.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.komil.mediapro.R
import uz.komil.mediapro.data.AppPrefs
import uz.komil.mediapro.data.ErrorLog
import uz.komil.mediapro.dl.WorkDir
import uz.komil.mediapro.lic.LicenseManager
import uz.komil.mediapro.sched.ScheduleManager
import uz.komil.mediapro.sched.SchedulePlan
import uz.komil.mediapro.sched.ScheduleRepeatType
import uz.komil.mediapro.ui.MainActivity
import uz.komil.mediapro.util.Lang
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Settings hub: UI language (uz / ru / en / system), the offline license card
 * (device ID, trial counters, coupon entry), the error journal (view / share /
 * clear) and contact/version footer.
 */
@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lic by LicenseManager.state.collectAsState()
    val maxResults by AppPrefs.maxResultsFlow(ctx).collectAsState(initial = 1)
    val plans by ScheduleManager.plans.collectAsState()

    var showErrors by rememberSaveable { mutableStateOf(false) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    // Refresh counters + license while this screen is open
    LaunchedEffect(Unit) {
        scope.launch { LicenseManager.refresh(ctx) }
    }

    fun msg(text: String) {
        Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        SectionTitle(stringResource(R.string.set_lang))
        Card {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                val current = Lang.tag
                LangOptions(current) { tag ->
                    if (tag != current) MainActivity.changeLanguage(tag)
                }
            }
        }

        SectionTitle(stringResource(R.string.set_detection_title))
        DetectionCard(maxResults = maxResults) { chosen ->
            scope.launch { AppPrefs.setMaxResults(ctx, chosen) }
        }

        SectionTitle(stringResource(R.string.set_sched_title))
        ScheduledRecordingsCard(
            plans = plans,
            onToggle = { id -> scope.launch { ScheduleManager.togglePlan(id) } },
            onDelete = { id -> scope.launch { ScheduleManager.deletePlan(id) } },
            onAddClick = { showAddDialog = true }
        )

        SectionTitle(stringResource(R.string.set_license))
        LicenseCard(lic)

        SectionTitle(stringResource(R.string.set_errors))
        ErrorCard(
            hasErrors = ErrorLog.sizeBytes() > 0,
            onView = { showErrors = true },
            onShare = {
                val text = ErrorLog.read()
                if (text.isBlank()) {
                    msg(ctx.getString(R.string.set_no_errors))
                } else {
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text.take(200_000))
                    }
                    runCatching {
                        ctx.startActivity(Intent.createChooser(i, ctx.getString(R.string.set_share)))
                    }.onFailure { ErrorLog.e("Settings", "share errors failed", it) }
                }
            },
            onClear = {
                ErrorLog.clear()
                msg(ctx.getString(R.string.set_log_cleared))
            }
        )

        SectionTitle(stringResource(R.string.set_cache_title))
        var cacheBytes by rememberSaveable { mutableLongStateOf(WorkDir.cacheSizeBytes(ctx)) }
        CacheCard(
            cacheSize = cacheBytes,
            onClear = {
                WorkDir.clearAll(ctx)
                cacheBytes = 0L
                msg(ctx.getString(R.string.set_cache_cleared))
            }
        )

        SectionTitle(stringResource(R.string.set_about))
        Card {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Text(stringResource(R.string.set_version), style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { openTelegram(ctx, "@komil_hamzayev") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Telegram: @komil_hamzayev", maxLines = 1)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { openEmail(ctx, "hamzayevkomil52@gmail.com") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Email: hamzayevkomil52@gmail.com", maxLines = 1)
                    }
                }
            }
        }
    }

    if (showErrors) {
        val text = ErrorLog.read().ifBlank { ctx.getString(R.string.set_no_errors) }
        AlertDialog(
            onDismissRequest = { showErrors = false },
            title = { Text(stringResource(R.string.set_errors)) },
            text = {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { showErrors = false }) { Text("OK") }
            }
        )
    }

    if (showAddDialog) {
        AddScheduleDialog(
            onDismiss = { showAddDialog = false },
            onSave = { newPlan ->
                scope.launch { ScheduleManager.addPlan(newPlan) }
                showAddDialog = false
                msg(ctx.getString(R.string.set_sched_save))
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun LangOptions(current: String, onPick: (String) -> Unit) {
    val systemLabel = stringResource(R.string.set_lang_system)
    val options = listOf(
        "" to systemLabel,
        "uz" to "O'zbekcha",
        "ru" to "Русский",
        "en" to "English"
    )
    Column {
        options.forEach { (tag, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = current == tag,
                        role = Role.RadioButton,
                        onClick = { onPick(tag) }
                    )
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(selected = current == tag, onClick = null)
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

@Composable
private fun DetectionCard(maxResults: Int, onPick: (Int) -> Unit) {
    val options = listOf(
        1 to stringResource(R.string.set_results_count, 1),
        3 to stringResource(R.string.set_results_count, 3),
        5 to stringResource(R.string.set_results_count, 5),
        10 to stringResource(R.string.set_results_count, 10),
        0 to stringResource(R.string.set_results_all)
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(
                stringResource(R.string.set_max_results_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            options.forEach { (count, label) ->
                val isSelected = maxResults == count
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onPick(count) }
                        )
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = null
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LicenseCard(lic: LicenseManager.UiState) {
    val ctx = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            RowLabel(stringResource(R.string.set_dev_id), lic.deviceId)
            Text(
                statusText(ctx, lic),
                style = MaterialTheme.typography.titleSmall,
                color = if (lic.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            if (!lic.unlocked) {
                Text(
                    stringResource(R.string.set_rec_left, mmss(lic.trialRecordingLeftSecs)),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(R.string.set_dl_left, lic.trialDownloadsLeft),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(R.string.set_edits_left, lic.trialEditsLeft),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (lic.lastError.isNotBlank()) {
                Text(
                    lic.lastError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun RowLabel(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp).weight(1f)
        )
    }
}

private fun statusText(ctx: android.content.Context, lic: LicenseManager.UiState): String =
    when {
        lic.lifetime -> ctx.getString(R.string.set_status_lifetime)
        lic.unlocked -> ctx.getString(R.string.set_status_until, fmtDate(lic.expiresEpochMs))
        else -> ctx.getString(R.string.set_status_free)
    }

@Composable
private fun ErrorCard(hasErrors: Boolean, onView: () -> Unit, onShare: () -> Unit, onClear: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(
                if (hasErrors) "" else stringResource(R.string.set_no_errors),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = onView, enabled = hasErrors) {
                    Text(stringResource(R.string.set_view))
                }
                OutlinedButton(onClick = onShare) { Text(stringResource(R.string.set_share)) }
                OutlinedButton(onClick = onClear, enabled = hasErrors) {
                    Text(stringResource(R.string.set_clear))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Formatting helpers
// ---------------------------------------------------------------------------

/** mm:ss from whole seconds (used for the 15-minute recording budget). */
private fun mmss(totalSecs: Long): String {
    val s = totalSecs.coerceAtLeast(0)
    val m = s / 60
    val r = s % 60
    return String.format(Locale.US, "%d:%02d", m, r)
}

private fun fmtDate(epochMs: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date(epochMs))

private fun openTelegram(ctx: android.content.Context, handle: String) {
    val url = "https://t.me/" + handle.removePrefix("@")
    val i = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
    runCatching { ctx.startActivity(i) }
        .onFailure { ErrorLog.e("Settings", "open telegram failed", it) }
}

private fun openEmail(ctx: android.content.Context, email: String) {
    val i = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:$email")).apply {
        putExtra(Intent.EXTRA_SUBJECT, "MediaPro")
    }
    runCatching { ctx.startActivity(i) }
        .onFailure { ErrorLog.e("Settings", "open email failed", it) }
}

@Composable
private fun ScheduledRecordingsCard(
    plans: List<SchedulePlan>,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAddClick: () -> Unit
) {
    val ctx = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(
                stringResource(R.string.set_sched_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (!ScheduleManager.canScheduleExactAlarms(ctx)) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text(
                            "⚠️ Aniq vaqtda yozib olish uchun 'Budilniklar va eslatmalar' ruxsati kerak",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        OutlinedButton(
                            onClick = { ScheduleManager.openExactAlarmSettings(ctx) },
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text("Ruxsatni yoqish (Sozlamalar)")
                        }
                    }
                }
            }

            if (plans.isEmpty()) {
                Text(
                    stringResource(R.string.set_sched_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                plans.forEach { plan ->
                    val repeatLabel = when (plan.repeatType) {
                        ScheduleRepeatType.DAILY_7 -> stringResource(R.string.set_sched_type_7)
                        ScheduleRepeatType.WORKDAYS_5 -> stringResource(R.string.set_sched_type_5)
                        ScheduleRepeatType.ONCE_DATE -> "${stringResource(R.string.set_sched_type_date)}: ${plan.specificDate}"
                    }
                    val statusDesc = if (plan.enabled) stringResource(R.string.set_sched_active) else stringResource(R.string.set_sched_inactive)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .semantics {
                                contentDescription = "${plan.title}, $repeatLabel, ${plan.timeOfDay}, ${plan.durationMin} daqiqa, ${plan.format.uppercase()}, $statusDesc"
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    plan.title.ifBlank { plan.url },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "$repeatLabel | ${plan.timeOfDay} (${plan.durationMin} min) | ${plan.format.uppercase()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    plan.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Switch(
                                checked = plan.enabled,
                                onCheckedChange = { onToggle(plan.id) },
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .semantics {
                                        contentDescription = "${plan.title} - $statusDesc"
                                    }
                            )
                            IconButton(
                                onClick = { onDelete(plan.id) },
                                modifier = Modifier.semantics {
                                    contentDescription = "O'chirish: ${plan.title}"
                                }
                            ) {
                                Text("✕", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            val addText = stringResource(R.string.set_sched_add)
            OutlinedButton(
                onClick = onAddClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .semantics {
                        contentDescription = addText
                    }
            ) {
                Text("+ $addText")
            }
        }
    }
}

@Composable
private fun ScheduleCombobox(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    isReadOnly: Boolean = true,
    contentDesc: String = label
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val displayLabel = options.find { it.second == value }?.first ?: value

    Box(modifier = modifier) {
        OutlinedTextField(
            value = if (isReadOnly) displayLabel else value,
            onValueChange = { if (!isReadOnly) onSelect(it) },
            readOnly = isReadOnly,
            label = { Text(label) },
            trailingIcon = {
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.semantics {
                        this.contentDescription = "$contentDesc spiskasi"
                    }
                ) {
                    Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.bodyMedium)
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    this.contentDescription = "$label: $displayLabel"
                }
        )

        if (isReadOnly) {
            Surface(
                color = androidx.compose.ui.graphics.Color.Transparent,
                modifier = Modifier
                    .matchParentSize()
                    .clickable { expanded = !expanded }
            ) {}
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 280.dp)
        ) {
            options.forEach { (title, optVal) ->
                val isSelected = optVal == value
                DropdownMenuItem(
                    text = {
                        Text(
                            title,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelect(optVal)
                        expanded = false
                    },
                    modifier = Modifier.semantics {
                        this.contentDescription = title
                    }
                )
            }
        }
    }
}

@Composable
private fun AddScheduleDialog(
    onDismiss: () -> Unit,
    onSave: (SchedulePlan) -> Unit
) {
    val ctx = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf("mp4") }
    var repeatType by rememberSaveable { mutableStateOf(ScheduleRepeatType.DAILY_7) }

    val todayDate = remember {
        SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Calendar.getInstance().time)
    }
    var specificDate by rememberSaveable { mutableStateOf(todayDate) }

    var timeOfDay by rememberSaveable { mutableStateOf("20:00") }
    var hour by rememberSaveable { mutableStateOf("20") }
    var minute by rememberSaveable { mutableStateOf("00") }

    var durationMin by rememberSaveable { mutableStateOf("60") }

    val hourOptions = remember {
        (0..23).map {
            val h = String.format(Locale.US, "%02d", it)
            "$h:00" to h
        }
    }

    val minuteOptions = remember {
        listOf(
            "00 min" to "00",
            "05 min" to "05",
            "10 min" to "10",
            "15 min" to "15",
            "20 min" to "20",
            "25 min" to "25",
            "30 min" to "30",
            "35 min" to "35",
            "40 min" to "40",
            "45 min" to "45",
            "50 min" to "50",
            "55 min" to "55"
        )
    }

    val durOptions = remember {
        listOf(
            "15 min" to "15",
            "30 min" to "30",
            "45 min" to "45",
            "60 min (1 soat)" to "60",
            "90 min (1.5 soat)" to "90",
            "120 min (2 soat)" to "120",
            "180 min (3 soat)" to "180",
            "240 min (4 soat)" to "240",
            "300 min (5 soat)" to "300"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_sched_add)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.set_sched_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.set_sched_url_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    stringResource(R.string.set_sched_format),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("mp4", "mkv", "mp3").forEach { fmt ->
                        FilterChip(
                            selected = format == fmt,
                            onClick = { format = fmt },
                            label = { Text(fmt.uppercase()) },
                            modifier = Modifier.semantics {
                                contentDescription = "Format ${fmt.uppercase()}"
                            }
                        )
                    }
                }

                Text(
                    stringResource(R.string.set_sched_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Column {
                    val types = listOf(
                        ScheduleRepeatType.DAILY_7 to stringResource(R.string.set_sched_type_7),
                        ScheduleRepeatType.WORKDAYS_5 to stringResource(R.string.set_sched_type_5),
                        ScheduleRepeatType.ONCE_DATE to stringResource(R.string.set_sched_type_date)
                    )
                    types.forEach { (type, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = repeatType == type,
                                    role = Role.RadioButton,
                                    onClick = { repeatType = type }
                                )
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(selected = repeatType == type, onClick = null)
                            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }

                if (repeatType == ScheduleRepeatType.ONCE_DATE) {
                    val dateOptions = remember {
                        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.US)
                        val list = mutableListOf<Pair<String, String>>()
                        val cal = Calendar.getInstance()
                        val d0 = sdf.format(cal.time)
                        list.add(ctx.getString(R.string.set_sched_today) + " ($d0)" to d0)
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                        val d1 = sdf.format(cal.time)
                        list.add(ctx.getString(R.string.set_sched_tomorrow) + " ($d1)" to d1)
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                        val d2 = sdf.format(cal.time)
                        list.add("+2 kun ($d2)" to d2)
                        cal.add(Calendar.DAY_OF_YEAR, 5)
                        val d7 = sdf.format(cal.time)
                        list.add("+1 hafta ($d7)" to d7)
                        list
                    }

                    Text(
                        stringResource(R.string.set_sched_date_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )

                    ScheduleCombobox(
                        label = stringResource(R.string.set_sched_date_hint),
                        value = specificDate,
                        options = dateOptions,
                        onSelect = { specificDate = it },
                        isReadOnly = false,
                        modifier = Modifier.fillMaxWidth(),
                        contentDesc = stringResource(R.string.set_sched_date_hint)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        dateOptions.forEach { (label, dt) ->
                            FilterChip(
                                selected = specificDate == dt,
                                onClick = { specificDate = dt },
                                label = { Text(label) },
                                modifier = Modifier.semantics {
                                    contentDescription = label
                                }
                            )
                        }
                    }
                }

                // ------------------ Boshlanish vaqti (Spiskadan tanlash) ------------------
                Text(
                    stringResource(R.string.set_sched_time_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )

                // Soat va Daqiqa komboboxlari (spiska)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScheduleCombobox(
                        label = stringResource(R.string.set_sched_hour),
                        value = hour,
                        options = hourOptions,
                        onSelect = { h ->
                            hour = h
                            timeOfDay = "$h:$minute"
                        },
                        modifier = Modifier.weight(1f),
                        contentDesc = stringResource(R.string.set_sched_hour)
                    )

                    ScheduleCombobox(
                        label = stringResource(R.string.set_sched_minute),
                        value = minute,
                        options = minuteOptions,
                        onSelect = { m ->
                            minute = m
                            timeOfDay = "$hour:$m"
                        },
                        modifier = Modifier.weight(1f),
                        contentDesc = stringResource(R.string.set_sched_minute)
                    )
                }

                // Tezkor vaqtlar (Preset chips)
                Text(
                    stringResource(R.string.set_sched_time_presets),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val quickTimes = listOf("06:00", "08:00", "10:00", "12:00", "15:00", "18:00", "20:00", "21:00", "22:00", "23:00")
                    quickTimes.forEach { qt ->
                        FilterChip(
                            selected = timeOfDay == qt,
                            onClick = {
                                timeOfDay = qt
                                val parts = qt.split(":")
                                hour = parts.getOrElse(0) { "20" }
                                minute = parts.getOrElse(1) { "00" }
                            },
                            label = { Text(qt) },
                            modifier = Modifier.semantics {
                                contentDescription = "Vaqt $qt"
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = timeOfDay,
                    onValueChange = {
                        timeOfDay = it
                        val parts = it.split(":")
                        if (parts.size == 2 && parts[0].length == 2 && parts[1].length == 2) {
                            hour = parts[0]
                            minute = parts[1]
                        }
                    },
                    label = { Text(stringResource(R.string.set_sched_time_hint)) },
                    placeholder = { Text("20:00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // ------------------ Davomiyligi (Spiskadan tanlash) ------------------
                Text(
                    stringResource(R.string.set_sched_dur_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )

                ScheduleCombobox(
                    label = stringResource(R.string.set_sched_dur_hint),
                    value = durationMin,
                    options = durOptions,
                    onSelect = { durationMin = it },
                    isReadOnly = false,
                    modifier = Modifier.fillMaxWidth(),
                    contentDesc = stringResource(R.string.set_sched_dur_hint)
                )

                Text(
                    stringResource(R.string.set_sched_dur_presets),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val quickDurs = listOf(
                        "15 min" to "15",
                        "30 min" to "30",
                        "45 min" to "45",
                        "1 soat" to "60",
                        "1.5 soat" to "90",
                        "2 soat" to "120",
                        "3 soat" to "180"
                    )
                    quickDurs.forEach { (label, minVal) ->
                        FilterChip(
                            selected = durationMin == minVal,
                            onClick = { durationMin = minVal },
                            label = { Text(label) },
                            modifier = Modifier.semantics {
                                contentDescription = "$label ($minVal daqiqa)"
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (url.isBlank()) {
                        Toast.makeText(ctx, ctx.getString(R.string.set_sched_url_hint), Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    val plan = SchedulePlan(
                        id = java.util.UUID.randomUUID().toString(),
                        title = title.ifBlank { "Live Rec " + timeOfDay },
                        url = url.trim(),
                        format = format,
                        repeatType = repeatType,
                        specificDate = specificDate.trim(),
                        timeOfDay = timeOfDay.trim().ifBlank { "20:00" },
                        durationMin = durationMin.toIntOrNull() ?: 60,
                        enabled = true,
                        requestCode = kotlin.random.Random.nextInt(1, 1_000_000_000)
                    )
                    if (repeatType == ScheduleRepeatType.ONCE_DATE) {
                        val triggerMs = ScheduleManager.calculateNextTriggerEpochMs(plan)
                        if (triggerMs == null) {
                            Toast.makeText(ctx, "O'tgan sana yoki vaqt kiritildi. Iltimos kelgusi vaqtni tanlang!", Toast.LENGTH_LONG).show()
                            return@TextButton
                        }
                    }
                    onSave(plan)
                }
            ) {
                Text(stringResource(R.string.set_sched_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
private fun CacheCard(cacheSize: Long, onClear: () -> Unit) {
    val sizeText = formatFileSize(cacheSize)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(
                stringResource(R.string.set_cache_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val cacheLabel = stringResource(R.string.set_cache_size, sizeText)
                val clearLabel = stringResource(R.string.set_cache_clean_btn)
                Text(
                    cacheLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics {
                        contentDescription = cacheLabel
                    }
                )
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.semantics {
                        contentDescription = "$clearLabel, $sizeText"
                    }
                ) {
                    Text(clearLabel)
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

