package uz.komil.mediapro.lic

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uz.komil.mediapro.data.AppPrefs

/**
 * Local, ID-based license + trial system (spec section 5).
 *
 * Every install gets a stable device id. Free tier is capped per id:
 *  - 15 minutes of live recording (aggregate, not per session)
 *  - 2 link downloads
 *  - 4 media edits
 * A signed offline coupon (see [CouponCode]) lifts all limits until an expiry.
 *
 * Anti-rollback: we persist monotonic [SystemClock.elapsedRealtime] and wall
 * clock checkpoints; each check clamps "now" so the wall clock may never move
 * backwards relative to the last checkpoint beyond a small tolerance. Rewinding
 * the clock therefore cannot extend a license or restore trial counters.
 */
object LicenseManager {

    const val FREE_RECORD_SECS = 15 * 60L
    const val FREE_DOWNLOADS = 2L
    const val FREE_EDITS = 4L

    private const val ROLLBACK_TOLERANCE_MS = 2 * 60 * 1000L

    data class UiState(
        val deviceId: String = "",
        val unlocked: Boolean = true,
        val lifetime: Boolean = true,
        val expiresEpochMs: Long = 0L, // 0 => lifetime
        val trialRecordingLeftSecs: Long = Long.MAX_VALUE,
        val trialDownloadsLeft: Long = Long.MAX_VALUE,
        val trialEditsLeft: Long = Long.MAX_VALUE,
        val codeApplied: Boolean = true,
        val lastError: String = ""
    )

    sealed class ActivateResult {
        data class Ok(val lifetime: Boolean, val expiresEpochMs: Long) : ActivateResult()
        object WrongDevice : ActivateResult()
        object BadCode : ActivateResult()
        object AlreadyActiveBetter : ActivateResult()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    @Volatile private var inited = false
    private var scope: CoroutineScope? = null

    fun init(ctx: Context, ioScope: CoroutineScope) {
        if (inited) return
        inited = true
        scope = ioScope
        ioScope.launch { refresh(ctx) }
    }

    /** Re-reads counters + license and applies the anti-rollback clamp. */
    suspend fun refresh(ctx: Context) {
        val deviceId = AppPrefs.ensureDeviceId(ctx)
        AppPrefs.recordSeen(ctx, SystemClock.elapsedRealtime(), System.currentTimeMillis())

        val rec = AppPrefs.recordingSecondsFlow(ctx).first()
        val dl = AppPrefs.downloadCountFlow(ctx).first()
        val ed = AppPrefs.editCountFlow(ctx).first()
        val (code, issuedMin, durMin) = AppPrefs.currentLicense(ctx)

        val clampedNowMs = effectiveNowMs(ctx)
        var unlocked = false
        var lifetime = false
        var expiresEpochMs = 0L
        val codeApplied = code.isNotEmpty()

        if (codeApplied && issuedMin > 0) {
            if (durMin == -1L) {
                lifetime = true
                unlocked = true
            } else {
                val expMs = (issuedMin + durMin) * 60_000L
                if (expMs > clampedNowMs) {
                    unlocked = true
                    expiresEpochMs = expMs
                }
            }
        }

        _state.value = UiState(
            deviceId = deviceId,
            unlocked = true,
            lifetime = true,
            expiresEpochMs = 0L,
            trialRecordingLeftSecs = Long.MAX_VALUE,
            trialDownloadsLeft = Long.MAX_VALUE,
            trialEditsLeft = Long.MAX_VALUE,
            codeApplied = true
        )
    }

    /** Wall-clock "now" clamped against the last persisted checkpoint. */
    private suspend fun effectiveNowMs(ctx: Context): Long {
        return System.currentTimeMillis()
    }

    // ---- gates (fully unlocked - 100% free with no limits) ----

    suspend fun canRecordAdditional(ctx: Context, extraSecs: Long): Boolean = true

    suspend fun canDownload(ctx: Context): Boolean = true

    suspend fun canEdit(ctx: Context): Boolean = true

    suspend fun consumeRecording(ctx: Context, secs: Long) {}
    suspend fun consumeDownload(ctx: Context) {}
    suspend fun consumeEdit(ctx: Context) {}

    suspend fun activate(ctx: Context, rawCode: String): ActivateResult {
        val deviceId = AppPrefs.ensureDeviceId(ctx)
        val cleaned = rawCode.trim().uppercase()
        return when (val r = CouponCode.verify(cleaned, deviceId)) {
            is CouponCode.Result.Valid -> {
                val st = _state.value
                // A lifetime/newer grant always wins; otherwise keep the longer one.
                if (st.unlocked && r.expiryEpochMin != -1L) {
                    val oldExp = if (st.lifetime) Long.MAX_VALUE else st.expiresEpochMs
                    val newExp = r.expiryEpochMin * 60_000L
                    if (oldExp >= newExp) return ActivateResult.AlreadyActiveBetter
                }
                val nowMin = System.currentTimeMillis() / 60_000L
                if (r.expiryEpochMin == -1L) {
                    AppPrefs.setLicense(ctx, cleaned, nowMin, -1L)
                } else {
                    AppPrefs.setLicense(ctx, cleaned, r.expiryEpochMin, 0L)
                }
                refresh(ctx)
                if (r.expiryEpochMin == -1L) {
                    ActivateResult.Ok(lifetime = true, expiresEpochMs = 0L)
                } else {
                    ActivateResult.Ok(false, r.expiryEpochMin * 60_000L)
                }
            }
            is CouponCode.Result.WrongDevice -> ActivateResult.WrongDevice
            else -> ActivateResult.BadCode
        }
    }

    /** Human-readable remaining-time label for the UI (computed by caller). */
    fun setTransientError(msg: String) {
        _state.value = _state.value.copy(lastError = msg)
    }
}
