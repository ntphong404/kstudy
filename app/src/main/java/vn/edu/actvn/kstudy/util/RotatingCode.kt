package vn.edu.actvn.kstudy.util

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// The attendance code shown as a QR code changes every STEP_SECONDS, so a photo of it sent to a
// friend outside the classroom is useless a moment later. It is derived like a TOTP (RFC 6238):
// an HMAC of the current time window, keyed with a secret stored on the session. The teacher's
// and the student's phone therefore compute the same code without exchanging anything.
object RotatingCode {

    const val STEP_SECONDS = 15
    const val CODE_LENGTH = 6

    // 32 symbols = 5 bits each; no I, O, 0, 1 because they are easy to confuse when typed by hand
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val STEP_MILLIS = STEP_SECONDS * 1000L
    private const val ALGORITHM = "HmacSHA256"

    fun newSecret(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun windowOf(timeMillis: Long): Long = timeMillis / STEP_MILLIS

    // Seconds until the code changes: STEP_SECONDS right after a change, down to 1
    fun secondsLeft(timeMillis: Long): Int = STEP_SECONDS - ((timeMillis % STEP_MILLIS) / 1000).toInt()

    // Empty when the session has no secret, so the screen shows nothing instead of crashing
    fun codeAt(secret: String, timeMillis: Long): String =
        if (secret.isEmpty()) "" else codeOfWindow(secret, windowOf(timeMillis))

    // The neighbouring windows are accepted too: the previous one because scanning takes a moment,
    // the next one because the clocks of two phones are never perfectly in sync. A code therefore
    // stops working at most 2 * STEP_SECONDS after it first appeared.
    fun matches(secret: String, code: String, timeMillis: Long): Boolean {
        val entered = code.trim().uppercase()
        if (secret.isEmpty() || entered.length != CODE_LENGTH) return false
        val window = windowOf(timeMillis)
        return (window - 1..window + 1).any { codeOfWindow(secret, it) == entered }
    }

    private fun codeOfWindow(secret: String, window: Long): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), ALGORITHM))
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(window).array())

        // Dynamic truncation as in RFC 4226: the last nibble says which 4 bytes to use
        val offset = hash[hash.size - 1].toInt() and 0x0f
        val bits = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)

        return (0 until CODE_LENGTH)
            .map { ALPHABET[(bits shr (5 * it)) and 0x1f] }
            .joinToString("")
    }
}
