package vn.edu.actvn.kstudy.util

import java.security.SecureRandom
import java.util.Random

// Rules of a password chosen in the app, kept free of Android so they can be unit tested
object PasswordRules {

    const val MIN_LENGTH = 6

    const val TOO_SHORT = "Mật khẩu phải có ít nhất $MIN_LENGTH ký tự"
    const val HAS_WHITESPACE = "Mật khẩu không được chứa khoảng trắng"
    const val SAME_AS_CURRENT = "Mật khẩu mới phải khác mật khẩu hiện tại"
    const val CONFIRMATION_MISMATCH = "Mật khẩu nhập lại không khớp"
    const val WRONG_CURRENT = "Mật khẩu hiện tại không đúng"

    // Null when the password can be used. currentPassword is left out when an admin sets the
    // password of somebody else. The sign-in form trims what is typed, so a password with a
    // space at either end could never be entered again: whitespace is refused altogether.
    fun problemWithNew(newPassword: String, currentPassword: String? = null): String? = when {
        newPassword.any { it.isWhitespace() } -> HAS_WHITESPACE
        newPassword.length < MIN_LENGTH -> TOO_SHORT
        newPassword == currentPassword -> SAME_AS_CURRENT
        else -> null
    }

    fun problemWithConfirmation(newPassword: String, confirmation: String): String? =
        if (newPassword == confirmation) null else CONFIRMATION_MISMATCH

    // Letters and digits that cannot be mistaken for one another when read out or copied from
    // paper: no 0/o, 1/l/i
    private const val ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"
    const val GENERATED_LENGTH = 8

    // A first password the admin hands out. It has to be guessed, unlike the student code it
    // replaces, so it comes from a cryptographic random source; it always mixes letters and digits.
    fun generate(random: Random = SecureRandom()): String {
        while (true) {
            val password = String(CharArray(GENERATED_LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })
            if (password.any { it.isDigit() } && password.any { it.isLetter() }) return password
        }
    }
}
