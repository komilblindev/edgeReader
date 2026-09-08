package uz.komil.mediapro.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// One DataStore for the whole app (top-level property delegate is required).
private val Context.mpDataStore by preferencesDataStore(name = "mediapro_prefs")

/**
 * All persistent app state. MediaPro has no account and no cloud sync, so this
 * local DataStore is the single source of truth:
 *  - UI language ("uz"/"ru"/"en", empty = follow system)
 *  - one-time unique device id (MVD-XXXXXXXX)
 *  - trial counters + license fields (the offline license system)
 */
object AppPrefs {

    private val KEY_LANGUAGE = stringPreferencesKey("ui_lang")
    private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
    private val KEY_REC_SECS = longPreferencesKey("trial_rec_secs")
    private val KEY_DL_COUNT = longPreferencesKey("trial_dl_count")
    private val KEY_EDIT_COUNT = longPreferencesKey("trial_edit_count")
    private val KEY_LIC_CODE = stringPreferencesKey("lic_code")
    private val KEY_LIC_ISSUED = longPreferencesKey("lic_issued_epoch_min")
    private val KEY_LIC_DURATION = longPreferencesKey("lic_duration_min") // -1 = lifetime
    private val KEY_LAST_SEEN_ELAPSED = longPreferencesKey("last_seen_elapsed_ms")
    private val KEY_LAST_SEEN_WALL = longPreferencesKey("last_seen_wall_ms")

    private fun data(ctx: Context): Flow<Preferences> =
        ctx.mpDataStore.data.catch { emit(emptyPreferences()) }

    // --- language ---
    fun languageFlow(ctx: Context): Flow<String> =
        data(ctx).map { it[KEY_LANGUAGE] ?: "" }
    suspend fun currentLanguage(ctx: Context): String = languageFlow(ctx).first()
    suspend fun setLanguage(ctx: Context, tag: String) {
        ctx.mpDataStore.edit { it[KEY_LANGUAGE] = tag }
    }

    // --- max detected media results (1, 3, 5, 10, 0=all) ---
    private val KEY_MAX_RESULTS = intPreferencesKey("max_media_results")
    fun maxResultsFlow(ctx: Context): Flow<Int> =
        data(ctx).map { it[KEY_MAX_RESULTS] ?: 0 }
    suspend fun currentMaxResults(ctx: Context): Int = maxResultsFlow(ctx).first()
    suspend fun setMaxResults(ctx: Context, count: Int) {
        ctx.mpDataStore.edit { it[KEY_MAX_RESULTS] = count }
    }

    // --- last active tab ---
    private val KEY_LAST_TAB = stringPreferencesKey("last_active_tab")
    fun lastTabFlow(ctx: Context): Flow<String> = data(ctx).map { it[KEY_LAST_TAB] ?: "" }
    suspend fun setLastTab(ctx: Context, tabName: String) {
        ctx.mpDataStore.edit { it[KEY_LAST_TAB] = tabName }
    }

    // --- device id ---
    fun deviceIdFlow(ctx: Context): Flow<String> = data(ctx).map { it[KEY_DEVICE_ID] ?: "" }
    suspend fun currentDeviceId(ctx: Context): String = deviceIdFlow(ctx).first()
    suspend fun ensureDeviceId(ctx: Context): String {
        deviceIdFlow(ctx).first().let { if (it.isNotEmpty()) return it }
        val fresh = "MVD-" + randomCode(8)
        ctx.mpDataStore.edit { p -> p[KEY_DEVICE_ID] = fresh }
        return fresh
    }

    // --- trial counters ---
    fun recordingSecondsFlow(ctx: Context): Flow<Long> = data(ctx).map { it[KEY_REC_SECS] ?: 0L }
    fun downloadCountFlow(ctx: Context): Flow<Long> = data(ctx).map { it[KEY_DL_COUNT] ?: 0L }
    fun editCountFlow(ctx: Context): Flow<Long> = data(ctx).map { it[KEY_EDIT_COUNT] ?: 0L }

    suspend fun addRecordingSeconds(ctx: Context, secs: Long) {
        ctx.mpDataStore.edit { p -> p[KEY_REC_SECS] = (p[KEY_REC_SECS] ?: 0L) + secs }
    }
    suspend fun addDownload(ctx: Context) {
        ctx.mpDataStore.edit { p -> p[KEY_DL_COUNT] = (p[KEY_DL_COUNT] ?: 0L) + 1 }
    }
    suspend fun addEdit(ctx: Context) {
        ctx.mpDataStore.edit { p -> p[KEY_EDIT_COUNT] = (p[KEY_EDIT_COUNT] ?: 0L) + 1 }
    }

    // --- license ---
    fun licenseCodeFlow(ctx: Context): Flow<String> = data(ctx).map { it[KEY_LIC_CODE] ?: "" }
    fun licenseIssuedFlow(ctx: Context): Flow<Long> = data(ctx).map { it[KEY_LIC_ISSUED] ?: 0L }
    fun licenseDurationFlow(ctx: Context): Flow<Long> = data(ctx).map { it[KEY_LIC_DURATION] ?: 0L }

    suspend fun currentLicense(ctx: Context): Triple<String, Long, Long> = Triple(
        licenseCodeFlow(ctx).first(),
        licenseIssuedFlow(ctx).first(),
        licenseDurationFlow(ctx).first()
    )

    suspend fun setLicense(ctx: Context, code: String, issuedEpochMin: Long, durationMin: Long) {
        ctx.mpDataStore.edit { p ->
            p[KEY_LIC_CODE] = code
            p[KEY_LIC_ISSUED] = issuedEpochMin
            p[KEY_LIC_DURATION] = durationMin
        }
    }

    // --- anti-rollback checkpoints ---
    suspend fun recordSeen(ctx: Context, elapsedMs: Long, wallMs: Long) {
        ctx.mpDataStore.edit { p ->
            p[KEY_LAST_SEEN_ELAPSED] = elapsedMs
            p[KEY_LAST_SEEN_WALL] = wallMs
        }
    }
    suspend fun readLastSeen(ctx: Context): Pair<Long, Long> =
        data(ctx).first().let {
            (it[KEY_LAST_SEEN_ELAPSED] ?: 0L) to (it[KEY_LAST_SEEN_WALL] ?: 0L)
        }

    /** 8 readable alphanumeric chars, no ambiguous 0/O/1/I. */
    private fun randomCode(len: Int): String {
        val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        return buildString { repeat(len) { append(alphabet.random()) } }
    }
}
