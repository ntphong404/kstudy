package vn.edu.actvn.kstudy.util

// Rules of the personal details of an account, kept free of Android so they can be unit tested
object ProfileRules {

    const val DATE_PATTERN = "dd/MM/yyyy"
    const val INVALID_EMAIL = "Email không đúng định dạng"

    val GENDERS = listOf("Nam", "Nữ", "Khác")
    val DEGREES = listOf("Cử nhân", "Kỹ sư", "Thạc sĩ", "Tiến sĩ")

    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+")

    // The email is optional: it may stay empty, anything else has to look like an address
    fun isValidEmail(email: String): Boolean = email.isEmpty() || EMAIL.matches(email)
}
