package uz.komil.mediapro.lic

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Offline license coupon codec.
 *
 * A coupon is a fixed-length, type-friendly token that
 *  1. binds to ONE device id (can't be shared),
 *  2. carries an absolute expiry (epoch minutes), -1 => lifetime,
 *  3. is authenticated with an HMAC-SHA256 truncated signature so it can be
 *     verified with no network and no account.
 *
 * Format: "MVD-" + 21 chars of a 32-symbol alphabet (no 0/O/1/I/L).
 * 13 decoded bytes: [0..3) device fingerprint, [3..7) expiry minutes (uint32
 * big-endian, 0xFFFFFFFF = lifetime), [7..13) first 6 bytes of
 * HMAC-SHA256(SECRET, bytes[0..7)).
 *
 * The SECRET ships inside the APK by design and the same signing code is
 * reused by the developer-side coupon generator (Windows/Android admin
 * tools). That makes this a SOFT licensing gate (an attacker who extracts the
 * constant can mint coupons) — accepted and documented; its real job is
 * stopping casual sharing and enforcing the trial limits.
 */
object CouponCode {

    private const val SECRET = "MediaPro.mvd.2026.coupon.v1.7fKpQ9"
    private val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray()
    private val CHAR_INDEX: Map<Char, Int> = ALPHABET.withIndex().associate { it.value to it.index }

    private const val TOKEN_BYTES = 13
    private const val TOKEN_CHARS = 21 // ceil(104 bits / 5)
    private const val PREFIX = "MVD-"
    const val LIFETIME_MINUTES = 0xFFFFFFFFL

    /** Fingerprint of the device id that a coupon is bound to. */
    private fun deviceFingerprint(deviceId: String): Int {
        val h = java.security.MessageDigest.getInstance("SHA-256")
            .digest(deviceId.trim().uppercase().toByteArray(Charsets.UTF_8))
        return ((h[0].toInt() and 0xFF) shl 16) or ((h[1].toInt() and 0xFF) shl 8) or (h[2].toInt() and 0xFF)
    }

    private fun hmac(payload: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(payload)
    }

    /**
     * Mint a coupon. Intended for the developer-side generator (and internal
     * smoke tests); the app itself only calls [verify].
     */
    fun mint(deviceId: String, expiryEpochMinutes: Long): String {
        require(expiryEpochMinutes == -1L || expiryEpochMinutes in 0..LIFETIME_MINUTES) {
            "bad expiry: $expiryEpochMinutes"
        }
        val exp = if (expiryEpochMinutes == -1L) LIFETIME_MINUTES else expiryEpochMinutes
        val fp = deviceFingerprint(deviceId)

        val body = ByteArray(7)
        body[0] = (fp ushr 16 and 0xFF).toByte()
        body[1] = (fp ushr 8 and 0xFF).toByte()
        body[2] = (fp and 0xFF).toByte()
        body[3] = (exp ushr 24 and 0xFF).toByte()
        body[4] = (exp ushr 16 and 0xFF).toByte()
        body[5] = (exp ushr 8 and 0xFF).toByte()
        body[6] = (exp and 0xFF).toByte()

        val sig = hmac(body).take(6).toByteArray()
        val token = body + sig // 13 bytes
        return PREFIX + encode(token)
    }

    /**
     * Verify a coupon string. Returns expiry in epoch minutes (-1 lifetime)
     * on success, or an [InvalidCoupon] reason.
     */
    fun verify(code: String, deviceId: String): Result {
        val c = code.trim().uppercase()
        if (!c.startsWith(PREFIX)) return Result.Invalid
        val body = c.removePrefix(PREFIX)
        if (body.length != TOKEN_CHARS) return Result.Invalid
        val token = decode(body) ?: return Result.Invalid
        if (token.size != TOKEN_BYTES) return Result.Invalid

        val fp = ((token[0].toInt() and 0xFF) shl 16) or
            ((token[1].toInt() and 0xFF) shl 8) or (token[2].toInt() and 0xFF)
        if (fp != deviceFingerprint(deviceId)) return Result.WrongDevice

        val sig = hmac(token.copyOfRange(0, 7))
        for (i in 0 until 6) {
            if (sig[i] != token[7 + i]) return Result.BadSignature
        }

        val exp = ((token[3].toLong() and 0xFF) shl 24) or
            ((token[4].toLong() and 0xFF) shl 16) or
            ((token[5].toLong() and 0xFF) shl 8) or (token[6].toLong() and 0xFF)
        return Result.Valid(if (exp == LIFETIME_MINUTES) -1L else exp)
    }

    sealed class Result {
        data class Valid(val expiryEpochMin: Long) : Result() // -1 = lifetime
        object WrongDevice : Result()
        object BadSignature : Result()
        object Invalid : Result()
    }

    // --- base32 (32 symbols, unambiguous) ---

    private fun encode(bytes: ByteArray): String {
        val bitBuf = bytes.map { it.toInt() and 0xFF }.flatMap { b ->
            (7 downTo 0).map { (b ushr it) and 1 }
        }
        val padded = (bitBuf.size + 4) / 5 * 5
        val bits = bitBuf + List(padded - bitBuf.size) { 0 }
        val sb = StringBuilder()
        for (i in bits.indices step 5) {
            val v = (0 until 5).fold(0) { acc, k -> (acc shl 1) or bits[i + k] }
            sb.append(ALPHABET[v])
        }
        return sb.toString()
    }

    private fun decode(s: String): ByteArray? {
        val out = ArrayList<Boolean>()
        for (ch in s) {
            val idx = CHAR_INDEX[ch] ?: return null
            out.addAll((4 downTo 0).map { (idx ushr it) and 1 == 1 })
        }
        // trim to whole bytes
        val usable = out.size - (out.size % 8)
        if (usable / 8 != TOKEN_BYTES) return null
        val bytes = ByteArray(usable / 8)
        for (b in bytes.indices) {
            var v = 0
            for (k in 0 until 8) v = (v shl 1) or (if (out[b * 8 + k]) 1 else 0)
            bytes[b] = v.toByte()
        }
        return bytes
    }
}
